package ru.rulhot.rBoersX.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.util.MessageUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public record ItemTemplate(@NotNull ItemStack base, @Nullable String name, @NotNull List<String> lore) {

    public static @Nullable ItemTemplate parse(@NotNull ConfigurationSection section, @NotNull String context) {
        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            Logger.warn(context + ": неизвестный материал '" + materialName + "', пропускаю");
            return null;
        }
        return of(new ItemStack(material), section, context);
    }

    public static @NotNull ItemTemplate of(@NotNull ItemStack base, @NotNull ConfigurationSection section,
                                           @NotNull String context) {
        List<ItemFlag> flags = new ArrayList<>();
        for (String flag : section.getStringList("item-flags")) {
            try {
                flags.add(ItemFlag.valueOf(flag.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                Logger.warn(context + ": неизвестный флаг '" + flag + "', пропускаю");
            }
        }
        boolean glow = section.getBoolean("glow", false);
        int modelData = section.getInt("model-data", 0);
        boolean hideTooltip = section.getBoolean("hide-tooltip", false);
        String texture = section.getString("texture");
        base.editMeta(meta -> {
            meta.addItemFlags(flags.toArray(ItemFlag[]::new));
            meta.setHideTooltip(hideTooltip);
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
            if (texture != null && !texture.isBlank() && meta instanceof SkullMeta skull) {
                PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
                profile.setProperty(new ProfileProperty("textures", texture));
                skull.setPlayerProfile(profile);
            }
        });
        return new ItemTemplate(base, section.isString("name") ? section.getString("name") : null,
                List.copyOf(section.getStringList("lore")));
    }

    public @NotNull ItemStack render(@NotNull TagResolver resolver, @NotNull Map<String, List<Component>> lists) {
        return apply(base.clone(), resolver, lists);
    }

    public @NotNull ItemStack apply(@NotNull ItemStack item, @NotNull TagResolver resolver,
                                    @NotNull Map<String, List<Component>> lists) {
        item.editMeta(meta -> {
            if (name != null) {
                meta.displayName(MessageUtil.parseItemText(name, resolver));
            }
            if (lore.isEmpty()) {
                return;
            }
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                List<Component> expanded = lists.get(listName(line));
                if (expanded != null) {
                    expanded.forEach(component -> lines.add(
                            component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
                    continue;
                }
                lines.add(MessageUtil.parseItemText(line, resolver));
            }
            meta.lore(lines);
        });
        return item;
    }

    private static @NotNull String listName(@NotNull String line) {
        String trimmed = line.trim();
        boolean wrapped = trimmed.startsWith("{") && trimmed.endsWith("}");
        return wrapped ? trimmed.substring(1, trimmed.length() - 1) : "";
    }
}
