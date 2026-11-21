package com.benji.goblinos.common.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class TentFootprint {
    private static final int GRID = 8;
    private static final List<Map<BlockPos, VoxelShape>> LAYOUTS = new ArrayList<>();

    static {
        for (int rotation = 0; rotation < 8; rotation++) {
            Map<BlockPos, VoxelShape> cells = new LinkedHashMap<>();
            double angle = Math.toRadians(rotation * 45);
            double sin = Math.sin(angle), cos = Math.cos(angle);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    VoxelShape cell = Shapes.empty();
                    for (int row = 0; row < GRID; row++) {
                        double dz = z - 0.5 + (row + 0.5) / GRID;
                        int first = GRID, last = -1;
                        for (int col = 0; col < GRID; col++) {
                            double dx = x - 0.5 + (col + 0.5) / GRID;
                            if (Math.abs(dx * cos + dz * sin) < 1.5 && Math.abs(-dx * sin + dz * cos) < 2.0) {
                                first = Math.min(first, col);
                                last = col;
                            }
                        }
                        if (last >= first) {
                            cell = Shapes.or(cell, Shapes.box((double) first / GRID, 0, (double) row / GRID, (last + 1.0) / GRID, 1, (row + 1.0) / GRID));
                        }
                    }
                    if (!cell.isEmpty()) {
                        cell = cell.optimize();
                        for (int y = 0; y < 2; y++) cells.put(new BlockPos(x, y, z), cell);
                    }
                }
            }
            LAYOUTS.add(Map.copyOf(cells));
        }
    }

    static Map<BlockPos, VoxelShape> cells(int rotation) {
        return LAYOUTS.get(rotation);
    }

    static int index(BlockPos offset) {
        return (offset.getY() * 5 + offset.getZ() + 2) * 5 + offset.getX() + 2;
    }

    static BlockPos offset(int index) {
        return new BlockPos(index % 5 - 2, index / 25, index / 5 % 5 - 2);
    }

    private TentFootprint() {
    }
}
