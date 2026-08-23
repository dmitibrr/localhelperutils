package com.dmitibrr.localhelperutils.client;

import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;

public class FarmHelper {
    private enum Phase { IDLE, WAIT_AIR, WAIT_REPLANT }

    private static Phase phase = Phase.IDLE;
    private static BlockPos targetPos = null;
    private static Direction targetFace = Direction.UP;
    private static String replantItem = "";
    private static int timer = 0;
    private static int prevSelectedSlot = -1;

    private FarmHelper() {}

    /** Готова ли культура к сбору: свойство age достигло максимума (если есть). */
    public static boolean isReady(net.minecraft.world.level.block.state.BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty ip && ip.getName().equals("age")) {
                return state.getValue(ip) >= Collections.max(ip.getPossibleValues());
            }
        }
        return true; // без age — всегда собираем
    }

    /** Записать прицелъ: культура + предметъ въ руке как посадочный. */
    public static void recordAimedCrop(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.hitResult == null
                || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        var state = mc.level.getBlockState(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        ItemStack hand = mc.player.getMainHandItem();
        String replant = "";
        if (!hand.isEmpty() && !(hand.getItem() instanceof BlockItem b && b.getBlock().defaultBlockState().isAir())) {
            // берём id предмета из руки только если это не сам блок-культура? нет — берём как есть
        }
        if (!hand.isEmpty()) {
            replant = BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
        }
        FarmDB.get().put(blockId, replant);
        msg(mc, ModLang.fmt("farm.recorded", blockId,
                replant.isEmpty() ? ModLang.fmt("farm.nothing") : replant));
    }

    /**
     * Вызывается из обработчика ПКМ по блоку. Если это готовая культура из базы —
     * начинает сбор. Возвращает true, если перехватили (событие надо отменить).
     */
    public static boolean tryHarvest(Minecraft mc, net.minecraft.world.level.block.state.BlockState state,
                                     BlockPos pos, Direction face) {
        if (!ModConfig.get().farmEnabled) return false;
        if (!StorageTaskExecutor.get().isIdle()) return false;
        if (phase != Phase.IDLE) return false;
        if (mc.player == null || mc.player.isShiftKeyDown()) return false;

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        FarmDB.Crop crop = FarmDB.get().getCrop(blockId);
        if (crop == null) return false;
        if (!isReady(state)) return false;

        targetPos = pos.immutable();
        targetFace = face;
        replantItem = crop.replant == null ? "" : crop.replant;

        try {
            mc.gameMode.startDestroyBlock(targetPos, targetFace);
        } catch (Exception e) {
            LocalHelperUtils.LOGGER.debug("Не удалось сломать культуру: {}", e.toString());
        }
        phase = Phase.WAIT_AIR;
        timer = 30;
        return true;
    }

    public static void tick(Minecraft mc) {
        if (phase == Phase.IDLE || mc.player == null || mc.level == null || mc.gameMode == null) {
            if (phase != Phase.IDLE && (mc.player == null)) reset();
            return;
        }
        switch (phase) {
            case WAIT_AIR -> {
                if (mc.level.getBlockState(targetPos).isAir()) {
                    beginReplant(mc);
                } else if (--timer <= 0) {
                    reset();
                }
            }
            case WAIT_REPLANT -> {
                if (--timer > 0) return;
                doReplant(mc);
                reset();
            }
            default -> {}
        }
    }

    private static void beginReplant(Minecraft mc) {
        if (replantItem.isEmpty() || !ModConfig.get().farmEnabled) return;
        int slot = findHotbarSlot(mc, replantItem);
        if (slot < 0) return; // нѣтъ семянъ въ хотбарѣ — просто не сажаем

        prevSelectedSlot = mc.player.getInventory().selected;
        if (prevSelectedSlot != slot) {
            mc.player.getInventory().selected = slot;
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
        phase = Phase.WAIT_REPLANT;
        timer = 1; // тик на смену слота
    }

    private static void doReplant(Minecraft mc) {
        if (!replantItem.isEmpty()) {
            BlockPos below = targetPos.below();
            Vec3 loc = Vec3.atCenterOf(below).add(0, 0.5, 0);
            BlockHitResult hit = new BlockHitResult(loc, Direction.UP, below, false);
            try {
                PlacementHelper.SYNTHETIC_USE = true;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.swing(InteractionHand.MAIN_HAND);
            } catch (Exception e) {
                LocalHelperUtils.LOGGER.debug("Не удалось пересадить: {}", e.toString());
            } finally {
                PlacementHelper.SYNTHETIC_USE = false;
            }
            if (prevSelectedSlot >= 0) {
                mc.player.getInventory().selected = prevSelectedSlot;
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(prevSelectedSlot));
                prevSelectedSlot = -1;
            }
        }
    }

    private static int findHotbarSlot(Minecraft mc, String itemId) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (!st.isEmpty()
                    && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)
                    && st.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    public static void reset() {
        phase = Phase.IDLE;
        targetPos = null;
        replantItem = "";
        timer = 0;
        prevSelectedSlot = -1;
    }

    private static void msg(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(ModLang.fmt("helper.prefix") + " " + text), false);
        }
        LocalHelperUtils.LOGGER.info("[localhelperutils] {}", text);
    }
}