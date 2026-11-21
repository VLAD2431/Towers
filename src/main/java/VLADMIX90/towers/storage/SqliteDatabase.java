package VLADMIX90.towers.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The connection is confined to one FIFO worker, including startup and shutdown. */
public final class SqliteDatabase {
    @FunctionalInterface
    public interface Operation<T> { T execute(Connection connection) throws Exception; }

    private final Path file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "towers-database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;
    private CompletableFuture<Void> initialization;
    private CompletableFuture<Void> closing;

    public SqliteDatabase(Path file) { this.file = file.toAbsolutePath(); }

    public synchronized CompletableFuture<Void> initialize() {
        if (closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Database is closing"));
        if (initialization != null) return initialization;
        initialization = enqueue(() -> {
            Files.createDirectories(file.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + file);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA foreign_keys=ON");
                    statement.execute("PRAGMA busy_timeout=5000");
                    statement.execute("PRAGMA synchronous=FULL");
                }
                inTransaction(SchemaMigrator::migrate);
            } catch (Exception failure) {
                try { connection.close(); } catch (Exception closeFailure) { failure.addSuppressed(closeFailure); }
                connection = null;
                throw failure;
            }
            return null;
        });
        return initialization;
    }

    public synchronized <T> CompletableFuture<T> read(Operation<T> operation) {
        if (closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Database is closing"));
        return enqueue(() -> operation.execute(requireConnection()));
    }

    public synchronized <T> CompletableFuture<T> transaction(Operation<T> operation) {
        if (closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Database is closing"));
        return enqueue(() -> inTransaction(operation));
    }

    public CompletableFuture<Void> flush() { return read(ignored -> null); }

    public synchronized CompletableFuture<Void> closeAsync() {
        if (closing != null) return closing;
        closing = enqueue(() -> {
            if (connection != null) {
                try { connection.close(); } finally { connection = null; }
            }
            return null;
        });
        executor.shutdown();
        return closing;
    }

    private Connection requireConnection() {
        if (connection == null) throw new IllegalStateException("Database is not initialized");
        return connection;
    }

    private <T> T inTransaction(Operation<T> operation) throws Exception {
        Connection current = requireConnection();
        current.setAutoCommit(false);
        try {
            T value = operation.execute(current);
            current.commit();
            return value;
        } catch (Exception | Error failure) {
            try { current.rollback(); } catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        } finally { current.setAutoCommit(true); }
    }

    private <T> CompletableFuture<T> enqueue(java.util.concurrent.Callable<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        executor.execute(() -> {
            try { result.complete(operation.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
}
