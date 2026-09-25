package ru.dez.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blocks_blades", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RTSBuildPreviewRenderer {

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

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (RTSBuildClient.ClientSite site : RTSBuildClient.sites.values()) {
            RTSBuilding building = RTSBuildings.get(site.buildingId);

            if (building != null && building.schematic != null) {
                drawSchematicUniform(pose, consumer, mc, building.schematic, site.pos, 0.9F, 0.75F, 0.2F, 0.35F);
            }
        }

        if (RTSBuildClient.placing && RTSBuildClient.previewPos != null && RTSBuildClient.selectedBuilding != null) {
            RTSBuilding building = RTSBuildings.get(RTSBuildClient.selectedBuilding);

            if (building != null && building.schematic != null) {
                drawPlacementSchematic(pose, consumer, mc, building.schematic, RTSBuildClient.previewPos);
            }
        }

        // Outline the whole building the cursor is over, not just one block.
        BlockHitResult hover = RTSCameraUtil.getMouseBlockHit(mc.gameRenderer.getMainCamera(), mc.level);

        if (hover != null) {
            RTSBuildingClient.Entry entry = RTSBuildingClient.find(
                    mc.level.dimension().location().toString(), hover.getBlockPos());

            if (entry != null) {
                RTSBuilding building = RTSBuildings.get(entry.id);

                if (building != null && building.schematic != null) {
                    // Schematics are centred: bounds go from origin+min to origin+max.
                    AABB box = new AABB(
                            entry.x + building.schematic.minX - 0.02D,
                            entry.y + building.schematic.minY - 0.02D,
                            entry.z + building.schematic.minZ - 0.02D,
                            entry.x + building.schematic.maxX + 1.02D,
                            entry.y + building.schematic.maxY + 1.02D,
                            entry.z + building.schematic.maxZ + 1.02D);

                    LevelRenderer.renderLineBox(pose, consumer, box, 0.25F, 1.0F, 0.45F, 1.0F);
                }
            }
        }

        pose.popPose();

        buffers.endBatch(RenderType.lines());
    }

    private static void drawSchematicUniform(PoseStack pose, VertexConsumer consumer, Minecraft mc, RTSSchematic schematic, BlockPos origin, float r, float g, float b, float a) {
        for (RTSSchematic.Entry entry : schematic.entries) {
            BlockPos pos = origin.offset(entry.x, entry.y, entry.z);

            if (!mc.level.isEmptyBlock(pos)) {
                continue;
            }

            AABB box = new AABB(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0,
                    pos.getY() + 1.0,
                    pos.getZ() + 1.0
            );

            LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, a);
        }
    }

    private static void drawPlacementSchematic(PoseStack pose, VertexConsumer consumer, Minecraft mc, RTSSchematic schematic, BlockPos origin) {
        for (RTSSchematic.Entry entry : schematic.entries) {
            BlockPos pos = origin.offset(entry.x, entry.y, entry.z);

            AABB box = new AABB(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0,
                    pos.getY() + 1.0,
                    pos.getZ() + 1.0
            );

            boolean blocked = !mc.level.isEmptyBlock(pos);

            float r = 0.15F;
            float g = 1.0F;
            float b = 0.35F;

            if (blocked) {
                r = 1.0F;
                g = 0.25F;
                b = 0.25F;
            } else if (entry.block == Blocks.GLASS) {
                r = 0.55F;
                g = 0.85F;
                b = 1.0F;
            } else if (entry.block == Blocks.COBBLESTONE) {
                r = 0.75F;
                g = 0.75F;
                b = 0.75F;
            } else if (entry.block == Blocks.OAK_LOG) {
                r = 0.55F;
                g = 0.35F;
                b = 0.15F;
            }

            LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, 0.55F);
        }
    }
}