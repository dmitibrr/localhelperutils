package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.ModConfig;
import com.dmitibrr.localhelperutils.client.ModLang;
import com.dmitibrr.localhelperutils.client.StorageTaskExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MainMenuScreen extends Screen {
    private static final String[] ROTATION = {
            "hint.rot.0", "hint.rot.1", "hint.rot.2", "hint.rot.3",
            "hint.rot.4", "hint.rot.5", "hint.rot.6"
    };

    public MainMenuScreen() {
        super(ModLang.c("menu.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 26;
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(toggle(y,
                "menu.replace.on", "menu.replace.off",
                () -> ModConfig.get().replaceEnabled,
                c -> c.replaceEnabled = !c.replaceEnabled));

        y += 24;
        addRenderableWidget(toggle(y,
                "menu.farm.on", "menu.farm.off",
                () -> ModConfig.get().farmEnabled,
                c -> c.farmEnabled = !c.farmEnabled));

        y += 24;
        addRenderableWidget(toggle(y,
                "menu.pickup.on", "menu.pickup.off",
                () -> ModConfig.get().smartPickup,
                c -> c.smartPickup = !c.smartPickup));

        y += 24;
        addRenderableWidget(toggle(y,
                "menu.doors.on", "menu.doors.off",
                () -> ModConfig.get().autoDoors,
                c -> c.autoDoors = !c.autoDoors));

        y += 26;
        addRenderableWidget(Button.builder(ModLang.c("menu.storage"), b ->
                mc.setScreen(new StorageScreen(this))).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(ModLang.c("menu.search"), b ->
                mc.setScreen(new SearchScreen())).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(ModLang.c("menu.settings"), b ->
                mc.setScreen(new SettingsScreen(this))).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        Button abort = addRenderableWidget(Button.builder(ModLang.c("menu.abort"), b -> {
            if (StorageTaskExecutor.get().isActive()) {
                StorageTaskExecutor.get().stop(mc);
                mc.player.displayClientMessage(ModLang.c("abort.done"), true);
            } else {
                mc.player.displayClientMessage(ModLang.c("abort.none"), true);
            }
        }).bounds(cx - 100, y, 200, 20).build());
        abort.active = StorageTaskExecutor.get().isActive();
    }

    private Button toggle(int y, String onKey, String offKey,
                          java.util.function.BooleanSupplier get,
                          java.util.function.Consumer<ModConfig> flip) {
        Button b = Button.builder(ModLang.c(get.getAsBoolean() ? onKey : offKey), btn -> {
            ModConfig c = ModConfig.get();
            flip.accept(c);
            c.save();
            btn.setMessage(ModLang.c(get.getAsBoolean() ? onKey : offKey));
        }).bounds(this.width / 2 - 100, y, 200, 20).build();
        return b;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        if (ModConfig.get().farmEnabled) {
            gui.drawCenteredString(this.font,
                    ModLang.c("hint.farm"), this.width / 2, this.height - 30, 0x5F9E5F);
        }
        long idx = (System.currentTimeMillis() / 4000L) % ROTATION.length;
        gui.drawCenteredString(this.font,
                ModLang.c(ROTATION[(int) idx]), this.width / 2, this.height - 18, 0x808080);
    }
}