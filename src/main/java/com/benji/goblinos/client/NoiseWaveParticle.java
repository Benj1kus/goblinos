package com.benji.goblinos.client;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.registry.NoiseParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NoiseWaveParticle extends TextureSheetParticle {
    private static final int FRAMES = 8;
    private static final int TICKS_PER_FRAME = 2;
    private final SpriteSet sprites;

    private NoiseWaveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        lifetime = FRAMES * TICKS_PER_FRAME;
        quadSize = 0.34F;
        hasPhysics = false;
        setSprite(sprites.get(0, FRAMES - 1));
    }

    @Override
    public void tick() {
        if (++age >= lifetime) remove();
        else setSprite(sprites.get(age / TICKS_PER_FRAME, FRAMES - 1));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(NoiseParticles.WAVE, sprites -> (type, level, x, y, z, vx, vy, vz) -> new NoiseWaveParticle(level, x, y, z, sprites));
    }
}
