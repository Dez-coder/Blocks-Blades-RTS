package ru.dez.entity;

import net.minecraft.world.entity.monster.Monster;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Animations shared by the RTS combatants (warrior / arbalest / summoner).
 *
 * <p>The animation file is the same one used by villagers
 * ({@code animations/npc.animation.json}); only the texture differs.
 */
public final class RTSRaiderAnims {

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    public static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    public static final RawAnimation MAGIC = RawAnimation.begin().thenPlay("magic");

    private RTSRaiderAnims() {
    }

    /** Builds the single animation controller used by every combatant. */
    public static <T extends Monster & GeoAnimatable> AnimationController<T> controller(T entity) {
        return new AnimationController<T>(entity, "main", 4, state -> mainState(state))
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("magic", MAGIC);
    }

    /** Movement/idle loop. Attack and magic are played as one-shot triggers. */
    public static <T extends Monster & GeoAnimatable> PlayState mainState(AnimationState<T> state) {
        if (state.isMoving()) {
            return state.setAndContinue(state.getAnimatable().isSprinting() ? RUN : WALK);
        }

        return state.setAndContinue(IDLE);
    }
}
