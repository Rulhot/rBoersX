package ru.rulhot.rBoersX.model;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public record DatabaseSettings(
        @NotNull Type type,
        @NotNull MySql mysql
) {

    public static @NotNull DatabaseSettings from(@NotNull ConfigurationSection root) {
        ConfigurationSection mysql = Settings.section(root, "mysql", "database.yml");
        return new DatabaseSettings(
                Settings.enumOr(Type.class, root.getString("storage"), Type.SQLITE, "database.yml storage"),
                new MySql(
                        mysql.getString("host", "localhost"),
                        mysql.getInt("port", 3306),
                        mysql.getString("database", "rboersx"),
                        mysql.getString("user", "root"),
                        mysql.getString("password", ""),
                        mysql.getBoolean("ssl", false)));
    }

    public enum Type {
        SQLITE,
        MYSQL
    }

    public record MySql(@NotNull String host, int port, @NotNull String database, @NotNull String username,
                        @NotNull String password, boolean useSsl) {

        @Override
        public @NotNull String toString() {
            return "MySql[host=" + host + ", port=" + port + ", database=" + database + "]";
        }
    }
}
