package ru.rulhot.rBoersX.manager;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.hook.HologramHook;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.service.FuelService;
import ru.rulhot.rBoersX.util.MessageUtil;
import ru.rulhot.rBoersX.util.SchedulerUtil;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramManager {

    private static final String NAME_PREFIX = "rboersx_";
    private static final double BLOCK_CENTER = 0.5D;
    private static final long PERCENT = 100L;

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;
    private final @NotNull BorderManager borders;
    private final @NotNull FuelService fuel;
    private final @Nullable HologramHook hook;
    private final @NotNull Map<Boer, List<String>> shown = new ConcurrentHashMap<>();
    private @Nullable SchedulerUtil.Task task;
    private volatile boolean broken;

    public HologramManager(@NotNull SchedulerUtil scheduler, @NotNull ConfigManager config,
                           @NotNull BoerManager boers, @NotNull BorderManager borders,
                           @NotNull FuelService fuel, @Nullable HologramHook hook) {
        this.scheduler = scheduler;
        this.config = config;
        this.boers = boers;
        this.borders = borders;
        this.fuel = fuel;
        this.hook = hook;
    }

    public void start() {
        stop();
        Settings.Hologram settings = config.settings().hologram();
        if (hook == null || broken || !settings.enabled()) {
            return;
        }
        task = scheduler.runGlobalTimer(this::tick, settings.updateTicks(), settings.updateTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        shown.keySet().forEach(this::remove);
    }

    public void remove(@NotNull Boer boer) {
        HologramHook current = hook;
        if (current == null || shown.remove(boer) == null) {
            return;
        }
        try {
            current.remove(name(boer));
        } catch (RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    private void tick() {
        HologramHook current = hook;
        if (current == null || broken) {
            return;
        }
        Settings.Hologram settings = config.settings().hologram();
        shown.keySet().stream().filter(Boer::isRemoved).toList().forEach(this::remove);
        for (Boer boer : boers.all()) {
            BoerType type = boers.type(boer);
            Boer.Pos pos = boer.pos();
            World world = Bukkit.getWorld(pos.world());
            if (type == null || world == null) {
                continue;
            }
            if (settings.onlyRunning() && !boer.isRunning()) {
                remove(boer);
                continue;
            }
            if (shown.containsKey(boer) && !borders.isWatched(boer)) {
                continue;
            }
            List<String> lines = lines(settings, boer, type);
            if (lines.equals(shown.put(boer, lines))) {
                continue;
            }
            try {
                current.show(name(boer), new Location(world, pos.x() + BLOCK_CENTER,
                        pos.y() + 1.0D + settings.height(), pos.z() + BLOCK_CENTER), lines, settings.style());
            } catch (RuntimeException | LinkageError exception) {
                disable(exception);
                return;
            }
        }
    }

    private @NotNull List<String> lines(@NotNull Settings.Hologram settings, @NotNull Boer boer,
                                        @NotNull BoerType type) {
        long cursor = Math.min(boer.cursor(), type.totalBlocks());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", type.name());
        values.put("level", String.valueOf(type.level()));
        values.put("owner", MiniMessage.miniMessage().escapeTags(boer.ownerName()));
        values.put("status", config.raw("status." + boer.status().id()));
        values.put("fuel", PlainTextComponentSerializer.plainText().serialize(config.duration(fuel.total(boer))));
        values.put("progress", String.valueOf(cursor * PERCENT / type.totalBlocks()));
        values.put("mined", MessageUtil.number(boer.mined()));
        values.put("stored", MessageUtil.number(boer.stored()));
        values.put("x", String.valueOf(boer.pos().x()));
        values.put("y", String.valueOf(boer.pos().y()));
        values.put("z", String.valueOf(boer.pos().z()));
        values.put("size", String.valueOf(type.size()));
        values.put("depth", String.valueOf(type.depth()));
        values.put("speed", MessageUtil.number(type.blocksPerMinute()));
        List<String> template = boer.isRunning() || settings.stoppedLines().isEmpty()
                ? settings.runningLines()
                : settings.stoppedLines();
        return template.stream().map(line -> {
            String result = line;
            for (Map.Entry<String, String> value : values.entrySet()) {
                result = result.replace("{" + value.getKey() + "}", value.getValue());
            }
            return result;
        }).toList();
    }

    private void disable(@NotNull Throwable throwable) {
        broken = true;
        Logger.error("Голограммы сломались, отключаю их до перезапуска", throwable);
    }

    private static @NotNull String name(@NotNull Boer boer) {
        Boer.Pos pos = boer.pos();
        return NAME_PREFIX + Integer.toHexString(pos.world().hashCode()) + "_" + pos.x() + "_" + pos.y() + "_" + pos.z();
    }
}
