package com.benji.goblinos.network;

import com.benji.goblinos.Goblinos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DirtNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "dirt"), () -> "1", "1"::equals, "1"::equals);

    private record Splat() {
    }

    private static Runnable onSplash = () -> {
    };

    public static void bindClient(Runnable listener) {
        onSplash = listener;
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        CHANNEL.messageBuilder(Splat.class, 0, NetworkDirection.PLAY_TO_CLIENT).encoder((msg, buf) -> {
        }).decoder(buf -> new Splat()).consumerMainThread((msg, ctx) -> onSplash.run()).add();
    }

    public static void splash(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Splat());
    }
}
