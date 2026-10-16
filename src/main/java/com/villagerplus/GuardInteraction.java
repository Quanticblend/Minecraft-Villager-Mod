package com.villagerplus;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Right-click a villager with an iron sword -> it becomes a guard posted where it stands.
 * Right-click a guard with an iron sword -> moves its post to where the guard is now.
 * Sneak + right-click a guard with an empty hand -> dismisses it and gives the sword back.
 * Guards don't trade while on duty.
 */
public final class GuardInteraction {
    private GuardInteraction() {}

    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
                                                Entity entity, EntityHitResult hit) {
        if (!(entity instanceof Villager villager) || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);

        // The client doesn't know who is a guard, so it only decides the sword case.
        if (level.isClientSide()) {
            return stack.is(Items.IRON_SWORD) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        boolean guard = Guards.isGuard(villager);

        if (!guard) {
            if (!stack.is(Items.IRON_SWORD)) {
                return InteractionResult.PASS; // normal trading
            }
            if (villager.isBaby()) {
                player.displayClientMessage(Component.literal("This villager is too young to stand guard."), true);
                return InteractionResult.SUCCESS;
            }
            Guards.makeGuard(villager, stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("The villager is now a guard and will protect this spot."), true);
            return InteractionResult.SUCCESS;
        }

        // Already a guard.
        if (player.isShiftKeyDown() && stack.isEmpty()) {
            Guards.retire(villager, serverLevel);
            player.displayClientMessage(Component.literal("Guard dismissed. The villager returns to normal life."), true);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.IRON_SWORD)) {
            Guards.setPost(villager, villager.blockPosition());
            player.displayClientMessage(Component.literal("Guard post moved here."), true);
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.literal("On guard duty. Sneak + right-click with an empty hand to dismiss."), true);
        return InteractionResult.SUCCESS; // blocks the trade screen
    }
}
