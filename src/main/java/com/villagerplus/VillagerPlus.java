package com.villagerplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerPlus implements ModInitializer {
    public static final String MOD_ID = "villagerplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Guards.init();
        UseEntityCallback.EVENT.register(GuardInteraction::onUseEntity);
        LOGGER.info("Villager Plus loaded: smarter pathing and village guards enabled.");
    }
}
