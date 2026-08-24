package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Подбор лута сразу в основной инвентарь.
 *
 * Как работает: новый предмет в хотбаре (слот был пуст) через пару тиков
 * отправляется одним shift-кликом (QUICK_MOVE) в основной инвентарь —
 * ванилла сама стекует его к частичным стакам и только потом в пустые слоты.
 * Один клик = ничего не лежит на курсоре = пропасть нечему.
 */
public class SmartPickup {
    private static final ItemStack[] prev = new ItemStack[9];
    private static int gap = 0;
    private static boolean screenWasOpen = false;

    private static int pendingSlot = -1;
    private static int pendingTimer = 0;
    private static ItemStack pendingStack = ItemStack.EMPTY;

    private SmartPickup() {}

    public static void tick(Minecraft mc) {
        if (!ModConfig.get().smartPickup) return;
        if (mc.player == null) return;

        if (mc.screen != null) {
            screenWasOpen = true;
            pendingSlot = -1;
            return;
        }
        if (screenWasOpen) {
            // только что закрыли экран — калибруем снимок, ничего не переносим
            screenWasOpen = false;
            snapshot(mc);
            return;
        }
        if (AutoClicker.isActive()) return;
        if (mc.player.containerMenu != mc.player.inventoryMenu) return; // чужое меню — молчим
        if (gap > 0) { gap--; return; }

        Inventory inv = mc.player.getInventory();

        // 1) есть отложенный перенос — ждём синк и исполняем
        if (pendingSlot >= 0) {
            if (--pendingTimer > 0) return;
            ItemStack now = inv.getItem(pendingSlot);
            if (!now.isEmpty()
                    && ItemStack.isSameItemSameComponents(now, pendingStack)
                    && pendingSlot != inv.selected) {
                Integer id = menuSlot(mc.player.inventoryMenu, pendingSlot);
                if (id != null) {
                    mc.player.inventoryMenu.clicked(id, 0, ClickType.QUICK_MOVE, mc.player);
                    gap = 4;
                }
            }
            pendingSlot = -1;
            pendingStack = ItemStack.EMPTY;
            snapshot(mc);
            return;
        }

        // 2) ищем новый лут: слот был пуст — стал занят
        for (int hb = 0; hb < 9; hb++) {
            if (hb == inv.selected) continue;
            ItemStack now = inv.getItem(hb);
            if (now.isEmpty()) continue;
            if (!prev[hb].isEmpty()) continue; // был занят — не наш клиент

            pendingSlot = hb;
            pendingStack = now.copy();
            pendingTimer = 3; // даём серверу подтвердить подбор
            return;
        }
        snapshot(mc);
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