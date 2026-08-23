package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Подбор лута сразу в основной инвентарь, минуя хотбар. */
public class SmartPickup {
    private static final ItemStack[] prev = new ItemStack[9];
    private static int gap = 0;

    private SmartPickup() {}

    static {
        for (int i = 0; i < 9; i++) prev[i] = ItemStack.EMPTY;
    }

    public static void tick(Minecraft mc) {
        if (!ModConfig.get().smartPickup) return;
        if (mc.player == null || mc.screen != null) return;
        if (gap > 0) { gap--; return; }

        Inventory inv = mc.player.getInventory();

        for (int hb = 0; hb < 9; hb++) {
            ItemStack now = inv.getItem(hb);
            ItemStack was = prev[hb];
            boolean isNewArrival = !now.isEmpty()
                    && (was.isEmpty()
                        || !ItemKey.stackKey(now).equals(ItemKey.stackKey(was))
                        || now.getCount() > was.getCount());
            if (!isNewArrival) continue;

            int free = findFreeMainSlot(inv);
            if (free < 0) break;

            var menu = mc.player.inventoryMenu;
            SlotRef from = slotOf(menu, hb);
            SlotRef to = slotOf(menu, free);
            if (from == null || to == null) continue;

            // забрать из хотбара, положить в свободный слот основного инвентаря
            menu.clicked(from.id, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            menu.clicked(to.id, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            gap = 3;
            break; // по одному переносу за раз
        }

        for (int i = 0; i < 9; i++) prev[i] = inv.getItem(i).copy();
    }

    private static int findFreeMainSlot(Inventory inv) {
        for (int i = 9; i < 36; i++) if (inv.getItem(i).isEmpty()) return i;
        return -1;
    }

    private record SlotRef(int id) {}

    /** Находим слот меню игрока по индексу инвентаря. */
    private static SlotRef slotOf(net.minecraft.world.inventory.AbstractContainerMenu menu, int invIndex) {
        for (var sl : menu.slots) {
            if (sl.container instanceof Inventory inv && sl.getContainerSlot() == invIndex) {
                return new SlotRef(sl.index);
            }
        }
        return null;
    }

    public static List<String> debugState(Minecraft mc) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 9; i++) l.add(String.valueOf(prev[i].isEmpty()));
        return l;
    }
}