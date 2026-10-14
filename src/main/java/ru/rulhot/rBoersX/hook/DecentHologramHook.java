package ru.rulhot.rBoersX.hook;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.util.MessageUtil;

import java.util.List;

public final class DecentHologramHook implements HologramHook {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    @Override
    public void show(@NotNull String name, @NotNull Location location, @NotNull List<String> lines,
                     @NotNull Settings.HologramStyle style) {
        Hologram hologram = DHAPI.getHologram(name);
        if (hologram != null) {
            DHAPI.setHologramLines(hologram, legacy(lines));
            return;
        }
        DHAPI.createHologram(name, location, false, legacy(lines));
    }

    @Override
    public void remove(@NotNull String name) {
        if (DHAPI.getHologram(name) != null) {
            DHAPI.removeHologram(name);
        }
    }

    private static @NotNull List<String> legacy(@NotNull List<String> lines) {
        return lines.stream().map(line -> LEGACY.serialize(MessageUtil.parseText(line))).toList();
    }
}
