package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
    private final Screen back;

    public SettingsScreen(Screen back) {
        super(Component.translatable("localhelperutils.settings.title"));
        this.back = back;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 30;
        ModConfig cfg = ModConfig.get();

        addRenderableWidget(Button.builder(
                status(cfg.replaceEnabled, "localhelperutils.settings.replace"), b -> {
                    ModConfig c = ModConfig.get();
                    c.replaceEnabled = !c.replaceEnabled;
                    c.save();
                    b.setMessage(status(c.replaceEnabled, "localhelperutils.settings.replace"));
                }).bounds(cx - 110, y, 220, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(
                status(!cfg.replaceWhileSneaking, "localhelperutils.settings.sneak"), b -> {
                    ModConfig c = ModConfig.get();
                    c.replaceWhileSneaking = !c.replaceWhileSneaking;
                    c.save();
                    b.setMessage(status(!c.replaceWhileSneaking, "localhelperutils.settings.sneak"));
                }).bounds(cx - 110, y, 220, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(
                Component.translatable("localhelperutils.settings.sort", sortLabel(cfg.sortMode)), b -> {
                    ModConfig c = ModConfig.get();
                    c.cycleSortMode();
                    c.save();
                    b.setMessage(Component.translatable("localhelperutils.settings.sort", sortLabel(c.sortMode)));
                }).bounds(cx - 110, y, 220, 20).build());

        String[] cats = {"chest", "barrel", "shulker", "ender", "functional", "modded"};
        for (int i = 0; i < cats.length; i++) {
            String cat = cats[i];
            int col = i % 2;
            int row = i / 2;
            int bx = cx - 115 + col * 120;
            int by = 120 + row * 22;
            addRenderableWidget(Button.builder(catButton(cat), b -> {
                ModConfig c = ModConfig.get();
                c.toggleCategory(cat);
                c.save();
                b.setMessage(catButton(cat));
            }).bounds(bx, by, 115, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.back"), b ->
                this.minecraft.setScreen(back)).bounds(cx - 110, 210, 220, 20).build());
    }

    private static Component catButton(String cat) {
        ModConfig c = ModConfig.get();
        return Component.translatable("localhelperutils.settings.cat." + cat,
                c.categories.contains(cat)
                        ? Component.translatable("localhelperutils.settings.yes")
                        : Component.translatable("localhelperutils.settings.no"));
    }

    private static Component status(boolean on, String key) {
        return Component.translatable(key,
                on ? Component.translatable("localhelperutils.settings.yes")
                    : Component.translatable("localhelperutils.settings.no"));
    }

    private static String sortLabel(String mode) {
        return "localhelperutils.settings.sort." + mode;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        gui.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        gui.drawCenteredString(this.font,
                Component.translatable("localhelperutils.settings.categories"), cx, 110, 0xAAAAAA);
    }
}