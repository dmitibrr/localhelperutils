package com.dmitibrr.localhelperutils.client;

import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StorageTaskExecutor {
    public enum OpType { NONE, SCAN, COMBINE, SORT, CATEGORIZE }

    private enum Phase { PICK_NEXT, WAIT_NEAR, WAIT_OPEN, WORKING, CLOSING, DONE }

    private static final class Step {
        final String type; // READ, EXTRACT, MERGE, SORT
        final String item;
        final int count;
        Step(String type, String item, int count) {
            this.type = type;
            this.item = item;
            this.count = count;
        }
    }

    private static final class Task {
        final String key;
        final List<Step> steps;
        Task(String key, List<Step> steps) {
            this.key = key;
            this.steps = steps;
        }
    }

    private static final class MoveOp {
        final String from, to, item;
        final int count;
        MoveOp(String from, String to, String item, int count) {
            this.from = from;
            this.to = to;
            this.item = item;
            this.count = count;
        }
    }

    private static final StorageTaskExecutor INSTANCE = new StorageTaskExecutor();

    private OpType opType = OpType.NONE;
    private Phase phase = Phase.PICK_NEXT;
    private boolean aborted = false;
    private String status = "";
    private String lastMessage = "";

    private final List<Task> tasks = new ArrayList<>();
    private int taskIndex = -1;
    private Task currentTask = null;
    private int stepIndex = 0;
    private int tickDelay = 0;
    private int openTimeout = 0;
    private String expectOpenKey = null;

    private final Deque<Integer> quickMoveQueue = new ArrayDeque<>();
    private final Deque<int[]> pickupQueue = new ArrayDeque<>();
    private int[] activeSeq = null;
    private int activeIdx = 0;
    private boolean verifyPending = false;
    private String verifyKind = null;

    private String bufferedItem = null;
    private int bufferedExpected = 0;

    private long startedAt = 0;

    private StorageTaskExecutor() {}

    public static StorageTaskExecutor get() {
        return INSTANCE;
    }

    public boolean isIdle() {
        return opType == OpType.NONE;
    }

    public OpType opType() {
        return opType;
    }

    public String status() {
        return status;
    }

    public String currentKey() {
        return currentTask == null ? null : currentTask.key;
    }

    public boolean isActive() {
        return !isIdle() && !aborted;
    }

    // ---------------------------------------------------------------- start

    public void startScan(Minecraft mc) {
        List<String> keys = targetKeys(mc, false);
        if (keys.isEmpty()) {
            message(mc, ModLang.fmt("exec.no_containers"));
            return;
        }
        List<Task> t = new ArrayList<>();
        for (String k : keys) {
            t.add(new Task(k, List.of(new Step("READ", null, 0))));
        }
        begin(mc, OpType.SCAN, t, "Сканирование сундуковъ");
    }

    public void startCombine(Minecraft mc) {
        List<String> keys = targetKeys(mc, false);
        if (keys.isEmpty()) {
            message(mc, ModLang.fmt("exec.no_containers"));
            return;
        }
        List<MoveOp> ops = planCombine(mc, keys);
        if (ops.isEmpty()) {
            message(mc, ModLang.fmt("exec.combine_none"));
            return;
        }
        List<Task> t = new ArrayList<>();
        for (MoveOp op : ops) {
            t.add(new Task(op.from, List.of(new Step("EXTRACT", op.item, op.count))));
            t.add(new Task(op.to, List.of(new Step("MERGE", op.item, op.count))));
        }
        begin(mc, OpType.COMBINE, t, "Совмѣщеніе стаковъ");
    }

    public void startSort(Minecraft mc) {
        List<String> keys = targetKeys(mc, false);
        if (keys.isEmpty()) {
            message(mc, ModLang.fmt("exec.no_containers"));
            return;
        }
        List<Task> t = new ArrayList<>();
        for (String k : keys) {
            t.add(new Task(k, List.of(new Step("SORT", null, 0))));
        }
        begin(mc, OpType.SORT, t, "Сортировка по " + ModConfig.get().sortMode);
    }

    public void startCategorize(Minecraft mc) {
        List<String> keys = targetKeys(mc, false);
        if (keys.isEmpty()) {
            message(mc, ModLang.fmt("exec.no_containers"));
            return;
        }
        List<MoveOp> ops = planCategorize(mc, keys);
        if (ops.isEmpty()) {
            message(mc, ModLang.fmt("exec.categ_done"));
            return;
        }
        List<Task> t = new ArrayList<>();
        for (MoveOp op : ops) {
            t.add(new Task(op.from, List.of(new Step("EXTRACT", op.item, op.count))));
            t.add(new Task(op.to, List.of(new Step("MERGE", op.item, op.count))));
        }
        begin(mc, OpType.CATEGORIZE, t, "Раскладка по модамъ");
    }

    private void begin(Minecraft mc, OpType type, List<Task> tasks, String what) {
        this.opType = type;
        this.tasks.clear();
        this.tasks.addAll(tasks);
        this.taskIndex = -1;
        this.currentTask = null;
        this.stepIndex = 0;
        this.tickDelay = 0;
        this.openTimeout = 0;
        this.expectOpenKey = null;
        this.clickQueuesReset();
        this.verifyPending = false;
        this.verifyKind = null;
        this.bufferedItem = null;
        this.bufferedExpected = 0;
        this.aborted = false;
        this.startedAt = System.currentTimeMillis();
        this.phase = Phase.PICK_NEXT;
        message(mc, ModLang.fmt("exec.started", what, tasks.size()));
    }

    public void stop() {
        aborted = true;
        opType = OpType.NONE;
        tasks.clear();
        currentTask = null;
        phase = Phase.PICK_NEXT;
        clickQueuesReset();
        status = "";
    }

    /** Прерывание из меню: закрываем и контейнер, если мод его открыл. */
    public void stop(Minecraft mc) {
        boolean wasActive = isActive();
        stop();
        if (wasActive && mc != null && mc.player != null
                && mc.screen instanceof AbstractContainerScreen<?>) {
            mc.player.closeContainer();
            tickDelay = 10;
        }
    }

    private void clickQueuesReset() {
        quickMoveQueue.clear();
        pickupQueue.clear();
        activeSeq = null;
        activeIdx = 0;
    }

    // ---------------------------------------------------------------- tick

    public void tick(Minecraft mc) {
        if (opType == OpType.NONE) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (aborted) {
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof AbstractContainerScreen<?>)) {
            return; // ждём, пока игрок закроет чужой экран
        }
        if (tickDelay > 0) {
            tickDelay--;
            return;
        }
        switch (phase) {
            case PICK_NEXT -> pickNext(mc);
            case WAIT_NEAR -> waitNear(mc);
            case WAIT_OPEN -> waitOpen(mc);
            case WORKING -> working(mc);
            case CLOSING -> closing(mc);
            case DONE -> finish(mc);
        }
    }

    private void pickNext(Minecraft mc) {
        while (taskIndex + 1 < tasks.size()) {
            taskIndex++;
            currentTask = tasks.get(taskIndex);
            if (inCurrentDim(currentTask.key)) {
                stepIndex = 0;
                expectOpenKey = currentTask.key;
                phase = Phase.WAIT_NEAR;
                updateStatus(mc);
                return;
            }
        }
        phase = Phase.DONE;
    }

    private void waitNear(Minecraft mc) {
        BlockPos pos = ContainerKey.parsePos(currentTask.key);
        if (pos == null) {
            advance(mc, ModLang.fmt("exec.skip_unknown"));
            return;
        }
        double d = distTo(mc, pos);
        if (mc.screen == null && d <= 16.0) {
            openTimeout = 0;
            status = ModLang.fmt("exec.opening");
            openContainer(mc, pos);
            phase = Phase.WAIT_OPEN;
        } else {
            status = ModLang.fmt("exec.approach", (int) Math.sqrt(d));
        }
    }

    private void waitOpen(Minecraft mc) {
        openTimeout++;
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            tickDelay = 5; // ждём загрузки слотов
            phase = Phase.WORKING;
            status = ModLang.fmt("exec.working");
        } else if (openTimeout > 80) {
            advance(mc, ModLang.fmt("exec.open_fail"));
        }
    }

    private void working(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            phase = Phase.WAIT_NEAR;
            status = ModLang.fmt("exec.screen_closed");
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (stepIndex >= currentTask.steps.size()) {
            mc.player.closeContainer();
            tickDelay = 6;
            phase = Phase.CLOSING;
            status = ModLang.fmt("exec.closing");
            return;
        }

        boolean busy = activeSeq != null || !pickupQueue.isEmpty() || !quickMoveQueue.isEmpty();

        if (busy) {
            if (activeSeq == null && !menu.getCarried().isEmpty()) {
                return; // ждём, пока курсор опустеет перед новой последовательностью
            }
            performClick(mc, menu);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            return; // последний клик ещё не подтверждён
        }
        if (verifyPending) {
            verifyStep(mc, menu);
            return;
        }

        Step s = currentTask.steps.get(stepIndex);
        switch (s.type) {
            case "READ" -> {
                doRead(mc, menu);
                stepIndex++;
            }
            case "EXTRACT" -> {
                buildExtract(menu, s);
                if (quickMoveQueue.isEmpty()) {
                    advance(mc, ModLang.fmt("exec.extract_fail"));
                } else {
                    bufferedItem = s.item;
                    bufferedExpected = s.count;
                    verifyPending = true;
                    verifyKind = "EXTRACT";
                }
            }
            case "MERGE" -> {
                buildMerge(menu, s);
                if (pickupQueue.isEmpty() && activeSeq == null) {
                    message(mc, ModLang.fmt("exec.merge_no_place", s.item));
                    stepIndex++;
                } else {
                    bufferedItem = s.item;
                    bufferedExpected = s.count;
                    verifyPending = true;
                    verifyKind = "MERGE";
                }
            }
            case "SORT" -> {
                buildSort(menu);
                if (pickupQueue.isEmpty() && activeSeq == null) {
                    stepIndex++;
                } else {
                    verifyPending = true;
                    verifyKind = "SORT";
                }
            }
        }
    }

    private void performClick(Minecraft mc, AbstractContainerMenu menu) {
        if (activeSeq != null) {
            if (activeIdx < activeSeq.length) {
                menu.clicked(activeSeq[activeIdx++], 0, ClickType.PICKUP, mc.player);
            }
            if (activeIdx >= activeSeq.length) {
                activeSeq = null;
                activeIdx = 0;
            }
            return;
        }
        if (!pickupQueue.isEmpty()) {
            activeSeq = pickupQueue.poll();
            activeIdx = 0;
            menu.clicked(activeSeq[activeIdx++], 0, ClickType.PICKUP, mc.player);
            if (activeIdx >= activeSeq.length) {
                activeSeq = null;
                activeIdx = 0;
            }
            return;
        }
        if (!quickMoveQueue.isEmpty()) {
            menu.clicked(quickMoveQueue.poll(), 0, ClickType.QUICK_MOVE, mc.player);
        }
    }

    private void verifyStep(Minecraft mc, AbstractContainerMenu menu) {
        verifyPending = false;
        String kind = verifyKind;
        verifyKind = null;
        switch (kind) {
            case "EXTRACT" -> {
                int invCount = countInInventory(mc, menu, bufferedItem);
                if (invCount > 0) {
                    stepIndex++;
                } else {
                    advance(mc, ModLang.fmt("exec.extract_fail_inv", bufferedItem));
                }
            }
            case "MERGE" -> {
                int invCount = countInInventory(mc, menu, bufferedItem);
                if (invCount == 0) {
                    stepIndex++;
                } else {
                    message(mc, ModLang.fmt("exec.merge_leftover", invCount, bufferedItem));
                    stepIndex++;
                }
            }
            case "SORT" -> {
                stepIndex++;
            }
        }
    }

    private void closing(Minecraft mc) {
        if (mc.screen == null) {
            advance(mc, null);
        }
    }

    private void finish(Minecraft mc) {
        long ms = System.currentTimeMillis() - startedAt;
        StorageDB.get().save();
        String msg = ModLang.fmt("exec.done", ms / 1000);
        message(mc, msg);
        stop();
    }

    private void advance(Minecraft mc, String note) {
        if (note != null && !note.isEmpty()) {
            message(mc, note);
        }
        taskIndex++;
        if (taskIndex >= tasks.size()) {
            phase = Phase.DONE;
        } else {
            currentTask = tasks.get(taskIndex);
            stepIndex = 0;
            expectOpenKey = currentTask.key;
            clickQueuesReset();
            verifyPending = false;
            verifyKind = null;
            if (inCurrentDim(currentTask.key)) {
                phase = Phase.WAIT_NEAR;
            } else {
                advance(mc, ModLang.fmt("exec.other_dim"));
            }
        }
    }

    private void updateStatus(Minecraft mc) {
        BlockPos pos = ContainerKey.parsePos(currentTask.key);
        status = currentTask.key.substring(currentTask.key.indexOf('|') + 1)
                + " (" + (int) Math.sqrt(pos == null ? 0 : distTo(mc, pos)) + " м)";
    }

    // ---------------------------------------------------------------- ops

    private void doRead(Minecraft mc, AbstractContainerMenu menu) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (Slot s : containerSlots(menu)) {
            ItemStack st = s.getItem();
            if (!st.isEmpty()) {
                String id = itemId(st);
                items.merge(id, st.getCount(), Integer::sum);
            }
        }
        StorageDB.Entry e = entryFor(mc, currentTask.key);
        if (e != null) {
            StorageDB.get().updateItems(e, items);
        }
    }

    private void buildExtract(AbstractContainerMenu menu, Step s) {
        List<Slot> slots = slotsHolding(menu, s.item);
        int remaining = s.count;
        for (Slot sl : slots) {
            if (remaining <= 0) break;
            ItemStack st = sl.getItem();
            if (st.isEmpty()) continue;
            quickMoveQueue.add(sl.index);
            remaining -= st.getCount();
        }
    }

    private void buildMerge(AbstractContainerMenu menu, Step s) {
        List<Slot> buffered = playerSlotsHolding(menu, s.item);
        Slot target = partialSlot(menu, s.item);
        Slot empty = emptyContainerSlot(menu);
        for (Slot b : buffered) {
            ItemStack st = b.getItem();
            if (st.isEmpty() || !itemId(st).equals(s.item)) continue;
            if (target != null) {
                pickupQueue.add(new int[]{b.index, target.index});
            } else if (empty != null) {
                pickupQueue.add(new int[]{b.index, empty.index});
                empty = emptyContainerSlot(menu);
            } else {
                break;
            }
        }
    }

    private void buildSort(AbstractContainerMenu menu) {
        List<Slot> cont = containerSlots(menu);
        List<ItemStack> items = new ArrayList<>();
        for (Slot s : cont) items.add(s.getItem());
        String mode = ModConfig.get().sortMode;
        int n = items.size();
        for (int i = 0; i < n; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                if (items.get(j).isEmpty()) continue;
                if (items.get(minIdx).isEmpty() || compare(items.get(j), items.get(minIdx), mode) < 0) {
                    minIdx = j;
                }
            }
            if (minIdx != i) {
                int a = cont.get(minIdx).index;
                int b = cont.get(i).index;
                if (a != b) {
                    pickupQueue.add(new int[]{a, b, a});
                    ItemStack tmp = items.get(i);
                    items.set(i, items.get(minIdx));
                    items.set(minIdx, tmp);
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private StorageDB.Entry entryFor(Minecraft mc, String key) {
        if (key == null) return null;
        StorageDB.Entry e = StorageDB.get().getEntry(key);
        if (e != null) return e;
        BlockPos pos = ContainerKey.parsePos(key);
        if (pos == null) return null;
        String dim = mc.level.dimension().location().toString();
        String label = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
        return StorageDB.get().blockEntry(dim, new int[]{pos.getX(), pos.getY(), pos.getZ()}, label);
    }

    private boolean inCurrentDim(String key) {
        if (key == null) return false;
        String dim = key.split("\\|", 2)[0];
        return dim.equals(Minecraft.getInstance().level.dimension().location().toString());
    }

    private double distTo(Minecraft mc, BlockPos pos) {
        BlockPos pp = mc.player.blockPosition();
        double dx = pp.getX() + 0.5 - pos.getX() - 0.5;
        double dy = pp.getY() + 0.5 - pos.getY() - 0.5;
        double dz = pp.getZ() + 0.5 - pos.getZ() - 0.5;
        return dx * dx + dy * dy + dz * dz;
    }

    private List<String> targetKeys(Minecraft mc, boolean dbOnly) {
        Set<String> keys = new LinkedHashSet<>();
        if (!dbOnly && !HelperState.selected.isEmpty()) {
            keys.addAll(HelperState.selected);
        }
        if (keys.isEmpty()) {
            keys.addAll(StorageDB.get().entries().keySet());
        }
        List<String> list = new ArrayList<>(keys);
        list.removeIf(k -> !inCurrentDim(k));
        list.sort((a, b) -> {
            BlockPos pa = ContainerKey.parsePos(a);
            BlockPos pb = ContainerKey.parsePos(b);
            double da = pa == null ? Double.MAX_VALUE : distTo(mc, pa);
            double db = pb == null ? Double.MAX_VALUE : distTo(mc, pb);
            return Double.compare(da, db);
        });
        return list;
    }

    private void openContainer(Minecraft mc, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        try {
            PlacementHelper.SYNTHETIC_USE = true;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        } finally {
            PlacementHelper.SYNTHETIC_USE = false;
        }
    }

    private List<Slot> containerSlots(AbstractContainerMenu menu) {
        List<Slot> res = new ArrayList<>();
        if (menu == null) return res;
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (Slot s : menu.slots) {
            if (s.container != inv) {
                res.add(s);
            }
        }
        return res;
    }

    private List<Slot> slotsHolding(AbstractContainerMenu menu, String item) {
        List<Slot> res = new ArrayList<>();
        for (Slot s : containerSlots(menu)) {
            ItemStack st = s.getItem();
            if (!st.isEmpty() && itemId(st).equals(item)) res.add(s);
        }
        return res;
    }

    private List<Slot> playerSlotsHolding(AbstractContainerMenu menu, String item) {
        List<Slot> res = new ArrayList<>();
        if (menu == null) return res;
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (Slot s : menu.slots) {
            if (s.container == inv) {
                ItemStack st = s.getItem();
                if (!st.isEmpty() && itemId(st).equals(item)) res.add(s);
            }
        }
        return res;
    }

    private Slot partialSlot(AbstractContainerMenu menu, String item) {
        for (Slot s : containerSlots(menu)) {
            ItemStack st = s.getItem();
            if (!st.isEmpty() && itemId(st).equals(item) && st.getCount() < st.getMaxStackSize()) {
                return s;
            }
        }
        return null;
    }

    private Slot emptyContainerSlot(AbstractContainerMenu menu) {
        for (Slot s : containerSlots(menu)) {
            if (s.getItem().isEmpty()) return s;
        }
        return null;
    }

    private int countInInventory(Minecraft mc, AbstractContainerMenu menu, String item) {
        int count = 0;
        if (menu == null) return 0;
        for (Slot s : menu.slots) {
            if (s.container == mc.player.getInventory()) {
                ItemStack st = s.getItem();
                if (!st.isEmpty() && itemId(st).equals(item)) count += st.getCount();
            }
        }
        return count;
    }

    private static String itemId(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private static int compare(ItemStack a, ItemStack b, String mode) {
        int c = switch (mode) {
            case "mod" -> {
                String na = BuiltInRegistries.ITEM.getKey(a.getItem()).getNamespace();
                String nb = BuiltInRegistries.ITEM.getKey(b.getItem()).getNamespace();
                yield na.compareToIgnoreCase(nb);
            }
            case "tag" -> {
                String ta = firstTag(a), tb = firstTag(b);
                yield ta.compareToIgnoreCase(tb);
            }
            default -> 0;
        };
        if (c != 0) return c;
        String na = BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
        String nb = BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
        c = na.compareToIgnoreCase(nb);
        if (c != 0) return c;
        return a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
    }

    private static String firstTag(ItemStack st) {
        var tags = st.getItem().builtInRegistryHolder().tags();
        return tags.map(t -> t.location().toString())
                .sorted()
                .findFirst()
                .orElse("");
    }

    // ---------------------------------------------------------------- planning

    private List<MoveOp> planCombine(Minecraft mc, List<String> keys) {
        // item -> list of (key, count) partial stacks
        Map<String, List<Object[]>> byItem = new LinkedHashMap<>();
        for (String k : keys) {
            StorageDB.Entry e = StorageDB.get().getEntry(k);
            if (e == null) continue;
            for (Map.Entry<String, Integer> it : e.items.entrySet()) {
                int max = maxStackOf(it.getKey());
                int count = it.getValue();
                if (count <= 0 || count % max == 0) continue; // только неполные стаки
                byItem.computeIfAbsent(it.getKey(), x -> new ArrayList<>()).add(new Object[]{k, count});
            }
        }
        List<MoveOp> ops = new ArrayList<>();
        for (Map.Entry<String, List<Object[]>> entry : byItem.entrySet()) {
            String item = entry.getKey();
            List<Object[]> lst = entry.getValue();
            if (lst.size() < 2) continue;
            lst.sort((x, y) -> Integer.compare((Integer) y[1], (Integer) x[1]));
            int max = maxStackOf(item);
            String target = null;
            int targetCount = 0;
            for (Object[] pair : lst) {
                String k = (String) pair[0];
                int count = (Integer) pair[1];
                if (target == null) {
                    target = k;
                    targetCount = count;
                } else if (!k.equals(target) && targetCount + count <= max) {
                    ops.add(new MoveOp(k, target, item, count));
                    targetCount += count;
                }
            }
        }
        return ops;
    }

    private List<MoveOp> planCategorize(Minecraft mc, List<String> keys) {
        // home per mod = container with most items of that mod, else first container
        Map<String, String> homeByMod = new LinkedHashMap<>();
        for (String k : keys) {
            StorageDB.Entry e = StorageDB.get().getEntry(k);
            if (e == null) continue;
            Map<String, Integer> perMod = new LinkedHashMap<>();
            for (String item : e.items.keySet()) {
                String mod = item.split(":", 2)[0];
                perMod.merge(mod, e.items.get(item), Integer::sum);
            }
            for (Map.Entry<String, Integer> m : perMod.entrySet()) {
                String cur = homeByMod.get(m.getKey());
                if (cur == null) {
                    homeByMod.put(m.getKey(), k);
                }
            }
        }
        List<MoveOp> ops = new ArrayList<>();
        for (String k : keys) {
            StorageDB.Entry e = StorageDB.get().getEntry(k);
            if (e == null) continue;
            for (String item : e.items.keySet()) {
                String mod = item.split(":", 2)[0];
                String home = homeByMod.get(mod);
                if (home == null || home.equals(k)) continue;
                int count = e.items.get(item);
                ops.add(new MoveOp(k, home, item, count));
            }
        }
        return ops;
    }

    private int maxStackOf(String itemId) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null) return 64;
        var item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return 64;
        return item.getDefaultInstance().getMaxStackSize();
    }

    private void message(Minecraft mc, String msg) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(ModLang.fmt("helper.prefix") + " " + msg), false);
        LocalHelperUtils.LOGGER.info("[localhelperutils] {}", msg);
    }
}