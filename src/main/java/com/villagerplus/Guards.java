package com.villagerplus;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Guard state. A villager is a guard if it carries the GUARD_POST attachment,
 * which stores the spot it protects. The attachment is saved with the entity,
 * so guards stay guards across restarts.
 */
public final class Guards {
    private Guards() {}

    public static final AttachmentType<BlockPos> GUARD_POST = AttachmentRegistry.<BlockPos>create(
            VillagerPlus.id("guard_post"),
            builder -> builder.persistent(BlockPos.CODEC));

    private static final Identifier HEALTH_BONUS_ID = VillagerPlus.id("guard_health");

    /** Forces class loading so the attachment registers during mod init. */
    public static void init() {}

    public static boolean isGuard(Villager villager) {
        return villager.hasAttached(GUARD_POST);
    }

    public static BlockPos getPost(Villager villager) {
        return villager.getAttached(GUARD_POST);
    }

    public static void makeGuard(Villager villager, ItemStack sword) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }

        villager.setItemSlot(EquipmentSlot.MAINHAND, sword);
        // Always drop the sword on death so the player gets it back.
        villager.setDropChance(EquipmentSlot.MAINHAND, 2.0f);

        AttributeInstance health = villager.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrReplacePermanentModifier(new AttributeModifier(
                    HEALTH_BONUS_ID, Config.GUARD_BONUS_HEALTH, AttributeModifier.Operation.ADD_VALUE));
        }
        villager.setHealth(villager.getMaxHealth());

        villager.setAttached(GUARD_POST, villager.blockPosition());

        // Clear whatever the villager brain was doing so the guard AI starts clean.
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getNavigation().stop();
    }

    public static void retire(Villager villager, ServerLevel level) {
        villager.removeAttached(GUARD_POST);
        GuardAI.forget(villager);

        ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!held.isEmpty()) {
            villager.spawnAtLocation(level, held.copy());
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        AttributeInstance health = villager.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_BONUS_ID);
        }
        villager.setHealth(Math.min(villager.getHealth(), villager.getMaxHealth()));
        villager.getNavigation().stop();
    }

    /** Moves an existing guard's post to a new spot. */
    public static void setPost(Villager villager, BlockPos pos) {
        villager.setAttached(GUARD_POST, pos.immutable());
    }
}
