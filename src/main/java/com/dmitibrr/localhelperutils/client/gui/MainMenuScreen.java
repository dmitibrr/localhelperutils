package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MainMenuScreen extends Screen {
    public MainMenuScreen() {
        super(Component.translatable("localhelperutils.menu.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 26;
        ModConfig cfg = ModConfig.get();
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(Button.builder(
                Component.translatable(cfg.replaceEnabled
                        ? "localhelperutils.menu.replace.on" : "localhelperutils.menu.replace.off"),
                b -> {
                    ModConfig c = ModConfig.get();
                    c.replaceEnabled = !c.replaceEnabled;
                    c.save();
                    b.setMessage(Component.translatable(
                            c.replaceEnabled ? "localhelperutils.menu.replace.on" : "localhelperutils.menu.replace.off"));
                }).bounds(cx - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(
                Component.translatable(cfg.farmEnabled
                        ? "localhelperutils.menu.farm.on" : "localhelperutils.menu.farm.off"),
                b -> {
                    ModConfig c = ModConfig.get();
                    c.farmEnabled = !c.farmEnabled;
                    c.save();
                    b.setMessage(Component.translatable(
                            c.farmEnabled ? "localhelperutils.menu.farm.on" : "localhelperutils.menu.farm.off"));
                }).bounds(cx - 100, y, 200, 20).build());

        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.storage"), b ->
                mc.setScreen(new StorageScreen(this))).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.search"), b ->
                mc.setScreen(new SearchScreen())).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.settings"), b ->
                mc.setScreen(new SettingsScreen(this))).bounds(cx - 100, y, 200, 20).build());

        y += 34;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.close"), b ->
                this.onClose()).bounds(cx - 100, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        gui.drawCenteredString(this.font,
                Component.translatable(ModConfig.get().replaceEnabled
                        ? "localhelperutils.menu.footer" : "localhelperutils.menu.footer.off"),
                this.width / 2, this.height - 18, 0x808080);
    }
}