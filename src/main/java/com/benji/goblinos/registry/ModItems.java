package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.item.DirtBallItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


public final class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Goblinos.MODID);

   // public static final RegistryObject<Item> LILY_ITEM = ITEMS.register("lily", () -> new PlaceOnWaterBlockItem(ModBlocks.LILY.get(), new Item.Properties()));
   // public static final RegistryObject<Item> PIPUS_ITEM = ITEMS.register("pipus", () -> new BlockItem(ModBlocks.PIPUS.get(), new Item.Properties()));
  // public static final RegistryObject<Item> WINDMILL_ITEM = ITEMS.register("windmill", () -> new GeoBlockItem(ModBlocks.WINDMILL.get(), new Item.Properties(), ResourceLocation.fromNamespaceAndPath(MODID, "geo/windmill.geo.json"), ResourceLocation.fromNamespaceAndPath(MODID, "textures/block/windmill.png"), ResourceLocation.fromNamespaceAndPath(MODID, "animations/windmill.animation.json"), ResourceLocation.fromNamespaceAndPath(MODID, "textures/block/empty.png")));
 //  public static final RegistryObject<Item> WAR_CORE = ITEMS.register("war_core", () -> new WarCoreItem(new Item.Properties().stacksTo(1)));
   public static final RegistryObject<Item> SNOTLING_SPAWN_EGG = ITEMS.register("snotling_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.SNOTLING, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));

    public static final RegistryObject<Item> DIRT_BALL = ITEMS.register("dirt_ball", () -> new DirtBallItem(new Item.Properties().stacksTo(16)));

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
