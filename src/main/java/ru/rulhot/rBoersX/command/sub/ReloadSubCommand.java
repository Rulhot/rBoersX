package ru.rulhot.rBoersX.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.SubCommand;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.service.ReloadService;

@RequiredArgsConstructor
public final class ReloadSubCommand implements SubCommand {

    private final @NotNull ConfigManager config;
    private final @NotNull ReloadService reloads;

    @Override
    public @NotNull String getName() {
        return "reload";
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
        if (args.length != 0) {
            config.send(sender, "reload.usage");
            return true;
        }
        reloads.reload(sender);
        return true;
    }
}
