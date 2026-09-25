package ru.dez.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dez.client.camera.RTSCameraManager;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void blocksBlades$applyRtsCamera(BlockGetter level, Entity entity, boolean detached,
                                             boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (RTSCameraManager.isEnabled()) {
            this.setPosition(RTSCameraManager.computeEyePosition(partialTick));
            this.setRotation(
                    RTSCameraManager.getInterpolatedYaw(partialTick),
                    RTSCameraManager.getInterpolatedPitch(partialTick)
            );
        }
    }
}