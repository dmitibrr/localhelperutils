package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.ModConfig;
import com.dmitibrr.localhelperutils.client.StorageTaskExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StorageScreen extends Screen {
    private final Screen back;

    public StorageScreen(Screen back) {
        super(Component.translatable("localhelperutils.menu.storage"));
        this.back = back;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 28;
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(Button.builder(
                Component.translatable(ModConfig.get().selectionMode
                        ? "localhelperutils.menu.selection.on" : "localhelperutils.menu.selection.off"),
                b -> {
                    ModConfig c = ModConfig.get();
                    c.selectionMode = !c.selectionMode;
                    c.save();
                    b.setMessage(Component.translatable(
                            c.selectionMode ? "localhelperutils.menu.selection.on" : "localhelperutils.menu.selection.off"));
                }).bounds(cx - 100, y, 200, 20).build());

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

        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.menu.back"), b ->
                mc.setScreen(back)).bounds(cx - 100, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }
}