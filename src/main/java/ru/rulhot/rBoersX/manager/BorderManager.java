package ru.rulhot.rBoersX.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.util.PacketDisplay;
import ru.rulhot.rBoersX.util.SchedulerUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class BorderManager {

    private static final int FULL_BRIGHTNESS = 15;
    private static final int CHUNK_SHIFT = 4;
    private static final float HALF = 0.5F;
    private static final float VANILLA_VIEW_DISTANCE = 64.0F;

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;

    private final @NotNull Map<UUID, Map<Boer, int[]>> views = new ConcurrentHashMap<>();
    private volatile @NotNull Set<Boer> watched = Set.of();
    private @Nullable SchedulerUtil.Task task;

    public void start() {
        stop();
        long period = config.settings().border().updateTicks();
        task = scheduler.runGlobalTimer(this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        views.forEach((viewerId, shown) -> {
            Player player = Bukkit.getPlayer(viewerId);
            User viewer = player == null ? null : PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (viewer != null) {
                shown.values().forEach(ids -> PacketDisplay.destroy(viewer, ids));
            }
        });
        views.clear();
    }

    public void forget(@NotNull UUID viewerId) {
        views.remove(viewerId);
    }

    public void hide(@NotNull Boer boer) {
        views.forEach((viewerId, shown) -> {
            int[] ids = shown.remove(boer);
            Player player = ids == null ? null : Bukkit.getPlayer(viewerId);
            User viewer = player == null ? null : PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (viewer != null) {
                PacketDisplay.destroy(viewer, ids);
            }
        });
    }

    public boolean isWatched(@NotNull Boer boer) {
        return watched.contains(boer);
    }

    public void update(@NotNull Player player) {
        User viewer = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (viewer == null) {
            return;
        }
        Map<Boer, int[]> shown = views.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        Set<Boer> near = nearby(player);
        shown.entrySet().removeIf(entry -> {
            if (near.contains(entry.getKey())) {
                return false;
            }
            PacketDisplay.destroy(viewer, entry.getValue());
            return true;
        });
        World world = player.getWorld();
        for (Boer boer : near) {
            BoerType type = boers.type(boer);
            if (type != null && !shown.containsKey(boer)) {
                shown.put(boer, spawn(viewer, world, boer, type));
            }
        }
    }

    private void tick() {
        if (boers.isEmpty() && views.isEmpty()) {
            watched = Set.of();
            return;
        }
        Set<Boer> seen = new HashSet<>();
        views.values().forEach(shown -> seen.addAll(shown.keySet()));
        watched = seen;
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runFor(player, () -> update(player));
        }
    }

    private @NotNull Set<Boer> nearby(@NotNull Player player) {
        Set<Boer> near = new HashSet<>();
        if (boers.isEmpty()) {
            return near;
        }
        Location location = player.getLocation();
        UUID worldId = player.getWorld().getUID();
        double radius = config.settings().border().viewRadius();
        int minX = (int) Math.floor(location.getX() - radius) >> CHUNK_SHIFT;
        int maxX = (int) Math.floor(location.getX() + radius) >> CHUNK_SHIFT;
        int minZ = (int) Math.floor(location.getZ() - radius) >> CHUNK_SHIFT;
        int maxZ = (int) Math.floor(location.getZ() + radius) >> CHUNK_SHIFT;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                for (Boer boer : boers.inChunk(worldId, Chunk.getChunkKey(chunkX, chunkZ))) {
                    Boer.Pos pos = boer.pos();
                    double deltaX = pos.x() - location.getX();
                    double deltaY = pos.y() - location.getY();
                    double deltaZ = pos.z() - location.getZ();
                    if (!boer.isRemoved() && deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= radius * radius) {
                        near.add(boer);
                    }
                }
            }
        }
        return near;
    }

    private int @NotNull [] spawn(@NotNull User viewer, @NotNull World world, @NotNull Boer boer,
                                  @NotNull BoerType type) {
        Settings.Border border = config.settings().border();
        Boer.Pos pos = boer.pos();
        Location origin = new Location(world, pos.x(), pos.y(), pos.z());
        List<Part> parts = parts(type, border, boer.zoneX(type.radius()) - pos.x(),
                boer.zoneZ(type.radius()) - pos.z());
        int[] ids = new int[parts.size()];
        for (int index = 0; index < ids.length; index++) {
            Part part = parts.get(index);
            List<EntityData<?>> state = new ArrayList<>();
            state.add(PacketDisplay.glowing(border.glow()));
            state.add(PacketDisplay.translation(part.x(), part.y(), part.z()));
            state.add(PacketDisplay.scale(part.width(), part.height(), part.depth()));
            state.add(PacketDisplay.brightness(FULL_BRIGHTNESS, FULL_BRIGHTNESS));
            state.add(PacketDisplay.viewRange((float) border.viewRadius() / VANILLA_VIEW_DISTANCE));
            state.add(PacketDisplay.block(type.borderBlock()));
            if (type.borderColor() != null) {
                state.add(PacketDisplay.glowColor(type.borderColor()));
            }
            ids[index] = PacketDisplay.spawn(viewer, origin, state);
        }
        return ids;
    }

    private static @NotNull List<Part> parts(@NotNull BoerType type, @NotNull Settings.Border border,
                                             float shiftX, float shiftZ) {
        float edge = border.thickness();
        float corner = border.cornerSize();
        float height = border.cornerHeight();
        float base = border.yOffset();
        float min = -type.radius();
        float max = type.radius() + 1.0F;
        float length = max - min - corner * 2.0F;
        float inset = (corner - edge) * HALF;
        float far = max - corner;
        List<Part> parts = new ArrayList<>();
        for (float x : new float[]{min, far}) {
            for (float z : new float[]{min, far}) {
                parts.add(new Part(shiftX + x, base, shiftZ + z, corner, height, corner));
            }
        }
        if (length <= 0.0F) {
            return parts;
        }
        for (float y : new float[]{base, base + height - edge}) {
            parts.add(new Part(shiftX + min + corner, y, shiftZ + min + inset, length, edge, edge));
            parts.add(new Part(shiftX + min + corner, y, shiftZ + far + inset, length, edge, edge));
            parts.add(new Part(shiftX + min + inset, y, shiftZ + min + corner, edge, edge, length));
            parts.add(new Part(shiftX + far + inset, y, shiftZ + min + corner, edge, edge, length));
        }
        return parts;
    }

    private record Part(float x, float y, float z, float width, float height, float depth) {
    }
}
