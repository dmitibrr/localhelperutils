package com.dmitibrr.localhelperutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Умный автокликер для мободробилок: бьёт только по полному откату удара,
 * свапает оружие при низком запасе прочности, при движении/повороте камеры
 * сразу выключается (чтобы не превращаться в триггербот).
 */
public class AutoClicker {
    private static final float DURABILITY_FLOOR = 40f;
    private static final double MOVE_TOLERANCE_SQ = 0.02;   // ~14 см суммарного смещения
    private static final float CAM_TOLERANCE_DEG = 2.0f;

    private static boolean active = false;
    private static Vec3 anchorPos = null;
    private static float anchorYaw = 0f, anchorPitch = 0f;
    private static boolean fistMode = false;

    private AutoClicker() {}

    public static boolean isActive() {
        return active;
    }

    public static void toggle(Minecraft mc) {
        active = !active;
        if (active) {
            if (mc.player != null) {
                anchorPos = mc.player.position();
                anchorYaw = mc.player.getYRot();
                anchorPitch = mc.player.getXRot();
            }
            msg(mc, ModLang.fmt("ac.on"));
        } else {
            msg(mc, ModLang.fmt("ac.off"));
        }
    }

    private static void disable(Minecraft mc, String reasonKey) {
        active = false;
        msg(mc, ModLang.fmt(reasonKey));
    }

    public static void tick(Minecraft mc) {
        if (!active) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            active = false;
            return;
        }
        if (mc.screen != null) return; // чат/меню открыты — просто ждём

        // --- стражи движения ---
        Vec3 pos = mc.player.position();
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        if (anchorPos != null && anchorPos.distanceToSqr(pos) > MOVE_TOLERANCE_SQ) {
            disable(mc, "ac.disabled_move");
            return;
        }
        if (Math.abs(wrapDeg(yaw - anchorYaw)) > CAM_TOLERANCE_DEG
                || Math.abs(pitch - anchorPitch) > CAM_TOLERANCE_DEG) {
            disable(mc, "ac.disabled_mouse");
            return;
        }

        // --- цель под прицелом ---
        if (!(mc.hitResult instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof LivingEntity target)) return;
        if (target == mc.player || target instanceof Player || target instanceof ArmorStand) return;
        if (!target.isAlive()) return;

        // бьём строго по откату
        if (mc.player.getAttackStrengthScale(0.5f) < 0.97f) return;

        if (!ensureWeapon(mc)) return; // выключились изнутри

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** true — можно бить; false — функция выключилась. */
    private static boolean ensureWeapon(Minecraft mc) {
        ItemStack main = mc.player.getMainHandItem();
        if (main.isEmpty()) return true;              // уже кулаки
        if (!main.isDamageableItem()) return true;    // нечему ломаться

        int remain = main.getMaxDamage() - main.getDamageValue();
        if (remain > DURABILITY_FLOOR) return true;   // прочность ещё ок

        // ищем замену в хотбаре
        Inventory inv = mc.player.getInventory();
        int best = -1, bestRemain = (int) DURABILITY_FLOOR;
        for (int i = 0; i < 9; i++) {
            if (i == inv.selected) continue;
            ItemStack st = inv.getItem(i);
            if (!st.isDamageableItem()) continue;
            int r = st.getMaxDamage() - st.getDamageValue();
            if (r > bestRemain) { bestRemain = r; best = i; }
        }
        if (best >= 0) {
            swapHotbar(mc, inv.selected, best);
            msg(mc, ModLang.fmt("ac.swapped", bestRemain));
            return true;
        }

        // замен нет: убираем почти сломанный предмет из руки и бьём кулаками
        int free = -1;
        for (int i = 0; i < 36 && free < 0; i++) {
            if (i == inv.selected) continue;
            if (inv.getItem(i).isEmpty()) free = i;
        }
        if (free >= 0) {
            swapHotbar(mc, inv.selected, free);
            fistMode = true;
            msg(mc, ModLang.fmt("ac.fist"));
            return true;
        }

        disable(mc, "ac.no_weapons");
        return false;
    }

    private static void swapHotbar(Minecraft mc, int a, int b) {
        var menu = mc.player.inventoryMenu;
        Integer ia = menuSlot(menu, a), ib = menuSlot(menu, b);
        if (ia == null || ib == null) return;
        menu.clicked(ia, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        menu.clicked(ib, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        menu.clicked(ia, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
    }

    private static Integer menuSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, int invIndex) {
        for (var sl : menu.slots) {
            if (sl.container instanceof Inventory inv && sl.getContainerSlot() == invIndex) {
                return sl.index;
            }
        }
        return null;
    }

    private static float wrapDeg(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static void msg(Minecraft mc, String text) {
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(
                    ModLang.fmt("helper.prefix") + " " + text), true);
        }
    }
}