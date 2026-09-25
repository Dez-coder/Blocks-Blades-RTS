package ru.dez.client.camera;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side bookkeeping for the RTS camera mode.
 *
 * <p>The client detaches its camera and keeps the (hidden) player entity parked
 * at the camera focus point so the server streams and generates chunks around
 * it. This class remembers each player's real position, gravity and invisibility
 * state so everything can be restored on exit / logout.
 *
 * <p>The real position is mirrored into the player's own saved data as well.
 * While in RTS mode the parked (high above the focus) position is what would be
 * written to disk, which is why a player used to respawn in mid-air after a
 * relog or a world restart. {@link #restoreOnLogin(ServerPlayer)} undoes that.
 */
public final class RTSCameraMode {

    private static final String HOME_FLAG = "rtsHomeActive";
    private static final String HOME_X = "rtsHomeX";
    private static final String HOME_Y = "rtsHomeY";
    private static final String HOME_Z = "rtsHomeZ";
    private static final String HOME_NO_GRAVITY = "rtsHomeNoGravity";
    private static final String HOME_INVISIBLE = "rtsHomeInvisible";

    private static final Map<UUID, State> STATES = new HashMap<>();

    private RTSCameraMode() {
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && STATES.containsKey(player.getUUID());
    }

    public static void enter(ServerPlayer player) {
        if (player == null || STATES.containsKey(player.getUUID())) {
            return;
        }

        State state = new State();
        state.pos = player.position();
        state.noGravity = player.isNoGravity();
        state.invisible = player.isInvisible();
        STATES.put(player.getUUID(), state);

        // Remember the real spot in the player's persistent data too: the
        // parked position (focus + lift) is what gets saved with the player.
        CompoundTag data = player.getPersistentData();
        data.putBoolean(HOME_FLAG, true);
        data.putDouble(HOME_X, state.pos.x);
        data.putDouble(HOME_Y, state.pos.y);
        data.putDouble(HOME_Z, state.pos.z);
        data.putBoolean(HOME_NO_GRAVITY, state.noGravity);
        data.putBoolean(HOME_INVISIBLE, state.invisible);

        player.setNoGravity(true);
        player.setInvisible(true);
        player.fallDistance = 0.0F;
    }

    public static void exit(ServerPlayer player) {
        if (player == null) {
            return;
        }

        State state = STATES.remove(player.getUUID());

        if (state == null) {
            return;
        }

        restore(player, state.pos.x, state.pos.y, state.pos.z, state.noGravity, state.invisible);
        clearHome(player);
    }

    /**
     * Called when a player joins. If they logged out (or the world was closed)
     * while the RTS camera was active, this puts them back where they really
     * stood instead of leaving them floating in the sky.
     */
    public static void restoreOnLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }

        CompoundTag data = player.getPersistentData();

        if (!data.getBoolean(HOME_FLAG)) {
            return;
        }

        restore(player,
                data.getDouble(HOME_X),
                data.getDouble(HOME_Y),
                data.getDouble(HOME_Z),
                data.getBoolean(HOME_NO_GRAVITY),
                data.getBoolean(HOME_INVISIBLE));

        clearHome(player);
    }

    private static void restore(ServerPlayer player, double x, double y, double z,
                                boolean noGravity, boolean invisible) {
        player.setNoGravity(noGravity);
        player.setInvisible(invisible);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.teleportTo(x, y, z);
    }

    private static void clearHome(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(HOME_FLAG);
        data.remove(HOME_X);
        data.remove(HOME_Y);
        data.remove(HOME_Z);
        data.remove(HOME_NO_GRAVITY);
        data.remove(HOME_INVISIBLE);
    }

    private static final class State {
        Vec3 pos;
        boolean noGravity;
        boolean invisible;
    }
}
