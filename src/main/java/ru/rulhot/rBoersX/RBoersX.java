package ru.rulhot.rBoersX;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.command.BoerCommand;
import ru.rulhot.rBoersX.command.sub.AdminSubCommand;
import ru.rulhot.rBoersX.command.sub.GiveSubCommand;
import ru.rulhot.rBoersX.command.sub.HelpSubCommand;
import ru.rulhot.rBoersX.command.sub.ReloadSubCommand;
import ru.rulhot.rBoersX.database.DatabaseManager;
import ru.rulhot.rBoersX.database.repository.BoerRepository;
import ru.rulhot.rBoersX.hook.CakeRegionsHook;
import ru.rulhot.rBoersX.hook.DecentHologramHook;
import ru.rulhot.rBoersX.hook.FancyHologramHook;
import ru.rulhot.rBoersX.hook.GriefPreventionHook;
import ru.rulhot.rBoersX.hook.HologramHook;
import ru.rulhot.rBoersX.hook.ProtectionHook;
import ru.rulhot.rBoersX.hook.WorldGuardHook;
import ru.rulhot.rBoersX.listener.BoerListener;
import ru.rulhot.rBoersX.listener.BoerProtectionListener;
import ru.rulhot.rBoersX.listener.MenuListener;
import ru.rulhot.rBoersX.listener.WorldListener;
import ru.rulhot.rBoersX.manager.BoerManager;
import ru.rulhot.rBoersX.manager.BorderManager;
import ru.rulhot.rBoersX.manager.ConfigManager;
import ru.rulhot.rBoersX.manager.HologramManager;
import ru.rulhot.rBoersX.menu.MenuManager;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.service.BoerItemService;
import ru.rulhot.rBoersX.service.FuelService;
import ru.rulhot.rBoersX.service.MiningService;
import ru.rulhot.rBoersX.service.ReloadService;
import ru.rulhot.rBoersX.util.Metrics;
import ru.rulhot.rBoersX.util.SchedulerUtil;
import ru.rulhot.rBoersX.util.UpdateChecker;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RBoersX extends JavaPlugin {

    private static final int BSTATS_ID = 34680;

    private static final List<Permission> PERMISSIONS = List.of(
            new Permission(BoerManager.PERMISSION_USE, PermissionDefault.TRUE),
            new Permission(BoerManager.PERMISSION_LIMIT_BYPASS, PermissionDefault.OP),
            new Permission(BoerCommand.ADMIN_PERMISSION, PermissionDefault.OP));

    private @Nullable SchedulerUtil scheduler;
    private @Nullable Metrics metrics;
    private @Nullable MenuManager menuManager;
    private @Nullable BoerManager boerManager;
    private @Nullable BorderManager borderManager;
    private @Nullable HologramManager hologramManager;

    @Override
    public void onEnable() {
        Logger.setup(this);
        SchedulerUtil scheduler = new SchedulerUtil(this);
        this.scheduler = scheduler;

        ConfigManager configManager = new ConfigManager(this);
        configManager.apply(configManager.readAll());
        if (configManager.settings().metrics()) {
            metrics = new Metrics(this, BSTATS_ID);
        }
        if (configManager.settings().updateCheck()) {
            new UpdateChecker(getPluginMeta().getVersion()).check();
        }

        FuelService fuelService = new FuelService(getServer(), scheduler, configManager);
        fuelService.reload();

        BoerRepository repository = new BoerRepository(new DatabaseManager(getDataFolder()));
        BoerManager boerManager = new BoerManager(this, scheduler, configManager, repository, hookProtection(),
                fuelService);
        this.boerManager = boerManager;
        BorderManager borderManager = new BorderManager(scheduler, configManager, boerManager);
        this.borderManager = borderManager;
        HologramManager hologramManager = new HologramManager(scheduler, configManager, boerManager, borderManager,
                fuelService, hookHolograms());
        this.hologramManager = hologramManager;
        repository.start(configManager.database(),
                boers -> scheduler.runGlobal(() -> boerManager.load(boers)));

        BoerItemService itemService = new BoerItemService(this, configManager);
        MenuManager menuManager = new MenuManager(scheduler, configManager, boerManager, fuelService,
                borderManager);
        this.menuManager = menuManager;

        PluginManager pluginManager = getServer().getPluginManager();
        for (Permission permission : PERMISSIONS) {
            if (pluginManager.getPermission(permission.getName()) == null) {
                pluginManager.addPermission(permission);
            }
        }
        pluginManager.registerEvents(new BoerListener(configManager, boerManager, menuManager,
                borderManager, hologramManager, itemService), this);
        pluginManager.registerEvents(new BoerProtectionListener(boerManager, itemService), this);
        pluginManager.registerEvents(new WorldListener(boerManager), this);
        pluginManager.registerEvents(new MenuListener(menuManager, borderManager), this);

        Settings.Command label = configManager.settings().command();
        BoerCommand command = new BoerCommand(configManager, List.of(
                new HelpSubCommand(configManager),
                new GiveSubCommand(configManager, itemService, scheduler),
                new AdminSubCommand(configManager, boerManager, menuManager, borderManager, hologramManager,
                        itemService, scheduler),
                new ReloadSubCommand(configManager,
                        new ReloadService(scheduler, configManager, boerManager, fuelService, menuManager,
                                borderManager, hologramManager))));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(label.name(), label.aliases(), command));

        boerManager.start(new MiningService(configManager, boerManager, fuelService)::work,
                menuManager::attachFuel);
        menuManager.start();
        borderManager.start();
        hologramManager.start();
    }

    private @Nullable HologramHook hookHolograms() {
        PluginManager pluginManager = getServer().getPluginManager();
        try {
            if (pluginManager.isPluginEnabled("FancyHolograms")) {
                return new FancyHologramHook();
            }
            return pluginManager.isPluginEnabled("DecentHolograms")
                    ? new DecentHologramHook()
                    : null;
        } catch (Throwable throwable) {
            Logger.error("Не удалось подключить голограммы, работаю без них", throwable);
            return null;
        }
    }

    private @NotNull ProtectionHook hookProtection() {
        List<ProtectionHook> hooks = new ArrayList<>();
        PluginManager plugins = getServer().getPluginManager();
        if (plugins.isPluginEnabled("WorldGuard")) {
            try {
                hooks.add(new WorldGuardHook());
            } catch (Throwable throwable) {
                Logger.error("Не удалось подключить WorldGuard, его регионы не проверяются", throwable);
            }
        }
        if (plugins.isPluginEnabled("GriefPrevention")) {
            try {
                hooks.add(new GriefPreventionHook());
            } catch (Throwable throwable) {
                Logger.error("Не удалось подключить GriefPrevention, его приваты не проверяются", throwable);
            }
        }
        if (plugins.isPluginEnabled("CakeRegionsPlus")) {
            try {
                hooks.add(new CakeRegionsHook());
            } catch (Throwable throwable) {
                Logger.error("Не удалось подключить CakeRegionsPlus, его регионы не проверяются", throwable);
            }
        }
        return switch (hooks.size()) {
            case 0 -> ProtectionHook.NONE;
            case 1 -> hooks.getFirst();
            default -> (owner, world, x, y, z) -> hooks.stream().allMatch(hook -> hook.canBreak(owner, world, x, y, z));
        };
    }

    @Override
    public void onDisable() {
        if (menuManager != null) {
            menuManager.shutdown();
        }
        if (borderManager != null) {
            borderManager.stop();
        }
        if (hologramManager != null) {
            hologramManager.stop();
        }
        if (boerManager != null) {
            boerManager.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        PERMISSIONS.forEach(permission -> getServer().getPluginManager().removePermission(permission));
    }
}
