package com.dmitibrr.localhelperutils.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Мини-i18n: свой переключатель языка поверх игровых файлов локализации. */
public final class ModLang {
    public enum Mode { AUTO, EN, RU }

    private static volatile Map<String, String> map = Map.of();

    private ModLang() {}

    public static synchronized void reload() {
        String mode = ModConfig.get().language;
        String file;
        switch (mode == null ? "auto" : mode) {
            case "en" -> file = "en_us.json";
            case "ru" -> file = "ru_ru.json";
            default -> {
                String code = "en_us";
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.options != null && mc.options.languageCode != null) {
                    code = mc.options.languageCode;
                }
                file = code.startsWith("ru") ? "ru_ru.json" : "en_us.json";
            }
        }
        try (InputStream in = ModLang.class.getResourceAsStream("/assets/localhelperutils/lang/" + file)) {
            if (in != null) {
                map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType());
                return;
            }
        } catch (Exception ignored) {
        }
        map = Map.of();
    }

    public static String tr(String key) {
        if (map.isEmpty()) reload();
        String s = map.get(key);
        if (s == null) s = map.get("localhelperutils." + key);
        return s != null ? s : key;
    }

    /** Форматирует "%s" подстановками и возвращает литеральный компонент. */
    public static MutableComponent c(String key, Object... args) {
        String s = tr(key);
        for (Object a : args) {
            int i = s.indexOf("%s");
            if (i >= 0) s = s.substring(0, i) + a + s.substring(i + 2);
        }
        return Component.literal(s);
    }

    public static String fmt(String key, Object... args) {
        return c(key, args).getString();
    }
}