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
        final String type;      // READ | EXTRACT_BATCH | MERGE_BATCH | SORT
        final List<String> items;
        Step(String type, List<String> items) {
            this.type = type;
            this.items = items == null ? List.of() : items;
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

    private static final int CLICK_GAP = 2;          // тиков между кликами
    private static final int MAX_SORT_SWAPS_FACTOR = 4;

    private OpType opType = OpType.NONE;
    private Phase phase = Phase.PICK_NEXT;
    private boolean aborted = false;
    private String status = "";

    private final Deque<Task> taskQueue = new ArrayDeque<>();
    private Task currentTask = null;
    private int stepIndex = 0;
    private int tickDelay = 0;
    private int openTimeout = 0;
    private int clickGap = 0;

    private final Deque<Integer> quickMoveQueue = new ArrayDeque<>();
    private final Deque<int[]> pickupQueue = new ArrayDeque<>();
    private int[] activeSeq = null;
    private int activeIdx = 0;
    private boolean verifyPending = false;
    private String verifyKind = null;

    // совмещение: отложенные операции и буфер
    private final Deque<MoveOp> pendingOps = new ArrayDeque<>();
    private final Map<String, Integer> bufferExpected = new LinkedHashMap<>(); // key -> кол-во ожидаемых стаков

    // сортировка: живой цикл
    private int sortSwaps = 0;
    private int sortCap = 0;

    private String what = "";
    private long startedAt = 0;

    private StorageTaskExecutor() {}

    public static StorageTaskExecutor get() { return INSTANCE; }

    public boolean isIdle() { return opType == OpType.NONE; }
    public OpType opType() { return opType; }
    public String status() { return status; }
    public String currentKey() { return currentTask == null ? null : currentTask.key; }
    public boolean isActive() { return !isIdle() && !aborted; }

    // ---------------------------------------------------------------- старт

    public void startScan(Minecraft mc) {
        List<String> keys = targetKeys(mc);
        if (keys.isEmpty()) { message(mc, ModLang.fmt("exec.no_containers")); return; }
        List<Task> t = new ArrayList<>();
        for (String k : keys) t.add(new Task(k, List.of(new Step("READ", null))));
        begin(mc, OpType.SCAN, t, ModLang.fmt("op.scan"));
    }

    public void startCombine(Minecraft mc) {
        List<String> keys = targetKeys(mc);
        if (keys.isEmpty()) { message(mc, ModLang.fmt("exec.no_containers")); return; }
        List<MoveOp> ops = planPartialMerges(keys, true);
        if (ops.isEmpty()) { message(mc, ModLang.fmt("exec.combine_none")); return; }
        pendingOps.clear();
        pendingOps.addAll(ops);
        bufferExpected.clear();
        begin(mc, OpType.COMBINE, new ArrayList<>(), ModLang.fmt("op.combine"));
        scheduleNextRound(mc);
    }

    public void startSort(Minecraft mc) {
        List<String> keys = targetKeys(mc);
        if (keys.isEmpty()) { message(mc, ModLang.fmt("exec.no_containers")); return; }
        List<Task> t = new ArrayList<>();
        for (String k : keys) t.add(new Task(k, List.of(new Step("SORT", null))));
        begin(mc, OpType.SORT, t, ModLang.fmt("op.sort") + " " + ModConfig.get().sortMode);
    }

    public void startCategorize(Minecraft mc) {
        List<String> keys = targetKeys(mc);
        if (keys.isEmpty()) { message(mc, ModLang.fmt("exec.no_containers")); return; }
        List<MoveOp> ops = planCategorize(keys);
        if (ops.isEmpty()) { message(mc, ModLang.fmt("exec.categ_done")); return; }
        pendingOps.clear();
        pendingOps.addAll(ops);
        bufferExpected.clear();
        begin(mc, OpType.CATEGORIZE, new ArrayList<>(), ModLang.fmt("op.categorize"));
        scheduleNextRound(mc);
    }

    private void begin(Minecraft mc, OpType type, List<Task> tasks, String what) {
        this.opType = type;
        this.what = what;
        this.taskQueue.clear();
        this.taskQueue.addAll(tasks);
        this.currentTask = null;
        this.stepIndex = 0;
        this.tickDelay = 0;
        this.openTimeout = 0;
        this.clickGap = 0;
        resetClickState();
        this.verifyPending = false;
        this.verifyKind = null;
        this.sortSwaps = 0;
        this.sortCap = 0;
        this.aborted = false;
        this.startedAt = System.currentTimeMillis();
        this.phase = Phase.PICK_NEXT;
        message(mc, ModLang.fmt("exec.started", what,
                Math.max(tasks.size(), 1) + (type == OpType.COMBINE || type == OpType.CATEGORIZE
                        ? "+" + pendingOps.size() : "")));
    }

    public void stop() {
        aborted = true;
        opType = OpType.NONE;
        taskQueue.clear();
        pendingOps.clear();
        bufferExpected.clear();
        currentTask = null;
        phase = Phase.PICK_NEXT;
        resetClickState();
        status = "";
    }

    /** Прерывание из меню: закрываем и контейнер, если мод его открыл. */
    public void stop(Minecraft mc) {
        boolean wasActive = isActive();
        stop();
        if (wasActive && mc != null && mc.player != null
                && mc.screen instanceof AbstractContainerScreen<?>) {
            mc.player.closeContainer();
        }
    }

    private void resetClickState() {
        quickMoveQueue.clear();
        pickupQueue.clear();
        activeSeq = null;
        activeIdx = 0;
        clickGap = 0;
    }

    // ---------------------------------------------------------------- тик

    public void tick(Minecraft mc) {
        if (opType == OpType.NONE || aborted) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.screen != null && !(mc.screen instanceof AbstractContainerScreen<?>)) return;
        if (tickDelay > 0) { tickDelay--; return; }

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
        Task t = taskQueue.poll();
        if (t == null) { phase = Phase.DONE; return; }
        if (!inCurrentDim(t.key)) {
            message(mc, ModLang.fmt("exec.other_dim"));
            return; // остаёмся в PICK_NEXT на след. тик
        }
        currentTask = t;
        stepIndex = 0;
        sortSwaps = 0;
        sortCap = 0;
        phase = Phase.WAIT_NEAR;
        BlockPos pos = ContainerKey.parsePos(t.key);
        status = t.key.substring(t.key.indexOf('|') + 1)
                + " (" + (pos == null ? "?" : (int) Math.sqrt(distTo(mc, pos)) + " м") + ")";
    }

    private void waitNear(Minecraft mc) {
        BlockPos pos = ContainerKey.parsePos(currentTask.key);
        if (pos == null) { advance(mc, ModLang.fmt("exec.skip_unknown")); return; }
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
            tickDelay = 5;
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
            closeCurrent(mc);
            return;
        }

        boolean busy = activeSeq != null || !pickupQueue.isEmpty() || !quickMoveQueue.isEmpty();

        if (busy) {
            if (clickGap > 0) { clickGap--; return; }
            if (activeSeq == null && !menu.getCarried().isEmpty()) return;
            performClick(mc, menu);
            clickGap = CLICK_GAP;
            return;
        }
        if (!menu.getCarried().isEmpty()) return; // сервер ещё подтверждает
        if (clickGap > 0) { clickGap--; return; }
        if (verifyPending) { verifyStep(mc, menu); return; }

        Step s = currentTask.steps.get(stepIndex);
        switch (s.type) {
            case "READ" -> { doRead(mc, menu); stepIndex++; }
            case "EXTRACT_BATCH" -> {
                buildExtractBatch(mc, menu, s);
                if (quickMoveQueue.isEmpty()) {
                    advance(mc, ModLang.fmt("exec.extract_fail"));
                } else {
                    verifyPending = true;
                    verifyKind = "EXTRACT";
                }
            }
            case "MERGE_BATCH" -> {
                buildMergeBatch(menu, s);
                if (pickupQueue.isEmpty() && activeSeq == null) {
                    finishBufferTask(mc);
                } else {
                    verifyPending = true;
                    verifyKind = "MERGE";
                }
            }
            case "SORT" -> {
                if (!planOneSortSwap(menu)) {
                    stepIndex++; // уже отсортировано или лимит
                }
                // иначе: триплет в очереди; после его выполнения управление вернётся сюда
            }
        }
    }

    private void verifyStep(Minecraft mc, AbstractContainerMenu menu) {
        verifyPending = false;
        String kind = verifyKind;
        verifyKind = null;
        if ("EXTRACT".equals(kind)) {
            stepIndex++;
        } else if ("MERGE".equals(kind)) {
            stepIndex++;
        }
    }

    private void closeCurrent(Minecraft mc) {
        mc.player.closeContainer();
        tickDelay = 6;
        phase = Phase.CLOSING;
        status = ModLang.fmt("exec.closing");
    }

    private void closing(Minecraft mc) {
        if (mc.screen == null) afterTask(mc);
    }

    /** Что делать после закрытия контейнера текущей задачи. */
    private void afterTask(Minecraft mc) {
        if (currentTask != null && !currentTask.steps.isEmpty()
                && "EXTRACT_BATCH".equals(currentTask.steps.get(0).type)
                && opType != OpType.NONE) {
            flushExtractedToTargets(mc); // ставим задачи доставки
        }
        currentTask = null;
        if (!taskQueue.isEmpty()) {
            phase = Phase.PICK_NEXT;
            return;
        }
        if ((opType == OpType.COMBINE || opType == OpType.CATEGORIZE) && !pendingOps.isEmpty()) {
            scheduleNextRound(mc);
            return;
        }
        phase = Phase.DONE;
    }

    private void finish(Minecraft mc) {
        long ms = System.currentTimeMillis() - startedAt;
        StorageDB.get().save();
        StringBuilder sb = new StringBuilder(ModLang.fmt("exec.done", ms / 1000));
        int leftover = bufferExpected.values().stream().mapToInt(Integer::intValue).sum();
        if (leftover > 0) sb.append(" ").append(ModLang.fmt("exec.buffer_leftover", leftover));
        message(mc, sb.toString());
        stop();
    }

    private void advance(Minecraft mc, String note) {
        if (note != null && !note.isEmpty()) message(mc, note);
        closeCurrent(mc);
        currentTask = null; // после закрытия просто пойдём дальше по очереди
        if (taskQueue.isEmpty() && pendingOps.isEmpty()) phase = Phase.DONE;
        else phase = Phase.CLOSING;
    }

    // ---------------------------------------------------------------- раунды совмещения

    private void scheduleNextRound(Minecraft mc) {
        if (pendingOps.isEmpty()) { phase = Phase.DONE; return; }
        // ближайший источник среди отложенных
        String src = null;
        double best = Double.MAX_VALUE;
        Set<String> sources = new LinkedHashSet<>();
        for (MoveOp op : pendingOps) sources.add(op.from);
        for (String k : sources) {
            BlockPos p = ContainerKey.parsePos(k);
            if (p == null || !inCurrentDim(k)) continue;
            double d = distTo(mc, p);
            if (d < best) { best = d; src = k; }
        }
        if (src == null) { phase = Phase.DONE; return; }
        List<Step> steps = List.of(new Step("EXTRACT_BATCH", List.of()));
        taskQueue.addFirst(new Task(src, steps));
        phase = Phase.PICK_NEXT;
    }

    /** Извлекаем из источника всё, что влезает в свободные слоты инвентаря. */
    private void buildExtractBatch(Minecraft mc, AbstractContainerMenu menu, Step s) {
        String src = currentTask.key;
        Inventory inv = mc.player.getInventory();
        int free = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) free++;
        int cap = Math.max(0, free - 1); // держим 1 слот запаса

        List<MoveOp> taken = new ArrayList<>();
        for (MoveOp op : pendingOps) {
            if (!op.from.equals(src)) continue;
            if (cap <= 0) break;
            List<Slot> slots = slotsHolding(menu, op.item);
            int used = 0;
            for (Slot sl : slots) {
                if (sl.getItem().isEmpty()) continue;
                quickMoveQueue.add(sl.index);
                used++;
                if (used >= cap) break;
            }
            if (used > 0) {
                cap -= used;
                taken.add(op);
                bufferExpected.merge(op.item, 1, Integer::sum);
            }
        }
        pendingOps.removeAll(taken);
        drainedJustNow.addAll(taken);
    }

    /** После закрытия источника раскидываем доставку по целям. */
    private void flushExtractedToTargets(Minecraft mc) {
        Map<String, List<String>> byTarget = new LinkedHashMap<>();
        for (MoveOp op : drainedJustNow) byTarget.computeIfAbsent(op.to, x -> new ArrayList<>()).add(op.item);
        drainedJustNow.clear();
        List<Task> delivery = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byTarget.entrySet()) {
            delivery.add(new Task(e.getKey(), List.of(new Step("MERGE_BATCH", e.getValue()))));
        }
        taskQueue.addAll(delivery);
    }

    private final List<MoveOp> drainedJustNow = new ArrayList<>();

    /** Сливаем весь буфер в целевой контейнер. */
    private void buildMergeBatch(AbstractContainerMenu menu, Step s) {
        Deque<Integer> freeSlots = new ArrayDeque<>();
        for (Slot sl : containerSlots(menu)) {
            if (sl.getItem().isEmpty()) freeSlots.add(sl.index);
        }
        for (String key : s.items) {
            Slot partial = partialSlot(menu, key);
            for (Slot b : playerSlotsHolding(menu, key)) {
                ItemStack st = b.getItem();
                if (st.isEmpty() || !itemId(st).equals(key)) continue;
                Integer dump = freeSlots.poll();
                if (partial != null) {
                    int[] seq = new int[]{b.index, partial.index};
                    if (dump != null) seq = append(seq, dump);
                    pickupQueue.add(seq);
                } else if (dump != null) {
                    pickupQueue.add(new int[]{b.index, dump});
                } else {
                    break; // совсем некуда класть
                }
            }
        }
    }

    private static int[] append(int[] seq, int extra) {
        int[] out = new int[seq.length + 1];
        System.arraycopy(seq, 0, out, 0, seq.length);
        out[seq.length] = extra;
        return out;
    }

    private void finishBufferTask(Minecraft mc) {
        message(mc, ModLang.fmt("exec.merge_no_place_all"));
        stepIndex++;
    }

    // ---------------------------------------------------------------- сортировка (живая)

    /**
     * Планирует ОДИН обмен из живого состояния меню. Возвращает false, когда всё отсортировано.
     * Каждая итерация перечитывает слоты, поэтому рассинхрон/чужие клики не ломают цепочку.
     */
    private boolean planOneSortSwap(AbstractContainerMenu menu) {
        List<Slot> cont = containerSlots(menu);
        int n = cont.size();
        if (n < 2) return false;
        if (sortSwaps == 0) sortCap = n * MAX_SORT_SWAPS_FACTOR;

        int k = -1, j = -1;
        for (int i = 0; i < n; i++) {
            int min = i;
            for (int m = i + 1; m < n; m++) {
                ItemStack a = cont.get(m).getItem(), b = cont.get(min).getItem();
                if (!a.isEmpty() && (b.isEmpty() || compare(a, b, ModConfig.get().sortMode) < 0)) min = m;
            }
            if (min != i) { k = i; j = min; break; }
        }
        if (k < 0) return false;

        sortSwaps++;
        if (sortSwaps > sortCap) {
            message(Minecraft.getInstance(), ModLang.fmt("exec.sort_giveup"));
            return false;
        }
        pickupQueue.add(new int[]{cont.get(j).index, cont.get(k).index, cont.get(j).index});
        return true;
    }

    // ---------------------------------------------------------------- чтение

    private void doRead(Minecraft mc, AbstractContainerMenu menu) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (Slot sl : containerSlots(menu)) {
            ItemStack st = sl.getItem();
            if (!st.isEmpty()) items.merge(itemId(st), st.getCount(), Integer::sum);
        }
        StorageDB.Entry e = entryFor(mc, currentTask.key);
        if (e != null) StorageDB.get().updateItems(e, items);
    }

    // ---------------------------------------------------------------- служебное

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
        return key.split("\\|", 2)[0]
                .equals(Minecraft.getInstance().level.dimension().location().toString());
    }

    private double distTo(Minecraft mc, BlockPos pos) {
        BlockPos pp = mc.player.blockPosition();
        double dx = pp.getX() + 0.5 - pos.getX() - 0.5;
        double dy = pp.getY() + 0.5 - pos.getY() - 0.5;
        double dz = pp.getZ() + 0.5 - pos.getZ() - 0.5;
        return dx * dx + dy * dy + dz * dz;
    }

    private List<String> targetKeys(Minecraft mc) {
        Set<String> keys = new LinkedHashSet<>();
        if (!HelperState.selected.isEmpty()) keys.addAll(HelperState.selected);
        if (keys.isEmpty()) keys.addAll(StorageDB.get().entries().keySet());
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

    private void performClick(Minecraft mc, AbstractContainerMenu menu) {
        if (activeSeq != null) {
            if (activeIdx < activeSeq.length) {
                menu.clicked(activeSeq[activeIdx++], 0, ClickType.PICKUP, mc.player);
            }
            if (activeIdx >= activeSeq.length) { activeSeq = null; activeIdx = 0; }
            return;
        }
        if (!pickupQueue.isEmpty()) {
            activeSeq = pickupQueue.poll();
            activeIdx = 0;
            menu.clicked(activeSeq[activeIdx++], 0, ClickType.PICKUP, mc.player);
            if (activeIdx >= activeSeq.length) { activeSeq = null; activeIdx = 0; }
            return;
        }
        if (!quickMoveQueue.isEmpty()) {
            menu.clicked(quickMoveQueue.poll(), 0, ClickType.QUICK_MOVE, mc.player);
        }
    }

    private List<Slot> containerSlots(AbstractContainerMenu menu) {
        List<Slot> res = new ArrayList<>();
        if (menu == null) return res;
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (Slot sl : menu.slots) if (sl.container != inv) res.add(sl);
        return res;
    }

    private List<Slot> slotsHolding(AbstractContainerMenu menu, String key) {
        List<Slot> res = new ArrayList<>();
        for (Slot sl : containerSlots(menu)) {
            ItemStack st = sl.getItem();
            if (!st.isEmpty() && itemId(st).equals(key)) res.add(sl);
        }
        return res;
    }

    private List<Slot> playerSlotsHolding(AbstractContainerMenu menu, String key) {
        List<Slot> res = new ArrayList<>();
        if (menu == null) return res;
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (Slot sl : menu.slots) {
            if (sl.container == inv) {
                ItemStack st = sl.getItem();
                if (!st.isEmpty() && itemId(st).equals(key)) res.add(sl);
            }
        }
        return res;
    }

    private Slot partialSlot(AbstractContainerMenu menu, String key) {
        for (Slot sl : containerSlots(menu)) {
            ItemStack st = sl.getItem();
            if (!st.isEmpty() && itemId(st).equals(key) && st.getCount() < st.getMaxStackSize()) return sl;
        }
        return null;
    }

    private Slot emptyContainerSlot(AbstractContainerMenu menu) {
        for (Slot sl : containerSlots(menu)) if (sl.getItem().isEmpty()) return sl;
        return null;
    }

    private static String itemId(ItemStack st) { return ItemKey.stackKey(st); }

    private static int compare(ItemStack a, ItemStack b, String mode) {
        int c = switch (mode) {
            case "mod" -> BuiltInRegistries.ITEM.getKey(a.getItem()).getNamespace()
                    .compareToIgnoreCase(BuiltInRegistries.ITEM.getKey(b.getItem()).getNamespace());
            case "tag" -> firstTag(a).compareToIgnoreCase(firstTag(b));
            default -> 0;
        };
        if (c != 0) return c;
        c = ItemKey.shortName(BuiltInRegistries.ITEM.getKey(a.getItem()).toString())
                .compareToIgnoreCase(ItemKey.shortName(BuiltInRegistries.ITEM.getKey(b.getItem()).toString()));
        if (c != 0) return c;
        return a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
    }

    private static String firstTag(ItemStack st) {
        var tags = st.getItem().builtInRegistryHolder().tags();
        return tags.map(t -> t.location().toString()).sorted().findFirst().orElse("");
    }

    // ---------------------------------------------------------------- планы

    /** Совмещение частичных стаков одного предмета между контейнерами. */
    private List<MoveOp> planPartialMerges(List<String> keys, boolean partialOnly) {
        Map<String, List<Object[]>> byItem = new LinkedHashMap<>();
        for (String k : keys) {
            StorageDB.Entry e = StorageDB.get().getEntry(k);
            if (e == null) continue;
            for (Map.Entry<String, Integer> it : e.items.entrySet()) {
                int max = maxStackOf(it.getKey());
                int count = it.getValue();
                if (partialOnly && (count <= 0 || count % max == 0)) continue;
                byItem.computeIfAbsent(it.getKey(), x -> new ArrayList<>()).add(new Object[]{k, count});
            }
        }
        List<MoveOp> ops = new ArrayList<>();
        for (Map.Entry<String, List<Object[]>> en : byItem.entrySet()) {
            String item = en.getKey();
            List<Object[]> lst = en.getValue();
            if (lst.size() < 2) continue;
            lst.sort((x, y) -> Integer.compare((Integer) y[1], (Integer) x[1]));
            int max = maxStackOf(item);
            String target = null;
            int targetCount = 0;
            for (Object[] pair : lst) {
                String k = (String) pair[0];
                int count = (Integer) pair[1];
                if (target == null) { target = k; targetCount = count; }
                else if (!k.equals(target) && targetCount + count <= max) {
                    ops.add(new MoveOp(k, target, item, count));
                    targetCount += count;
                }
            }
        }
        return ops;
    }

    private List<MoveOp> planCategorize(List<String> keys) {
        Map<String, String> homeByMod = new LinkedHashMap<>();
        for (String k : keys) {
            StorageDB.Entry e = StorageDB.get().getEntry(k);
            if (e == null) continue;
            for (String item : e.items.keySet()) {
                String mod = ItemKey.shortName(item).split(":", 2)[0];
                homeByMod.putIfAbsent(mod, k);
            }
        }
        List<MoveOp> ops = new ArrayList<>();
        for (String k : keys) {
            StorageDB.Entry e = StorageDB.get().getEntry(k);
            if (e == null) continue;
            for (String item : e.items.keySet()) {
                String mod = ItemKey.shortName(item).split(":", 2)[0];
                String home = homeByMod.get(mod);
                if (home == null || home.equals(k)) continue;
                ops.add(new MoveOp(k, home, item, e.items.get(item)));
            }
        }
        return ops;
    }

    private int maxStackOf(String key) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(ItemKey.shortName(key));
        if (rl == null) return 64;
        var item = BuiltInRegistries.ITEM.get(rl);
        return item == null ? 64 : item.getDefaultInstance().getMaxStackSize();
    }

    private void message(Minecraft mc, String msg) {
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    ModLang.fmt("helper.prefix") + " " + msg), false);
        }
        LocalHelperUtils.LOGGER.info("[localhelperutils] {}", msg);
    }
}