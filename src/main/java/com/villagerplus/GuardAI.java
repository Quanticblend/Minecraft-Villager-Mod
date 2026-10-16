package com.villagerplus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces the villager brain while a villager is a guard.
 * Guards patrol around their post, chase hostile mobs that come within range,
 * hit them with their sword, and return to their post when the area is clear.
 * They don't sleep, panic, or trade while on duty.
 */
public final class GuardAI {
    private GuardAI() {}

    private static final Map<Villager, State> STATES = new WeakHashMap<>();

    private static final class State {
        LivingEntity target;
        Vec3 goal;
        int attackCooldown;
        int scanCooldown;
        int repathCooldown;
    }

    public static void forget(Villager villager) {
        STATES.remove(villager);
        WallSense.forget(villager);
    }

    public static void tick(Villager guard, ServerLevel level) {
        BlockPos post = Guards.getPost(guard);
        if (post == null) return;
        State s = STATES.computeIfAbsent(guard, g -> new State());
        Vec3 postCenter = Vec3.atBottomCenterOf(post);

        if (guard.isSleeping()) guard.stopSleeping();
        if (s.attackCooldown > 0) s.attackCooldown--;
        if (s.repathCooldown > 0) s.repathCooldown--;

        if (guard.tickCount % Config.GUARD_REGEN_INTERVAL == 0 && guard.getHealth() < guard.getMaxHealth()) {
            guard.heal(1.0f);
        }

        // Drop targets that died or ran too far from the post.
        LivingEntity target = s.target;
        if (target != null && (!target.isAlive() || target.isRemoved() || target.level() != level
                || target.position().distanceToSqr(postCenter) > Config.GUARD_CHASE_RADIUS * Config.GUARD_CHASE_RADIUS)) {
            s.target = target = null;
            guard.getNavigation().stop();
        }

        // Look for threats twice a second; switch if something is much closer.
        if (--s.scanCooldown <= 0) {
            s.scanCooldown = 10;
            LivingEntity threat = findThreat(guard, level, post);
            if (threat != null && (target == null || guard.distanceToSqr(threat) + 4.0 < guard.distanceToSqr(target))) {
                s.target = target = threat;
                s.repathCooldown = 0;
            }
        }

        if (target != null) {
            engage(guard, level, s, target);
        } else {
            patrol(guard, s, post, postCenter);
        }

        // Same wall-handling the regular villagers get.
        Vec3 detour = WallSense.tickDirect(guard, level, s.goal);
        if (detour != null) {
            guard.getNavigation().moveTo(detour.x, detour.y, detour.z,
                    target != null ? Config.GUARD_CHASE_SPEED : Config.GUARD_PATROL_SPEED);
            s.repathCooldown = 30; // give the detour a moment before re-chasing
        }
    }

    private static void engage(Villager guard, ServerLevel level, State s, LivingEntity target) {
        guard.getLookControl().setLookAt(target, 30.0f, 30.0f);
        s.goal = target.position();

        double reach = 2.0 + target.getBbWidth() * 0.5;
        double distSqr = guard.distanceToSqr(target);

        if (distSqr > reach * reach) {
            if (s.repathCooldown <= 0) {
                guard.getNavigation().moveTo(target, Config.GUARD_CHASE_SPEED);
                s.repathCooldown = 10;
            }
            return;
        }

        guard.getNavigation().stop();
        if (s.attackCooldown <= 0 && guard.hasLineOfSight(target)) {
            guard.swing(InteractionHand.MAIN_HAND);
            DamageSource source = level.damageSources().mobAttack(guard);
            if (target.hurtServer(level, source, Config.GUARD_ATTACK_DAMAGE)) {
                target.knockback(0.4, guard.getX() - target.getX(), guard.getZ() - target.getZ());
            }
            s.attackCooldown = Config.GUARD_ATTACK_COOLDOWN;
        }
    }

    private static void patrol(Villager guard, State s, BlockPos post, Vec3 postCenter) {
        double fromPost = guard.position().distanceToSqr(postCenter);
        boolean idle = guard.getNavigation().isDone();

        if (fromPost > (Config.GUARD_PATROL_RADIUS + 4) * (Config.GUARD_PATROL_RADIUS + 4)) {
            // Too far away: head home.
            if (idle || s.repathCooldown <= 0) {
                guard.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, Config.GUARD_PATROL_SPEED);
                s.goal = postCenter;
                s.repathCooldown = 40;
            }
            return;
        }

        if (idle) {
            s.goal = null;
            // Pause a bit between patrol legs, then walk somewhere new near the post.
            if (guard.getRandom().nextInt(60) == 0) {
                Vec3 next = LandRandomPos.getPosTowards(guard, Config.GUARD_PATROL_RADIUS, 4, postCenter);
                if (next != null && next.distanceToSqr(postCenter) <= Config.GUARD_PATROL_RADIUS * Config.GUARD_PATROL_RADIUS) {
                    guard.getNavigation().moveTo(next.x, next.y, next.z, Config.GUARD_PATROL_SPEED);
                    s.goal = next;
                } else if (next == null) {
                    next = LandRandomPos.getPos(guard, Config.GUARD_PATROL_RADIUS / 2, 3);
                    if (next != null) {
                        guard.getNavigation().moveTo(next.x, next.y, next.z, Config.GUARD_PATROL_SPEED);
                        s.goal = next;
                    }
                }
            }
        }
    }

    private static LivingEntity findThreat(Villager guard, ServerLevel level, BlockPos post) {
        // Anything that hurt the guard recently (that isn't a player, villager or golem) is fair game.
        LivingEntity attacker = guard.getLastHurtByMob();
        if (attacker instanceof Mob mob && mob.isAlive() && isHostile(mob)) {
            return attacker;
        }

        double r = Config.GUARD_DETECT_RADIUS;
        AABB area = new AABB(post).inflate(r, 6.0, r);
        List<Mob> threats = level.getEntitiesOfClass(Mob.class, area,
                m -> m.isAlive() && isHostile(m) && !m.isInvisible() && guard.hasLineOfSight(m));
        return threats.stream()
                .min(Comparator.comparingDouble(guard::distanceToSqr))
                .orElse(null);
    }

    private static boolean isHostile(Mob mob) {
        if (!(mob instanceof Enemy)) return false;
        if (mob instanceof Creeper) return Config.GUARDS_FIGHT_CREEPERS;
        return true;
    }
}
