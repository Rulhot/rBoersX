package ru.rulhot.rBoersX.database;

import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.model.DatabaseSettings;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

@RequiredArgsConstructor
public final class DatabaseManager {

    private static final String SQLITE_FOLDER = "data";
    private static final String SQLITE_FILE = "data.db";
    private static final String SQLITE_BUSY_TIMEOUT_MS = "5000";
    private static final String HIKARI_LOGGER = "com.zaxxer.hikari";
    private static final String ORMLITE_LOGGER = "com.j256.ormlite";
    private static final int POOL_SIZE = 4;
    private static final int POOL_MIN_IDLE = 2;
    private static final long CONNECTION_TIMEOUT_MS = 30_000L;
    private static final long IDLE_TIMEOUT_MS = 600_000L;
    private static final long MAX_LIFETIME_MS = 1_800_000L;

    private final @NotNull File dataFolder;
    private @Nullable HikariDataSource dataSource;
    private @Nullable ConnectionSource connectionSource;

    public @NotNull ConnectionSource connect(@NotNull DatabaseSettings settings) throws SQLException {
        quietLibraries();
        HikariConfig config = new HikariConfig();
        config.setPoolName("rBoersX");
        switch (settings.type()) {
            case SQLITE -> {
                config.setDriverClassName("org.sqlite.JDBC");
                File folder = new File(dataFolder, SQLITE_FOLDER);
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    throw new SQLException("Не удалось создать папку " + folder.getAbsolutePath());
                }
                config.setJdbcUrl("jdbc:sqlite:" + new File(folder, SQLITE_FILE).getAbsolutePath());
                config.addDataSourceProperty("busy_timeout", SQLITE_BUSY_TIMEOUT_MS);
            }
            case MYSQL -> {
                DatabaseSettings.MySql mysql = settings.mysql();
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                        + "?useSSL=" + mysql.useSsl() + "&rewriteBatchedStatements=true&characterEncoding=utf8");
                config.setUsername(mysql.username());
                config.setPassword(mysql.password());
            }
        }
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(POOL_MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        try {
            dataSource = new HikariDataSource(config);
        } catch (HikariPool.PoolInitializationException exception) {
            throw new SQLException("Не удалось открыть пул соединений", exception);
        }
        connectionSource = new DataSourceConnectionSource(dataSource, config.getJdbcUrl());
        return connectionSource;
    }

    public void close() {
        if (connectionSource != null) {
            connectionSource.closeQuietly();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static void quietLibraries() {
        Configurator.setLevel(Map.of(HIKARI_LOGGER, Level.WARN, ORMLITE_LOGGER, Level.WARN));
    }
}
