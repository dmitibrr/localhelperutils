package com.dmitibrr.localhelperutils.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class WorldHighlightRenderer {
    private WorldHighlightRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        PoseStack pose = event.getPoseStack();
        Camera cam = event.getCamera();
        if (pose == null) return;
        String dim = mc.level.dimension().location().toString();

        pose.pushPose();
        pose.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);

        if (ModConfig.get().selectionMode && !HelperState.selected.isEmpty()) {
            for (String key : HelperState.selected) {
                if (!key.startsWith(dim + "|")) continue;
                BlockPos pos = ContainerKey.parsePos(key);
                if (pos != null) box(pose, boxAt(pos), 0.15f, 1.0f, 0.2f, 0.85f);
            }
        }

        String cur = StorageTaskExecutor.get().currentKey();
        if (cur != null) {
            BlockPos pos = ContainerKey.parsePos(cur);
            if (pos != null) {
                double t = (mc.level.getGameTime() % 40) / 40.0 * Math.PI * 2;
                float pulse = 0.5f + 0.5f * (float) Math.sin(t);
                box(pose, boxAt(pos), 1.0f, 0.85f, 0.1f, 0.6f + 0.4f * pulse);
            }
        }

        if (HelperState.searchItem != null) {
            for (String key : StorageDB.get().findContainers(HelperState.searchItem)) {
                if (!key.startsWith(dim + "|")) continue;
                BlockPos pos = ContainerKey.parsePos(key);
                if (pos != null) box(pose, boxAt(pos), 1.0f, 0.25f, 0.25f, 0.8f);
            }
        }

        pose.popPose();
    }

    public static void renderHud(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int cx = gui.guiWidth() / 2;
        int y = 4;
        if (StorageTaskExecutor.get().isActive()) {
            String s = StorageTaskExecutor.get().status();
            if (s != null && !s.isEmpty()) {
                gui.drawCenteredString(mc.font, "§e" + s, cx, y, 0xFFFFFF);
                y += 10;
            }
            String k = StorageTaskExecutor.get().currentKey();
            if (k != null) {
                BlockPos pos = ContainerKey.parsePos(k);
                if (pos != null && mc.level != null) {
                    int dist = (int) Math.sqrt(mc.player.blockPosition().distSqr(pos));
                    gui.drawCenteredString(mc.font, "§eЦель: " + dist + " м", cx, y, 0xFFFFFF);
                }
            }
            return;
        }
        if (ModConfig.get().selectionMode) {
            gui.drawCenteredString(mc.font,
                    "§aРежимъ выбора: " + HelperState.selected.size() + " сундуковъ", cx, y, 0xFFFFFF);
        }
    }

    private static AABB boxAt(BlockPos pos) {
        return new AABB(pos).inflate(0.08);
    }

    private static void box(PoseStack pose, AABB box, float r, float g, float b, float a) {
        outline(pose, box, r, g, b, a);
        fill(pose, box, r, g, b, a * 0.12f);
    }

    private static void outline(PoseStack pose, AABB box, float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bb = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        LevelRenderer.renderLineBox(pose, bb, box, r, g, b, a);
        BufferUploader.drawWithShader(bb.build());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void fill(PoseStack pose, AABB box, float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bb = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int cr = (int) (r * 255), cg = (int) (g * 255), cb = (int) (b * 255), ca = (int) (a * 255);
        addQuad(bb, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, cr, cg, cb, ca);
        addQuad(bb, box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, cr, cg, cb, ca);
        addQuad(bb, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ, cr, cg, cb, ca);
        addQuad(bb, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, cr, cg, cb, ca);
        addQuad(bb, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, cr, cg, cb, ca);
        addQuad(bb, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, cr, cg, cb, ca);
        BufferUploader.drawWithShader(bb.build());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void addQuad(BufferBuilder bb, double x1, double y1, double z1,
                                double x2, double y2, double z2, int r, int g, int b, int a) {
        VertexConsumer vc = bb;
        vc.addVertex((float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        vc.addVertex((float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
        vc.addVertex((float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        vc.addVertex((float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
    }
}