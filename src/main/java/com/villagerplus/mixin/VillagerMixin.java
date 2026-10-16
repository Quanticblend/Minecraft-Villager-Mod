package com.villagerplus.mixin;

import com.villagerplus.GuardAI;
import com.villagerplus.Guards;
import com.villagerplus.WallSense;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {

    /** Guards skip the normal villager brain entirely and run the guard AI instead. */
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void villagerplus$runGuardAi(ServerLevel level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (Guards.isGuard(self)) {
            GuardAI.tick(self, level);
            ci.cancel();
        }
    }

    /** After the vanilla brain has decided where to go, watch for walls and fenced-off targets. */
    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void villagerplus$handleWalls(ServerLevel level, CallbackInfo ci) {
        WallSense.tickVillager((Villager) (Object) this, level);
    }
}
