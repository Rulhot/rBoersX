package ru.rulhot.rBoersX.economy;

import lombok.experimental.UtilityClass;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.by1337.bvault.api.BEconomy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.model.Settings;
import ru.rulhot.rBoersX.util.logger.Logger;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

@UtilityClass
public class EconomyRegistry {

    private final int BVAULT_MAX_BANK_LENGTH = 16;

    public @Nullable EconomyProvider create(@NotNull Settings.Money money, @NotNull Server server) {
        String pluginName = switch (money.provider()) {
            case VAULT -> "Vault";
            case BVAULT -> "BVault";
            case COINSENGINE -> "CoinsEngine";
            case PLAYERPOINTS -> "PlayerPoints";
        };
        if (!server.getPluginManager().isPluginEnabled(pluginName)) {
            return null;
        }
        try {
            return switch (money.provider()) {
                case VAULT -> vault(server);
                case BVAULT -> bVault(server, money.currency());
                case COINSENGINE -> coinsEngine(server, money.currency());
                case PLAYERPOINTS -> new PlayerPointsEconomyProvider(PlayerPoints.getInstance().getAPI());
            };
        } catch (Throwable throwable) {
            Logger.error("Экономика " + pluginName + " не подключилась", throwable);
            return null;
        }
    }

    private @Nullable EconomyProvider vault(@NotNull Server server) {
        RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultEconomyProvider(registration.getProvider());
    }

    private @Nullable EconomyProvider bVault(@NotNull Server server, @NotNull String bank) {
        RegisteredServiceProvider<BEconomy> registration = server.getServicesManager().getRegistration(BEconomy.class);
        if (registration == null) {
            return null;
        }
        if (bank.length() > BVAULT_MAX_BANK_LENGTH) {
            Logger.error("fuel.yml: money.currency длиннее " + BVAULT_MAX_BANK_LENGTH + " символов");
            return null;
        }
        return new BVaultEconomyProvider(registration.getProvider(), bank);
    }

    private @Nullable EconomyProvider coinsEngine(@NotNull Server server, @NotNull String currency) {
        RegisteredServiceProvider<ExcellentEconomyAPI> registration =
                server.getServicesManager().getRegistration(ExcellentEconomyAPI.class);
        if (registration == null) {
            return null;
        }
        ExcellentEconomyAPI api = registration.getProvider();
        if (!api.hasCurrency(currency)) {
            Logger.error("CoinsEngine: нет валюты " + currency);
            return null;
        }
        return new CoinsEngineEconomyProvider(api, currency);
    }
}
