package ru.rulhot.rBoersX.manager;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.database.model.BoerRecord;
import ru.rulhot.rBoersX.database.repository.BoerRepository;
import ru.rulhot.rBoersX.hook.ProtectionHook;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.service.FuelService;
import ru.rulhot.rBoersX.util.SchedulerUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class BoerManager {

    public static final String PERMISSION_USE = "rboersx.use";
    public static final String PERMISSION_LIMIT_BYPASS = "rboersx.limit.bypass";

    private static final long TICKS_PER_SECOND = 20L;
    private static final int CHUNK_KEY_SHIFT = 32;

    private final @NotNull Plugin plugin;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager configManager;
    private final @NotNull BoerRepository repository;
    private final @NotNull ProtectionHook protection;
    private final @NotNull FuelService fuel;
    private final @NotNull Map<Boer.Pos, Boer> boers = new ConcurrentHashMap<>();
    private final @NotNull Map<ChunkPos, Set<Boer>> byChunk = new ConcurrentHashMap<>();
    private final @NotNull Set<Boer> running = ConcurrentHashMap.newKeySet();
    private final @NotNull Map<ChunkPos, Integer> tickets = new HashMap<>();
    private volatile boolean loaded;
    private @Nullable Worker worker;
    private @Nullable Consumer<Boer> fuelMenus;
    private @Nullable SchedulerUtil.Task tickTask;
    private @Nullable SchedulerUtil.Task autosaveTask;

    public BoerManager(@NotNull Plugin plugin, @NotNull SchedulerUtil scheduler, @NotNull ConfigManager configManager,
                        @NotNull BoerRepository repository, @NotNull ProtectionHook protection,
                        @NotNull FuelService fuel) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.repository = repository;
        this.protection = protection;
        this.fuel = fuel;
    }

    public void reload() {
        boers.values().forEach(this::refresh);
        restartTasks();
    }

    public void load(@NotNull List<Boer> stored) {
        for (Boer boer : stored) {
            if (boers.putIfAbsent(boer.pos(), boer) != null) {
                continue;
            }
            index(boer);
            boer.loadStorage();
            attachFuelMenu(boer);
            if (boer.isRunning()) {
                running.add(boer);
            }
        }
        loaded = true;
        boers.values().forEach(this::refresh);
    }

    public void start(@NotNull Worker worker, @NotNull Consumer<Boer> fuelMenus) {
        this.worker = worker;
        this.fuelMenus = fuelMenus;
        boers.values().forEach(fuelMenus);
        restartTasks();
    }

    public void shutdown() {
        cancelTasks();
        repository.shutdown(dirtyRecords());
        boers.clear();
        byChunk.clear();
        running.clear();
        synchronized (tickets) {
            tickets.clear();
        }
    }

    public @NotNull PlaceResult place(@NotNull Player player, @NotNull Block block, @NotNull BoerType type,
                                      long fuelMillis) {
        if (!loaded) {
            return new PlaceResult.NotLoaded();
        }
        Settings settings = configManager.settings();
        World world = block.getWorld();
        if (!settings.worlds().allows(world.getName())) {
            return new PlaceResult.WorldDenied();
        }
        int limit = settings.limits().perPlayer();
        if (!player.hasPermission(PERMISSION_LIMIT_BYPASS) && countOwned(player.getUniqueId()) >= limit) {
            return new PlaceResult.LimitReached(limit);
        }
        Boer.Pos pos = new Boer.Pos(world.getUID(), block.getX(), block.getY(), block.getZ());
        Boer boer = new Boer(pos, type.id(), player.getUniqueId(), player.getName(), facing(player),
                Math.min(fuelMillis, type.maxFuelMillis()));
        if (!settings.limits().allowOverlap() && overlaps(boer, type.radius(), false)) {
            return new PlaceResult.Overlap();
        }
        if (!isAreaAllowed(boer, world, type, false)) {
            return new PlaceResult.RegionDenied();
        }
        attachFuelMenu(boer);
        boers.put(pos, boer);
        index(boer);
        boer.takeDirty();
        repository.insert(boer, boer.toRecord());
        return new PlaceResult.Placed(boer);
    }

    public void remove(@NotNull Boer boer) {
        Boer.Pos pos = boer.pos();
        if (!boers.remove(pos, boer)) {
            return;
        }
        boer.markRemoved();
        running.remove(boer);
        Set<Boer> inChunk = byChunk.get(new ChunkPos(pos.world(), pos.chunkKey()));
        if (inChunk != null) {
            inChunk.remove(boer);
        }
        release(boer);
        repository.delete(boer);
    }

    public @NotNull StartResult launch(@NotNull Boer boer) {
        BoerType type = type(boer);
        if (type == null) {
            return new StartResult.UnknownType();
        }
        if (boer.isRunning()) {
            return new StartResult.AlreadyRunning();
        }
        if (boer.cursor() >= type.totalBlocks()) {
            return new StartResult.Finished();
        }
        if (boer.isStorageFull(type.storageLimit())) {
            return new StartResult.StorageFull();
        }
        if (!fuel.hasFuel(boer)) {
            return new StartResult.NoFuel();
        }
        boer.status(Boer.Status.RUNNING);
        boer.schedule(type.intervalTicks());
        running.add(boer);
        hold(boer, List.of(boer.pos().chunkKey()));
        return new StartResult.Started();
    }

    public @NotNull ZoneResult switchZone(@NotNull Boer boer) {
        BoerType type = type(boer);
        World world = Bukkit.getWorld(boer.pos().world());
        if (type == null || world == null) {
            return new ZoneResult.Unavailable();
        }
        boolean forward = !boer.forwardZone();
        if (!configManager.settings().limits().allowOverlap() && overlaps(boer, type.radius(), forward)) {
            return new ZoneResult.Overlap();
        }
        if (!isAreaAllowed(boer, world, type, forward)) {
            return new ZoneResult.RegionDenied();
        }
        boer.toggleForwardZone();
        boer.resetCursor();
        if (boer.isRunning()) {
            hold(boer, List.of(boer.pos().chunkKey()));
        }
        return new ZoneResult.Switched(forward);
    }

    public boolean resume(@NotNull Boer boer) {
        Boer.Status status = boer.status();
        if (boer.isRemoved() || status != Boer.Status.NO_FUEL && status != Boer.Status.STORAGE_FULL) {
            return false;
        }
        return launch(boer) instanceof StartResult.Started;
    }

    public void stop(@NotNull Boer boer, @NotNull Boer.Status status, boolean notifyOwner) {
        boer.status(status);
        running.remove(boer);
        release(boer);
        if (!notifyOwner) {
            return;
        }
        Player owner = Bukkit.getPlayer(boer.owner());
        if (owner == null) {
            return;
        }
        Boer.Pos pos = boer.pos();
        configManager.send(owner, "notify." + status.id(), Map.of("x", pos.x(), "y", pos.y(), "z", pos.z()));
    }

    public void target(@NotNull Boer boer, int chunkX, int chunkZ) {
        hold(boer, List.of(boer.pos().chunkKey(), Chunk.getChunkKey(chunkX, chunkZ)));
    }

    public boolean canBreak(@NotNull Boer boer, @NotNull World world, int x, int y, int z) {
        return protection.canBreak(boer.owner(), world, x, y, z);
    }

    public void onWorldLoad(@NotNull World world) {
        for (Boer boer : boers.values()) {
            if (boer.pos().world().equals(world.getUID())) {
                refresh(boer);
            }
        }
    }

    public void onWorldUnload(@NotNull World world) {
        UUID worldId = world.getUID();
        for (Boer boer : boers.values()) {
            if (boer.pos().world().equals(worldId)) {
                boer.heldChunks(List.of());
            }
        }
        synchronized (tickets) {
            tickets.keySet().removeIf(chunk -> chunk.world().equals(worldId));
        }
    }

    public @Nullable Boer at(@NotNull Block block) {
        if (boers.isEmpty()) {
            return null;
        }
        return boers.get(new Boer.Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    public @NotNull Collection<Boer> inChunk(@NotNull UUID world, long chunkKey) {
        Set<Boer> found = byChunk.get(new ChunkPos(world, chunkKey));
        return found == null ? List.of() : found;
    }

    public @NotNull Collection<Boer> all() {
        return boers.values();
    }

    public @Nullable BoerType type(@NotNull Boer boer) {
        return configManager.settings().boers().get(boer.typeId());
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isEmpty() {
        return boers.isEmpty();
    }

    public boolean canAccess(@NotNull Player player, @NotNull Boer boer) {
        return boer.owner().equals(player.getUniqueId()) || player.hasPermission(BoerCommand.ADMIN_PERMISSION);
    }

    private void attachFuelMenu(@NotNull Boer boer) {
        Consumer<Boer> factory = fuelMenus;
        if (factory != null) {
            factory.accept(boer);
        }
    }

    private void tick() {
        Worker current = worker;
        if (current == null || !loaded) {
            return;
        }
        for (Boer boer : running) {
            if (!boer.tick(BoerType.TICK_STEP)) {
                continue;
            }
            BoerType type = type(boer);
            if (type == null) {
                continue;
            }
            boer.schedule(type.intervalTicks());
            Boer.Pos pos = boer.pos();
            World world = Bukkit.getWorld(pos.world());
            if (world == null) {
                continue;
            }
            scheduler.runAt(world, pos.chunkX(), pos.chunkZ(), () -> current.work(boer, type, world));
        }
    }

    private void restartTasks() {
        cancelTasks();
        if (worker == null) {
            return;
        }
        tickTask = scheduler.runGlobalTimer(this::tick, BoerType.TICK_STEP, BoerType.TICK_STEP);
        long autosave = configManager.settings().autosaveSeconds() * TICKS_PER_SECOND;
        autosaveTask = scheduler.runGlobalTimer(() -> repository.save(dirtyRecords()), autosave, autosave);
    }

    private void cancelTasks() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
    }

    private @NotNull List<BoerRecord> dirtyRecords() {
        List<BoerRecord> records = new ArrayList<>();
        for (Boer boer : boers.values()) {
            if (boer.id() != 0L && boer.takeDirty()) {
                records.add(boer.toRecord());
            }
        }
        return records;
    }

    private boolean isAreaAllowed(@NotNull Boer boer, @NotNull World world, @NotNull BoerType type,
                                  boolean forward) {
        Boer.Pos pos = boer.pos();
        int radius = type.radius();
        int step = Math.max(1, radius);
        int centerX = boer.zoneX(radius, forward);
        int centerZ = boer.zoneZ(radius, forward);
        int top = pos.y() - 1;
        int bottom = Math.max(world.getMinHeight(), pos.y() - type.depth());
        for (int y : new int[]{top, bottom}) {
            for (int offsetX = -radius; offsetX <= radius; offsetX += step) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ += step) {
                    if (!protection.canBreak(boer.owner(), world, centerX + offsetX, y, centerZ + offsetZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static @NotNull BlockFace facing(@NotNull Player player) {
        BlockFace face = player.getFacing();
        return face.getModY() == 0 && (face.getModX() == 0 || face.getModZ() == 0) ? face : BlockFace.NORTH;
    }

    private void refresh(@NotNull Boer boer) {
        BoerType type = type(boer);
        release(boer);
        if (type == null) {
            Logger.warn("Бур " + boer.pos() + ": нет типа " + boer.typeId() + " в items.yml");
        } else if (boer.isRunning()) {
            hold(boer, List.of(boer.pos().chunkKey()));
        }
    }

    private void hold(@NotNull Boer boer, @NotNull List<Long> keys) {
        List<Long> wanted = keys.stream().distinct().toList();
        List<Long> previous = boer.heldChunks();
        if (wanted.equals(previous)) {
            return;
        }
        Boer.Pos pos = boer.pos();
        World world = Bukkit.getWorld(pos.world());
        if (world == null) {
            return;
        }
        synchronized (tickets) {
            for (long key : wanted) {
                if (tickets.merge(new ChunkPos(pos.world(), key), 1, Integer::sum) == 1) {
                    world.addPluginChunkTicket((int) key, (int) (key >> CHUNK_KEY_SHIFT), plugin);
                }
            }
            drop(pos.world(), world, previous);
        }
        boer.heldChunks(wanted);
    }

    private void release(@NotNull Boer boer) {
        List<Long> keys = boer.heldChunks();
        if (keys.isEmpty()) {
            return;
        }
        boer.heldChunks(List.of());
        Boer.Pos pos = boer.pos();
        synchronized (tickets) {
            drop(pos.world(), Bukkit.getWorld(pos.world()), keys);
        }
    }

    private void drop(@NotNull UUID worldId, @Nullable World world, @NotNull List<Long> keys) {
        for (long key : keys) {
            Integer left = tickets.computeIfPresent(new ChunkPos(worldId, key),
                    (chunk, count) -> count > 1 ? count - 1 : null);
            if (left == null && world != null && plugin.isEnabled()) {
                world.removePluginChunkTicket((int) key, (int) (key >> CHUNK_KEY_SHIFT), plugin);
            }
        }
    }

    private void index(@NotNull Boer boer) {
        Boer.Pos pos = boer.pos();
        byChunk.computeIfAbsent(new ChunkPos(pos.world(), pos.chunkKey()), key -> ConcurrentHashMap.newKeySet())
                .add(boer);
    }

    private int countOwned(@NotNull UUID owner) {
        int count = 0;
        for (Boer boer : boers.values()) {
            if (boer.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    private boolean overlaps(@NotNull Boer boer, int radius, boolean forward) {
        Boer.Pos pos = boer.pos();
        int centerX = boer.zoneX(radius, forward);
        int centerZ = boer.zoneZ(radius, forward);
        for (Boer other : boers.values()) {
            Boer.Pos otherPos = other.pos();
            if (other == boer || !otherPos.world().equals(pos.world())) {
                continue;
            }
            BoerType otherType = type(other);
            int otherRadius = otherType == null ? 0 : otherType.radius();
            int reach = radius + otherRadius;
            if (Math.abs(other.zoneX(otherRadius) - centerX) <= reach
                    && Math.abs(other.zoneZ(otherRadius) - centerZ) <= reach) {
                return true;
            }
        }
        return false;
    }

    public interface Worker {

        void work(@NotNull Boer boer, @NotNull BoerType type, @NotNull World world);
    }

    public sealed interface PlaceResult {

        record Placed(@NotNull Boer boer) implements PlaceResult {
        }

        record NotLoaded() implements PlaceResult {
        }

        record WorldDenied() implements PlaceResult {
        }

        record LimitReached(int limit) implements PlaceResult {
        }

        record Overlap() implements PlaceResult {
        }

        record RegionDenied() implements PlaceResult {
        }
    }

    public sealed interface ZoneResult {

        record Switched(boolean forward) implements ZoneResult {
        }

        record Overlap() implements ZoneResult {
        }

        record RegionDenied() implements ZoneResult {
        }

        record Unavailable() implements ZoneResult {
        }
    }

    public sealed interface StartResult {

        record Started() implements StartResult {
        }

        record AlreadyRunning() implements StartResult {
        }

        record UnknownType() implements StartResult {
        }

        record NoFuel() implements StartResult {
        }

        record StorageFull() implements StartResult {
        }

        record Finished() implements StartResult {
        }
    }

    private record ChunkPos(@NotNull UUID world, long key) {
    }
}
