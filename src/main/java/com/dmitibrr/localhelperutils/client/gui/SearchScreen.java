package com.dmitibrr.localhelperutils.client.gui;

import com.dmitibrr.localhelperutils.client.HelperState;
import com.dmitibrr.localhelperutils.client.StorageDB;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SearchScreen extends Screen {
    private EditBox box;

    public SearchScreen() {
        super(Component.translatable("localhelperutils.search.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.box = new EditBox(this.font, cx - 120, 40, 240, 20, Component.translatable("localhelperutils.search.field"));
        this.box.setResponder(str -> {
            HelperState.searchItem = str.trim().isEmpty() ? null : str.trim().toLowerCase();
        });
        addRenderableWidget(this.box);
        addRenderableWidget(Button.builder(Component.translatable("localhelperutils.search.clear"), b -> {
            this.box.setValue("");
            HelperState.searchItem = null;
        }).bounds(cx - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        gui.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);
        if (this.box != null) {
            this.box.render(gui, mouseX, mouseY, partialTick);
        }
        String q = HelperState.searchItem;
        if (q == null) {
            gui.drawCenteredString(this.font,
                    Component.translatable("localhelperutils.search.hint"), cx, 70, 0x808080);
            return;
        }
        List<String> matches = new ArrayList<>();
        for (String item : StorageDB.get().allItems()) {
            if (item.toLowerCase().contains(q)) {
                matches.add(item);
            }
        }
        int y = 70;
        if (matches.isEmpty()) {
            gui.drawCenteredString(this.font,
                    Component.translatable("localhelperutils.search.none"), cx, y, 0xFF5555);
            return;
        }
        int shown = Math.min(matches.size(), 10);
        for (int i = 0; i < shown; i++) {
            String item = matches.get(i);
            int count = StorageDB.get().findContainers(item).size();
            String label = com.dmitibrr.localhelperutils.client.ItemKey.shortName(item)
                    + (com.dmitibrr.localhelperutils.client.ItemKey.hasComponents(item)
                        ? " §8[nbt]" : "");
            gui.drawCenteredString(this.font,
                    Component.literal("§e" + label + "§7 — " + count + " конт."), cx, y, 0xFFFFFF);
            y += 12;
        }
        if (matches.size() > shown) {
            gui.drawCenteredString(this.font, "...", cx, y, 0x808080);
        }
    }

    @Override
    public void onClose() {
        HelperState.searchItem = null;
        super.onClose();
    }
}