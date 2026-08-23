package com.dmitibrr.localhelperutils.client;

import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModEvents {
    private ModEvents() {}

    @EventBusSubscriber(modid = LocalHelperUtils.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @net.neoforged.bus.api.SubscribeEvent
        public static void onKeyMappings(RegisterKeyMappingsEvent event) {
            ModKeybinds.register(event);
        }
    }

    @EventBusSubscriber(modid = LocalHelperUtils.MODID, value = Dist.CLIENT)
    public static class GameEvents {
        @net.neoforged.bus.api.SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (ModKeybinds.OPEN_MENU.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new com.dmitibrr.localhelperutils.client.gui.MainMenuScreen());
                }
            }
            while (ModKeybinds.TOGGLE_FARM.consumeClick()) {
                ModConfig cfg = ModConfig.get();
                cfg.farmEnabled = !cfg.farmEnabled;
                cfg.save();
                mc.player.displayClientMessage(ModLang.c(
                        cfg.farmEnabled ? "farm.on" : "farm.off"), true);
            }
            while (ModKeybinds.ADD_CROP.consumeClick()) {
                FarmHelper.recordAimedCrop(mc);
            }
            PlacementHelper.tick(mc);
            FarmHelper.tick(mc);
            SmartPickup.tick(mc);
            AutoDoors.tick(mc);
            StorageTaskExecutor.get().tick(mc);
            while (ModKeybinds.DOOR_RULE.consumeClick()) {
                AutoDoors.cycleAimedRule(mc);
            }
            while (ModKeybinds.AUTOCLICKER.consumeClick()) {
                AutoClicker.toggle(mc);
            }
            while (ModKeybinds.AUTOLOG.consumeClick()) {
                AutoLog.cycle(mc);
            }
            AutoClicker.tick(mc);
            AutoLog.tick(mc);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getSide() != LogicalSide.CLIENT) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            BlockState state = mc.level.getBlockState(event.getPos());

            // Фермерство: ПКМ по готовой культуре из базы — собрать и пересадить.
            // ВАЖНО: до проверки на контейнер — культуры не имеют MenuProvider.
            if (!PlacementHelper.SYNTHETIC_USE && StorageTaskExecutor.get().isIdle()
                    && ModConfig.get().farmEnabled && !mc.player.isShiftKeyDown()
                    && FarmHelper.isReady(state)
                    && FarmDB.get().getCrop(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()) != null) {
                if (FarmHelper.tryHarvest(mc, state, event.getPos(), event.getFace())) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    return;
                }
            }

            if (state.getMenuProvider(mc.level, event.getPos()) == null) return;

            HelperState.lastDim = mc.level.dimension();
            HelperState.lastPos = event.getPos().immutable();

            if (StorageTaskExecutor.get().isIdle() && ModConfig.get().selectionMode
                    && !PlacementHelper.SYNTHETIC_USE) {
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (ModConfig.get().categoryAllowed(blockId)) {
                    String key = ContainerKey.blockKey(mc.level.dimension(), event.getPos());
                    boolean added;
                    if (!HelperState.selected.remove(key)) {
                        HelperState.selected.add(key);
                        added = true;
                    } else {
                        added = false;
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    mc.player.displayClientMessage(com.dmitibrr.localhelperutils.client.ModLang.c(
                            added ? "sel.added" : "sel.removed", HelperState.selected.size()), true);
                    mc.player.playSound(
                            added ? net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP
                                  : net.minecraft.sounds.SoundEvents.ITEM_BREAK,
                            0.5f, added ? 1.5f : 0.7f);
                }
            }
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onScreenOpen(ScreenEvent.Opening event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
                return;
            }
            if (HelperState.lastPos == null || HelperState.lastDim == null) return;
            if (!HelperState.lastDim.equals(mc.level.dimension())) return;
            String key = ContainerKey.blockKey(HelperState.lastDim, HelperState.lastPos);
            if (StorageTaskExecutor.get().isActive()) {
                return;
            }
            if (HelperState.selected.contains(key) || StorageDB.get().getEntry(key) != null) {
                recordContainer(mc, key);
            }
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            WorldHighlightRenderer.render(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onScreenKey(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Post event) {
            if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) {
                return;
            }
            var hovered = hoveredSlotOf(cs);
            if (hovered == null || hovered.getItem().isEmpty()) return;
            if (!ModKeybinds.MARK_IMPORTANT.matches(event.getKeyCode(), event.getScanCode())) return;
            String key = ItemKey.stackKey(hovered.getItem());
            boolean added = Marks.get().toggle(key);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(ModLang.c(
                        added ? "marks.added" : "marks.removed",
                        ItemKey.shortName(key)), true);
            }
            event.setCanceled(true);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onScreenRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) {
                return;
            }
            var marks = Marks.get().items();
            if (marks.isEmpty()) return;
            int[] origin = screenOrigin(cs);
            if (origin == null) return;
            var gui = event.getGuiGraphics();
            for (var sl : cs.getMenu().slots) {
                ItemStack st = sl.getItem();
                if (st.isEmpty()) continue;
                if (!Marks.get().isMarked(ItemKey.stackKey(st))) continue;
                int x = origin[0] + sl.x - 1, y = origin[1] + sl.y - 1;
                gui.fill(x, y, x + 18, y + 1, 0xFFFFD24A);
                gui.fill(x, y + 17, x + 18, y + 18, 0xFFFFD24A);
                gui.fill(x, y, x + 1, y + 18, 0xFFFFD24A);
                gui.fill(x + 17, y, x + 18, y + 18, 0xFFFFD24A);
                gui.fill(x + 1, y + 1, x + 17, y + 17, 0x28FFD24A);
            }
        }

        private static java.lang.reflect.Field LEFT_POS, TOP_POS, HOVERED;

        @SuppressWarnings("unchecked")
        private static net.minecraft.world.inventory.Slot hoveredSlotOf(
                net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) {
            try {
                if (HOVERED == null) {
                    HOVERED = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class
                            .getDeclaredField("hoveredSlot");
                    HOVERED.setAccessible(true);
                }
                return (net.minecraft.world.inventory.Slot) HOVERED.get(cs);
            } catch (Exception e) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static int[] screenOrigin(net.minecraft.client.gui.screens.Screen scr) {
            try {
                if (LEFT_POS == null) {
                    LEFT_POS = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class
                            .getDeclaredField("leftPos");
                    TOP_POS = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class
                            .getDeclaredField("topPos");
                    LEFT_POS.setAccessible(true);
                    TOP_POS.setAccessible(true);
                }
                return new int[]{LEFT_POS.getInt(scr), TOP_POS.getInt(scr)};
            } catch (Exception e) {
                return null;
            }
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            WorldHighlightRenderer.renderHud(event.getGuiGraphics());
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            HelperState.reset();
            StorageTaskExecutor.get().stop();
            PlacementHelper.reset();
        }

        private static void recordContainer(Minecraft mc, String key) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu == null) return;
            Map<String, Integer> items = new LinkedHashMap<>();
            for (Slot s : menu.slots) {
                if (s.container == mc.player.getInventory()) continue;
                ItemStack st = s.getItem();
                if (!st.isEmpty()) {
                    items.merge(ItemKey.stackKey(st), st.getCount(), Integer::sum);
                }
            }
            StorageDB.Entry e = StorageDB.get().getEntry(key);
            if (e == null) {
                BlockPos pos = ContainerKey.parsePos(key);
                if (pos == null) return;
                String label = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
                e = StorageDB.get().blockEntry(mc.level.dimension().location().toString(),
                        new int[]{pos.getX(), pos.getY(), pos.getZ()}, label);
            }
            StorageDB.get().updateItems(e, items);
        }
    }
}