package com.lunar_prototype.deepwither.modules.minerun;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

public class MineRunGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Simplex Noise を使用して自然な洞窟（空洞）を生成する
        // 全体を石で埋め、特定のスレッショルド（閾値）を超える部分を空気にすることで洞窟空間を作る
        SimplexOctaveGenerator noise1 = new SimplexOctaveGenerator(worldInfo.getSeed(), 8);
        SimplexOctaveGenerator noise2 = new SimplexOctaveGenerator(worldInfo.getSeed() + 777, 8); // 異なる波にするためシードをずらす
        noise1.setScale(0.035); 
        noise2.setScale(0.035);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 上下の境界を岩盤で塞ぐ (Y=20 を下限、Y=55 を上限とする)
                for (int y = 0; y <= 20; y++) chunkData.setBlock(x, y, z, Material.BEDROCK);
                for (int y = 55; y <= 100; y++) chunkData.setBlock(x, y, z, Material.BEDROCK);

                for (int y = 21; y < 55; y++) {
                    int realX = chunkX * 16 + x;
                    int realZ = chunkZ * 16 + z;
                    
                    // 2つの3Dノイズ値 (-1 to 1) を取得
                    double val1 = noise1.noise(realX, y * 1.5, realZ, 0.5, 0.5);
                    double val2 = noise2.noise(realX, y * 1.5, realZ, 0.5, 0.5);

                    // 両方のノイズの絶対値が非常に小さい領域（2つの波の交差＝チューブ）のみを空気とする
                    if (Math.abs(val1) < 0.08 && Math.abs(val2) < 0.08) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    } else {
                        // 確率で廃坑要素(木材等)や鉱石を混ぜる、基本は石
                        Material mat = Material.STONE;
                        double randVal = random.nextDouble();
                        // 空洞の近くほど鉱石や廃坑要素が出やすくする等の工夫(val1, val2が0.15以下の領域)
                        if (Math.abs(val1) < 0.15 && Math.abs(val2) < 0.15 && y < 35 && randVal < 0.01) {
                            mat = Material.OAK_PLANKS;
                        } else if (randVal < 0.05) {
                            mat = Material.ANDESITE;
                        } else if (randVal < 0.01) {
                            mat = Material.GOLD_ORE; // Layerに応じたドロップ設定用
                        }
                        
                        chunkData.setBlock(x, y, z, mat);
                    }
                }
            }
        }
    }
}
