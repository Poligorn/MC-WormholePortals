package com.eveportalnether;

import com.eveportalnether.block.WormholeBlock;
import com.eveportalnether.client.ScanPanel;
import com.eveportalnether.client.SpiralParticle;
import com.eveportalnether.client.TransitScreen;
import com.eveportalnether.client.WormholePulseRenderer;
import com.eveportalnether.registry.ModBlockEntities;
import com.eveportalnether.registry.ModParticles;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.eveportalnether.network.AnonymityPayload;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import com.eveportalnether.network.ShakePayload;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.eveportalnether.registry.ModBlocks;
import com.eveportalnether.registry.ModItems;
import com.eveportalnether.world.PortalColor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = EvePortalNether.MODID, dist = Dist.CLIENT)
public class EvePortalNetherClient {
    public EvePortalNetherClient(ModContainer container, IEventBus modBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(EvePortalNetherClient::registerBlockColors);
        modBus.addListener(EvePortalNetherClient::registerItemColors);
        modBus.addListener((RegisterGuiLayersEvent event) -> {
            event.registerAboveAll(TransitScreen.LAYER_ID, TransitScreen::renderArrival);
            event.registerAboveAll(ScanPanel.LAYER_ID, ScanPanel::render);
        });
        modBus.addListener((RegisterParticleProvidersEvent event) ->
                event.registerSpriteSet(ModParticles.WORMHOLE_SPIRAL.get(), SpiralParticle.Provider::new));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(ModBlockEntities.WORMHOLE_BLOCK_ENTITY.get(), WormholePulseRenderer::new));
        NeoForge.EVENT_BUS.addListener(TransitScreen::onScreenRender);
        NeoForge.EVENT_BUS.addListener(EvePortalNetherClient::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(EvePortalNetherClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(EvePortalNetherClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EvePortalNetherClient::onCameraAngles);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (ShakePayload.clientRemaining > 0) {
            ShakePayload.clientRemaining--;
        }
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        int remaining = ShakePayload.clientRemaining;
        if (remaining <= 0 || Minecraft.getInstance().isPaused()) {
            return;
        }
        float fade = (remaining - (float) event.getPartialTick()) / ShakePayload.clientDuration;
        float amp = ShakePayload.clientStrength * Math.max(0.0F, fade) * fade;
        double t = (System.nanoTime() / 1.0E9) * 28.0;
        event.setYaw(event.getYaw() + amp * (float) (Math.sin(t * 1.13) + 0.5 * Math.sin(t * 2.71)));
        event.setPitch(event.getPitch() + amp * (float) (Math.sin(t * 1.57 + 1.3) + 0.5 * Math.sin(t * 3.07)));
        event.setRoll(event.getRoll() + amp * 1.5F * (float) Math.sin(t * 0.91 + 2.1));
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            // Frame overlay is the coloured grout; the window still greys out as the portal dies.
            if (state.getValue(WormholeBlock.FRAME)) {
                return 0xFF000000 | state.getValue(WormholeBlock.COLOR).rgb();
            }
            return 0xFF000000 | state.getValue(WormholeBlock.COLOR).tint(state.getValue(WormholeBlock.DECAY));
        }, ModBlocks.WORMHOLE.get());
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0
                ? 0xFF000000 | PortalColor.BLUE.rgb()
                : 0xFFFFFFFF,
                ModItems.WORMHOLE_ITEM.get());
    }

    private static void onRenderNameTag(RenderNameTagEvent event) {
        if (!AnonymityPayload.clientEnabled || !(event.getEntity() instanceof Player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.dimension() == Level.NETHER) {
            event.setCanRender(TriState.FALSE);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AnonymityPayload.clientEnabled = false;
        ShakePayload.clientRemaining = 0;
        ScanPanel.clear();
        TransitScreen.clear();
    }
}
