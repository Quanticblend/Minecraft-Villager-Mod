package com.villagerplus;

/**
 * Tuning knobs. Change these and rebuild to adjust behaviour.
 */
public final class Config {
    private Config() {}

    // ---- Wall / stuck handling --------------------------------------------

    /** Ticks a villager can make almost no progress before it reacts (20 ticks = 1 second). */
    public static final int STUCK_TICKS = 30;

    /** Movement (in blocks) below which a villager counts as "not making progress". */
    public static final double STUCK_MOVE_THRESHOLD = 0.35;

    /** After this many failed unstick attempts in a row, give up for a while to avoid jittering. */
    public static final int MAX_STRIKES = 4;

    /** How long (ticks) to back off after MAX_STRIKES failed attempts. */
    public static final int GIVE_UP_COOLDOWN = 200;

    /** Whether villagers open fence gates that block their route. They always close them again. */
    public static final boolean OPEN_FENCE_GATES = true;

    /** How far (blocks) a villager looks for a fence gate when its destination is fenced off. */
    public static final int GATE_SEARCH_RADIUS = 8;

    // ---- Guards -----------------------------------------------------------

    /** Extra max health a villager gets when it becomes a guard (vanilla villager has 20). */
    public static final double GUARD_BONUS_HEALTH = 20.0;

    /** Damage per hit (an iron sword does 6). */
    public static final float GUARD_ATTACK_DAMAGE = 6.0f;

    /** Ticks between guard attacks. */
    public static final int GUARD_ATTACK_COOLDOWN = 20;

    /** Radius around the guard post in which guards spot hostile mobs. */
    public static final double GUARD_DETECT_RADIUS = 16.0;

    /** Guards stop chasing a mob that gets further than this from their post. */
    public static final double GUARD_CHASE_RADIUS = 24.0;

    /** Guards wander within this distance of their post while patrolling. */
    public static final int GUARD_PATROL_RADIUS = 10;

    public static final double GUARD_PATROL_SPEED = 0.5;
    public static final double GUARD_CHASE_SPEED = 0.7;

    /** Guards heal 1 health every this many ticks. */
    public static final int GUARD_REGEN_INTERVAL = 100;

    /** Creepers explode when hit in melee, so guards leave them alone by default. */
    public static final boolean GUARDS_FIGHT_CREEPERS = false;
}
