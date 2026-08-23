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

/** Пометки «важных» предметов (подсвечиваются в инвентаре). */
public class Marks {
    private Set<String> items = new LinkedHashSet<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Marks INSTANCE;

    private Marks() {}

    public static Marks get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public Set<String> items() {
        return items;
    }

    public boolean isMarked(String stackKey) {
        if (items.contains(stackKey)) return true;
        String bare = ItemKey.shortName(stackKey);
        for (String m : items) {
            if (!ItemKey.hasComponents(m) && m.equals(bare)) return true;
        }
        return false;
    }

    /** @return true, если добавили; false — если сняли пометку */
    public boolean toggle(String stackKey) {
        boolean added;
        if (!items.remove(stackKey)) {
            items.add(stackKey);
            // снимаем «голую» пометку того же id, чтобы не дублировать
            items.remove(ItemKey.shortName(stackKey));
            added = true;
        } else {
            added = false;
        }
        save();
        return added;
    }

    public int clear() {
        int n = items.size();
        items.clear();
        save();
        return n;
    }

    public void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("marks.json"), GSON.toJson(this));
        } catch (IOException e) {
            LocalHelperUtils.LOGGER.error("Не удалось сохранить пометки.", e);
        }
    }

    private static Marks load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(LocalHelperUtils.MODID).resolve("marks.json");
        if (Files.exists(path)) {
            try {
                Marks m = GSON.fromJson(Files.readString(path), Marks.class);
                if (m != null && m.items != null) return m;
            } catch (Exception e) {
                LocalHelperUtils.LOGGER.warn("Не удалось прочитать пометки.", e);
            }
        }
        return new Marks();
    }
}