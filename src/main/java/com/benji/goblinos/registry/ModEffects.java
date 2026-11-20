package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Goblinos.MODID);

   // public static final RegistryObject<MobEffect> ENTROPY_EFFECT = MOB_EFFECTS.register("entropy", EntropyEffect::new);

    private ModEffects() {
    }
    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
