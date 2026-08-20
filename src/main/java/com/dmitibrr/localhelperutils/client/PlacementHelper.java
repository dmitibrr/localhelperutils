package com.dmitibrr.localhelperutils.client;

import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PlacementHelper {
    public static boolean SYNTHETIC_USE = false;

    private static BlockPos miningPos = null;
    private static Direction miningDir = Direction.UP;
    private static boolean armed = false;
    private static boolean handled = false;
    private static int retriesLeft = 0;
    private static int retryTimer = 0;

    private PlacementHelper() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }
        if (mc.screen != null) {
            return;
        }
        if (!ModConfig.get().replaceEnabled) {
            reset();
            return;
        }

        boolean destroying = mc.gameMode.isDestroying();
        BlockPos aim = null;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) mc.hitResult;
            aim = bhr.getBlockPos();
            miningDir = bhr.getDirection();
        }

        if (destroying && aim != null) {
            if (miningPos == null || !miningPos.equals(aim)) {
                miningPos = aim;
                armed = true;
                handled = false;
            }
        }

        if (retriesLeft > 0 && miningPos != null) {
            if (--retryTimer <= 0) {
                retryTimer = 2;
                retriesLeft--;
                if (!mc.level.getBlockState(miningPos).isAir()) {
                    retriesLeft = 0;
                } else {
                    attemptPlace(mc, miningPos, miningDir);
                }
            }
            if (retriesLeft == 0) {
                handled = true;
            }
        }

        if (armed && miningPos != null && mc.level.getBlockState(miningPos).isAir()) {
            armed = false;
            if (!handled) {
                attemptPlace(mc, miningPos, miningDir);
                handled = true;
                retriesLeft = 3;
                retryTimer = 2;
            }
            return;
        }

        if (!destroying) {
            if (miningPos != null && mc.level.getBlockState(miningPos).isAir() && !handled) {
                attemptPlace(mc, miningPos, miningDir);
                handled = true;
            }
            miningPos = null;
            armed = false;
            retriesLeft = 0;
        }
    }

    private static void attemptPlace(Minecraft mc, BlockPos pos, Direction dir) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.isShiftKeyDown() && !ModConfig.get().replaceWhileSneaking) return;

        InteractionHand hand = pickHand(mc.player.getMainHandItem(), mc.player.getOffhandItem());
        if (hand == null) return;

        if (!mc.level.getBlockState(pos).isAir()) return;

        for (Direction d : orderedDirections(dir)) {
            BlockPos neighbor = pos.relative(d);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.isAir()) continue;
            Direction hitFace = d.getOpposite();
            Vec3 loc = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(hitFace.getNormal()).scale(0.5));
            BlockHitResult hit = new BlockHitResult(loc, hitFace, neighbor, false);
            try {
                SYNTHETIC_USE = true;
                mc.gameMode.useItemOn(mc.player, hand, hit);
            } catch (Exception e) {
                LocalHelperUtils.LOGGER.debug("Ошибка установки блока: {}", e.toString());
            } finally {
                SYNTHETIC_USE = false;
            }
            return;
        }
    }

    private static InteractionHand pickHand(ItemStack main, ItemStack off) {
        if (off.getItem() instanceof BlockItem) return InteractionHand.OFF_HAND;
        if (main.getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        return null;
    }

    private static Direction[] orderedDirections(Direction first) {
        Direction[] dirs = new Direction[6];
        dirs[0] = first;
        int i = 1;
        for (Direction d : Direction.values()) {
            if (d != first) dirs[i++] = d;
        }
        return dirs;
    }

    public static void reset() {
        miningPos = null;
        armed = false;
        handled = false;
        retriesLeft = 0;
        retryTimer = 0;
    }
}