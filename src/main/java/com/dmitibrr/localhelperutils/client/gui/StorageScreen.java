package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.HelperState;
import com.dmitibrr.localhelperutils.client.ModConfig;
import com.dmitibrr.localhelperutils.client.ModLang;
import com.dmitibrr.localhelperutils.client.StorageTaskExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StorageScreen extends Screen {
    private final Screen back;

    public StorageScreen(Screen back) {
        super(ModLang.c("menu.storage"));
        this.back = back;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 26;
        Minecraft mc = Minecraft.getInstance();

        addRenderableWidget(Button.builder(
                ModLang.c(ModConfig.get().selectionMode
                        ? "menu.selection.on" : "menu.selection.off"),
                b -> {
                    ModConfig c = ModConfig.get();
                    c.selectionMode = !c.selectionMode;
                    c.save();
                    b.setMessage(ModLang.c(c.selectionMode
                            ? "menu.selection.on" : "menu.selection.off"));
                }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.literal(ModLang.fmt("settings.sort",
                        ModLang.fmt("settings.sort." + ModConfig.get().sortMode))), b -> {
            ModConfig c = ModConfig.get();
            c.cycleSortMode();
            c.save();
            b.setMessage(Component.literal(ModLang.fmt("settings.sort",
                    ModLang.fmt("settings.sort." + c.sortMode))));
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(ModLang.c("menu.scan"), b -> {
            StorageTaskExecutor.get().startScan(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(ModLang.c("menu.sort"), b -> {
            StorageTaskExecutor.get().startSort(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(ModLang.c("menu.combine"), b -> {
            StorageTaskExecutor.get().startCombine(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 22;
        addRenderableWidget(Button.builder(ModLang.c("menu.categorize"), b -> {
            StorageTaskExecutor.get().startCategorize(mc);
            mc.setScreen(null);
        }).bounds(cx - 100, y, 200, 20).build());

        y += 28;
        boolean running = StorageTaskExecutor.get().isActive();

        Button abort = addRenderableWidget(Button.builder(ModLang.c("menu.abort"), b -> {
            if (StorageTaskExecutor.get().isActive()) {
                StorageTaskExecutor.get().stop(mc);
                mc.player.displayClientMessage(ModLang.c("abort.done"), true);
            } else {
                mc.player.displayClientMessage(ModLang.c("abort.none"), true);
            }
        }).bounds(cx - 100, y, 97, 20).build());
        abort.active = running;

        addRenderableWidget(Button.builder(ModLang.c("storage.clear"), b -> {
            int n = HelperState.selected.size();
            HelperState.selected.clear();
            mc.player.displayClientMessage(ModLang.c("storage.cleared", n), true);
        }).bounds(cx + 3, y, 97, 20).build());

        y += 26;
        addRenderableWidget(Button.builder(ModLang.c("menu.back"), b ->
                mc.setScreen(back)).bounds(cx - 100, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }
}