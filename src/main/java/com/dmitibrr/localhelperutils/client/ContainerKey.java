package com.dmitibrr.localhelperutils.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ContainerKey {
    private ContainerKey() {}

    public static String blockKey(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos parsePos(String key) {
        try {
            String[] parts = key.split("\\|");
            if (parts.length < 2) return null;
            String[] xyz = parts[1].split(",");
            return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        } catch (Exception e) {
            return null;
        }
    }
}