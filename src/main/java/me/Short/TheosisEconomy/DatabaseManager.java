package me.Short.TheosisEconomy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.logging.Level;

public class DatabaseManager
{

    private final TheosisEconomy instance;

    private final String databasePath;

    private Connection connection;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "TheosisEconomy-Database"));

    private final ReentrantReadWriteLock executorLifecycleLock = new ReentrantReadWriteLock();

    private boolean executorShuttingDown;

    public DatabaseManager(TheosisEconomy instance, String databasePath)
    {
        this.instance = instance;
        this.databasePath = databasePath;
    }

    /*
     * Connection
     */

    public void connect() throws ClassNotFoundException, SQLException
    {
        // If a connection has already been established and is not closed, return
        if (connection != null && !connection.isClosed())
        {
            return;
        }

        // Required according to Paper documentation: https://docs.papermc.io/paper/dev/using-databases/#usage
        Class.forName("org.sqlite.JDBC");

        // Establish connection
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    public void disconnect() throws SQLException
    {
        // If the connection is null, return
        if (connection == null)
        {
            return;
        }

        // Close the connection
        try
        {
            connection.close();
        }
        finally
        {
            connection = null;
        }
    }

    public Connection getConnection()
    {
        if (connection == null)
        {
            throw new IllegalStateException("Database connection has not been established.");
        }

        return connection;
    }

    /*
     * Executor
     */

    // Submit a task to the database executor and return its future
    public <T> CompletableFuture<T> submitTask(Supplier<T> task)
    {
        executorLifecycleLock.readLock().lock();

        try
        {
            requireExecutorNotShuttingDown();

            return CompletableFuture.supplyAsync(task, databaseExecutor);
        }
        finally
        {
            executorLifecycleLock.readLock().unlock();
        }
    }

    // Queue a fire-and-forget task to the database executor
    public void executeTask(Runnable task)
    {
        executorLifecycleLock.readLock().lock();

        try
        {
            requireExecutorNotShuttingDown();

            databaseExecutor.execute(task);
        }
        finally
        {
            executorLifecycleLock.readLock().unlock();
        }
    }

    public void shutdownExecutor()
    {
        executorLifecycleLock.writeLock().lock();

        try
        {
            executorShuttingDown = true;
            databaseExecutor.shutdown();
        }
        finally
        {
            executorLifecycleLock.writeLock().unlock();
        }

        try
        {
            if (!databaseExecutor.awaitTermination(30, TimeUnit.SECONDS))
            {
                instance.getLogger().log(Level.SEVERE, "Database tasks did not finish before shutdown.");

                databaseExecutor.shutdownNow();
            }
        }
        catch (InterruptedException ignored)
        {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void requireExecutorNotShuttingDown()
    {
        if (executorShuttingDown)
        {
            throw new IllegalStateException("Database executor is shutting down.");
        }
    }

    /*
     * Schema
     */

    public void createTables() throws SQLException
    {
        String metadataSql = """
            CREATE TABLE IF NOT EXISTS economy_metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """;

        String playerAccountsSql = """
            CREATE TABLE IF NOT EXISTS player_accounts (
                uuid TEXT PRIMARY KEY,
                balance INTEGER NOT NULL,
                last_balance_change_timestamp INTEGER NOT NULL,
                accepting_payments INTEGER NOT NULL DEFAULT 1
            );
            """;

        String balanceTopIndexSql = """
            CREATE INDEX IF NOT EXISTS idx_player_accounts_balance_top
            ON player_accounts(balance DESC, last_balance_change_timestamp ASC, uuid ASC);
            """;

        String mostRecentPlayerNamesSql = """
            CREATE TABLE IF NOT EXISTS most_recent_player_names (
                uuid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                last_seen_timestamp INTEGER NOT NULL
            );
            """;

        try (Statement statement = getConnection().createStatement())
        {
            statement.execute(metadataSql);
            statement.execute(playerAccountsSql);
            statement.execute(balanceTopIndexSql);
            statement.execute(mostRecentPlayerNamesSql);
        }
    }

    /*
     * Economy metadata
     */

    public String getEconomyMetadata(String key) throws SQLException
    {
        String sql = """
            SELECT value
            FROM economy_metadata
            WHERE key = ?;
            """;

        try (PreparedStatement statement = getConnection().prepareStatement(sql))
        {
            statement.setString(1, key);

            try (ResultSet resultSet = statement.executeQuery())
            {
                return resultSet.next() ? resultSet.getString("value") : null;
            }
        }
    }

    public void setEconomyMetadata(String key, String value) throws SQLException
    {
        String sql = """
            INSERT INTO economy_metadata (key, value)
            VALUES (?, ?)
            ON CONFLICT(key)
            DO UPDATE SET value = excluded.value;
            """;

        try (PreparedStatement statement = getConnection().prepareStatement(sql))
        {
            statement.setString(1, key);
            statement.setString(2, value);

            statement.executeUpdate();
        }
    }

    /*
     * Balance scale migration
     */

    public void prepareBalanceRescale(int newScale) throws SQLException
    {
        if (newScale < 0)
        {
            throw new IllegalArgumentException("Balance scale cannot be negative.");
        }

        String storedScaleString = getEconomyMetadata("balance_scale");

        if (storedScaleString == null)
        {
            setEconomyMetadata("balance_scale", Integer.toString(newScale));
            return;
        }

        int oldScale;

        try
        {
            oldScale = Integer.parseInt(storedScaleString);
        }
        catch (NumberFormatException e)
        {
            throw new SQLException("Invalid stored balance scale: " + storedScaleString, e);
        }

        if (oldScale == newScale)
        {
            return;
        }

        rescaleBalances(oldScale, newScale);
    }

    private void rescaleBalances(int oldScale, int newScale) throws SQLException
    {
        boolean previousAutoCommit = connection.getAutoCommit();

        connection.setAutoCommit(false);

        try
        {
            if (newScale > oldScale)
            {
                increaseBalanceScale(newScale - oldScale);
            }
            else
            {
                decreaseBalanceScale(oldScale - newScale);
            }

            setEconomyMetadata("balance_scale", Integer.toString(newScale));

            connection.commit();
        }
        catch (Exception e)
        {
            connection.rollback();

            if (e instanceof SQLException sqlException)
            {
                throw sqlException;
            }

            throw new SQLException("Failed to rescale balances from " + oldScale + " to " + newScale + " decimal places.", e);
        }
        finally
        {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void increaseBalanceScale(int scaleDifference) throws SQLException
    {
        long multiplier = powerOfTen(scaleDifference);

        Long maximumStoredBalance = getMaximumStoredBalance();

        if (maximumStoredBalance != null)
        {
            try
            {
                Math.multiplyExact(maximumStoredBalance, multiplier);
            }
            catch (ArithmeticException e)
            {
                throw new SQLException("Balances cannot be rescaled because doing so would exceed SQLite's INTEGER range.", e);
            }
        }

        String sql = """
            UPDATE player_accounts
            SET balance = balance * ?;
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setLong(1, multiplier);

            statement.executeUpdate();
        }
    }

    private void decreaseBalanceScale(int scaleDifference) throws SQLException
    {
        long divisor = powerOfTen(scaleDifference);

        String sql = """
            UPDATE player_accounts
            SET balance = balance / ?;
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setLong(1, divisor);

            statement.executeUpdate();
        }
    }

    private Long getMaximumStoredBalance() throws SQLException
    {
        String sql = """
            SELECT MAX(balance)
            FROM player_accounts;
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery())
        {
            if (!resultSet.next())
            {
                return null;
            }

            Object value = resultSet.getObject(1);

            return value != null ? resultSet.getLong(1) : null;
        }
    }

    private long powerOfTen(int exponent) throws SQLException
    {
        long result = 1L;

        try
        {
            for (int i = 0; i < exponent; i++)
            {
                result = Math.multiplyExact(result, 10L);
            }
        }
        catch (ArithmeticException e)
        {
            throw new SQLException("The requested decimal place difference is too large.", e);
        }

        return result;
    }

}