package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Идентичность предмета с учётом компонентов/NBT — книги и самоцветы не должны слипаться. */
public final class ItemKey {
    private ItemKey() {}

    public static String stackKey(ItemStack st) {
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        if (st.isEmpty()) return id;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Tag saved = st.save(mc.level.registryAccess());
                if (saved instanceof CompoundTag ct && ct.contains("components")) {
                    String snbt = ct.get("components").toString();
                    return id + "#" + shortHash(snbt);
                }
            }
        } catch (Exception ignored) {
        }
        return id;
    }

    /** Для показа пользователю: id без хвоста компонентов. */
    public static String shortName(String key) {
        int i = key.indexOf('#');
        return i >= 0 ? key.substring(0, i) : key;
    }

    public static boolean hasComponents(String key) {
        return key.indexOf('#') >= 0;
    }

    private static String shortHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}