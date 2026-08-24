package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Подбор лута сразу в основной инвентарь.
 * Строгие правила, чтобы не воевать с игроком:
 *  - реагируем ТОЛЬКО на слот хотбара, который был пуст и стал занят (свежий лут);
 *  - занятые игроком стаки и выбранный слот не трогаем;
 *  - после закрытия любого экрана — калибровка снимка, никаких «сносов»;
 *  - пока открыт экран или работает автокликер — молчим.
 */
public class SmartPickup {
    private static final ItemStack[] prev = new ItemStack[9];
    private static int gap = 0;
    private static boolean screenWasOpen = false;

    private SmartPickup() {}

    public static void tick(Minecraft mc) {
        if (!ModConfig.get().smartPickup) return;
        if (mc.player == null) return;

        if (mc.screen != null) {
            screenWasOpen = true;
            return;
        }
        if (screenWasOpen) {
            // только что закрыли инвентарь/сундук — фиксируем текущее состояние как норму
            screenWasOpen = false;
            snapshot(mc);
            return;
        }
        if (AutoClicker.isActive()) return; // не мешаем бою
        if (gap > 0) { gap--; return; }

        Inventory inv = mc.player.getInventory();
        for (int hb = 0; hb < 9; hb++) {
            if (hb == inv.selected) continue;          // активный слот не трогаем
            ItemStack now = inv.getItem(hb);
            if (now.isEmpty()) continue;
            if (!prev[hb].isEmpty()) continue;         // был занят до этого — не наш клиент

            int free = findFreeMainSlot(inv);
            if (free < 0) return;                      // в основном инвентаре пусто — делать нечего

            var menu = mc.player.inventoryMenu;
            Integer from = menuSlot(menu, hb);
            Integer to = menuSlot(menu, free);
            if (from == null || to == null) continue;

            menu.clicked(from, 0, ClickType.PICKUP, mc.player);
            menu.clicked(to, 0, ClickType.PICKUP, mc.player);
            gap = 3;
            snapshot(mc);
            return; // один перенос за раз
        }
        snapshot(mc);
    }

    private static int findFreeMainSlot(Inventory inv) {
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static Integer menuSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, int invIndex) {
        for (var sl : menu.slots) {
            if (sl.container instanceof Inventory inv && sl.getContainerSlot() == invIndex) {
                return sl.index;
            }
        }
        return null;
    }

    private static void snapshot(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) prev[i] = inv.getItem(i).copy();
    }
}