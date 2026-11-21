package com.benji.goblinos.registry;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.item.DirtBallItem;
import com.benji.goblinos.common.item.GeoBlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.benji.goblinos.Goblinos.MODID;


public final class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

   // public static final RegistryObject<Item> LILY_ITEM = ITEMS.register("lily", () -> new PlaceOnWaterBlockItem(ModBlocks.LILY.get(), new Item.Properties()));
   // public static final RegistryObject<Item> PIPUS_ITEM = ITEMS.register("pipus", () -> new BlockItem(ModBlocks.PIPUS.get(), new Item.Properties()));
  // public static final RegistryObject<Item> WINDMILL_ITEM = ITEMS.register("windmill", () -> new GeoBlockItem(ModBlocks.WINDMILL.get(), new Item.Properties(), ResourceLocation.fromNamespaceAndPath(MODID, "geo/windmill.geo.json"), ResourceLocation.fromNamespaceAndPath(MODID, "textures/block/windmill.png"), ResourceLocation.fromNamespaceAndPath(MODID, "animations/windmill.animation.json"), ResourceLocation.fromNamespaceAndPath(MODID, "textures/block/empty.png")));
 //  public static final RegistryObject<Item> WAR_CORE = ITEMS.register("war_core", () -> new WarCoreItem(new Item.Properties().stacksTo(1)));
   public static final RegistryObject<Item> SNOTLING_SPAWN_EGG = ITEMS.register("snotling_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.SNOTLING, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));

    public static final RegistryObject<Item> DIRT_BALL = ITEMS.register("dirt_ball", () -> new DirtBallItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> TENT_ITEM = ITEMS.register("tent", () -> new GeoBlockItem(ModBlocks.TENT.get(), new Item.Properties().stacksTo(1), ResourceLocation.fromNamespaceAndPath(MODID, "geo/tent.geo.json"), ResourceLocation.fromNamespaceAndPath(MODID, "textures/block/tent.png"), ResourceLocation.fromNamespaceAndPath(MODID, "animations/tent.animation.json"), ResourceLocation.fromNamespaceAndPath(MODID, "textures/block/empty.png")));
    public static final RegistryObject<Item> BOOTS = ITEMS.register("boots", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> DIRT_WORM = ITEMS.register("dirt_worm", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> TENT_FABRIC = ITEMS.register("tent_fabric", () -> new Item(new Item.Properties().stacksTo(16)));

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
