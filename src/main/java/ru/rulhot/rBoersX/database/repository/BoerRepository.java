package ru.rulhot.rBoersX.database.repository;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rBoersX.database.DatabaseManager;
import ru.rulhot.rBoersX.database.model.BoerRecord;
import ru.rulhot.rBoersX.model.Boer;
import ru.rulhot.rBoersX.model.DatabaseSettings;
import ru.rulhot.rBoersX.util.logger.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class BoerRepository {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;

    private final @NotNull DatabaseManager databaseManager;
    private final @NotNull ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rBoersX-database");
        thread.setDaemon(true);
        return thread;
    });
    private volatile @Nullable Dao<BoerRecord, Long> dao;

    public BoerRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void start(@NotNull DatabaseSettings settings, @NotNull Consumer<List<Boer>> onLoaded) {
        executor.execute(() -> {
            try {
                ConnectionSource source = databaseManager.connect(settings);
                TableUtils.createTableIfNotExists(source, BoerRecord.class);
                Dao<BoerRecord, Long> created = DaoManager.createDao(source, BoerRecord.class);
                List<Boer> boers = new ArrayList<>();
                for (BoerRecord record : created.queryForAll()) {
                    Boer boer = Boer.fromRecord(record);
                    if (boer != null) {
                        boers.add(boer);
                    }
                }
                dao = created;
                onLoaded.accept(boers);
            } catch (SQLException exception) {
                Logger.error("База данных недоступна", exception);
            }
        });
    }

    public void insert(@NotNull Boer boer, @NotNull BoerRecord record) {
        executor.execute(() -> {
            Dao<BoerRecord, Long> current = dao;
            if (current == null) {
                return;
            }
            try {
                current.create(record);
                boer.assignId(record.getId());
            } catch (SQLException exception) {
                Logger.error("Бур " + boer.pos() + " не сохранён", exception);
            }
        });
    }

    public void delete(@NotNull Boer boer) {
        executor.execute(() -> {
            Dao<BoerRecord, Long> current = dao;
            long id = boer.id();
            if (current == null || id == 0L) {
                return;
            }
            try {
                current.deleteById(id);
            } catch (SQLException exception) {
                Logger.error("Бур id=" + id + " не удалён из базы", exception);
            }
        });
    }

    public void save(@NotNull List<BoerRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        executor.execute(() -> write(records));
    }

    public void shutdown(@NotNull List<BoerRecord> records) {
        Future<?> flush = executor.submit(() -> write(records));
        try {
            flush.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            Logger.error("Буры не сохранены при выключении", exception);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        databaseManager.close();
    }

    private void write(@NotNull List<BoerRecord> records) {
        Dao<BoerRecord, Long> current = dao;
        if (current == null || records.isEmpty()) {
            return;
        }
        try {
            TransactionManager.callInTransaction(current.getConnectionSource(), () -> {
                for (BoerRecord record : records) {
                    current.update(record);
                }
                return null;
            });
        } catch (SQLException exception) {
            Logger.error("Буры не сохранены (" + records.size() + ")", exception);
        }
    }
}
