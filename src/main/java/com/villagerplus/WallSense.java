package com.villagerplus;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Makes villagers react when they walk into walls or can't reach where they want to go.
 *
 * Two situations are handled:
 *  1. Physically stuck: the villager has somewhere to go but has barely moved for
 *     {@link Config#STUCK_TICKS} ticks. It then tries, in order: open a fence gate in front of it,
 *     hop up a one-block ledge, or pick a detour away from the obstacle.
 *  2. Fenced off: vanilla pathfinding treats closed fence gates as walls, so the villager brain
 *     gives up on targets behind one. When that happens we look for a closed gate on the way,
 *     walk the villager to it, open it, send it on to the original target and close the gate behind it.
 */
public final class WallSense {
    private WallSense() {}

    private static final Map<PathfinderMob, Tracker> TRACKERS = new WeakHashMap<>();

    static final class Tracker {
        Vec3 anchor = Vec3.ZERO;
        int stillTicks;
        int strikes;
        int cooldown;

        // A gate this mob opened and should close again.
        BlockPos openedGate;
        int gateTicks;

        // Remembered destination, so we still know it after the brain gives up.
        Vec3 lastGoal;
        float lastSpeed = 0.5f;
        long lastGoalTime;
        long lastCantReachSeen = Long.MIN_VALUE;

        // Walking to a gate before opening it.
        BlockPos approachingGate;
        Vec3 goalAfterGate;
        int approachTicks;

        void resetStuck(Vec3 pos) {
            anchor = pos;
            stillTicks = 0;
        }
    }

    static Tracker tracker(PathfinderMob mob) {
        return TRACKERS.computeIfAbsent(mob, m -> {
            Tracker t = new Tracker();
            t.anchor = m.position();
            return t;
        });
    }

    // ------------------------------------------------------------------------
    // Regular villagers (driven by the vanilla brain)
    // ------------------------------------------------------------------------

    public static void tickVillager(Villager villager, ServerLevel level) {
        Tracker t = tracker(villager);
        tickGateClosing(villager, level, t);
        if (t.cooldown > 0) t.cooldown--;

        if (villager.isSleeping() || villager.isPassenger() || villager.isTrading() || villager.isLeashed()) {
            t.resetStuck(villager.position());
            return;
        }

        Brain<Villager> brain = villager.getBrain();
        Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);

        // Phase: walking up to a gate we intend to open.
        if (t.approachingGate != null) {
            handleGateApproach(villager, level, brain, walkTarget, t);
            walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
        }

        walkTarget.ifPresent(wt -> {
            t.lastGoal = wt.getTarget().currentPosition();
            t.lastSpeed = wt.getSpeedModifier();
            t.lastGoalTime = level.getGameTime();
        });

        // The brain just gave up on a target: maybe a fence gate is in the way.
        if (Config.OPEN_FENCE_GATES && t.approachingGate == null) {
            Optional<Long> cantReach = brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            if (cantReach.isPresent() && cantReach.get() != t.lastCantReachSeen) {
                t.lastCantReachSeen = cantReach.get();
                if (t.lastGoal != null && level.getGameTime() - t.lastGoalTime < 40 && t.cooldown == 0) {
                    BlockPos gate = findGateToward(level, villager.blockPosition(), t.lastGoal);
                    if (gate != null) {
                        t.approachingGate = gate;
                        t.goalAfterGate = t.lastGoal;
                        t.approachTicks = 0;
                        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(gate, t.lastSpeed, 1));
                        return;
                    }
                    t.cooldown = 60;
                }
            }
        }

        // Physical stuck detection.
        if (walkTarget.isEmpty() || villager.getNavigation().isDone()) {
            t.resetStuck(villager.position());
            return;
        }
        WalkTarget wt = walkTarget.get();
        Vec3 goal = wt.getTarget().currentPosition();
        double arrive = wt.getCloseEnoughDist() + 1.5;
        if (horizontalDistSqr(villager.position(), goal) < arrive * arrive) {
            t.resetStuck(villager.position());
            return;
        }

        if (!checkStuck(villager, t)) return;

        Direction dir = headingDirection(villager, goal);
        if (tryOpenAdjacentGate(villager, level, dir, t)) {
            villager.getNavigation().recomputePath();
            return;
        }
        if (tryStepUp(villager, level, dir)) return;

        Vec3 detour = findDetour(villager, dir);
        if (detour != null) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(detour, wt.getSpeedModifier(), 0));
            villager.getLookControl().setLookAt(detour);
        }
        registerStrike(t);
    }

    private static void handleGateApproach(Villager villager, ServerLevel level, Brain<Villager> brain,
                                           Optional<WalkTarget> walkTarget, Tracker t) {
        t.approachTicks++;
        BlockPos gate = t.approachingGate;
        double dist = horizontalDistSqr(villager.position(), Vec3.atCenterOf(gate));

        boolean close = dist < 2.5 * 2.5;
        boolean brainStopped = walkTarget.isEmpty() && dist < 3.5 * 3.5;

        if (close || brainStopped) {
            if (isClosedGate(level, gate)) {
                setGate(level, gate, true, villager);
                t.openedGate = gate;
                t.gateTicks = 0;
            }
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(t.goalAfterGate, t.lastSpeed, 1));
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            t.approachingGate = null;
            t.goalAfterGate = null;
            t.cooldown = 40;
        } else if (t.approachTicks > 200 || walkTarget.isEmpty()) {
            // Couldn't get there, or the brain moved on to something else.
            t.approachingGate = null;
            t.goalAfterGate = null;
            t.cooldown = 100;
        }
    }

    // ------------------------------------------------------------------------
    // Guards and other mobs whose navigation we drive ourselves
    // ------------------------------------------------------------------------

    /**
     * Stuck handling for a mob moving toward {@code goal} via its navigation directly.
     * Returns a detour position the caller should walk to, or null.
     */
    public static Vec3 tickDirect(PathfinderMob mob, ServerLevel level, Vec3 goal) {
        Tracker t = tracker(mob);
        tickGateClosing(mob, level, t);
        if (t.cooldown > 0) t.cooldown--;

        if (goal == null || mob.getNavigation().isDone()
                || horizontalDistSqr(mob.position(), goal) < 2.5 * 2.5) {
            t.resetStuck(mob.position());
            return null;
        }
        if (!checkStuck(mob, t)) return null;

        Direction dir = headingDirection(mob, goal);
        if (tryOpenAdjacentGate(mob, level, dir, t)) {
            mob.getNavigation().recomputePath();
            return null;
        }
        if (tryStepUp(mob, level, dir)) return null;

        registerStrike(t);
        return findDetour(mob, dir);
    }

    // ------------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------------

    private static boolean checkStuck(PathfinderMob mob, Tracker t) {
        if (t.cooldown > 0) return false;
        if (horizontalDistSqr(mob.position(), t.anchor) > Config.STUCK_MOVE_THRESHOLD * Config.STUCK_MOVE_THRESHOLD) {
            t.resetStuck(mob.position());
            if (t.strikes > 0 && mob.tickCount % 40 == 0) t.strikes--;
            return false;
        }
        if (++t.stillTicks < Config.STUCK_TICKS) return false;
        t.resetStuck(mob.position());
        return true;
    }

    private static void registerStrike(Tracker t) {
        if (++t.strikes >= Config.MAX_STRIKES) {
            t.strikes = 0;
            t.cooldown = Config.GIVE_UP_COOLDOWN;
        }
    }

    /** Direction toward the next path node (or the goal if there is no path). */
    private static Direction headingDirection(PathfinderMob mob, Vec3 goal) {
        Vec3 toward = goal;
        Path path = mob.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            toward = Vec3.atBottomCenterOf(path.getNextNodePos());
        }
        double dx = toward.x - mob.getX();
        double dz = toward.z - mob.getZ();
        if (Math.abs(dx) < 1.0E-3 && Math.abs(dz) < 1.0E-3) {
            return mob.getDirection();
        }
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean tryOpenAdjacentGate(PathfinderMob mob, ServerLevel level, Direction dir, Tracker t) {
        if (!Config.OPEN_FENCE_GATES) return false;
        BlockPos feet = mob.blockPosition();
        BlockPos front = feet.relative(dir);
        BlockPos[] candidates = {
                front,
                front.relative(dir.getClockWise()),
                front.relative(dir.getCounterClockWise()),
                feet
        };
        for (BlockPos p : candidates) {
            if (isClosedGate(level, p)) {
                setGate(level, p, true, mob);
                t.openedGate = p.immutable();
                t.gateTicks = 0;
                return true;
            }
        }
        return false;
    }

    /** Hop onto a one-block ledge (or slab, path block, etc.) the mob is pushing into. */
    private static boolean tryStepUp(PathfinderMob mob, ServerLevel level, Direction dir) {
        if (!mob.onGround()) return false;
        BlockPos feet = mob.blockPosition();
        BlockPos front = feet.relative(dir);

        VoxelShape frontShape = level.getBlockState(front).getCollisionShape(level, front);
        if (frontShape.isEmpty() || frontShape.max(Direction.Axis.Y) > 1.0) {
            return false; // nothing to climb, or a fence/wall (1.5 high) that can't be jumped
        }
        if (!isPassable(level, front.above()) || !isPassable(level, front.above(2)) || !isPassable(level, feet.above(2))) {
            return false; // no headroom
        }
        mob.getJumpControl().jump();
        mob.setDeltaMovement(mob.getDeltaMovement().add(dir.getStepX() * 0.12, 0.0, dir.getStepZ() * 0.12));
        return true;
    }

    private static Vec3 findDetour(PathfinderMob mob, Direction dir) {
        Vec3 obstacle = Vec3.atCenterOf(mob.blockPosition().relative(dir));
        Vec3 detour = LandRandomPos.getPosAway(mob, 6, 3, obstacle);
        if (detour == null) {
            detour = LandRandomPos.getPos(mob, 5, 3);
        }
        return detour;
    }

    /** Finds a closed fence gate that lies roughly between {@code from} and {@code goal}. */
    private static BlockPos findGateToward(ServerLevel level, BlockPos from, Vec3 goal) {
        int r = Config.GATE_SEARCH_RADIUS;
        Vec3 start = Vec3.atCenterOf(from);
        double direct = start.distanceTo(goal);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos p : BlockPos.betweenClosed(from.offset(-r, -2, -r), from.offset(r, 2, r))) {
            if (!isClosedGate(level, p)) continue;
            Vec3 c = Vec3.atCenterOf(p);
            double score = start.distanceTo(c) + c.distanceTo(goal);
            // Only gates that are "on the way" (small detour compared to the straight line).
            if (score < direct + 6.0 && score < bestScore) {
                bestScore = score;
                best = p.immutable();
            }
        }
        return best;
    }

    private static void tickGateClosing(PathfinderMob mob, ServerLevel level, Tracker t) {
        if (t.openedGate == null) return;
        t.gateTicks++;
        BlockPos gate = t.openedGate;
        double dist = horizontalDistSqr(mob.position(), Vec3.atCenterOf(gate));
        boolean passedThrough = t.gateTicks > 20 && dist > 2.5 * 2.5;
        boolean timedOut = t.gateTicks > 400;
        if (!passedThrough && !timedOut) return;

        BlockState state = level.getBlockState(gate);
        boolean stillOurOpenGate = state.getBlock() instanceof FenceGateBlock
                && state.getValue(FenceGateBlock.OPEN)
                && !state.getValue(FenceGateBlock.POWERED);
        boolean doorwayClear = level.getEntitiesOfClass(LivingEntity.class, new AABB(gate)).isEmpty();

        if (stillOurOpenGate && doorwayClear) {
            setGate(level, gate, false, mob);
            t.openedGate = null;
        } else if (!stillOurOpenGate || timedOut) {
            t.openedGate = null; // someone else changed it, or we waited long enough
        }
    }

    static boolean isClosedGate(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof FenceGateBlock && !s.getValue(FenceGateBlock.OPEN);
    }

    private static void setGate(ServerLevel level, BlockPos pos, boolean open, PathfinderMob who) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof FenceGateBlock)) return;
        level.setBlock(pos, s.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE,
                SoundSource.BLOCKS, 1.0f, 0.9f + level.getRandom().nextFloat() * 0.1f);
        level.gameEvent(who, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static double horizontalDistSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    public static void forget(PathfinderMob mob) {
        TRACKERS.remove(mob);
    }
}
