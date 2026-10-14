package ru.rulhot.rBoersX.manager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.menu.MenuConfig;
import ru.rulhot.rBoersX.menu.MenuHolder;
import ru.rulhot.rBoersX.model.DatabaseSettings;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.util.MessageUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final String CONFIG = "config.yml";
    private static final String DATABASE = "database.yml";
    private static final String ITEMS = "items.yml";
    private static final String FUEL = "fuel.yml";
    private static final String BLOCKS = "blocks.yml";
    private static final String HOLOGRAMS = "holograms.yml";
    private static final String MESSAGES = "messages.yml";
    private static final String MAIN_MENU = "menus/main.yml";
    private static final String FUEL_MENU = "menus/fuel.yml";
    private static final String BLOCKS_MENU = "menus/blocks.yml";
    private static final String STORAGE_MENU = "menus/storage.yml";
    private static final Set<String> ITEMS_LIVE_PATHS = Set.of("list");
    private static final Set<String> FUEL_LIVE_PATHS = Set.of("items");
    private static final Set<String> BLOCKS_LIVE_PATHS = Set.of("smelt");
    private static final Set<String> MENU_LIVE_PATHS = Set.of("buttons");
    private static final String PREFIX_KEY = "prefix";
    private static final NamedTextColor CONSOLE_PREFIX_COLOR = NamedTextColor.GOLD;
    private static final Pattern HEX_TAG = Pattern.compile("</?#[0-9a-fA-F]{6}>");
    private static final String LONG_DASH = String.valueOf((char) 0x2014);
    private static final Map<String, String> CONSOLE_SYMBOLS = Map.of("»", ">", LONG_DASH, "-", " ⛃", "", "▌", "|");
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE;
    private static final long SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR;

    private final @NotNull JavaPlugin plugin;
    private volatile @Nullable Snapshot snapshot;

    public ConfigManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull Bundle readAll() {
        return new Bundle(
                ConfigFile.read(plugin, CONFIG, Set.of()),
                ConfigFile.read(plugin, DATABASE, Set.of()),
                ConfigFile.read(plugin, ITEMS, ITEMS_LIVE_PATHS),
                ConfigFile.read(plugin, FUEL, FUEL_LIVE_PATHS),
                ConfigFile.read(plugin, BLOCKS, BLOCKS_LIVE_PATHS),
                ConfigFile.read(plugin, HOLOGRAMS, Set.of()),
                ConfigFile.read(plugin, MESSAGES, Set.of()),
                ConfigFile.read(plugin, MAIN_MENU, MENU_LIVE_PATHS),
                ConfigFile.read(plugin, FUEL_MENU, MENU_LIVE_PATHS),
                ConfigFile.read(plugin, BLOCKS_MENU, MENU_LIVE_PATHS),
                ConfigFile.read(plugin, STORAGE_MENU, MENU_LIVE_PATHS));
    }

    public void apply(@NotNull Bundle bundle) {
        Map<String, String> messages = new HashMap<>();
        for (String key : bundle.messages().getKeys(true)) {
            if (bundle.messages().isString(key)) {
                messages.put(key, bundle.messages().getString(key, ""));
            } else if (bundle.messages().isList(key)) {
                messages.put(key, String.join("\n", bundle.messages().getStringList(key)));
            }
        }
        Settings settings = Settings.from(bundle.config(), bundle.items(), bundle.fuel(), bundle.blocks(),
                bundle.holograms());
        Logger.setDebugEnabled(settings.debug());
        snapshot = new Snapshot(settings, DatabaseSettings.from(bundle.database()), Map.copyOf(messages), Map.of(
                MenuHolder.View.MAIN, MenuConfig.parse(MAIN_MENU, bundle.mainMenu()),
                MenuHolder.View.FUEL, MenuConfig.parse(FUEL_MENU, bundle.fuelMenu()),
                MenuHolder.View.BLOCKS, MenuConfig.parse(BLOCKS_MENU, bundle.blocksMenu()),
                MenuHolder.View.STORAGE, MenuConfig.parse(STORAGE_MENU, bundle.storageMenu())));
    }

    public @NotNull Settings settings() {
        return snapshot().settings();
    }

    public @NotNull DatabaseSettings database() {
        return snapshot().database();
    }

    public @NotNull MenuConfig menu(@NotNull MenuHolder.View view) {
        return snapshot().menus().get(view);
    }

    public @NotNull Component message(@NotNull String key) {
        return message(key, Map.of());
    }

    public @NotNull Component message(@NotNull String key, @NotNull Map<String, ?> placeholders) {
        Snapshot current = snapshot();
        return parse(raw(key), Placeholder.parsed(PREFIX_KEY, current.messages().getOrDefault(PREFIX_KEY, "")),
                placeholders);
    }

    public @NotNull Component duration(long millis) {
        long total = Math.max(0L, millis) / MILLIS_PER_SECOND;
        long days = total / SECONDS_PER_DAY;
        long hours = total % SECONDS_PER_DAY / SECONDS_PER_HOUR;
        long minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        long seconds = total % SECONDS_PER_MINUTE;
        if (days > 0) {
            return message("time.days-hours", Map.of("days", days, "hours", hours));
        }
        if (hours > 0) {
            return message("time.hours-minutes", Map.of("hours", hours, "minutes", minutes));
        }
        if (minutes > 0) {
            return message("time.minutes-seconds", Map.of("minutes", minutes, "seconds", seconds));
        }
        return message("time.seconds", Map.of("seconds", seconds));
    }

    public void send(@NotNull Audience audience, @NotNull String key) {
        send(audience, key, Map.of());
    }

    public void send(@NotNull Audience audience, @NotNull String key, @NotNull Map<String, ?> placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        if (!(audience instanceof ConsoleCommandSender)) {
            audience.sendMessage(message(key, placeholders));
            return;
        }
        String prefix = PlainTextComponentSerializer.plainText().serialize(
                MessageUtil.parseText(snapshot().messages().getOrDefault(PREFIX_KEY, "")));
        audience.sendMessage(parse(consoleSafe(raw), Placeholder.component(PREFIX_KEY,
                Component.text(consoleSafe(prefix), CONSOLE_PREFIX_COLOR)), placeholders));
    }

    private @NotNull Component parse(@NotNull String raw, @NotNull TagResolver prefix,
                                     @NotNull Map<String, ?> placeholders) {
        return MessageUtil.parseText(raw,
                prefix,
                Placeholder.unparsed("command", snapshot().settings().command().name()),
                MessageUtil.placeholders(placeholders));
    }

    private static @NotNull String consoleSafe(@NotNull String text) {
        String result = HEX_TAG.matcher(text).replaceAll("");
        for (Map.Entry<String, String> symbol : CONSOLE_SYMBOLS.entrySet()) {
            result = result.replace(symbol.getKey(), symbol.getValue());
        }
        return result;
    }

    public @NotNull String raw(@NotNull String key) {
        String value = snapshot().messages().get(key);
        if (value == null) {
            Logger.warn("В messages.yml нет ключа '" + key + "'");
            return "";
        }
        return value;
    }

    private @NotNull Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("Настройки ещё не загружены");
        }
        return current;
    }

    public record Bundle(@NotNull YamlConfiguration config, @NotNull YamlConfiguration database,
                         @NotNull YamlConfiguration items,
                         @NotNull YamlConfiguration fuel, @NotNull YamlConfiguration blocks,
                         @NotNull YamlConfiguration holograms,
                         @NotNull YamlConfiguration messages,
                         @NotNull YamlConfiguration mainMenu, @NotNull YamlConfiguration fuelMenu,
                         @NotNull YamlConfiguration blocksMenu,
                         @NotNull YamlConfiguration storageMenu) {
    }

    private record Snapshot(@NotNull Settings settings, @NotNull DatabaseSettings database,
                            @NotNull Map<String, String> messages,
                            @NotNull Map<MenuHolder.View, MenuConfig> menus) {
    }
}
