package ru.rulhot.rBoersX.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public final class MiningService {

    private static final long MILLIS_PER_TICK = 50L;
    private static final int CHUNK_SHIFT = 4;
    private static final double BLOCK_CENTER = 0.5D;
    private static final Set<Material> FLUIDS = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW);

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull FuelService fuel;

    public void work(@NotNull Boer boer, @NotNull BoerType type, @NotNull World world) {
        Boer.Pos pos = boer.pos();
        if (boer.isRemoved() || !boer.isRunning()) {
            return;
        }
        if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            return;
        }
        Block body = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (body.getType().isAir()) {
            body.setType(type.block());
        }
        if (boer.fuelMillis() <= 0L && !fuel.burn(boer)) {
            boers.stop(boer, Boer.Status.NO_FUEL, true);
            return;
        }
        boer.consumeFuel(Math.max(1L, Math.round(type.intervalTicks() * MILLIS_PER_TICK * type.fuelMultiplier())));
        Boer.Status outcome = mineCycle(boer, type, world);
        if (outcome == Boer.Status.FINISHED) {
            boer.finish(type.totalBlocks());
        }
        if (outcome != Boer.Status.RUNNING) {
            boers.stop(boer, outcome, true);
        }
    }

    private @NotNull Boer.Status mineCycle(@NotNull Boer boer, @NotNull BoerType type, @NotNull World world) {
        Settings settings = config.settings();
        Boer.Pos pos = boer.pos();
        int size = type.size();
        long area = type.area();
        int depth = (int) Math.min(type.depth(), (long) pos.y() - world.getMinHeight());
        int minedBlocks = 0;
        for (int scanned = 0; scanned < settings.mining().maxScanPerCycle(); scanned++) {
            if (minedBlocks >= type.blocksPerCycle()) {
                break;
            }
            long cursor = boer.cursor();
            if (depth <= 0 || cursor >= area * depth) {
                return Boer.Status.FINISHED;
            }
            int index = (int) (cursor % area);
            int layer = (int) (cursor / area);
            int x = boer.zoneX(type.radius()) - type.radius() + index % size;
            int y = pos.y() - 1 - layer;
            int z = boer.zoneZ(type.radius()) - type.radius() + index / size;
            int chunkX = x >> CHUNK_SHIFT;
            int chunkZ = z >> CHUNK_SHIFT;
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                boers.target(boer, chunkX, chunkZ);
                break;
            }
            switch (mine(boer, type, settings, world.getBlockAt(x, y, z))) {
                case SKIPPED -> boer.advance(false);
                case MINED -> {
                    boer.advance(true);
                    minedBlocks++;
                }
                case FULL -> {
                    return Boer.Status.STORAGE_FULL;
                }
                case REGION -> {
                    return Boer.Status.REGION;
                }
            }
        }
        return Boer.Status.RUNNING;
    }

    private @NotNull Outcome mine(@NotNull Boer boer, @NotNull BoerType type, @NotNull Settings settings,
                                  @NotNull Block block) {
        if (!boers.canBreak(boer, block.getWorld(), block.getX(), block.getY(), block.getZ())) {
            return Outcome.REGION;
        }
        Material material = block.getType();
        if (material.isAir()) {
            return Outcome.SKIPPED;
        }
        boolean fluid = block.isLiquid() || FLUIDS.contains(material);
        Settings.Mining mining = settings.mining();
        if (fluid && !type.removeLiquids() || !fluid && !isMineable(boer, settings, block, material)) {
            return Outcome.SKIPPED;
        }
        if (fluid) {
            block.setType(Material.AIR, false);
            return Outcome.SKIPPED;
        }
        List<ItemStack> loot = new ArrayList<>();
        for (ItemStack drop : block.getDrops(type.tool())) {
            ItemStack item = drop;
            Material smelted = type.autoSmelt() ? mining.smelt().get(drop.getType()) : null;
            if (smelted != null) {
                item = new ItemStack(smelted, drop.getAmount());
            }
            if (!item.isEmpty()) {
                loot.add(item);
            }
        }
        if (!boer.store(loot, type.storageLimit())) {
            return Outcome.FULL;
        }
        playSound(block, settings.breakSound());
        block.setType(Material.AIR);
        return Outcome.MINED;
    }

    private static boolean isMineable(@NotNull Boer boer, @NotNull Settings settings, @NotNull Block block,
                                      @NotNull Material material) {
        if (material.getHardness() < 0.0F || settings.mining().skipBlocks().contains(material)
                || settings.boerBlocks().contains(material)) {
            return false;
        }
        return boer.mines(material, settings.filters().blockSet().contains(material))
                && !(block.getState(false) instanceof TileState);
    }

    private static void playSound(@NotNull Block block, @NotNull Settings.BreakSound sound) {
        BlockData data = block.getBlockData();
        Location center = block.getLocation().add(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        World world = block.getWorld();
        if (sound.volume() > 0.0F) {
            world.playSound(center, data.getSoundGroup().getBreakSound(), sound.volume(), sound.pitch());
        }
    }

    private enum Outcome {
        SKIPPED,
        MINED,
        FULL,
        REGION
    }
}
