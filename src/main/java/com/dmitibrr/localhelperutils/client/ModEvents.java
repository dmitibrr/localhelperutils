package com.dmitibrr.localhelperutils.client;

import com.dmitibrr.localhelperutils.LocalHelperUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                cfg.farmEnabled ? "§aФермерство: вкл" : "§7Фермерство: выкл"), true);
            }
            while (ModKeybinds.ADD_CROP.consumeClick()) {
                FarmHelper.recordAimedCrop(mc);
            }
            PlacementHelper.tick(mc);
            FarmHelper.tick(mc);
            StorageTaskExecutor.get().tick(mc);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getSide() != LogicalSide.CLIENT) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            BlockState state = mc.level.getBlockState(event.getPos());
            if (state.getMenuProvider(mc.level, event.getPos()) == null) return;

            HelperState.lastDim = mc.level.dimension();
            HelperState.lastPos = event.getPos().immutable();

            // Фермерство: ПКМ по готовой культуре из базы — собрать и пересадить
            if (!PlacementHelper.SYNTHETIC_USE && StorageTaskExecutor.get().isIdle()
                    && ModConfig.get().farmEnabled && !mc.player.isShiftKeyDown()
                    && FarmHelper.isReady(state)
                    && FarmDB.get().getCrop(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()) != null) {
                if (FarmHelper.tryHarvest(mc, state, event.getPos(), event.getFace())) {
                    event.setCanceled(true);
                    return;
                }
            }

            if (StorageTaskExecutor.get().isIdle() && ModConfig.get().selectionMode
                    && !PlacementHelper.SYNTHETIC_USE) {
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (ModConfig.get().categoryAllowed(blockId)) {
                    String key = ContainerKey.blockKey(mc.level.dimension(), event.getPos());
                    if (!HelperState.selected.remove(key)) {
                        HelperState.selected.add(key);
                    }
                    event.setCanceled(true);
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
                    String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    items.merge(id, st.getCount(), Integer::sum);
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