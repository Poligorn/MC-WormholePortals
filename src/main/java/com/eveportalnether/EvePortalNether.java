package com.eveportalnether;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.eveportalnether.compat.aeroportals.AeroPortalsHook;
import com.eveportalnether.network.ModNetwork;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.registry.ModEffects;
import com.eveportalnether.registry.ModParticles;
import com.eveportalnether.registry.ModTriggers;
import com.eveportalnether.registry.ModItems;
import com.eveportalnether.registry.ModBlockEntities;
import com.eveportalnether.registry.ModSounds;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EvePortalNether.MODID)
public class EvePortalNether {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "eveportalnether";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a creative tab with the id "eveportalnether:eveportal_tab"
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EVEPORTAL_TAB = CREATIVE_MODE_TABS.register("eveportal_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.eveportalnether"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.WORMHOLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.WORMHOLE_ITEM.get());
                output.accept(ModItems.PORTAL_DETECTOR.get());
                output.accept(ModItems.PORTAL_RADAR.get());
                output.accept(ModItems.PORTAL_SHARD.get());
            }).build());

    public EvePortalNether(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetwork::register);

        // Register the Deferred Register to the mod event bus so blocks get registered
        ModBlocks.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ModItems.register(modEventBus);
        // Register the Deferred Register to the mod event bus so block entities get registered
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEffects.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (EvePortalNether) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        event.enqueueWork(AeroPortalsHook::register);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
