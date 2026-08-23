package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Авто-двери: вплотную и в поле зрения — открывается; прошли мимо (за спиной) — закрывается.
 * Правила на тип двери: normal / open_only / close_only / never.
 */
public class AutoDoors {
    private static final Map<BlockPos, Long> openedByUs = new HashMap<>();
    private static final Map<BlockPos, Long> cooldown = new HashMap<>();

    private AutoDoors() {}

    public static void tick(Minecraft mc) {
        if (!ModConfig.get().autoDoors) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.isShiftKeyDown() || mc.screen != null) return;
        if ((mc.level.getGameTime() % 3) != 0) return;

        long now = mc.level.getGameTime();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        BlockPos base = mc.player.blockPosition();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState st = mc.level.getBlockState(p);
                    boolean isDoor = st.getBlock() instanceof DoorBlock;
                    boolean isTrap = st.getBlock() instanceof TrapDoorBlock;
                    if (!isDoor && !isTrap) continue;

                    BlockPos key = p;
                    if (isDoor && st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            && st.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                        key = p.below(); // обе половины — одна дверь
                    }

                    Long cd = cooldown.get(key);
                    if (cd != null && now - cd < 15) continue;

                    String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                    String rule = ModConfig.get().doorRules.getOrDefault(id, "normal");

                    double distSq = p.getCenter().distanceToSqr(mc.player.position());
                    boolean adjacent = distSq <= 2.6; // вплотную или внутри проёма
                    Vec3 toDoor = p.getCenter().subtract(eye).normalize();
                    double dot = toDoor.x * look.x + toDoor.y * look.y + toDoor.z * look.z;

                    boolean isOpen = st.getValue(BlockStateProperties.OPEN);
                    boolean wantOpen = adjacent && dot > 0.35 && !isOpen;
                    boolean wantClose = !adjacent && openedByUs.containsKey(key)
                            && (dot < -0.2 || now - openedByUs.get(key) > 100) && isOpen;

                    try {
                        if (wantOpen && !rule.equals("never") && !rule.equals("close_only")) {
                            toggle(mc, p);
                            openedByUs.put(key, now);
                            cooldown.put(key, now);
                        } else if (wantClose && !rule.equals("never") && !rule.equals("open_only")) {
                            toggle(mc, p);
                            openedByUs.remove(key);
                            cooldown.put(key, now);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // чистим память о далёких дверях
        Iterator<Map.Entry<BlockPos, Long>> it = openedByUs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().distSqr(base) > 64) it.remove();
        }
    }

    private static void toggle(Minecraft mc, BlockPos pos) {
        BlockState st = mc.level.getBlockState(pos);
        BlockHit hit = new BlockHit(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos);
        try {
            PlacementHelper.SYNTHETIC_USE = true;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(hit.loc(), hit.face(), hit.pos(), false));
        } finally {
            PlacementHelper.SYNTHETIC_USE = false;
        }
    }

    private record BlockHit(Vec3 loc, net.minecraft.core.Direction face, BlockPos pos) {}

    /** Циклит правило для прицеленной двери: normal → open_only → close_only → never. */
    public static void cycleAimedRule(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.hitResult == null
                || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        BlockState st = mc.level.getBlockState(pos);
        if (!(st.getBlock() instanceof DoorBlock) && !(st.getBlock() instanceof TrapDoorBlock)) {
            mc.player.displayClientMessage(Component.literal(
                    ModLang.fmt("helper.prefix") + " " + ModLang.fmt("doors.not_aimed")), true);
            return;
        }
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
        String cur = ModConfig.get().doorRules.getOrDefault(id, "normal");
        String next = switch (cur) {
            case "normal" -> "open_only";
            case "open_only" -> "close_only";
            case "close_only" -> "never";
            default -> "normal";
        };
        if (next.equals("normal")) ModConfig.get().doorRules.remove(id);
        else ModConfig.get().doorRules.put(id, next);
        ModConfig.get().save();
        mc.player.displayClientMessage(Component.literal(ModLang.fmt("helper.prefix") + " "
                + ModLang.fmt("doors.rule_set", id, ModLang.fmt("doors.rule." + next))), true);
    }
}