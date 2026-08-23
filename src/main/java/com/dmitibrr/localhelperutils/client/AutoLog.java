package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Автовыход: мгновенный дисконнект при уроне или при низком HP. */
public class AutoLog {
    public enum Mode { OFF, ON_DAMAGE, LOW_HP }

    private static Mode mode = Mode.OFF;
    private static float prevHealth = -1f;
    private static boolean fired = false;

    private AutoLog() {}

    public static Mode mode() {
        return mode;
    }

    public static void cycle(Minecraft mc) {
        mode = switch (mode) {
            case OFF -> Mode.ON_DAMAGE;
            case ON_DAMAGE -> Mode.LOW_HP;
            default -> Mode.OFF;
        };
        prevHealth = mc.player == null ? -1f : mc.player.getHealth();
        fired = false;
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(
                    ModLang.fmt("helper.prefix") + " " + ModLang.fmt("al.mode", ModLang.fmt("al.mode." + mode.name()))), false);
        }
    }

    public static void tick(Minecraft mc) {
        if (mode == Mode.OFF || fired) return;
        if (mc.player == null) { mode = Mode.OFF; return; }

        float h = mc.player.getHealth();
        if (prevHealth >= 0 && h < prevHealth - 0.01f && mode == Mode.ON_DAMAGE) {
            logout(mc, "al.bye_damage");
            return;
        }
        if (mode == Mode.LOW_HP && h <= 4.0f) {
            logout(mc, "al.bye_lowhp");
            return;
        }
        prevHealth = h;
    }

    private static void logout(Minecraft mc, String reasonKey) {
        fired = true;
        mode = Mode.OFF;
        try {
            var conn = mc.getConnection();
            if (conn != null) {
                conn.disconnect(Component.literal(
                        ModLang.fmt("helper.prefix") + " " + ModLang.fmt(reasonKey)));
                return;
            }
        } catch (Exception ignored) {
        }
        // запасной путь: просто выкинуть в меню
        try {
            if (mc.level != null) mc.level.disconnect();
            mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        } catch (Exception ignored2) {
        }
    }
}