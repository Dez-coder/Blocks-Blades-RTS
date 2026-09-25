package ru.dez.entity;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Custom GeckoLib-animated villager.
 *
 * <p>It extends vanilla {@link Villager}, so every existing RTS system
 * (professions, tasks, orders, skins, tags) keeps working unchanged, but it is
 * rendered with the mod's own GeckoLib model and animations instead of the
 * vanilla villager model.
 *
 * <p>Animations expected in {@code animations/npc.animation.json}:
 * idle, walk, run, attack, magic, rejected (mining/chopping), talk2, smeh.
 *
 * <p>Social behaviour: while a villager is walking it only plays the walk/run
 * animation. Two villagers standing very close to each other hold a proper
 * turn-based conversation: one talks ({@code talk2}) while the other listens
 * (idle), then they swap, and now and then both burst out laughing
 * ({@code smeh}). No other idle animations are used.
 *
 * <p>Any player order interrupts a conversation immediately (see
 * {@link #interruptSocialising()}), and {@code rejected} is played only while
 * the villager is actually breaking/mining a block - not while it walks to it.
 */
public class RTSNpc extends Villager implements GeoEntity {

    public static final int EMOTE_NONE = 0;
    public static final int EMOTE_TALK = 1;
    public static final int EMOTE_LAUGH = 2;

    /** How far a villager looks for someone to talk to (blocks). */
    private static final double CHAT_RANGE = 2.0D;
    /** Squared distance at which a conversation is broken off (~2.5 blocks). */
    private static final double CHAT_BREAK_SQR = 6.25D;

    // Conversation turns (driven by the villager that started the chat).
    private static final int PHASE_LEADER_TALKS = 0;
    private static final int PHASE_PARTNER_TALKS = 1;
    private static final int PHASE_BOTH_LAUGH = 2;
    private static final int PHASE_PAUSE = 3;

    private static final EntityDataAccessor<Boolean> DATA_WORKING =
            SynchedEntityData.defineId(RTSNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BREAKING =
            SynchedEntityData.defineId(RTSNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SHELTERED =
            SynchedEntityData.defineId(RTSNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_EMOTE =
            SynchedEntityData.defineId(RTSNpc.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Ticks until the next social check when not talking. */
    private int emoteTimer = 30;

    /** Server-side only: who we are talking to (null when alone). */
    private RTSNpc chatPartner;
    /** Server-side only: only the villager that started the chat drives it. */
    private boolean chatLeader;
    /** Server-side only: current conversation turn. */
    private int chatPhase = PHASE_LEADER_TALKS;
    /** Server-side only: ticks left in the current turn. */
    private int chatPhaseTimer;
    /** Server-side only: whose turn it is next (keeps the speakers alternating). */
    private boolean leaderSpokeLast = true;

    public RTSNpc(EntityType<? extends Villager> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WORKING, false);
        this.entityData.define(DATA_BREAKING, false);
        this.entityData.define(DATA_SHELTERED, false);
        this.entityData.define(DATA_EMOTE, EMOTE_NONE);
    }

    /**
     * True while the NPC has any standing order. A busy villager never starts
     * a chat and never gets its movement stopped by one.
     */
    public boolean isWorking() {
        return this.entityData.get(DATA_WORKING);
    }

    public void setWorking(boolean working) {
        this.entityData.set(DATA_WORKING, working);

        if (!working) {
            setBreaking(false);
            // Level the head again once the order is over.
            setXRot(0.0F);
        }
    }

    /**
     * True only while the villager is actually breaking/mining the block it
     * stands next to (this is what plays the {@code rejected} animation).
     */
    public boolean isBreaking() {
        return this.entityData.get(DATA_BREAKING);
    }

    public void setBreaking(boolean breaking) {
        this.entityData.set(DATA_BREAKING, breaking);
    }

    /**
     * True while the villager is hiding inside a shelter for the night. A
     * sheltered villager sits still, never chats and never wanders off.
     */
    public boolean isSheltered() {
        return this.entityData.get(DATA_SHELTERED);
    }

    public void setSheltered(boolean sheltered) {
        this.entityData.set(DATA_SHELTERED, sheltered);
    }

    /** Immediately ends any conversation (called when the player gives an order). */
    public void interruptSocialising() {
        stopChat();
    }

    /**
     * Turns the whole villager - body, head and pitch - towards the given point.
     * Used so it visibly looks at the block it is mining/chopping.
     */
    public void lookAtPoint(double x, double y, double z) {
        double dx = x - getX();
        double dy = y - getEyeY();
        double dz = z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;

        double flat = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) (-(Mth.atan2(dy, flat) * (180.0D / Math.PI)));

        setXRot(Mth.clamp(pitch, -80.0F, 80.0F));
        getLookControl().setLookAt(x, y, z, 90.0F, 90.0F);
    }

    public int getEmote() {
        return this.entityData.get(DATA_EMOTE);
    }

    public void setEmote(int emote) {
        this.entityData.set(DATA_EMOTE, emote);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            return;
        }

        // A villager hiding in the shelter all night neither chats nor wanders.
        if (isSheltered()) {
            stopChat();
            return;
        }

        // Work overrides socialising.
        if (isWorking()) {
            stopChat();
            return;
        }

        if (chatPartner != null) {
            if (!canKeepTalking()) {
                stopChat();
            } else {
                facePartner();

                // Only the initiator advances the conversation.
                if (chatLeader) {
                    advanceConversation();
                }

                return;
            }
        }

        if (emoteTimer > 0) {
            emoteTimer--;
            return;
        }

        setEmote(EMOTE_NONE);

        // Only villagers standing still start a chat: while walking the
        // walk/run animation must be the only thing playing.
        if (!isStandingStill()) {
            emoteTimer = 20;
            return;
        }

        RTSNpc partner = findChatPartner();

        if (partner == null) {
            emoteTimer = 30 + random.nextInt(70);
            return;
        }

        startChat(partner);
    }

    private boolean isStandingStill() {
        return getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 0.0015D;
    }

    private boolean canKeepTalking() {
        if (chatPartner == null || !chatPartner.isAlive() || chatPartner.isWorking()) {
            return false;
        }

        double distance = distanceToSqr(chatPartner);

        return distance > 0.2D && distance < CHAT_BREAK_SQR;
    }

    private RTSNpc findChatPartner() {
        List<RTSNpc> near = level().getEntitiesOfClass(RTSNpc.class,
                getBoundingBox().inflate(CHAT_RANGE, 1.5D, CHAT_RANGE),
                other -> other != this && other.isAlive() && !other.isWorking()
                        && other.chatPartner == null && other.getEmote() == EMOTE_NONE
                        && other.isStandingStill());

        RTSNpc best = null;
        double bestDistance = Double.MAX_VALUE;

        for (RTSNpc other : near) {
            double distance = distanceToSqr(other);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }

        return best;
    }

    private void startChat(RTSNpc partner) {
        chatPartner = partner;
        partner.chatPartner = this;

        chatLeader = true;
        partner.chatLeader = false;

        // The leader speaks first; the partner listens (EMOTE_NONE -> idle).
        setEmote(EMOTE_TALK);
        partner.setEmote(EMOTE_NONE);

        leaderSpokeLast = true;
        partner.leaderSpokeLast = true;

        // After the opening line it is the partner's turn.
        chatPhase = PHASE_PAUSE;
        chatPhaseTimer = 60 + random.nextInt(70);

        partner.chatPhase = PHASE_PAUSE;
        partner.chatPhaseTimer = 0;

        facePartner();
    }

    /**
     * Turn-based dialogue: leader talks, then the partner answers, then they
     * may both laugh, then a short pause decides whether to carry on.
     */
    private void advanceConversation() {
        if (chatPhaseTimer > 0) {
            chatPhaseTimer--;
            return;
        }

        switch (chatPhase) {
            case PHASE_LEADER_TALKS -> {
                // Our turn: we speak, the partner listens.
                setEmote(EMOTE_TALK);
                chatPartner.setEmote(EMOTE_NONE);
                chatPhase = PHASE_PAUSE;
                chatPhaseTimer = 60 + random.nextInt(70);
            }
            case PHASE_PARTNER_TALKS -> {
                // The partner's turn: they speak, we listen.
                setEmote(EMOTE_NONE);
                chatPartner.setEmote(EMOTE_TALK);
                chatPhase = PHASE_PAUSE;
                chatPhaseTimer = 60 + random.nextInt(70);
            }
            case PHASE_BOTH_LAUGH -> {
                // Now and then they both crack up at the same time.
                setEmote(EMOTE_LAUGH);
                chatPartner.setEmote(EMOTE_LAUGH);
                chatPhase = PHASE_PAUSE;
                chatPhaseTimer = 30 + random.nextInt(30);
            }
            default -> {
                // Short silent beat: sometimes the chat ends here, sometimes
                // they laugh, otherwise the next speaker takes their turn.
                setEmote(EMOTE_NONE);
                chatPartner.setEmote(EMOTE_NONE);

                int roll = random.nextInt(10);

                if (roll < 2) {
                    stopChat();
                    return;
                }

                if (roll < 5) {
                    chatPhase = PHASE_BOTH_LAUGH;
                } else {
                    chatPhase = leaderSpokeLast ? PHASE_PARTNER_TALKS : PHASE_LEADER_TALKS;
                    leaderSpokeLast = !leaderSpokeLast;
                }

                chatPhaseTimer = 20 + random.nextInt(40);
            }
        }
    }

    /** Both villagers stand still and look straight at each other. */
    private void facePartner() {
        getNavigation().stop();
        chatPartner.getNavigation().stop();

        turnTowards(chatPartner);
        chatPartner.turnTowards(this);
    }

    private void turnTowards(RTSNpc other) {
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setXRot(0.0F);
        getLookControl().setLookAt(other, 90.0F, 90.0F);
    }

    private void stopChat() {
        if (chatPartner != null) {
            RTSNpc other = chatPartner;
            chatPartner = null;

            other.chatPartner = null;
            other.chatLeader = false;
            other.chatPhase = PHASE_LEADER_TALKS;
            other.chatPhaseTimer = 0;
            other.leaderSpokeLast = true;
            other.emoteTimer = 40;

            if (other.getEmote() != EMOTE_NONE) {
                other.setEmote(EMOTE_NONE);
            }
        }

        chatLeader = false;
        chatPhase = PHASE_LEADER_TALKS;
        chatPhaseTimer = 0;
        leaderSpokeLast = true;

        if (getEmote() != EMOTE_NONE) {
            setEmote(EMOTE_NONE);
        }

        emoteTimer = 40;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RtsEmote", getEmote());
        tag.putBoolean("RtsWorking", isWorking());
        tag.putBoolean("RtsBreaking", isBreaking());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setEmote(tag.getInt("RtsEmote"));

        if (tag.contains("RtsWorking")) {
            setWorking(tag.getBoolean("RtsWorking"));
        }

        if (tag.contains("RtsBreaking")) {
            setBreaking(tag.getBoolean("RtsBreaking"));
        }
    }

    // === GeckoLib ===

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("talk2");
    private static final RawAnimation LAUGH = RawAnimation.begin().thenLoop("smeh");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("rejected");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");

    public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    public static final RawAnimation MAGIC = RawAnimation.begin().thenPlay("magic");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::mainState)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("magic", MAGIC));
    }

    private PlayState mainState(AnimationState<RTSNpc> state) {
        // "rejected" only while a block is actually being broken/chopped.
        if (isBreaking()) {
            return state.setAndContinue(WORK);
        }

        // Movement always wins: while walking only walk/run is played.
        if (state.isMoving()) {
            return state.setAndContinue(isSprinting() ? RUN : WALK);
        }

        // Sitting inside the shelter for the night.
        if (isSheltered()) {
            return state.setAndContinue(SIT);
        }

        // Standing still: only then may a social emote play. EMOTE_NONE (which
        // is what the "listening" villager uses) simply falls through to idle.
        int emote = getEmote();

        if (emote == EMOTE_TALK) {
            return state.setAndContinue(TALK);
        }

        if (emote == EMOTE_LAUGH) {
            return state.setAndContinue(LAUGH);
        }

        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
