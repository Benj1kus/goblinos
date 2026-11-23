package com.benji.goblinos.client.kick;

import com.benji.goblinos.Goblinos;
import com.benji.goblinos.common.entity.SnotlingEntity;
import com.benji.goblinos.kick.*;
import com.mojang.blaze3d.vertex.PoseStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.*;

@Mod.EventBusSubscriber(modid = Goblinos.MODID, value = Dist.CLIENT)
public final class KickClient {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Method setCameraPos = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90584_", double.class, double.class, double.class);
    private static final Map<Integer, KickNet.State> scenes = new HashMap<>();
    private static final ResourceLocation[] prompt = frames("gui/click_rmb", 2), qte = frames("particle/kick_qte", 8);
    private static ClientLevel world;
    private static CameraType previous;
    private static final int CAMERA_RETURN_TICKS = 10;

    private record CameraPose(Vec3 position, float yaw, float pitch, float roll) {
    }

    private static CameraPose lastKickView, returnFrom;
    private static double returnStart;
    private static KickRig model;
    private static boolean useHeld, projected;
    private static float screenX, screenY;

    private static ResourceLocation[] frames(String path, int count) {
        var result = new ResourceLocation[count];
        for (int i = 0; i < count; i++) result[i] = KickRig.id("textures/" + path + (i + 1) + ".png");
        return result;
    }

    private static KickNet.State local() {
        return mc.player == null ? null : scenes.get(mc.player.getId());
    }

    private static float age(KickNet.State s, float partial) {
        return (mc.level.getGameTime() - s.start()) + partial;
    }

    private static KickRig pose(KickNet.State s, float partial) {
        if (model == null) model = new KickRig();
        model.sample(s.phase() == Kick.DONE, age(s, partial));
        return model;
    }

    private static void restore() {
        if (previous == null) return;
        mc.options.setCameraType(previous);
        returnFrom = lastKickView;
        lastKickView = null;
        returnStart = Double.NaN;
        previous = null;
        projected = false;
    }

    private static void syncLevel() {
        if (world != mc.level) {
            restore();
            returnFrom = null;
            scenes.clear();
            useHeld = false;
            world = mc.level;
        }
    }

    private static void receive(KickNet.State s) {
        syncLevel();
        if (mc.player == null) return;
        if (s.phase() == Kick.END) {
            scenes.remove(s.player());
            if (s.player() == mc.player.getId()) restore();
        } else {
            scenes.put(s.player(), s);
            if (s.player() == mc.player.getId() && previous == null) {
                returnFrom = null;
                lastKickView = null;
                previous = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
    }

    private static SnotlingEntity underCursor() {
        if (mc.player != null && mc.hitResult instanceof EntityHitResult h && h.getEntity() instanceof SnotlingEntity m && Kick.canStart(mc.player, m))
            return m;
        return null;
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        var scene = local();
        if (scene != null && !e.isUseItem()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (!e.isUseItem() || mc.screen != null) return;
        var target = scene == null ? underCursor() : null;
        if (scene == null && target == null) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (useHeld) return;
        useHeld = true;
        if (scene == null) KickNet.click(new KickNet.Click(target.getId(), Kick.START, 0, 0));
        else if (scene.phase() == Kick.START)
            KickNet.click(new KickNet.Click(scene.mob(), Kick.DONE, scene.start(), (int) age(scene, 0)));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        syncLevel();
        if (!mc.options.keyUse.isDown()) useHeld = false;
        if (mc.level == null) return;
        scenes.entrySet().removeIf(entry -> age(entry.getValue(), 0) > (entry.getValue().phase() == Kick.START ? Kick.WINDOW_END + 20 : Kick.DONE_TICKS + 10));
        if (local() == null) {
            restore();
            return;
        }
        while (mc.options.keyTogglePerspective.consumeClick()) {
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent e) {
        if (local() == null) return;
        var in = e.getInput();
        in.leftImpulse = 0;
        in.forwardImpulse = 0;
        in.jumping = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    public static void hands(RenderHandEvent e) {
        if (local() != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        var scene = local();
        float partial = (float) e.getPartialTick();
        if (scene == null) {
            if (returnFrom == null) return;
            if (mc.player == null || !mc.player.isAlive() || e.getCamera().getEntity() != mc.player) {
                returnFrom = null;
                return;
            }
            double now = (double) mc.player.tickCount + partial;
            if (Double.isNaN(returnStart)) returnStart = now;
            double t = Mth.clamp((now - returnStart) / CAMERA_RETURN_TICKS, 0.0, 1.0);
            if (t >= 1.0) {
                returnFrom = null;
                return;
            }
            float blend = (float) (t * t * (3.0 - 2.0 * t));
            Vec3 position = returnFrom.position().lerp(e.getCamera().getPosition(), blend);
            applyView(e, position, Mth.rotLerp(blend, returnFrom.yaw(), e.getYaw()), Mth.lerp(blend, returnFrom.pitch(), e.getPitch()), Mth.rotLerp(blend, returnFrom.roll(), e.getRoll()));
            return;
        }
        var matrix = pose(scene, partial).eyes(scene.yaw()).last().pose();
        var offset = matrix.transformPosition(new Vector3f());
        var forward = matrix.transformDirection(new Vector3f(0, 0, -1)).normalize();
        var position = mc.player.getPosition(partial).add(offset.x, offset.y, offset.z);
        float yaw = (float) -java.lang.Math.toDegrees(java.lang.Math.atan2(forward.x, forward.z));
        float pitch = (float) -java.lang.Math.toDegrees(java.lang.Math.asin(Mth.clamp(forward.y, -1, 1)));
        lastKickView = new CameraPose(position, yaw, pitch, 0);
        applyView(e, position, yaw, pitch, 0);
    }

    private static void applyView(ViewportEvent.ComputeCameraAngles event, Vec3 position, float yaw, float pitch, float roll) {
        try {
            setCameraPos.invoke(event.getCamera(), position.x, position.y, position.z);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);
    }

    private static void draw(AbstractClientPlayer p, KickNet.State s, PoseStack ps, MultiBufferSource buffers, int light, float partial, boolean first) {
        var rig = pose(s, partial);
        ps.pushPose();
        KickRig.orient(ps, s.yaw());
        rig.head.visible = !first;
        rig.root.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(p.getSkinTextureLocation())), light, OverlayTexture.NO_OVERLAY);
        rig.head.visible = true;
        ps.popPose();
    }

    @SubscribeEvent
    public static void player(RenderPlayerEvent.Pre e) {
        var s = scenes.get(e.getEntity().getId());
        if (s == null || !(e.getEntity() instanceof AbstractClientPlayer p)) return;
        e.setCanceled(true);
        if (p != mc.player)
            draw(p, s, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), e.getPartialTick(), false);
    }

    @SubscribeEvent
    public static void world(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var s = local();
        if (s == null) return;
        var ps = e.getPoseStack();
        var eye = e.getCamera().getPosition();
        float partial = e.getPartialTick();
        var pos = mc.player.getPosition(partial).subtract(eye);
        var buffers = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        draw(mc.player, s, ps, buffers, LevelRenderer.getLightColor(mc.level, mc.player.blockPosition()), partial, true);
        ps.popPose();
        buffers.endBatch(RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation()));
        var mob = mc.level.getEntity(s.mob());
        projected = false;
        if (mob != null) {
            var at = mob.getPosition(partial).add(0, mob.getBbHeight() * 0.76, 0).subtract(eye);
            Vector4f v = new Vector4f((float) at.x, (float) at.y, (float) at.z, 1);
            ps.last().pose().transform(v);
            e.getProjectionMatrix().transform(v);
            if (v.w > 0.01f) {
                screenX = (1 + v.x / v.w) * 0.5f;
                screenY = (1 - v.y / v.w) * 0.5f;
                projected = true;
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Goblinos.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                KickNet.onState = KickClient::receive;
            });
        }

        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) manager -> model = null);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("kick_prompt", (gui, g, partial, w, h) -> {
                var s = local();
                if (mc.player == null || mc.screen != null) return;
                if (s == null) {
                    if (underCursor() == null) return;
                    var tex = prompt[(mc.player.tickCount / 10) % 2];
                    g.blit(tex, w / 2 - 7, h - 47, 0, 0, 14, 16, 14, 16);
                } else if (s.phase() == Kick.START) {
                    int frame = (int) age(s, partial) / Kick.FRAME_TICKS;
                    if (frame < 0 || frame >= qte.length) return;
                    int x = projected ? Mth.clamp(java.lang.Math.round(screenX * w), 16, w - 16) : w / 2;
                    int y = projected ? Mth.clamp(java.lang.Math.round(screenY * h), 16, h - 16) : h / 2;
                    g.blit(qte[frame], x - 16, y - 16, 0, 0, 32, 32, 32, 32);
                }
            });
        }
    }
}
