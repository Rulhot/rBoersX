package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.ConfigManager;

@RequiredArgsConstructor
public final class HelpSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;

    @Override
    public @NotNull String getName() {
        return "help";
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
        config.send(sender, "help");
        return true;
    }
}
