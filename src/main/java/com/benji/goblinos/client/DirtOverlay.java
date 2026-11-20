package com.benji.goblinos.client;

import java.util.ArrayList;
import java.util.List;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.network.DirtNetwork;
import com.benji.goblinos.registry.ModEntities;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DirtOverlay {
    private static final int SIZE = 100, HOLD = 60, FADE = 15;

    private record Mark(float x, float y, ResourceLocation texture, long start) {
    }

    private static final List<Mark> marks = new ArrayList<>();
    private static net.minecraft.client.player.LocalPlayer markedPlayer;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DirtNetwork.bindClient(DirtOverlay::add));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DIRT_BALL.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void overlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("dirt_splashes", (gui, graphics, partial, width, height) -> draw(graphics, partial, width, height));
    }

    private static void add() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (markedPlayer != mc.player) marks.clear();
        markedPlayer = mc.player;
        var rng = mc.player.getRandom();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, "textures/gui/dirt_overlay" + (1 + rng.nextInt(3)) + ".png");
        long now = mc.player.level().getGameTime();
        marks.removeIf(mark -> now - mark.start >= HOLD + FADE);
        marks.add(new Mark((rng.nextFloat() - 0.5F) * 0.34F, (rng.nextFloat() - 0.5F) * 0.34F, texture, now));
    }

    private static void draw(GuiGraphics g, float partial, int w, int h) {
        var player = Minecraft.getInstance().player;
        if (player == null || player != markedPlayer) {
            marks.clear();
            markedPlayer = null;
            return;
        }
        if (marks.isEmpty()) return;
        long now = player.level().getGameTime();
        marks.removeIf(mark -> now - mark.start >= HOLD + FADE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Mark mark : marks) {
            float age = now - mark.start + partial;
            float alpha = Mth.clamp(1 - (age - HOLD) / FADE, 0, 1);
            int x = Math.round(w * (0.5F + mark.x)) - SIZE / 2;
            int y = Math.round(h * (0.5F + mark.y)) - SIZE / 2;
            g.setColor(1, 1, 1, alpha);
            g.blit(mark.texture, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        }
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }
}
