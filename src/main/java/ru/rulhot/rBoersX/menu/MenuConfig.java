package ru.rulhot.rBoersX.menu;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MenuConfig(
        @NotNull String title,
        int size,
        @NotNull Map<Integer, Button> buttons,
        @NotNull List<Integer> contentSlots,
        @Nullable ItemTemplate contentItem,
        boolean glowWhenEnabled,
        @NotNull Map<String, String> formats
) {

    private static final int ROW_SIZE = 9;
    private static final int MAX_SIZE = 54;
    private static final Pattern ACTION = Pattern.compile("^\\[([a-z-]+?)(?:-(\\d{1,6}))?]$");

    public static @NotNull MenuConfig parse(@NotNull String file, @NotNull ConfigurationSection root) {
        int size = root.getInt("size", MAX_SIZE);
        if (size % ROW_SIZE != 0 || size < ROW_SIZE || size > MAX_SIZE) {
            Logger.warn(file + ": size не кратен 9, взято " + MAX_SIZE);
            size = MAX_SIZE;
        }
        Set<Integer> contentSlots = parseSlots(root.getList("content-slots", List.of()), size,
                file + " content-slots");
        Set<Integer> used = new HashSet<>(contentSlots);

        Map<Integer, Button> buttons = new HashMap<>();
        ConfigurationSection buttonSection = root.getConfigurationSection("buttons");
        if (buttonSection != null) {
            for (String key : buttonSection.getKeys(false)) {
                String context = file + " buttons." + key;
                ConfigurationSection section = buttonSection.getConfigurationSection(key);
                ItemTemplate icon = section == null ? null : ItemTemplate.parse(section, context);
                if (icon == null) {
                    continue;
                }
                Button button = new Button(icon, parseActions(section, context),
                        state(section.getConfigurationSection("state"), context + ".state"));
                for (int slot : slots(section, size, context)) {
                    if (claim(used, slot, context)) {
                        buttons.put(slot, button);
                    }
                }
            }
        }

        ConfigurationSection contentItem = root.getConfigurationSection("content-item");
        return new MenuConfig(root.getString("title", ""), size, Map.copyOf(buttons), List.copyOf(contentSlots),
                contentItem == null ? null : ItemTemplate.parse(contentItem, file + " content-item"),
                contentItem != null && contentItem.getBoolean("glow-when-enabled", false),
                formats(root.getConfigurationSection("formats")));
    }

    public @NotNull String format(@NotNull String key, @NotNull String fallback) {
        return formats.getOrDefault(key, fallback);
    }

    private static @NotNull Map<String, String> formats(@Nullable ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> formats = new HashMap<>();
        for (String key : section.getKeys(true)) {
            if (section.isString(key)) {
                formats.put(key, section.getString(key, ""));
            }
        }
        return Map.copyOf(formats);
    }

    private static @Nullable State state(@Nullable ConfigurationSection section, @NotNull String context) {
        if (section == null) {
            return null;
        }
        Condition condition;
        try {
            condition = Condition.valueOf(section.getString("condition", "").trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            Logger.warn(context + ": неверный condition");
            return null;
        }
        ItemTemplate icon = ItemTemplate.parse(section, context);
        return icon == null ? null : new State(condition, icon);
    }

    private static boolean claim(@NotNull Set<Integer> used, int slot, @NotNull String context) {
        if (used.add(slot)) {
            return true;
        }
        Logger.warn(context + ": слот " + slot + " занят");
        return false;
    }

    private static @NotNull List<Click> parseActions(@NotNull ConfigurationSection section,
                                                     @NotNull String context) {
        List<Click> clicks = new ArrayList<>();
        for (Side side : Side.values()) {
            for (String raw : section.getStringList(side.key)) {
                Matcher matcher = ACTION.matcher(raw.trim().toLowerCase(Locale.ROOT));
                Action action = matcher.matches() ? Action.find(matcher.group(1)) : null;
                if (action == null) {
                    Logger.warn(context + ": нет действия " + raw);
                    continue;
                }
                String value = matcher.group(2);
                if (action == Action.BUY_FUEL && value == null) {
                    Logger.warn(context + ": у " + raw + " нет минут, пиши [buy-fuel-10]");
                    continue;
                }
                clicks.add(new Click(side, action, value == null ? 0 : Integer.parseInt(value)));
            }
        }
        return List.copyOf(clicks);
    }

    private static @NotNull Set<Integer> slots(@NotNull ConfigurationSection section, int size,
                                               @NotNull String context) {
        if (section.isInt("slot")) {
            return parseSlots(List.of(section.getInt("slot")), size, context);
        }
        return parseSlots(section.getList("slots", List.of()), size, context);
    }

    private static @NotNull Set<Integer> parseSlots(@NotNull List<?> raw, int size, @NotNull String context) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Object entry : raw) {
            String text = String.valueOf(entry).trim();
            int dash = text.indexOf('-', 1);
            try {
                int first = Integer.parseInt(dash < 0 ? text : text.substring(0, dash).trim());
                int second = dash < 0 ? first : Integer.parseInt(text.substring(dash + 1).trim());
                int from = Math.min(first, second);
                int to = Math.max(first, second);
                if (from < 0 || to >= size) {
                    Logger.warn(context + ": слоты " + from + "-" + to + " больше " + (size - 1));
                }
                for (int slot = Math.max(0, from); slot <= Math.min(to, size - 1); slot++) {
                    result.add(slot);
                }
            } catch (NumberFormatException exception) {
                Logger.warn(context + ": неверный слот " + text);
            }
        }
        return result;
    }

    public enum Action {
        TOGGLE,
        FUEL,
        BUY_FUEL,
        STORAGE,
        BLOCKS,
        ZONE,
        TOGGLE_OTHER,
        ENABLE_ALL,
        DISABLE_ALL,
        PREVIOUS,
        NEXT,
        BACK,
        CLOSE;

        public static @Nullable Action find(@NotNull String name) {
            for (Action action : values()) {
                if (action.name().replace('_', '-').equalsIgnoreCase(name)) {
                    return action;
                }
            }
            return null;
        }
    }

    public enum Condition {
        RUNNING
    }

    public record Button(@NotNull ItemTemplate icon, @NotNull List<Click> actions, @Nullable State state) {
    }

    public record Click(@NotNull Side side, @NotNull Action action, int value) {

        public boolean matches(boolean left) {
            return side == Side.ANY || (side == Side.LEFT) == left;
        }
    }

    public enum Side {
        ANY("actions"),
        LEFT("left-actions"),
        RIGHT("right-actions");

        private final @NotNull String key;

        Side(@NotNull String key) {
            this.key = key;
        }
    }

    public record State(@NotNull Condition condition, @NotNull ItemTemplate icon) {
    }
}
