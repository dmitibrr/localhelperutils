package com.dmitibrr.localhelperutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class ModConfig {
    public static final Set<String> ALL_CATEGORIES =
            Set.of("chest", "barrel", "shulker", "ender", "functional", "modded");

    public boolean replaceEnabled = true;
    public boolean replaceWhileSneaking = false;
    public boolean selectionMode = false;
    public boolean farmEnabled = true;
    public Set<String> categories = new LinkedHashSet<>(ALL_CATEGORIES);
    public String sortMode = "name"; // name | tag | mod
    public boolean categorizeByMod = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE;

    private ModConfig() {}

    public static ModConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = load();
    }

    private static ModConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                ModConfig cfg = GSON.fromJson(Files.readString(path), ModConfig.class);
                if (cfg != null && cfg.categories != null && !cfg.categories.isEmpty()) {
                    INSTANCE = cfg;
                    return cfg;
                }
            } catch (Exception e) {
                LocalHelperUtils.LOGGER.warn("Не удалось прочитать настройки, беру по умолчанию.", e);
            }
        }
        ModConfig cfg = new ModConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("config.json"), GSON.toJson(this));
        } catch (IOException e) {
            LocalHelperUtils.LOGGER.error("Не удалось сохранить настройки.", e);
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID).resolve("config.json");
    }

    public boolean categoryAllowed(String blockId) {
        String ns = blockId.contains(":") ? blockId.split(":", 2)[0] : "minecraft";
        if (ns.equals("minecraft")) {
            String id = blockId;
            if (id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest")) {
                return categories.contains("chest");
            }
            if (id.equals("minecraft:barrel")) {
                return categories.contains("barrel");
            }
            if (id.equals("minecraft:ender_chest")) {
                return categories.contains("ender");
            }
            if (id.equals("minecraft:shulker_box") || id.endsWith("_shulker_box")) {
                return categories.contains("shulker");
            }
            return categories.contains("functional");
        }
        return categories.contains("modded");
    }

    public void toggleCategory(String cat) {
        if (!categories.remove(cat)) {
            categories.add(cat);
        }
    }

    public void cycleSortMode() {
        sortMode = switch (sortMode) {
            case "name" -> "tag";
            case "tag" -> "mod";
            default -> "name";
        };
    }
}