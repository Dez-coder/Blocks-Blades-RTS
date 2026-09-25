package ru.dez.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RTSTargetHighlightRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!RTSCameraManager.isEnabled()) {
            return;
        }

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        drawBox(pose, cam, RTSTaskClient.moveTarget, 0.0F, 1.0F, 0.2F, 0.9F);
        drawBox(pose, cam, RTSTaskClient.mineTarget, 1.0F, 0.25F, 0.25F, 0.95F);
    }

    private static void drawBox(PoseStack pose, Vec3 cam, BlockPos pos, float r, float g, float b, float a) {
        if (pos == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.level.getBlockState(pos).isAir()) {
            return;
        }

        AABB box = new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1.0,
                pos.getY() + 1.0,
                pos.getZ() + 1.0
        );

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, a);
        LevelRenderer.renderLineBox(pose, consumer, box.inflate(0.002), r, g, b, a);

        pose.popPose();

        buffers.endBatch(RenderType.lines());
    }
}