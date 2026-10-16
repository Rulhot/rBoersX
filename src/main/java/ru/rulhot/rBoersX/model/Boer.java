package ru.rulhot.rBoersX.model;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.database.model.BoerRecord;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class Boer {

    private static final String SEPARATOR = ",";
    private static final int CHUNK_SHIFT = 4;
    private static final List<BlockFace> FACES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST);

    private final @NotNull Pos pos;
    private final @NotNull String typeId;
    private final @NotNull UUID owner;
    private final @NotNull String ownerName;
    private final @NotNull BlockFace facing;
    private final @NotNull Set<Material> disabledBlocks = EnumSet.noneOf(Material.class);
    private final @NotNull Map<ItemStack, Integer> storage = new LinkedHashMap<>();
    private final @NotNull AtomicInteger countdown = new AtomicInteger();
    private volatile long id;
    private volatile boolean removed;
    private volatile @NotNull List<Long> heldChunks = List.of();
    private @NotNull Status status = Status.STOPPED;
    private volatile @Nullable Inventory fuelInventory;
    private volatile @NotNull List<Integer> fuelSlots = List.of();
    private byte @Nullable [] savedFuel;
    private byte @Nullable [] savedItems;
    private @Nullable String savedCounts;
    private long fuelMillis;
    private long cursor;
    private long mined;
    private long stored;
    private boolean mineOther = true;
    private boolean forwardZone;
    private boolean dirty;
    private boolean storageChanged;

    public Boer(@NotNull Pos pos, @NotNull String typeId, @NotNull UUID owner, @NotNull String ownerName,
                 @NotNull BlockFace facing, long fuelMillis) {
        this.pos = pos;
        this.typeId = typeId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.facing = facing;
        this.fuelMillis = Math.max(0L, fuelMillis);
    }

    public static @Nullable Boer fromRecord(@NotNull BoerRecord record) {
        String world = record.getWorld();
        String owner = record.getOwner();
        String type = record.getType();
        if (world == null || owner == null || type == null) {
            Logger.warn("Бур id=" + record.getId() + " повреждён в базе, пропускаю");
            return null;
        }
        Boer boer;
        try {
            boer = new Boer(new Pos(UUID.fromString(world), record.getX(), record.getY(), record.getZ()), type,
                    UUID.fromString(owner), record.getOwnerName() == null ? "" : record.getOwnerName(),
                    parseFacing(record.getFacing()), record.getFuelMillis());
        } catch (IllegalArgumentException exception) {
            Logger.warn("Бур id=" + record.getId() + ": неверный UUID в базе, пропускаю");
            return null;
        }
        boer.id = record.getId();
        boer.status = Status.find(record.getStatus());
        boer.cursor = Math.max(0L, record.getCursor());
        boer.mined = Math.max(0L, record.getMined());
        boer.mineOther = record.isMineOther();
        boer.forwardZone = record.isForwardZone();
        boer.disabledBlocks.addAll(parseMaterials(record.getDisabledBlocks()));
        boer.savedFuel = record.getFuelItems();
        boer.savedItems = record.getStorageItems();
        boer.savedCounts = record.getStorageCounts();
        return boer;
    }

    public synchronized @NotNull BoerRecord toRecord() {
        if (storageChanged) {
            savedItems = storage.isEmpty() ? null : ItemStack.serializeItemsAsBytes(storage.keySet());
            savedCounts = storage.values().stream().map(String::valueOf).collect(Collectors.joining(SEPARATOR));
            storageChanged = false;
        }
        byte[] items = savedItems;
        String counts = savedCounts;
        return new BoerRecord(id, pos.world().toString(), pos.x(), pos.y(), pos.z(), typeId, owner.toString(),
                ownerName, facing.name(), forwardZone, fuelMillis, status.name(), cursor, mined, mineOther,
                joinMaterials(disabledBlocks), encodeFuel(), items, counts);
    }

    public @Nullable Inventory fuelInventory() {
        return fuelInventory;
    }

    public @NotNull List<Integer> fuelSlots() {
        return fuelSlots;
    }

    public synchronized @NotNull List<ItemStack> attachFuel(@NotNull Inventory inventory,
                                                           @NotNull List<Integer> slots) {
        List<ItemStack> items = fuelItems();
        fuelInventory = inventory;
        fuelSlots = List.copyOf(slots);
        savedFuel = null;
        dirty = true;
        return items;
    }

    public synchronized @NotNull List<ItemStack> fuelItems() {
        Inventory inventory = fuelInventory;
        List<ItemStack> items = new ArrayList<>();
        if (inventory != null) {
            for (int slot : fuelSlots) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && !item.isEmpty()) {
                    items.add(item.clone());
                }
            }
            return items;
        }
        byte[] bytes = savedFuel;
        if (bytes == null || bytes.length == 0) {
            return items;
        }
        try {
            for (ItemStack item : ItemStack.deserializeItemsFromBytes(bytes)) {
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        } catch (RuntimeException exception) {
            Logger.error("Бур id=" + id + ": топливо повреждено", exception);
        }
        return items;
    }

    public synchronized @NotNull List<ItemStack> takeFuelItems() {
        List<ItemStack> items = fuelItems();
        Inventory inventory = fuelInventory;
        if (inventory != null) {
            fuelSlots.forEach(slot -> inventory.setItem(slot, null));
        }
        dirty = true;
        return items;
    }

    public synchronized void finish(long totalBlocks) {
        cursor = Math.max(cursor, totalBlocks);
        dirty = true;
    }

    public synchronized void resetCursor() {
        cursor = 0L;
        if (status == Status.FINISHED) {
            status = Status.STOPPED;
        }
        dirty = true;
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    private byte @Nullable [] encodeFuel() {
        if (fuelInventory == null) {
            return savedFuel;
        }
        List<ItemStack> items = fuelItems();
        return items.isEmpty() ? null : ItemStack.serializeItemsAsBytes(items);
    }

    public synchronized void loadStorage() {
        byte[] items = savedItems;
        String counts = savedCounts;
        if (items == null || items.length == 0 || counts == null || counts.isBlank()) {
            return;
        }
        try {
            ItemStack[] keys = ItemStack.deserializeItemsFromBytes(items);
            String[] amounts = counts.split(SEPARATOR);
            for (int index = 0; index < Math.min(keys.length, amounts.length); index++) {
                int amount = Integer.parseInt(amounts[index].trim());
                if (!keys[index].isEmpty() && amount > 0) {
                    storage.merge(keys[index].asOne(), amount, Integer::sum);
                    stored += amount;
                }
            }
        } catch (RuntimeException exception) {
            Logger.error("Бур id=" + id + ": склад повреждён", exception);
        }
    }

    public @NotNull Pos pos() {
        return pos;
    }

    public @NotNull String typeId() {
        return typeId;
    }

    public @NotNull UUID owner() {
        return owner;
    }

    public @NotNull String ownerName() {
        return ownerName;
    }

    public long id() {
        return id;
    }

    public void assignId(long id) {
        this.id = id;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void markRemoved() {
        removed = true;
    }

    public @NotNull List<Long> heldChunks() {
        return heldChunks;
    }

    public void heldChunks(@NotNull List<Long> chunks) {
        heldChunks = List.copyOf(chunks);
    }

    public boolean tick(int elapsedTicks) {
        return countdown.addAndGet(-elapsedTicks) <= 0;
    }

    public void schedule(int ticks) {
        countdown.set(ticks);
    }

    public synchronized @NotNull Status status() {
        return status;
    }

    public synchronized boolean isRunning() {
        return status == Status.RUNNING;
    }

    public synchronized void status(@NotNull Status status) {
        if (this.status != status) {
            this.status = status;
            dirty = true;
        }
    }

    public synchronized long fuelMillis() {
        return fuelMillis;
    }

    public synchronized void addFuel(long millis) {
        fuelMillis += Math.max(0L, millis);
        dirty = true;
    }

    public synchronized long consumeFuel(long millis) {
        fuelMillis = Math.max(0L, fuelMillis - Math.max(0L, millis));
        dirty = true;
        return fuelMillis;
    }

    public synchronized long cursor() {
        return cursor;
    }

    public synchronized void advance(boolean minedBlock) {
        cursor++;
        if (minedBlock) {
            mined++;
        }
        dirty = true;
    }

    public synchronized long mined() {
        return mined;
    }

    public synchronized boolean forwardZone() {
        return forwardZone;
    }

    public synchronized void toggleForwardZone() {
        forwardZone = !forwardZone;
        dirty = true;
    }

    public int zoneX(int radius) {
        return zoneX(radius, forwardZone());
    }

    public int zoneZ(int radius) {
        return zoneZ(radius, forwardZone());
    }

    public int zoneX(int radius, boolean forward) {
        return forward ? pos.x() + facing.getModX() * (radius + 1) : pos.x();
    }

    public int zoneZ(int radius, boolean forward) {
        return forward ? pos.z() + facing.getModZ() * (radius + 1) : pos.z();
    }

    public synchronized boolean mineOther() {
        return mineOther;
    }

    public synchronized void toggleMineOther() {
        mineOther = !mineOther;
        dirty = true;
    }

    public synchronized boolean mines(@NotNull Material block, boolean inCatalogue) {
        return inCatalogue ? !disabledBlocks.contains(block) : mineOther;
    }

    public synchronized boolean isBlockEnabled(@NotNull Material block) {
        return !disabledBlocks.contains(block);
    }

    public synchronized void toggleBlock(@NotNull Material block) {
        if (!disabledBlocks.remove(block)) {
            disabledBlocks.add(block);
        }
        dirty = true;
    }

    public synchronized void setBlocks(@NotNull Collection<Material> catalogue, boolean enabled) {
        if (enabled) {
            disabledBlocks.clear();
        } else {
            disabledBlocks.addAll(catalogue);
        }
        dirty = true;
    }

    public synchronized boolean store(@NotNull List<ItemStack> drops, long itemLimit) {
        long amount = 0L;
        for (ItemStack drop : drops) {
            amount += drop.getAmount();
        }
        if (itemLimit > 0L && stored + amount > itemLimit) {
            return false;
        }
        for (ItemStack drop : drops) {
            restore(drop.asOne(), drop.getAmount());
        }
        return true;
    }

    public synchronized boolean isStorageFull(long itemLimit) {
        return itemLimit > 0L && stored >= itemLimit;
    }

    public synchronized int take(@NotNull ItemStack key, int requested) {
        Integer available = storage.get(key);
        if (available == null || requested <= 0) {
            return 0;
        }
        int taken = Math.min(available, requested);
        if (taken == available) {
            storage.remove(key);
        } else {
            storage.put(key, available - taken);
        }
        stored -= taken;
        storageChanged = true;
        dirty = true;
        return taken;
    }

    public synchronized void restore(@NotNull ItemStack key, int amount) {
        if (amount <= 0 || key.isEmpty()) {
            return;
        }
        storage.merge(key, amount, (left, right) -> (int) Math.min(Integer.MAX_VALUE, (long) left + right));
        stored += amount;
        storageChanged = true;
        dirty = true;
    }

    public synchronized int storageStackCount() {
        long count = 0L;
        for (Map.Entry<ItemStack, Integer> entry : storage.entrySet()) {
            int max = entry.getKey().getMaxStackSize();
            count += (entry.getValue() + max - 1) / max;
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    public synchronized @NotNull List<ItemStack> storagePage(int offset, int limit) {
        List<ItemStack> stacks = new ArrayList<>(limit);
        long index = 0L;
        for (Map.Entry<ItemStack, Integer> entry : storage.entrySet()) {
            int max = entry.getKey().getMaxStackSize();
            int amount = entry.getValue();
            long count = (amount + max - 1) / max;
            if (index + count <= offset) {
                index += count;
                continue;
            }
            for (long position = Math.max(index, offset); position < index + count; position++) {
                if (stacks.size() >= limit) {
                    return stacks;
                }
                boolean last = position == index + count - 1;
                stacks.add(entry.getKey().asQuantity(last ? amount - (int) (count - 1) * max : max));
            }
            index += count;
        }
        return stacks;
    }

    public synchronized long stored() {
        return stored;
    }

    public synchronized boolean isStorageEmpty() {
        return storage.isEmpty();
    }

    public synchronized boolean takeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    private static @NotNull BlockFace parseFacing(@Nullable String raw) {
        for (BlockFace face : FACES) {
            if (face.name().equalsIgnoreCase(raw)) {
                return face;
            }
        }
        return BlockFace.NORTH;
    }

    private static @NotNull String joinMaterials(@NotNull Set<Material> materials) {
        return materials.stream().map(Material::name).collect(Collectors.joining(SEPARATOR));
    }

    private static @NotNull List<Material> parseMaterials(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Material> materials = new ArrayList<>();
        for (String name : raw.split(SEPARATOR)) {
            Material material = Material.getMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    public enum Status {
        STOPPED,
        RUNNING,
        NO_FUEL,
        STORAGE_FULL,
        REGION,
        FINISHED;

        public @NotNull String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static @NotNull Status find(@Nullable String name) {
            for (Status status : values()) {
                if (status.name().equalsIgnoreCase(name)) {
                    return status;
                }
            }
            return STOPPED;
        }
    }

    public record Pos(@NotNull UUID world, int x, int y, int z) {

        public int chunkX() {
            return x >> CHUNK_SHIFT;
        }

        public int chunkZ() {
            return z >> CHUNK_SHIFT;
        }

        public long chunkKey() {
            return Chunk.getChunkKey(chunkX(), chunkZ());
        }

        @Override
        public @NotNull String toString() {
            return x + " " + y + " " + z;
        }
    }
}
