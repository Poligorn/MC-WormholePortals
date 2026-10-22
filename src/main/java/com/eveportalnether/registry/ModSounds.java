package com.eveportalnether.registry;

import com.eveportalnether.EvePortalNether;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, EvePortalNether.MODID);

    /** Distance at which the living-portal drone is still audible. */
    public static final float HUM_RANGE = 300.0F;

    public static final DeferredHolder<SoundEvent, SoundEvent> RADAR_CHARGE = register("radar.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADAR_EMPTY = register("radar.empty");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADAR_PING = register("radar.ping");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_USE = register("detector.use");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = register("portal.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CLOSE = register("portal.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_TRAVEL = register("portal.travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_CLICK = register("detector.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_WARNING = register("portal.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_IMPLODE = register("portal.implode");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CRACKLE = register("portal.crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CHARGE = register("portal.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_RUMBLE = register("portal.rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_HUM = registerFixed("portal.hum", HUM_RANGE);

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerFixed(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, name), range));
    }

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
