package ru.rulhot.rBoersX.model;

import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record Settings(
        boolean debug,
        boolean metrics,
        boolean updateCheck,
        @NotNull Command command,
        @NotNull Limits limits,
        @NotNull Pickup pickup,
        @NotNull Worlds worlds,
        @NotNull Fuel fuel,
        @NotNull Mining mining,
        @NotNull Filters filters,
        @NotNull Border border,
        @NotNull Hologram hologram,
        @Nullable Sound clickSound,
        @NotNull BreakSound breakSound,
        int autosaveSeconds,
        int menuRefreshTicks,
        @NotNull Map<String, BoerType> boers,
        @NotNull Set<Material> boerBlocks
) {

    private static final String CONFIG = "config.yml";
    private static final String ITEMS = "items.yml";
    private static final String FUEL = "fuel.yml";
    private static final String BLOCKS = "blocks.yml";
    private static final String HOLOGRAMS = "holograms.yml";
    private static final int MIN_MENU_REFRESH_TICKS = 5;
    private static final int MIN_AUTOSAVE_SECONDS = 10;
    private static final double MIN_VOLUME = 0.0D;
    private static final double MAX_VOLUME = 1.0D;
    private static final double MIN_PITCH = 0.5D;
    private static final double MAX_PITCH = 2.0D;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final int RGB_LENGTH = 6;
    private static final int ARGB_LENGTH = 8;
    private static final int HEX_RADIX = 16;

    public Settings {
        autosaveSeconds = Math.max(MIN_AUTOSAVE_SECONDS, autosaveSeconds);
        menuRefreshTicks = Math.max(MIN_MENU_REFRESH_TICKS, menuRefreshTicks);
    }

    public static @NotNull Settings from(@NotNull ConfigurationSection config, @NotNull ConfigurationSection items,
                                         @NotNull ConfigurationSection fuel,
                                         @NotNull ConfigurationSection catalogue,
                                         @NotNull ConfigurationSection holograms) {
        ConfigurationSection general = section(config, "settings");
        ConfigurationSection performance = section(config, "performance");
        ConfigurationSection command = section(config, "command");
        ConfigurationSection limits = section(config, "limits");
        ConfigurationSection worlds = section(config, "worlds");
        ConfigurationSection border = section(items, "border");
        Map<String, BoerType> types = boers(items);
        Set<Material> blocks = EnumSet.noneOf(Material.class);
        types.values().forEach(type -> blocks.add(type.block()));
        return new Settings(
                general.getBoolean("debug", false),
                general.getBoolean("metrics", true),
                general.getBoolean("update-check", true),
                new Command(command.getString("main", "rboersx"), List.copyOf(command.getStringList("aliases"))),
                new Limits(limits.getInt("per-player", 3), limits.getBoolean("allow-overlap", false)),
                enumOr(Pickup.class, limits.getString("pickup"), Pickup.OWNER, CONFIG + " limits.pickup"),
                new Worlds(
                        enumOr(Worlds.Mode.class, worlds.getString("mode"), Worlds.Mode.BLACKLIST,
                                CONFIG + " worlds.mode"),
                        Set.copyOf(worlds.getStringList("list"))),
                fuel(fuel),
                mining(catalogue, performance),
                new Filters(materials(catalogue.getStringList("list"), BLOCKS + " list")),
                new Border(
                        (float) decimal(border, "thickness", 0.06D, ITEMS + " border"),
                        (float) decimal(border, "corner-size", 0.16D, ITEMS + " border"),
                        (float) decimal(border, "corner-height", 1.0D, ITEMS + " border"),
                        (float) decimal(border, "y-offset", 0.01D, ITEMS + " border"),
                        border.getBoolean("glow", true),
                        decimal(performance, "border-view-radius", 48.0D, CONFIG + " performance"),
                        performance.getInt("border-update-ticks", 20)),
                hologram(holograms, performance),
                sound(section(config, "sounds.click"), CONFIG + " sounds.click"),
                breakSound(section(config, "sounds.break"), CONFIG + " sounds.break"),
                performance.getInt("autosave-seconds", 60),
                performance.getInt("menu-refresh-ticks", 20),
                Collections.unmodifiableMap(types),
                Collections.unmodifiableSet(blocks));
    }

    public static @NotNull ConfigurationSection section(@NotNull ConfigurationSection root, @NotNull String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section != null) {
            return section;
        }
        Logger.warn("Нет секции " + path);
        return new MemoryConfiguration();
    }

    public static <E extends Enum<E>> @NotNull E enumOr(@NotNull Class<E> type, @Nullable String raw,
                                                        @NotNull E fallback, @NotNull String context) {
        if (raw != null && !raw.isBlank()) {
            try {
                return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                Logger.warn(context + ": '" + raw + "' не подходит, взято " + fallback);
                return fallback;
            }
        }
        Logger.warn(context + ": пусто, взято " + fallback);
        return fallback;
    }

    public static double decimal(@NotNull ConfigurationSection section, @NotNull String path, double fallback,
                                 @NotNull String context) {
        Object value = section.get(path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            Logger.warn(context + "." + path + ": '" + value + "' не число, взято " + fallback);
            return fallback;
        }
    }

    public static @Nullable Material material(@Nullable String raw, @NotNull String context) {
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material == null) {
            Logger.warn(context + ": нет материала " + raw);
        }
        return material;
    }

    private static @NotNull Hologram hologram(@NotNull ConfigurationSection section,
                                              @NotNull ConfigurationSection performance) {
        String context = HOLOGRAMS;
        ConfigurationSection style = section(section, "style");
        return new Hologram(
                section.getBoolean("enabled", true),
                decimal(section, "height", 0.6D, context),
                performance.getInt("hologram-update-ticks", 20),
                section.getBoolean("only-running", false),
                List.copyOf(section.getStringList("lines.running")),
                List.copyOf(section.getStringList("lines.stopped")),
                new HologramStyle(
                        (float) decimal(style, "scale", 1.0D, context + " style"),
                        enumOr(Display.Billboard.class, style.getString("billboard"), Display.Billboard.CENTER,
                                context + ".style.billboard"),
                        enumOr(TextDisplay.TextAlignment.class, style.getString("alignment"),
                                TextDisplay.TextAlignment.CENTER, context + ".style.alignment"),
                        background(style.getString("background", "default"), context + ".style.background"),
                        style.getBoolean("text-shadow", false),
                        style.getBoolean("see-through", false),
                        style.getInt("brightness", -1),
                        style.getInt("visibility-distance", -1),
                        (float) decimal(style, "shadow-radius", 0.0D, context + " style"),
                        (float) decimal(style, "shadow-strength", 1.0D, context + " style")));
    }

    private static @Nullable Color background(@NotNull String raw, @NotNull String context) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("default")) {
            return null;
        }
        if (value.equals("transparent")) {
            return Color.fromARGB(0);
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            long parsed = Long.parseLong(hex, HEX_RADIX);
            if (hex.length() == RGB_LENGTH) {
                return Color.fromRGB((int) parsed);
            }
            if (hex.length() == ARGB_LENGTH) {
                return Color.fromARGB((int) parsed);
            }
        } catch (NumberFormatException exception) {
            Logger.warn(context + ": неверный фон '" + raw + "', взят default");
            return null;
        }
        Logger.warn(context + ": неверный фон '" + raw + "', взят default");
        return null;
    }

    private static @NotNull BreakSound breakSound(@NotNull ConfigurationSection section, @NotNull String context) {
        return new BreakSound(
                (float) Math.clamp(decimal(section, "volume", MAX_VOLUME, context), MIN_VOLUME, MAX_VOLUME),
                (float) Math.clamp(decimal(section, "pitch", 1.0D, context), MIN_PITCH, MAX_PITCH));
    }

    private static @Nullable Sound sound(@NotNull ConfigurationSection section, @NotNull String context) {
        String raw = section.getString("key");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        if (key == null || Registry.SOUND_EVENT.get(key) == null) {
            Logger.warn(context + ": нет звука " + raw);
            return null;
        }
        return Sound.sound(key, Sound.Source.MASTER,
                (float) Math.clamp(decimal(section, "volume", MAX_VOLUME, context), MIN_VOLUME, MAX_VOLUME),
                (float) Math.clamp(decimal(section, "pitch", 1.0D, context), MIN_PITCH, MAX_PITCH));
    }

    private static @NotNull List<Material> materials(@NotNull List<String> raw, @NotNull String context) {
        Set<Material> result = new LinkedHashSet<>();
        for (String name : raw) {
            Material material = material(name, context);
            if (material != null) {
                result.add(material);
            }
        }
        return List.copyOf(new ArrayList<>(result));
    }

    private static @NotNull Map<String, BoerType> boers(@NotNull ConfigurationSection root) {
        Map<String, BoerType> types = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection("list");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection typeSection = section.getConfigurationSection(key);
                if (typeSection == null) {
                    continue;
                }
                BoerType type = BoerType.parse(key.toLowerCase(Locale.ROOT), typeSection, ITEMS + " list." + key);
                if (type != null) {
                    types.put(type.id(), type);
                }
            }
        }
        if (types.isEmpty()) {
            Logger.warn(ITEMS + ": нет ни одного бура");
        }
        return types;
    }

    private static @NotNull Fuel fuel(@NotNull ConfigurationSection root) {
        Map<Material, Long> items = new LinkedHashMap<>();
        ConfigurationSection itemSection = root.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                String context = FUEL + " items." + key;
                Material material = material(key, context);
                long seconds = itemSection.getLong(key, 0L);
                if (material == null) {
                    continue;
                }
                if (seconds <= 0L || !material.isItem()) {
                    Logger.warn(context + ": неверное топливо");
                    continue;
                }
                items.put(material, seconds * MILLIS_PER_SECOND);
            }
        }
        ConfigurationSection money = section(root, "money");
        return new Fuel(Collections.unmodifiableMap(items), new Money(
                money.getBoolean("enabled", true),
                enumOr(Provider.class, money.getString("provider"), Provider.VAULT, FUEL + " money.provider"),
                money.getString("currency", "money"),
                decimal(money, "price-per-minute", 50.0D, FUEL + " money")));
    }

    private static @NotNull Mining mining(@NotNull ConfigurationSection catalogue,
                                          @NotNull ConfigurationSection performance) {
        Map<Material, Material> smelt = new EnumMap<>(Material.class);
        ConfigurationSection smeltSection = catalogue.getConfigurationSection("smelt");
        if (smeltSection != null) {
            for (String key : smeltSection.getKeys(false)) {
                String context = BLOCKS + " smelt." + key;
                Material from = material(key, context);
                Material to = material(smeltSection.getString(key), context);
                if (from != null && to != null && to.isItem()) {
                    smelt.put(from, to);
                }
            }
        }
        List<Material> skip = materials(catalogue.getStringList("skip-blocks"), BLOCKS + " skip-blocks");
        return new Mining(
                skip.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(skip)),
                Collections.unmodifiableMap(smelt),
                performance.getInt("max-scan-per-cycle", 64));
    }

    public record Command(@NotNull String name, @NotNull List<String> aliases) {

        public Command {
            name = name.toLowerCase(Locale.ROOT);
        }
    }

    public record Limits(int perPlayer, boolean allowOverlap) {

        public Limits {
            perPlayer = Math.max(0, perPlayer);
        }
    }

    public record BreakSound(float volume, float pitch) {
    }

    public enum Pickup {
        OWNER,
        ANYONE
    }

    public record Worlds(@NotNull Mode mode, @NotNull Set<String> names) {

        public boolean allows(@NotNull String worldName) {
            return names.contains(worldName) == (mode == Mode.WHITELIST);
        }

        public enum Mode {
            BLACKLIST,
            WHITELIST
        }
    }

    public record Fuel(@NotNull Map<Material, Long> items, @NotNull Money money) {
    }

    public record Money(boolean enabled, @NotNull Provider provider, @NotNull String currency, double pricePerMinute) {

        public Money {
            pricePerMinute = Math.max(0.0D, pricePerMinute);
        }
    }

    public enum Provider {
        VAULT,
        BVAULT,
        COINSENGINE,
        PLAYERPOINTS
    }

    public record Mining(@NotNull Set<Material> skipBlocks, @NotNull Map<Material, Material> smelt,
                         int maxScanPerCycle) {

        private static final int MIN_SCAN = 1;
        private static final int MAX_SCAN = 1024;
    
        public Mining {
            maxScanPerCycle = Math.clamp(maxScanPerCycle, MIN_SCAN, MAX_SCAN);
        }
    }

    public record Filters(@NotNull List<Material> blocks, @NotNull Set<Material> blockSet) {

        public Filters(@NotNull List<Material> blocks) {
            this(blocks, blocks.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(blocks)));
        }
    }

    public record Hologram(boolean enabled, double height, int updateTicks,
                           boolean onlyRunning, @NotNull List<String> runningLines,
                           @NotNull List<String> stoppedLines, @NotNull HologramStyle style) {

        private static final int MIN_UPDATE_TICKS = 10;

        public Hologram {
            updateTicks = Math.max(MIN_UPDATE_TICKS, updateTicks);
        }
    }

    public record HologramStyle(float scale, @NotNull Display.Billboard billboard,
                                @NotNull TextDisplay.TextAlignment alignment, @Nullable Color background,
                                boolean textShadow, boolean seeThrough, int brightness, int visibilityDistance,
                                float shadowRadius, float shadowStrength) {

        private static final float MIN_SCALE = 0.1F;
        private static final float MAX_SCALE = 20.0F;
        private static final int MAX_BRIGHTNESS = 15;

        public HologramStyle {
            scale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
            brightness = Math.clamp(brightness, -1, MAX_BRIGHTNESS);
            shadowRadius = Math.max(0.0F, shadowRadius);
            shadowStrength = Math.max(0.0F, shadowStrength);
        }
    }

    public record Border(float thickness, float cornerSize, float cornerHeight, float yOffset, boolean glow,
                         double viewRadius, int updateTicks) {

        private static final float MIN_THICKNESS = 0.02F;
        private static final float MAX_THICKNESS = 1.0F;
        private static final float MAX_CORNER_HEIGHT = 16.0F;
        private static final double MIN_VIEW_RADIUS = 8.0D;
        private static final double MAX_VIEW_RADIUS = 128.0D;
        private static final int MIN_UPDATE_TICKS = 5;

        public Border {
            thickness = Math.clamp(thickness, MIN_THICKNESS, MAX_THICKNESS);
            cornerSize = Math.clamp(cornerSize, thickness, MAX_THICKNESS);
            cornerHeight = Math.clamp(cornerHeight, thickness, MAX_CORNER_HEIGHT);
            viewRadius = Math.clamp(viewRadius, MIN_VIEW_RADIUS, MAX_VIEW_RADIUS);
            updateTicks = Math.max(MIN_UPDATE_TICKS, updateTicks);
        }
    }
}
