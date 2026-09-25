package ru.dez.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Free-flying RTS camera.
 *
 * <p>It is completely independent from the player: the view is driven by its
 * own focus point, distance and altitude, while the (invisible) player entity is
 * parked at the focus so the server keeps loading chunks around it.
 *
 * <p>The movement is deliberately "cinematic": panning uses momentum (the camera
 * accelerates, glides and gently coasts to a stop), Q/E spin up smoothly, the
 * wheel/height ease towards their target instead of snapping, and a still camera
 * slowly breathes. That is what makes it feel unlike a plain spectator camera.
 */
public class RTSCameraManager {

    private static boolean enabled = false;

    private static double targetX, targetY, targetZ;
    private static double prevTargetX, prevTargetY, prevTargetZ;

    private static float yaw = 0f;
    private static float prevYaw = 0f;

    private static float pitch = 55f;
    private static float prevPitch = 55f;

    private static double distance = 15.0;
    private static double prevDistance = 15.0;

    /** Camera altitude relative to the focus - free, independent of the player. */
    private static double height = 16.0;
    private static double prevHeight = 16.0;

    // === Cinematic state: everything eases instead of snapping ===

    /** Pan momentum, so the camera glides instead of jerking to a stop. */
    private static double momentumX, momentumZ;
    /** Spin momentum for Q/E turning. */
    private static float spinMomentum;
    /** Where the wheel / Space / Shift want the camera to end up. */
    private static double wantedDistance = 15.0;
    private static double wantedHeight = 16.0;
    /** Slow idle breathing, so a standing camera does not look frozen. */
    private static double breatheTime;
    private static double breatheOffset;

    private static final double BASE_SPEED = 1.1;
    private static final double ZOOM_SPEED = 1.6;
    private static final float ROTATE_SPEED = 1.2f;
    private static final double MIN_DISTANCE = 4.0;
    private static final double MAX_DISTANCE = 60.0;
    private static final double MIN_HEIGHT = 2.0;
    private static final double MAX_HEIGHT = 120.0;

    /**
     * How far above the camera focus the hidden player is parked. Large enough
     * to clear terrain, but well under the server's 100-block "moved too fast"
     * limit so the very first tick is accepted.
     */
    private static final double ANCHOR_LIFT = 60.0;

    private static final double HEIGHT_SPEED = 1.6;

    // === Feel tuning ===
    private static final double PAN_ACCEL = 0.26;
    private static final double PAN_DAMPING = 0.78;
    private static final float SPIN_ACCEL = 0.30f;
    private static final float SPIN_DAMPING = 0.80f;
    private static final double ZOOM_EASE = 0.16;
    private static final double HEIGHT_EASE = 0.16;
    private static final double BREATHE_AMPLITUDE = 0.22;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) return;

        if (enabled) {
            targetX = prevTargetX = mc.player.getX();
            targetY = prevTargetY = mc.player.getY() + 1.0;
            targetZ = prevTargetZ = mc.player.getZ();

            yaw = prevYaw = mc.player.getYRot();
            pitch = prevPitch = 55f;
            distance = prevDistance = wantedDistance = 15.0;
            height = prevHeight = wantedHeight = 16.0;

            resetMotion();

            mc.mouseHandler.releaseMouse();

            RTSClientState.selecting = false;
            RTSClientState.selectionStart = null;
            RTSClientState.placementMode = false;
            RTSClientState.selectedVillagers.clear();
            RTSPlacementHandler.clearPhantoms();

            // Ask the server to re-send active construction sites so a
            // half-built schematic does not vanish from the preview when
            // re-entering RTS mode (the client list is memory-only).
            RTSNetwork.INSTANCE.sendToServer(new RTSRequestSitesPacket());

            // Tell the server we parked the player as the chunk anchor.
            RTSNetwork.INSTANCE.sendToServer(new RTSModePacket(true));
        } else {
            mc.mouseHandler.grabMouse();

            // Give the player back their real position and visibility.
            RTSNetwork.INSTANCE.sendToServer(new RTSModePacket(false));

            resetMotion();

            RTSClientState.selecting = false;
            RTSClientState.selectionStart = null;
            RTSClientState.placementMode = false;
            RTSPlacementHandler.clearPhantoms();
        }
    }

    private static void resetMotion() {
        momentumX = 0.0;
        momentumZ = 0.0;
        spinMomentum = 0f;
        breatheTime = 0.0;
        breatheOffset = 0.0;
    }

    public static void tick() {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) return;

        prevTargetX = targetX;
        prevTargetY = targetY;
        prevTargetZ = targetZ;

        prevYaw = yaw;
        prevPitch = pitch;
        prevDistance = distance;
        prevHeight = height;

        if (mc.screen != null) return;

        long window = mc.getWindow().getWindow();

        double speed = BASE_SPEED * (0.5 + distance / 15.0);

        double yawRad = Math.toRadians(yaw);

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);

        double inputX = 0.0;
        double inputZ = 0.0;

        if (isDown(window, GLFW.GLFW_KEY_W)) {
            inputX += forwardX;
            inputZ += forwardZ;
        }

        if (isDown(window, GLFW.GLFW_KEY_S)) {
            inputX -= forwardX;
            inputZ -= forwardZ;
        }

        if (isDown(window, GLFW.GLFW_KEY_A)) {
            inputX += leftX;
            inputZ += leftZ;
        }

        if (isDown(window, GLFW.GLFW_KEY_D)) {
            inputX -= leftX;
            inputZ -= leftZ;
        }

        // Accelerate towards the input, then always damp: the camera picks up
        // speed smoothly and coasts to a stop instead of stopping dead.
        double inputLength = Math.sqrt(inputX * inputX + inputZ * inputZ);

        if (inputLength > 1.0E-4) {
            momentumX += (inputX / inputLength) * PAN_ACCEL;
            momentumZ += (inputZ / inputLength) * PAN_ACCEL;
        }

        momentumX *= PAN_DAMPING;
        momentumZ *= PAN_DAMPING;

        targetX += momentumX * speed;
        targetZ += momentumZ * speed;

        // Q/E spin up smoothly as well.
        if (isDown(window, GLFW.GLFW_KEY_Q)) {
            spinMomentum -= SPIN_ACCEL;
        }

        if (isDown(window, GLFW.GLFW_KEY_E)) {
            spinMomentum += SPIN_ACCEL;
        }

        spinMomentum *= SPIN_DAMPING;
        yaw += spinMomentum * ROTATE_SPEED;

        // Space / Shift raise and lower the camera (the pitch stays fixed, so
        // the view does not rotate while going up/down).
        if (isDown(window, GLFW.GLFW_KEY_SPACE)) {
            wantedHeight += HEIGHT_SPEED * 1.5;
        }

        if (isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)) {
            wantedHeight -= HEIGHT_SPEED * 1.5;
        }

        wantedHeight = clamp(wantedHeight, MIN_HEIGHT, MAX_HEIGHT);
        wantedDistance = clamp(wantedDistance, MIN_DISTANCE, MAX_DISTANCE);

        // Eased dolly / lift.
        distance += (wantedDistance - distance) * ZOOM_EASE;
        height += (wantedHeight - height) * HEIGHT_EASE;

        // Gentle breathing, fading out while the camera is actually moving.
        double panAmount = Math.min(1.0, Math.sqrt(momentumX * momentumX + momentumZ * momentumZ) * 12.0);
        breatheTime += 0.045;
        breatheOffset = Math.sin(breatheTime) * BREATHE_AMPLITUDE * (1.0 - panAmount);
    }

    public static void zoom(double delta) {
        if (!enabled) return;

        wantedDistance = clamp(wantedDistance - delta * ZOOM_SPEED, MIN_DISTANCE, MAX_DISTANCE);
    }

    private static boolean isDown(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }

    /**
     * Keeps the (hidden) player at the camera focus, high above the world, so
     * the server streams and generates chunks around wherever the camera looks.
     * This is what makes the world load while panning around in RTS mode.
     */
    public static void applyPlayerAnchor() {
        if (!enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        mc.player.setPos(targetX, targetY + ANCHOR_LIFT, targetZ);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        mc.player.fallDistance = 0.0F;
    }

    public static Vec3 computeEyePosition(float partialTick) {
        double ix = lerp(prevTargetX, targetX, partialTick);
        double iy = lerp(prevTargetY, targetY, partialTick) + breatheOffset;
        double iz = lerp(prevTargetZ, targetZ, partialTick);

        float iyaw = getInterpolatedYaw(partialTick);
        double idist = lerp(prevDistance, distance, partialTick);
        double iheight = lerp(prevHeight, height, partialTick);

        double yawRad = Math.toRadians(iyaw);

        double eyeX = ix + Math.sin(yawRad) * idist;
        double eyeY = iy + iheight;
        double eyeZ = iz - Math.cos(yawRad) * idist;

        return new Vec3(eyeX, eyeY, eyeZ);
    }

    public static float getInterpolatedYaw(float partialTick) {
        return lerpAngle(prevYaw, yaw, partialTick);
    }

    public static float getInterpolatedPitch(float partialTick) {
        return (float) lerp(prevPitch, pitch, partialTick);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;

        while (diff < -180f) diff += 360f;
        while (diff > 180f) diff -= 360f;

        return a + diff * t;
    }
}
