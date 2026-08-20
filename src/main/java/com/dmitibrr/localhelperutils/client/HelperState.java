package com.dmitibrr.localhelperutils.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;

public final class HelperState {
    public static ResourceKey<Level> lastDim = null;
    public static BlockPos lastPos = null;
    public static final Set<String> selected = new LinkedHashSet<>();
    public static String searchItem = null;

    private HelperState() {}

    public static void reset() {
        lastDim = null;
        lastPos = null;
        selected.clear();
        searchItem = null;
    }
}