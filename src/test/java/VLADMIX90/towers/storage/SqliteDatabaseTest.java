package VLADMIX90.towers.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {
    @TempDir Path directory;

    @Test void initializesOnceAndUsesDurableConnectionSettingsOffCallerThread() {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db"));
        try {
            database.initialize().join();
            database.initialize().join();
            Thread caller = Thread.currentThread();
            database.read(connection -> {
                assertNotSame(caller, Thread.currentThread());
                for (String[] setting : new String[][]{{"journal_mode", "wal"}, {"foreign_keys", "1"},
                        {"busy_timeout", "5000"}, {"synchronous", "2"}}) {
                    try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA " + setting[0])) {
                        assertTrue(result.next());
                        assertEquals(setting[1], result.getString(1));
                    }
                }
                return null;
            }).join();
        } finally { database.closeAsync().join(); }
    }

    @Test void rollsBackAllWritesWhenTransactionFails() {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db"));
        database.initialize().join();
        try {
            assertThrows(CompletionException.class, () -> database.transaction(connection -> {
                try (var statement = connection.prepareStatement("INSERT INTO migration_history(file_name) VALUES (?)")) {
                    statement.setString(1, "players.yml"); statement.executeUpdate();
                }
                throw new IllegalStateException("abort");
            }).join());
            assertEquals(0, database.read(connection -> {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM migration_history")) {
                    return result.getInt(1);
                }
            }).join());
        } finally { database.closeAsync().join(); }
    }

    @Test void closingDrainsAcceptedWorkAndRejectsNewWork() throws Exception {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db"));
        database.initialize().join();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var accepted = database.read(connection -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return 42;
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var closed = database.closeAsync();
        try {
            assertFalse(closed.isDone());
            assertThrows(CompletionException.class, () -> database.read(connection -> 0).join());
        } finally { release.countDown(); }
        assertEquals(42, accepted.join());
        closed.join();
        assertSame(closed, database.closeAsync());
    }
}
