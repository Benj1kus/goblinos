package com.benji.goblinos.common.worldgen;

import com.benji.goblinos.Goblinos;
import com.mojang.serialization.Codec;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public final class SnotCampStructure extends Structure {
    public static final Codec<SnotCampStructure> CODEC = simpleCodec(SnotCampStructure::new);
    private static final int HALF_WIDTH = 9;

    public SnotCampStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return Goblinos.SNOT_CAMP.get();
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int centerX = context.chunkPos().getMiddleBlockX();
        int centerZ = context.chunkPos().getMiddleBlockZ();
        int[] heights = new int[9];
        int sample = 0;
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx += HALF_WIDTH) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz += HALF_WIDTH) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int ground = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                if (ground < context.chunkGenerator().getSeaLevel() - 1) return Optional.empty();
                heights[sample++] = ground;
            }
        }
        Arrays.sort(heights);
        if (heights[8] - heights[0] > 2) return Optional.empty();
        int groundY = heights[4];

        int variant = 1 + context.random().nextInt(4);
        int rimY = variant == 2 ? 4 : 3;
        BlockPos origin = new BlockPos(centerX - HALF_WIDTH, groundY - rimY, centerZ - HALF_WIDTH);
        var element = StructurePoolElement.single(Goblinos.MODID + ":snot_camp" + variant).apply(StructureTemplatePool.Projection.RIGID);
        var templates = context.structureTemplateManager();
        var rotation = Rotation.NONE;
        var piece = new PoolElementStructurePiece(templates, element, origin, element.getGroundLevelDelta(), rotation, element.getBoundingBox(templates, origin, rotation));
        return Optional.of(new GenerationStub(new BlockPos(centerX, groundY, centerZ), builder -> builder.addPiece(piece)));
    }
}
