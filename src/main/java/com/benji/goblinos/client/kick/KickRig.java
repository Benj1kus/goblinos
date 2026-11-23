package com.benji.goblinos.client.kick;

import com.benji.goblinos.Goblinos;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.io.*;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.*;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

final class KickRig extends HierarchicalModel<Player> {
    final ModelPart root, base, waist, head;
    private final Map<String, AnimationDefinition> clips = new HashMap<>();
    private final Vector3f scratch = new Vector3f();

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Goblinos.MODID, path);
    }

    private static JsonObject load(String path) {
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(id(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    KickRig() {
        var geometry = load("geo/template_player.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        Map<String, Vector3f> pivots = new HashMap<>();
        for (var entry : geometry.getAsJsonArray("bones")) {
            var bone = entry.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : "";
            Vector3f pivot = vec(bone.getAsJsonArray("pivot"));
            var cubes = CubeListBuilder.create();
            if (bone.has("cubes")) for (var element : bone.getAsJsonArray("cubes")) {
                var c = element.getAsJsonObject();
                var origin = vec(c.getAsJsonArray("origin"));
                var size = vec(c.getAsJsonArray("size"));
                if (size.lengthSquared() == 0) continue;
                var uv = c.getAsJsonArray("uv");
                cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).addBox(origin.x - pivot.x, pivot.y - origin.y - size.y, origin.z - pivot.z, size.x, size.y, size.z, new CubeDeformation(c.has("inflate") ? c.get("inflate").getAsFloat() : 0));
            }
            var relative = new Vector3f(pivot).sub(pivots.getOrDefault(parent, new Vector3f()));
            parts.put(name, parts.getOrDefault(parent, mesh.getRoot()).addOrReplaceChild(name, cubes, PartPose.offset(relative.x, -relative.y, relative.z)));
            pivots.put(name, pivot);
        }
        var desc = geometry.getAsJsonObject("description");
        root = LayerDefinition.create(mesh, desc.get("texture_width").getAsInt(), desc.get("texture_height").getAsInt()).bakeRoot();
        base = root.getChild("player");
        waist = base.getChild("Waist");
        head = waist.getChild("Head");
        var animations = load("animations/template_player.animation.json").getAsJsonObject("animations");
        for (var anim : animations.entrySet()) {
            var a = anim.getValue().getAsJsonObject();
            var builder = AnimationDefinition.Builder.withLength(a.get("animation_length").getAsFloat());
            for (var bone : a.getAsJsonObject("bones").entrySet()) {
                for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                    boolean scale = channel.getKey().equals("scale");
                    List<Keyframe> keys = new ArrayList<>();
                    for (var key : channel.getValue().getAsJsonObject().entrySet()) {
                        var data = key.getValue().getAsJsonObject();
                        var post = data.has("post") ? data.getAsJsonObject("post") : data;
                        var v = vec(post.getAsJsonArray("vector"));
                        keys.add(new Keyframe(Float.parseFloat(key.getKey()), scale ? KeyframeAnimations.scaleVec(v.x, v.y, v.z) : KeyframeAnimations.degreeVec(v.x, v.y, v.z), data.has("lerp_mode") ? AnimationChannel.Interpolations.CATMULLROM : AnimationChannel.Interpolations.LINEAR));
                    }
                    keys.sort(Comparator.comparingDouble(Keyframe::timestamp));
                    builder.addAnimation(bone.getKey(), new AnimationChannel(scale ? AnimationChannel.Targets.SCALE : AnimationChannel.Targets.ROTATION, keys.toArray(Keyframe[]::new)));
                }
            }
            clips.put(anim.getKey(), builder.build());
        }
    }

    void sample(boolean done, float ticks) {
        root.getAllParts().forEach(ModelPart::resetPose);
        var clip = clips.get(done ? "kick_done" : "kick_start");
        long millis = Math.round(Math.max(0, Math.min(clip.lengthInSeconds(), ticks / 20)) * 1000);
        KeyframeAnimations.animate(this, clip, millis, 1, scratch);
    }

    static void orient(PoseStack ps, float yaw) {
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        ps.scale(-1, -1, 1);
    }

    PoseStack eyes(float yaw) {
        PoseStack ps = new PoseStack();
        orient(ps, yaw);
        base.translateAndRotate(ps);
        waist.translateAndRotate(ps);
        head.translateAndRotate(ps);
        ps.translate(0, -4.2 / 16, -3.9 / 16);
        return ps;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Player p, float a, float b, float c, float d, float e) {
    }
}
