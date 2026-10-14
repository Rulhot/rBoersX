package ru.rulhot.rBoersX.command.sub;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.ConfigManager;

import java.util.Arrays;
import java.util.List;

public final class AdminsSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;
    private final @NotNull List<SubCommand> actions;

    public AdminsSubCommand(@NotNull ConfigManager config, @NotNull List<SubCommand> actions) {
        this.config = config;
        this.actions = List.copyOf(actions);
    }

    @Override
    public @NotNull String getName() {
        return "admins";
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
        SubCommand action = args.length == 0 ? null : find(args[0]);
        if (action == null) {
            config.send(sender, "admin.usage");
            return true;
        }
        return action.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length <= 1) {
            return SubCommand.suggest(actions.stream().map(SubCommand::getName), args);
        }
        SubCommand action = find(args[0]);
        return action == null ? List.of() : action.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private @Nullable SubCommand find(@NotNull String name) {
        return actions.stream().filter(action -> action.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
