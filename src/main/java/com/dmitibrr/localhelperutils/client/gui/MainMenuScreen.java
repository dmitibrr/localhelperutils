package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.ModConfig;
import com.dmitibrr.localhelperutils.client.StorageTaskExecutor;
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
        int y = 24;
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(Button.builder(
                Component.translatable("localhelperutils.menu.selection"),
                b -> {
                    ModConfig cfg = ModConfig.get();
                    cfg.selectionMode = !cfg.selectionMode;
                    cfg.save();
                    b.setMessage(Component.translatable(
                            cfg.selectionMode ? "localhelperutils.menu.selection.on" : "localhelperutils.menu.selection.off"));
                })
                .bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.scan"), b -> {
            StorageTaskExecutor.get().startScan(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.combine"), b -> {
            StorageTaskExecutor.get().startCombine(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.sort"), b -> {
            StorageTaskExecutor.get().startSort(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.categorize"), b -> {
            StorageTaskExecutor.get().startCategorize(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(
                Component.translatable(cfgFarm()
                        ? "localhelperutils.menu.farm.on" : "localhelperutils.menu.farm.off"),
                b -> {
                    ModConfig c = ModConfig.get();
                    c.farmEnabled = !c.farmEnabled;
                    c.save();
                    b.setMessage(Component.translatable(
                            c.farmEnabled ? "localhelperutils.menu.farm.on" : "localhelperutils.menu.farm.off"));
                }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.search"), b ->
                mc.setScreen(new SearchScreen())).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.settings"), b ->
                mc.setScreen(new SettingsScreen(this))).bounds(cx - 100, y, 200, 20).build());

        y += 28;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.close"), b ->
                this.onClose()).bounds(cx - 100, y, 200, 20).build());
    }

    private static boolean cfgFarm() {
        return ModConfig.get().farmEnabled;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        gui.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);
        String footer = ModConfig.get().replaceEnabled
                ? "localhelperutils.menu.footer"
                : "localhelperutils.menu.footer.off";
        gui.drawCenteredString(this.font, Component.translatable(footer), cx, this.height - 20, 0x808080);
    }
}