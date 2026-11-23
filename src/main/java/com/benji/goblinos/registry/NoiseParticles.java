package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoiseParticles {
    public static final SimpleParticleType WAVE = new SimpleParticleType(false);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "noise_wave"), () -> WAVE);
    }
}
