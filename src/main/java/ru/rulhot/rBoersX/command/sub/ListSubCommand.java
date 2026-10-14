package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.BoerType;
import ru.rulhot.rBoersX.util.MessageUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class ListSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;
    private final @NotNull BoerManager boers;

    @Override
    public @NotNull String getName() {
        return "list";
    }

    @Override
    public @NotNull String getPermission() {
        return BoerCommand.ADMIN_PERMISSION;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            config.send(sender, "no-permission");
            return true;
        }
        if (args.length != 1) {
            config.send(sender, "admin.list.usage");
            return true;
        }
        List<Boer> owned = owned(boers, args[0]);
        if (owned.isEmpty()) {
            config.send(sender, "admin.list.empty", Map.of("player", args[0]));
            return true;
        }
        config.send(sender, "admin.list.header", Map.of("player", owned.getFirst().ownerName(),
                "count", owned.size()));
        for (int index = 0; index < owned.size(); index++) {
            Boer boer = owned.get(index);
            Boer.Pos pos = boer.pos();
            World world = Bukkit.getWorld(pos.world());
            BoerType type = boers.type(boer);
            Map<String, Object> values = new HashMap<>();
            values.put("name", type == null ? Component.text(boer.typeId()) : MessageUtil.parseText(type.name()));
            values.put("number", index + 1);
            values.put("world", world == null ? "?" : world.getName());
            values.put("x", pos.x());
            values.put("y", pos.y());
            values.put("z", pos.z());
            values.put("status", config.message("status." + boer.status().id()));
            if (world == null || !(sender instanceof Player)) {
                config.send(sender, "admin.list.line", values);
                continue;
            }
            sender.sendMessage(config.message("admin.list.line", values)
                    .hoverEvent(HoverEvent.showText(config.message("admin.list.hover")))
                    .clickEvent(ClickEvent.runCommand("/minecraft:execute in " + world.getKey().asString()
                            + " run tp @s " + pos.x() + " " + (pos.y() + 1) + " " + pos.z())));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return args.length == 1 ? SubCommand.suggest(owners(boers), args) : List.of();
    }

    static @NotNull List<Boer> owned(@NotNull BoerManager boers, @NotNull String owner) {
        return boers.all().stream()
                .filter(boer -> boer.ownerName().equalsIgnoreCase(owner))
                .sorted(Comparator.comparing((Boer boer) -> boer.pos().world())
                        .thenComparingInt(boer -> boer.pos().x())
                        .thenComparingInt(boer -> boer.pos().y())
                        .thenComparingInt(boer -> boer.pos().z()))
                .toList();
    }

    static @NotNull Stream<String> owners(@NotNull BoerManager boers) {
        return boers.all().stream().map(Boer::ownerName).distinct();
    }
}
