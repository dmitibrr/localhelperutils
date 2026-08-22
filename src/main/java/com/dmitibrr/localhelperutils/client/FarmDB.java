package com.dmitibrr.localhelperutils.client;

import com.dmitibrr.localhelperutils.LocalHelperUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FarmDB {
    public static class Crop {
        public String label = "";
        public String replant = ""; // item id, пусто = не пересаживать
    }

    private Map<String, Crop> crops = new LinkedHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FarmDB INSTANCE;

    private FarmDB() {}

    public static FarmDB get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public Map<String, Crop> crops() {
        return crops;
    }

    public Crop getCrop(String blockId) {
        return crops.get(blockId);
    }

    /** Добавляет/обновляет культуру; replantItem может быть null/пустым. */
    public void put(String blockId, String replantItem) {
        Crop c = crops.computeIfAbsent(blockId, k -> {
            Crop n = new Crop();
            n.label = blockId;
            return n;
        });
        if (replantItem != null && !replantItem.isEmpty()) {
            c.replant = replantItem;
        }
        save();
    }

    public void remove(String blockId) {
        if (crops.remove(blockId) != null) save();
    }

    public int size() {
        return crops.size();
    }

    public void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("farm.json"), GSON.toJson(this));
        } catch (IOException e) {
            LocalHelperUtils.LOGGER.error("Не удалось сохранить базу грядокъ.", e);
        }
    }

    private static FarmDB load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID).resolve("farm.json");
        if (Files.exists(path)) {
            try {
                FarmDB db = GSON.fromJson(Files.readString(path), FarmDB.class);
                if (db != null && db.crops != null) return db;
            } catch (Exception e) {
                LocalHelperUtils.LOGGER.warn("Не удалось прочитать базу грядокъ.", e);
            }
        }
        return new FarmDB();
    }
}