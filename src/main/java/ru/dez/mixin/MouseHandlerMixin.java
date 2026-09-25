package ru.dez.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dez.client.camera.RTSCameraManager;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void blocksBlades$preventGrabInRts(CallbackInfo ci) {
        if (RTSCameraManager.isEnabled()) {
            ci.cancel();
        }
    }
}