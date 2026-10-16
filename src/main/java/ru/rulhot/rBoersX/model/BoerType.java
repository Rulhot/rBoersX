package ru.rulhot.rBoersX.model;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.menu.ItemTemplate;
import ru.rulhot.rBoersX.util.MessageUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.HashMap;
import java.util.Map;

public record BoerType(
        @NotNull String id,
        int level,
        @NotNull String name,
        @NotNull Material block,
        int radius,
        int depth,
        int intervalTicks,
        int blocksPerCycle,
        long storageLimit,
        long maxFuelMillis,
        double fuelMultiplier,
        boolean autoSmelt,
        boolean removeLiquids,
        @NotNull BlockData borderBlock,
        @Nullable Color borderColor,
        @NotNull ItemTemplate item,
        @NotNull ItemStack tool
) {

    public static final int TICK_STEP = 5;

    private static final int MAX_RADIUS = 32;
    private static final int MAX_DEPTH = 512;
    private static final int MAX_INTERVAL_TICKS = 72_000;
    private static final int MAX_BLOCKS_PER_CYCLE = 64;
    private static final int MAX_FORTUNE = 10;
    private static final int MAX_FUEL_MINUTES = 100_000;
    private static final double MIN_FUEL_MULTIPLIER = 0.01D;
    private static final double MAX_FUEL_MULTIPLIER = 100.0D;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final int TICKS_PER_MINUTE = 1_200;
    private static final int HEX_RADIX = 16;
    private static final Material DEFAULT_BORDER = Material.WHITE_STAINED_GLASS;

    public static @Nullable BoerType parse(@NotNull String id, @NotNull ConfigurationSection section,
                                           @NotNull String context) {
        Material block = Settings.material(section.getString("block"), context + ".block");
        if (block == null) {
            return null;
        }
        if (!block.isBlock() || !block.isItem() || !block.isSolid() || block.hasGravity()) {
            Logger.warn(context + ".block: " + block + " не подходит для бура, пропускаю");
            return null;
        }
        boolean silkTouch = section.getBoolean("silk-touch", false);
        int fortune = Math.clamp(section.getInt("fortune", 0), 0, MAX_FORTUNE);
        ItemStack tool = new ItemStack(Material.NETHERITE_PICKAXE);
        if (silkTouch) {
            tool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        } else if (fortune > 0) {
            tool.addUnsafeEnchantment(Enchantment.FORTUNE, fortune);
        }
        return new BoerType(id,
                Math.max(1, section.getInt("level", 1)),
                section.getString("name", id),
                block,
                Math.clamp(section.getInt("radius", 1), 0, MAX_RADIUS),
                Math.clamp(section.getInt("depth", 16), 1, MAX_DEPTH),
                Math.clamp(section.getInt("interval-ticks", 40), TICK_STEP, MAX_INTERVAL_TICKS),
                Math.clamp(section.getInt("blocks-per-cycle", 1), 1, MAX_BLOCKS_PER_CYCLE),
                Math.max(0L, section.getLong("storage-limit", 0L)),
                Math.clamp(section.getLong("max-fuel-minutes", 120L), 1L, MAX_FUEL_MINUTES) * MILLIS_PER_MINUTE,
                Math.clamp(Settings.decimal(section, "fuel-multiplier", 1.0D, context),
                        MIN_FUEL_MULTIPLIER, MAX_FUEL_MULTIPLIER),
                section.getBoolean("auto-smelt", false),
                section.getBoolean("remove-liquids", true),
                borderBlock(section.getString("border.material"), context),
                color(section.getString("border.glow-color"), context),
                ItemTemplate.of(new ItemStack(block), Settings.section(section, "item", context), context + ".item"),
                tool);
    }

    public int size() {
        return radius * 2 + 1;
    }

    public long area() {
        return (long) size() * size();
    }

    public long totalBlocks() {
        return area() * depth;
    }

    public double blocksPerMinute() {
        return (double) blocksPerCycle * TICKS_PER_MINUTE / intervalTicks;
    }

    public @NotNull Map<String, Object> placeholders() {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("id", id);
        placeholders.put("level", level);
        placeholders.put("name", MessageUtil.parseItemText(name));
        placeholders.put("radius", radius);
        placeholders.put("size", size());
        placeholders.put("depth", depth);
        placeholders.put("speed", MessageUtil.number(blocksPerMinute()));
        return placeholders;
    }

    private static @NotNull BlockData borderBlock(@Nullable String raw, @NotNull String context) {
        Material material = raw == null ? DEFAULT_BORDER : Settings.material(raw, context + ".border.material");
        if (material == null || !material.isBlock()) {
            return DEFAULT_BORDER.createBlockData();
        }
        return material.createBlockData();
    }

    private static @Nullable Color color(@Nullable String raw, @NotNull String context) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String hex = raw.trim().startsWith("#") ? raw.trim().substring(1) : raw.trim();
        try {
            return Color.fromRGB(Integer.parseInt(hex, HEX_RADIX));
        } catch (IllegalArgumentException exception) {
            Logger.warn(context + ".border.glow-color: '" + raw + "' не цвет, подсветка без цвета");
            return null;
        }
    }
}
