package com.dmitibrr.localhelperutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class StorageDB {
    public static class Entry {
        public String key = "";
        public String kind = "block"; // block | item
        public String label = "";
        public String dim = "";
        public int[] pos = new int[0];
        public Map<String, Integer> items = new LinkedHashMap<>();
        public long lastScanned = 0L;
    }

    private Map<String, Entry> containers = new LinkedHashMap<>();
    private Map<String, Set<String>> index = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static StorageDB INSTANCE;

    private StorageDB() {}

    public static StorageDB get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = load();
    }

    public Entry getEntry(String key) {
        return containers.get(key);
    }

    public Map<String, Entry> entries() {
        return containers;
    }

    public Entry blockEntry(String dim, int[] pos, String label) {
        String key = dim + "|" + pos[0] + "," + pos[1] + "," + pos[2];
        Entry e = containers.computeIfAbsent(key, k -> {
            Entry n = new Entry();
            n.key = k;
            n.kind = "block";
            n.dim = dim;
            n.pos = pos;
            n.label = label;
            return n;
        });
        return e;
    }

    public void updateItems(Entry e, Map<String, Integer> items) {
        e.items = items;
        e.lastScanned = System.currentTimeMillis();
        rebuildIndex();
        save();
    }

    public void remove(String key) {
        containers.remove(key);
        rebuildIndex();
        save();
    }

    public void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("db.json"), GSON.toJson(this));
        } catch (IOException e) {
            LocalHelperUtils.LOGGER.error("Не удалось сохранить базу данных сундуковъ.", e);
        }
    }

    private static StorageDB load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID).resolve("db.json");
        if (Files.exists(path)) {
            try {
                StorageDB db = GSON.fromJson(Files.readString(path), StorageDB.class);
                if (db != null && db.containers != null) {
                    db.rebuildIndex();
                    return db;
                }
            } catch (Exception e) {
                LocalHelperUtils.LOGGER.warn("Не удалось прочитать базу данных сундуковъ.", e);
            }
        }
        return new StorageDB();
    }

    public void rebuildIndex() {
        index.clear();
        for (Entry e : containers.values()) {
            for (String item : e.items.keySet()) {
                index.computeIfAbsent(item, k -> new java.util.LinkedHashSet<>()).add(e.key);
            }
        }
    }

    public Set<String> findContainers(String itemId) {
        Set<String> res = index.get(itemId);
        return res == null ? Set.of() : res;
    }

    public Set<String> allItems() {
        return index.keySet();
    }
}