package ru.rulhot.rBoersX.hook;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import ru.rulhot.rBoersX.model.Settings;

import java.util.List;
import java.util.Optional;

public final class FancyHologramHook implements HologramHook {

    @Override
    public void show(@NotNull String name, @NotNull Location location, @NotNull List<String> lines,
                     @NotNull Settings.HologramStyle style) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Optional<Hologram> present = manager.getHologram(name);
        if (present.isPresent() && present.get().getData() instanceof TextHologramData text) {
            apply(text, lines, style);
            present.get().forceUpdate();
            present.get().refreshForViewersInWorld();
            return;
        }
        present.ifPresent(manager::removeHologram);
        TextHologramData data = new TextHologramData(name, location);
        apply(data, lines, style);
        data.setPersistent(false);
        manager.addHologram(manager.create(data));
    }

    @Override
    public void remove(@NotNull String name) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(name).ifPresent(manager::removeHologram);
    }

    private static void apply(@NotNull TextHologramData data, @NotNull List<String> lines,
                              @NotNull Settings.HologramStyle style) {
        data.setText(lines);
        data.setTextAlignment(style.alignment());
        data.setBackground(style.background());
        data.setTextShadow(style.textShadow());
        data.setSeeThrough(style.seeThrough());
        data.setBillboard(style.billboard());
        data.setScale(new Vector3f(style.scale()));
        data.setBrightness(style.brightness() < 0 ? null : new Display.Brightness(style.brightness(), style.brightness()));
        data.setShadowRadius(style.shadowRadius());
        data.setShadowStrength(style.shadowStrength());
        if (style.visibilityDistance() > 0) {
            data.setVisibilityDistance(style.visibilityDistance());
        }
    }
}
