package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.Marks;
import com.dmitibrr.localhelperutils.client.ModConfig;
import com.dmitibrr.localhelperutils.client.ModLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
    private final Screen back;

    public SettingsScreen(Screen back) {
        super(ModLang.c("settings.title"));
        this.back = back;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(toggle(cx - 115, 28, 110, "settings.sneak",
                () -> !ModConfig.get().replaceWhileSneaking,
                c -> c.replaceWhileSneaking = !c.replaceWhileSneaking));

        addRenderableWidget(Button.builder(
                Component.literal(ModLang.fmt("settings.language", langLabel(ModConfig.get().language))), b -> {
                    ModConfig c = ModConfig.get();
                    String mode = c.cycleLanguage();
                    c.save();
                    b.setMessage(Component.literal(ModLang.fmt("settings.language", langLabel(mode))));
                }).bounds(cx + 5, 28, 110, 20).build());

        addRenderableWidget(toggle(cx - 115, 50, 110, "settings.pickup",
                () -> ModConfig.get().smartPickup,
                c -> c.smartPickup = !c.smartPickup));

        addRenderableWidget(toggle(cx + 5, 50, 110, "settings.doors",
                () -> ModConfig.get().autoDoors,
                c -> c.autoDoors = !c.autoDoors));

        Button marksBtn = addRenderableWidget(Button.builder(
                Component.literal(ModLang.fmt("settings.marks", Marks.get().items().size())), b -> { }).bounds(cx - 115, 72, 110, 20).build());
        marksBtn.active = false;

        addRenderableWidget(Button.builder(
                Component.literal(ModLang.fmt("settings.marks_clear")), b -> {
                    int n = Marks.get().clear();
                    mc.player.displayClientMessage(ModLang.c("storage.cleared_marks", n), true);
                    mc.setScreen(new SettingsScreen(back)); // пересобрать
                }).bounds(cx + 5, 72, 110, 20).build());

        String[] cats = {"chest", "barrel", "shulker", "ender", "functional", "modded"};
        for (int i = 0; i < cats.length; i++) {
            String cat = cats[i];
            int col = i % 3;
            int row = i / 3;
            int bx = cx - 115 + col * 78;
            int by = 118 + row * 22;
            addRenderableWidget(Button.builder(catButton(cat), b -> {
                ModConfig c = ModConfig.get();
                c.toggleCategory(cat);
                c.save();
                b.setMessage(catButton(cat));
            }).bounds(bx, by, 73, 20).build());
        }

        addRenderableWidget(Button.builder(ModLang.c("menu.back"), b ->
                this.minecraft.setScreen(back)).bounds(cx - 100, 200, 200, 20).build());
    }

    private Button toggle(int x, int y, int w, String key,
                          java.util.function.BooleanSupplier get,
                          java.util.function.Consumer<ModConfig> flip) {
        return Button.builder(status(get.getAsBoolean(), key), btn -> {
            ModConfig c = ModConfig.get();
            flip.accept(c);
            c.save();
            btn.setMessage(status(get.getAsBoolean(), key));
        }).bounds(x, y, w, 20).build();
    }

    private static Component catButton(String cat) {
        ModConfig c = ModConfig.get();
        return Component.literal(ModLang.fmt("settings.cat." + cat,
                c.categories.contains(cat)
                        ? ModLang.fmt("settings.yes")
                        : ModLang.fmt("settings.no")));
    }

    private static Component status(boolean on, String key) {
        return Component.literal(ModLang.fmt(key,
                on ? ModLang.fmt("settings.yes") : ModLang.fmt("settings.no")));
    }

    private static String langLabel(String mode) {
        return switch (mode == null ? "auto" : mode) {
            case "en" -> ModLang.fmt("settings.lang.en");
            case "ru" -> ModLang.fmt("settings.lang.ru");
            default -> ModLang.fmt("settings.lang.auto");
        };
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        gui.drawCenteredString(this.font, ModLang.c("settings.categories"),
                this.width / 2, 106, 0xAAAAAA);
    }
}