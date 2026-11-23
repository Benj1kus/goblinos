package com.benji.goblinos.kick;

import com.benji.goblinos.Goblinos;

import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;

import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KickNet {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "kick"), () -> "1", "1"::equals, "1"::equals);

    public record Click(int mob, int phase, long start, int age) {
    }

    public record State(int player, int mob, int phase, long start, float yaw) {
    }

    public static Consumer<State> onState = s -> {
    };

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        CHANNEL.messageBuilder(Click.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder((m, b) -> {
            b.writeVarInt(m.mob);
            b.writeByte(m.phase);
            b.writeLong(m.start);
            b.writeVarInt(m.age);
        }).decoder(b -> new Click(b.readVarInt(), b.readByte(), b.readLong(), b.readVarInt())).consumerMainThread((m, c) -> {
            ServerPlayer p = c.get().getSender();
            if (p != null) Kick.click(p, m);
        }).add();
        CHANNEL.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder((m, b) -> {
            b.writeVarInt(m.player);
            b.writeVarInt(m.mob);
            b.writeByte(m.phase);
            b.writeLong(m.start);
            b.writeFloat(m.yaw);
        }).decoder(b -> new State(b.readVarInt(), b.readVarInt(), b.readByte(), b.readLong(), b.readFloat())).consumerMainThread((m, c) -> onState.accept(m)).add();
    }

    public static void click(Click m) {
        CHANNEL.sendToServer(m);
    }

    static void broadcast(ServerPlayer p, State s) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), s);
    }

    static void send(ServerPlayer p, State s) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s);
    }
}
