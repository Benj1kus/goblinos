package com.benji.goblinos.client;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.client.renderer.SnotlingRenderer;
import com.benji.goblinos.registry.ModBlockEntities;
import com.benji.goblinos.registry.ModBlocks;
import com.benji.goblinos.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    //  @SubscribeEvent
    // public static void clientSetup(FMLClientSetupEvent event) {
    //     event.enqueueWork(() -> MinecraftForge.EVENT_BUS.register(AzumaalMegaBeamCameraShake.class));
    //  }


    //   @SubscribeEvent
    //   public static void registerDimensionEffects(
    //        RegisterDimensionSpecialEffectsEvent event
    //  ) {
    //     event.register(
    //              Oasiso.CHAOS_SKY_EFFECTS,
    //             new ChaosDimensionEffects()
    //      );
    //  }

    //   @SubscribeEvent
    // public static void registerParticleProviders(
    //         RegisterParticleProvidersEvent event
    // ) {
    //     event.registerSpriteSet(
    //            Oasiso.PURPLE_STARS.get(),
    //             PurpleStarsParticle.Provider::new
    //     );
    // }

    //  @SubscribeEvent
    //  public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    //    event.registerEntityRenderer(
    //           Oasiso.MONKI.get(),
    //            MonkiRenderer::new
    //  );
    //  event.registerBlockEntityRenderer(
    //          Oasiso.LIE_BLOCK_BE.get(),
    //          com.benji.oasiso.client.renderer.LieBlockRenderer::new
    //    );
    //  }

  @SubscribeEvent
  public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(
           ModEntities.SNOTLING.get(),
            SnotlingRenderer::new
  );
      event.registerBlockEntityRenderer(
              ModBlockEntities.CLEAN_GEO_BE.get(),
              com.benji.goblinos.client.renderer.TentRenderer::new
        );
  }
}