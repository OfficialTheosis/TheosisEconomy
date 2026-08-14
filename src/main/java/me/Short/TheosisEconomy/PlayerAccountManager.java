package me.Short.TheosisEconomy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.logging.Level;

public class PlayerAccountManager
{
    private static final int BALANCE_TOP_BATCH_SIZE = 100;

    private final TheosisEconomy instance;

    private final DatabaseManager databaseManager;

    private final ConcurrentMap<UUID, PlayerAccount> loadedAccounts = new ConcurrentHashMap<>();

    private volatile List<BalanceTopEntry> cachedBalanceTopEntries = List.of();

    private final ExecutorService balanceTopFilterExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "TheosisEconomy-BalanceTop-Filter"));

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    private boolean shuttingDown;

    private final AtomicBoolean balanceTopCacheRefreshInProgress = new AtomicBoolean();

    public PlayerAccountManager(TheosisEconomy instance, DatabaseManager databaseManager)
    {
        this.instance = instance;
        this.databaseManager = databaseManager;
    }

    /*
     * Lifecycle/executor helpers
     */

    public void shutdownExecutor()
    {
        lifecycleLock.writeLock().lock();

        try
        {
            shuttingDown = true;
            balanceTopFilterExecutor.shutdown();
        }
        finally
        {
            lifecycleLock.writeLock().unlock();
        }

        try
        {
            if (!balanceTopFilterExecutor.awaitTermination(30, TimeUnit.SECONDS))
            {
                instance.getLogger().log(Level.SEVERE, "BalanceTop filtering tasks did not finish before shutdown.");

                balanceTopFilterExecutor.shutdownNow();
            }
        }
        catch (InterruptedException ignored)
        {
            balanceTopFilterExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Submit a task to the database executor and get its future
    private <T> CompletableFuture<T> submitDatabaseTask(Supplier<T> task)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            return databaseManager.submitTask(task);
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    // Queue a fire-and-forget task to the database executor
    private void executeDatabaseTask(Runnable task)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            databaseManager.executeTask(task);
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    private <T> CompletableFuture<T> submitBalanceTopFilterTask(Supplier<T> task)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            return CompletableFuture.supplyAsync(task, balanceTopFilterExecutor);
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    private void requireNotShuttingDown()
    {
        if (shuttingDown)
        {
            throw new IllegalStateException("Player account manager is shutting down.");
        }
    }

    /*
     * Loading and unloading
     */

    public CompletableFuture<PlayerAccount> loadOrCreateAccount(UUID uuid, String name)
    {
        PlayerAccount loadedAccount = loadedAccounts.get(uuid);

        if (loadedAccount != null)
        {
            return CompletableFuture.completedFuture(loadedAccount);
        }

        return submitDatabaseTask(() ->
        {
            try
            {
                PlayerAccount account = loadAccountFromDatabase(uuid);

                if (account == null)
                {
                    account = createAccountInDatabase(uuid);

                    ConfigSnapshot config = instance.getConfigSnapshot();

                    if (config.getBoolean("settings.logging.account-create.log"))
                    {
                        instance.getActivityLogger().log(Level.INFO, config.getString("settings.logging.account-create.message")
                                .replace("<player>", name)
                                .replace("<uuid>", uuid.toString()));
                    }
                }

                PlayerAccount existingAccount = loadedAccounts.putIfAbsent(uuid, account);

                return existingAccount != null ? existingAccount : account;
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    private PlayerAccount loadAccountFromDatabase(UUID uuid) throws SQLException
    {
        String sql = """
                SELECT balance, last_balance_change_timestamp, accepting_payments
                FROM player_accounts
                WHERE uuid = ?;
                """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery())
            {
                if (!resultSet.next())
                {
                    return null;
                }

                BigDecimal balance = balanceFromDatabaseValue(resultSet.getLong("balance"));

                long lastBalanceChangeTimestamp = resultSet.getLong("last_balance_change_timestamp");

                boolean acceptingPayments = resultSet.getBoolean("accepting_payments");

                return new PlayerAccount(uuid, balance, lastBalanceChangeTimestamp, acceptingPayments);
            }
        }
    }

    private PlayerAccount createAccountInDatabase(UUID uuid) throws SQLException
    {
        ConfigSnapshot config = instance.getConfigSnapshot();

        BigDecimal defaultBalance = new BigDecimal(config.getString("settings.currency.default-balance")).stripTrailingZeros();

        if (defaultBalance.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalStateException("The default balance cannot be negative.");
        }

        if (defaultBalance.scale() > instance.getDecimalPlaces())
        {
            throw new IllegalStateException("The default balance cannot use more decimal places than what is configured.");
        }

        if (defaultBalance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) > 0)
        {
            throw new IllegalStateException("The default balance cannot exceed the maximum balance.");
        }

        long balanceChangeTimestamp = System.currentTimeMillis();

        String sql = """
            INSERT INTO player_accounts (
                uuid,
                balance,
                last_balance_change_timestamp,
                accepting_payments
            )
            VALUES (?, ?, ?, ?);
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setString(1, uuid.toString());
            statement.setLong(2, balanceToDatabaseValue(defaultBalance));
            statement.setLong(3, balanceChangeTimestamp);
            statement.setBoolean(4, true);

            statement.executeUpdate();
        }

        return new PlayerAccount(uuid, defaultBalance, balanceChangeTimestamp, true);
    }

    public void unloadAccount(UUID uuid)
    {
        PlayerAccount account = loadedAccounts.get(uuid);

        if (account == null)
        {
            return;
        }

        synchronized (account)
        {
            loadedAccounts.remove(uuid, account);
        }
    }

    public boolean isAccountLoaded(UUID uuid)
    {
        return loadedAccounts.containsKey(uuid);
    }

    /*
     * Loaded account reads and mutations
     */

    public BigDecimal getLoadedAccountBalance(UUID uuid)
    {
        PlayerAccount account = loadedAccounts.get(uuid);

        if (account == null)
        {
            return null;
        }

        synchronized (account)
        {
            if (loadedAccounts.get(uuid) != account)
            {
                return null;
            }

            return account.getBalance();
        }
    }

    public BalanceChange addToLoadedAccountBalance(UUID uuid, BigDecimal amount)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            PlayerAccount account = loadedAccounts.get(uuid);

            if (account == null)
            {
                return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, amount, null);
            }

            synchronized (account)
            {
                if (loadedAccounts.get(uuid) != account)
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, amount, null);
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                int decimalPlaces = instance.getDecimalPlaces();

                amount = Util.round(amount, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

                BigDecimal currentBalance = account.getBalance();

                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                {
                    return new BalanceChange(BalanceChangeResult.ZERO_OR_LESS_AMOUNT, amount, currentBalance);
                }

                if (amount.scale() > decimalPlaces)
                {
                    return new BalanceChange(BalanceChangeResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, amount, currentBalance);
                }

                BigDecimal newBalance = currentBalance.add(amount);

                if (newBalance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) > 0)
                {
                    return new BalanceChange(BalanceChangeResult.ABOVE_MAXIMUM_BALANCE, amount, currentBalance);
                }

                long balanceChangeTimestamp = System.currentTimeMillis();

                account.setBalance(newBalance);
                account.setLastBalanceChangeTimestamp(balanceChangeTimestamp);

                submitDatabaseBalanceUpdate(account, newBalance, balanceChangeTimestamp);

                return new BalanceChange(BalanceChangeResult.SUCCESS, amount, newBalance);
            }
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    public BalanceChange subtractFromLoadedAccountBalance(UUID uuid, BigDecimal amount)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            PlayerAccount account = loadedAccounts.get(uuid);

            if (account == null)
            {
                return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, amount, null);
            }

            synchronized (account)
            {
                if (loadedAccounts.get(uuid) != account)
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, amount, null);
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                int decimalPlaces = instance.getDecimalPlaces();

                amount = Util.round(amount, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

                BigDecimal currentBalance = account.getBalance();

                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                {
                    return new BalanceChange(BalanceChangeResult.ZERO_OR_LESS_AMOUNT, amount, currentBalance);
                }

                if (amount.scale() > decimalPlaces)
                {
                    return new BalanceChange(BalanceChangeResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, amount, currentBalance);
                }

                BigDecimal newBalance = currentBalance.subtract(amount);

                if (newBalance.compareTo(BigDecimal.ZERO) < 0)
                {
                    return new BalanceChange(BalanceChangeResult.INSUFFICIENT_FUNDS, amount, currentBalance);
                }

                long balanceChangeTimestamp = System.currentTimeMillis();

                account.setBalance(newBalance);
                account.setLastBalanceChangeTimestamp(balanceChangeTimestamp);

                submitDatabaseBalanceUpdate(account, newBalance, balanceChangeTimestamp);

                return new BalanceChange(BalanceChangeResult.SUCCESS, amount, newBalance);
            }
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    public BalanceChange setLoadedAccountBalance(UUID uuid, BigDecimal balance)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            PlayerAccount account = loadedAccounts.get(uuid);

            if (account == null)
            {
                return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, balance, null);
            }

            synchronized (account)
            {
                if (loadedAccounts.get(uuid) != account)
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, balance, null);
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                int decimalPlaces = instance.getDecimalPlaces();

                balance = Util.round(balance, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

                if (balance.compareTo(BigDecimal.ZERO) < 0)
                {
                    return new BalanceChange(BalanceChangeResult.NEGATIVE_AMOUNT, balance, account.getBalance());
                }

                if (balance.scale() > decimalPlaces)
                {
                    return new BalanceChange(BalanceChangeResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, balance, account.getBalance());
                }

                if (balance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) > 0)
                {
                    return new BalanceChange(BalanceChangeResult.ABOVE_MAXIMUM_BALANCE, balance, account.getBalance());
                }

                // Don't actually make a change if the player's balance is equal to what it is being set to
                BigDecimal currentBalance = account.getBalance();
                if (currentBalance.compareTo(balance) == 0)
                {
                    return new BalanceChange(BalanceChangeResult.SUCCESS, balance, currentBalance);
                }

                long balanceChangeTimestamp = System.currentTimeMillis();

                account.setBalance(balance);
                account.setLastBalanceChangeTimestamp(balanceChangeTimestamp);

                submitDatabaseBalanceUpdate(account, balance, balanceChangeTimestamp);
            }

            return new BalanceChange(BalanceChangeResult.SUCCESS, balance, balance);
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    public Boolean getLoadedAccountAcceptingPayments(UUID uuid)
    {
        PlayerAccount account = loadedAccounts.get(uuid);

        if (account == null)
        {
            return null;
        }

        synchronized (account)
        {
            if (loadedAccounts.get(uuid) != account)
            {
                return null;
            }

            return account.getAcceptingPayments();
        }
    }

    public boolean setLoadedAccountAcceptingPayments(UUID uuid, boolean acceptingPayments)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            PlayerAccount account = loadedAccounts.get(uuid);

            if (account == null)
            {
                return false;
            }

            synchronized (account)
            {
                if (loadedAccounts.get(uuid) != account)
                {
                    return false;
                }

                account.setAcceptingPayments(acceptingPayments);

                submitDatabaseAcceptingPaymentsUpdate(account, acceptingPayments);
            }

            return true;
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    public Boolean toggleLoadedAccountAcceptingPayments(UUID uuid)
    {
        lifecycleLock.readLock().lock();

        try
        {
            requireNotShuttingDown();

            PlayerAccount account = loadedAccounts.get(uuid);

            if (account == null)
            {
                return null;
            }

            synchronized (account)
            {
                if (loadedAccounts.get(uuid) != account)
                {
                    return null;
                }

                boolean acceptingPayments = !account.getAcceptingPayments();

                account.setAcceptingPayments(acceptingPayments);

                submitDatabaseAcceptingPaymentsUpdate(account, acceptingPayments);

                return acceptingPayments;
            }
        }
        finally
        {
            lifecycleLock.readLock().unlock();
        }
    }

    /*
     * General account reads and mutations
     */

    public CompletableFuture<BigDecimal> getBalance(UUID uuid)
    {
        BigDecimal loadedAccountBalance = getLoadedAccountBalance(uuid);

        if (loadedAccountBalance != null)
        {
            return CompletableFuture.completedFuture(loadedAccountBalance);
        }

        return submitDatabaseTask(() ->
        {
            BigDecimal newlyLoadedAccountBalance = getLoadedAccountBalance(uuid); // The player's account may have become loaded while this task was waiting in the database executor

            if (newlyLoadedAccountBalance != null)
            {
                return newlyLoadedAccountBalance;
            }

            // Get the balance from the database
            try
            {
                return getBalanceFromDatabase(uuid);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<BalanceChange> addToBalance(UUID uuid, BigDecimal amount)
    {
        BalanceChange balanceChange = addToLoadedAccountBalance(uuid, amount);

        if (balanceChange.result() != BalanceChangeResult.ACCOUNT_NOT_FOUND)
        {
            return CompletableFuture.completedFuture(balanceChange);
        }

        return submitDatabaseTask(() ->
        {
            BalanceChange newlyLoadedAccountBalanceChange = addToLoadedAccountBalance(uuid, amount); // The player's account may have become loaded while this task was waiting in the database executor

            if (newlyLoadedAccountBalanceChange.result() != BalanceChangeResult.ACCOUNT_NOT_FOUND)
            {
                return newlyLoadedAccountBalanceChange;
            }

            // Update the balance in the database
            try
            {
                BigDecimal currentBalance = getBalanceFromDatabase(uuid);

                if (currentBalance == null)
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, amount, null);
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                int decimalPlaces = instance.getDecimalPlaces();

                BigDecimal newAmount = Util.round(amount, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

                if (newAmount.compareTo(BigDecimal.ZERO) <= 0)
                {
                    return new BalanceChange(BalanceChangeResult.ZERO_OR_LESS_AMOUNT, newAmount, currentBalance);
                }

                if (newAmount.scale() > decimalPlaces)
                {
                    return new BalanceChange(BalanceChangeResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, newAmount, currentBalance);
                }

                BigDecimal newBalance = currentBalance.add(newAmount);

                if (newBalance.compareTo(new BigDecimal(instance.getConfigSnapshot().getString("settings.currency.max-balance"))) > 0)
                {
                    return new BalanceChange(BalanceChangeResult.ABOVE_MAXIMUM_BALANCE, newAmount, currentBalance);
                }

                if (!setBalanceInDatabase(uuid, newBalance, System.currentTimeMillis()))
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, newAmount, currentBalance);
                }

                return new BalanceChange(BalanceChangeResult.SUCCESS, newAmount, newBalance);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<BalanceChange> subtractFromBalance(UUID uuid, BigDecimal amount)
    {
        BalanceChange loadedAccountBalanceChange = subtractFromLoadedAccountBalance(uuid, amount);

        if (loadedAccountBalanceChange.result() != BalanceChangeResult.ACCOUNT_NOT_FOUND)
        {
            return CompletableFuture.completedFuture(loadedAccountBalanceChange);
        }

        return submitDatabaseTask(() ->
        {
            BalanceChange newlyLoadedAccountBalanceChange = subtractFromLoadedAccountBalance(uuid, amount); // The player's account may have become loaded while this task was waiting in the database executor

            if (newlyLoadedAccountBalanceChange.result() != BalanceChangeResult.ACCOUNT_NOT_FOUND)
            {
                return newlyLoadedAccountBalanceChange;
            }

            // Update the balance in the database
            try
            {
                BigDecimal currentBalance = getBalanceFromDatabase(uuid);

                if (currentBalance == null)
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, amount, null);
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                int decimalPlaces = instance.getDecimalPlaces();

                BigDecimal newAmount = Util.round(amount, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

                if (newAmount.compareTo(BigDecimal.ZERO) <= 0)
                {
                    return new BalanceChange(BalanceChangeResult.ZERO_OR_LESS_AMOUNT, newAmount, currentBalance);
                }

                if (newAmount.scale() > decimalPlaces)
                {
                    return new BalanceChange(BalanceChangeResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, newAmount, currentBalance);
                }

                BigDecimal newBalance = currentBalance.subtract(newAmount);

                if (newBalance.compareTo(BigDecimal.ZERO) < 0)
                {
                    return new BalanceChange(BalanceChangeResult.INSUFFICIENT_FUNDS, newAmount, currentBalance);
                }

                if (!setBalanceInDatabase(uuid, newBalance, System.currentTimeMillis()))
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, newAmount, currentBalance);
                }

                return new BalanceChange(BalanceChangeResult.SUCCESS, newAmount, newBalance);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<BalanceChange> setBalance(UUID uuid, BigDecimal balance)
    {
        BalanceChange loadedAccountBalanceChange = setLoadedAccountBalance(uuid, balance);

        if (loadedAccountBalanceChange.result() != BalanceChangeResult.ACCOUNT_NOT_FOUND)
        {
            return CompletableFuture.completedFuture(loadedAccountBalanceChange);
        }

        return submitDatabaseTask(() ->
        {
            BalanceChange newlyLoadedAccountBalanceChange = setLoadedAccountBalance(uuid, balance); // The player's account may have become loaded while this task was waiting in the database executor

            if (newlyLoadedAccountBalanceChange.result() != BalanceChangeResult.ACCOUNT_NOT_FOUND)
            {
                return newlyLoadedAccountBalanceChange;
            }

            // Set the balance in the database
            try
            {
                ConfigSnapshot config = instance.getConfigSnapshot();

                int decimalPlaces = instance.getDecimalPlaces();

                BigDecimal newBalance = Util.round(balance, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

                if (newBalance.compareTo(BigDecimal.ZERO) < 0)
                {
                    return new BalanceChange(BalanceChangeResult.NEGATIVE_AMOUNT, newBalance, getBalanceFromDatabase(uuid));
                }

                if (newBalance.scale() > decimalPlaces)
                {
                    return new BalanceChange(BalanceChangeResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, newBalance, getBalanceFromDatabase(uuid));
                }

                if (newBalance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) > 0)
                {
                    return new BalanceChange(BalanceChangeResult.ABOVE_MAXIMUM_BALANCE, newBalance, getBalanceFromDatabase(uuid));
                }

                // Don't actually make a change if the player's balance is equal to what it is being set to
                BigDecimal currentBalance = getBalanceFromDatabase(uuid);
                if (currentBalance == null)
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, newBalance, null);
                }
                if (currentBalance.compareTo(newBalance) == 0)
                {
                    return new BalanceChange(BalanceChangeResult.SUCCESS, newBalance, currentBalance);
                }

                if (!setBalanceInDatabase(uuid, newBalance, System.currentTimeMillis()))
                {
                    return new BalanceChange(BalanceChangeResult.ACCOUNT_NOT_FOUND, newBalance, getBalanceFromDatabase(uuid));
                }

                return new BalanceChange(BalanceChangeResult.SUCCESS, newBalance, newBalance);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Boolean> getAcceptingPayments(UUID uuid)
    {
        Boolean loadedAccountAcceptingPayments = getLoadedAccountAcceptingPayments(uuid);

        if (loadedAccountAcceptingPayments != null)
        {
            return CompletableFuture.completedFuture(loadedAccountAcceptingPayments);
        }

        return submitDatabaseTask(() ->
        {
            Boolean newlyLoadedAccountAcceptingPayments = getLoadedAccountAcceptingPayments(uuid); // The player's account may have become loaded while this task was waiting in the database executor

            if (newlyLoadedAccountAcceptingPayments != null)
            {
                return newlyLoadedAccountAcceptingPayments;
            }

            // Get the "accepting_payments" value from the database
            try
            {
                return getAcceptingPaymentsFromDatabase(uuid);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Boolean> setAcceptingPayments(UUID uuid, boolean acceptingPayments)
    {
        if (setLoadedAccountAcceptingPayments(uuid, acceptingPayments))
        {
            return CompletableFuture.completedFuture(true);
        }

        return submitDatabaseTask(() ->
        {
            if (setLoadedAccountAcceptingPayments(uuid, acceptingPayments)) // The player's account may have become loaded while this task was waiting in the database executor
            {
                return true;
            }

            // Set the "accepting_payments" value in the database
            try
            {
                return setAcceptingPaymentsInDatabase(uuid, acceptingPayments);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Boolean> toggleAcceptingPayments(UUID uuid)
    {
        Boolean acceptingPayments = toggleLoadedAccountAcceptingPayments(uuid);

        if (acceptingPayments != null)
        {
            return CompletableFuture.completedFuture(acceptingPayments);
        }

        return submitDatabaseTask(() ->
        {
            Boolean newlyLoadedAcceptingPayments = toggleLoadedAccountAcceptingPayments(uuid);

            if (newlyLoadedAcceptingPayments != null)
            {
                return newlyLoadedAcceptingPayments;
            }

            try
            {
                Boolean currentAcceptingPayments = getAcceptingPaymentsFromDatabase(uuid);

                if (currentAcceptingPayments == null)
                {
                    return null;
                }

                boolean newAcceptingPayments = !currentAcceptingPayments;

                if (!setAcceptingPaymentsInDatabase(uuid, newAcceptingPayments))
                {
                    return null;
                }

                return newAcceptingPayments;
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        });
    }

    /*
     * Money transfers
     */

    public CompletableFuture<MoneyTransfer> transferMoney(UUID senderUuid, UUID targetUuid, BigDecimal amount)
    {
        if (senderUuid.equals(targetUuid))
        {
            return CompletableFuture.completedFuture(new MoneyTransfer(MoneyTransferResult.SAME_ACCOUNT, amount, null, null));
        }

        ConfigSnapshot config = instance.getConfigSnapshot();

        int decimalPlaces = instance.getDecimalPlaces();

        BigDecimal newAmount = Util.round(amount, decimalPlaces, RoundingMode.valueOf(config.getString("settings.currency.rounding-mode"))).stripTrailingZeros();

        if (newAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            return CompletableFuture.completedFuture(new MoneyTransfer(MoneyTransferResult.ZERO_OR_LESS_AMOUNT, newAmount, null, null));
        }

        if (newAmount.scale() > decimalPlaces)
        {
            return CompletableFuture.completedFuture(new MoneyTransfer(MoneyTransferResult.TOO_MANY_DECIMAL_PLACES_AMOUNT, newAmount, null, null));
        }

        return submitDatabaseTask(() ->
        {
            PlayerAccount senderAccount = loadedAccounts.get(senderUuid);
            PlayerAccount targetAccount = loadedAccounts.get(targetUuid);

            if (senderAccount != null && targetAccount != null)
            {
                return transferMoneyBetweenLoadedAccounts(senderAccount, targetAccount, newAmount);
            }

            if (senderAccount != null)
            {
                return transferMoneyFromLoadedToUnloadedAccount(senderAccount, targetUuid, newAmount);
            }

            if (targetAccount != null)
            {
                return transferMoneyFromUnloadedToLoadedAccount(senderUuid, targetAccount, newAmount);
            }

            return transferMoneyBetweenUnloadedAccounts(senderUuid, targetUuid, newAmount);
        });
    }

    private MoneyTransfer transferMoneyBetweenLoadedAccounts(PlayerAccount senderAccount, PlayerAccount targetAccount, BigDecimal amount)
    {
        PlayerAccount firstLock;
        PlayerAccount secondLock;

        if (senderAccount.getUuid().compareTo(targetAccount.getUuid()) < 0)
        {
            firstLock = senderAccount;
            secondLock = targetAccount;
        }
        else
        {
            firstLock = targetAccount;
            secondLock = senderAccount;
        }

        BigDecimal newSenderBalance;
        BigDecimal newTargetBalance;
        long balanceChangeTimestamp;

        synchronized (firstLock)
        {
            synchronized (secondLock)
            {
                if (!targetAccount.getAcceptingPayments())
                {
                    return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_ACCEPTING_PAYMENTS, amount, null, null);
                }

                BigDecimal senderBalance = senderAccount.getBalance();

                if (senderBalance.compareTo(amount) < 0)
                {
                    return new MoneyTransfer(MoneyTransferResult.INSUFFICIENT_FUNDS, amount, null, null);
                }

                newSenderBalance = senderBalance.subtract(amount);
                newTargetBalance = targetAccount.getBalance().add(amount);

                if (newTargetBalance.compareTo(new BigDecimal(instance.getConfigSnapshot().getString("settings.currency.max-balance"))) > 0)
                {
                    return new MoneyTransfer(MoneyTransferResult.ABOVE_MAXIMUM_BALANCE, amount, null, null);
                }

                balanceChangeTimestamp = System.currentTimeMillis();

                senderAccount.setBalance(newSenderBalance);
                senderAccount.setLastBalanceChangeTimestamp(balanceChangeTimestamp);

                targetAccount.setBalance(newTargetBalance);
                targetAccount.setLastBalanceChangeTimestamp(balanceChangeTimestamp);
            }
        }

        try
        {
            transferMoneyInDatabase(senderAccount.getUuid(), newSenderBalance, targetAccount.getUuid(), newTargetBalance, balanceChangeTimestamp);
        }
        catch (SQLException e)
        {
            throw new CompletionException(e);
        }

        return new MoneyTransfer(MoneyTransferResult.SUCCESS, amount, newSenderBalance, newTargetBalance);
    }

    private MoneyTransfer transferMoneyFromLoadedToUnloadedAccount(PlayerAccount senderAccount, UUID targetUuid, BigDecimal amount)
    {
        try
        {
            BigDecimal targetBalance = getBalanceFromDatabase(targetUuid);

            if (targetBalance == null)
            {
                return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_FOUND, amount, null, null);
            }

            Boolean acceptingPayments = getAcceptingPaymentsFromDatabase(targetUuid);

            if (acceptingPayments == null)
            {
                return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_FOUND, amount, null, null);
            }

            if (!acceptingPayments)
            {
                return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_ACCEPTING_PAYMENTS, amount, null, null);
            }

            BigDecimal newSenderBalance;
            BigDecimal newTargetBalance;
            long balanceChangeTimestamp;

            synchronized (senderAccount)
            {
                BigDecimal senderBalance = senderAccount.getBalance();

                if (senderBalance.compareTo(amount) < 0)
                {
                    return new MoneyTransfer(MoneyTransferResult.INSUFFICIENT_FUNDS, amount, null, null);
                }

                newSenderBalance = senderBalance.subtract(amount);
                newTargetBalance = targetBalance.add(amount);

                if (newTargetBalance.compareTo(new BigDecimal(instance.getConfigSnapshot().getString("settings.currency.max-balance"))) > 0)
                {
                    return new MoneyTransfer(MoneyTransferResult.ABOVE_MAXIMUM_BALANCE, amount, null, null);
                }

                balanceChangeTimestamp = System.currentTimeMillis();

                senderAccount.setBalance(newSenderBalance);
                senderAccount.setLastBalanceChangeTimestamp(balanceChangeTimestamp);
            }

            transferMoneyInDatabase(senderAccount.getUuid(), newSenderBalance, targetUuid, newTargetBalance, balanceChangeTimestamp);

            return new MoneyTransfer(MoneyTransferResult.SUCCESS, amount, newSenderBalance, newTargetBalance);
        }
        catch (SQLException e)
        {
            throw new CompletionException(e);
        }
    }

    private MoneyTransfer transferMoneyFromUnloadedToLoadedAccount(UUID senderUuid, PlayerAccount targetAccount, BigDecimal amount)
    {
        try
        {
            BigDecimal senderBalance = getBalanceFromDatabase(senderUuid);

            if (senderBalance == null)
            {
                return new MoneyTransfer(MoneyTransferResult.SENDER_NOT_FOUND, amount, null, null);
            }

            BigDecimal newSenderBalance;
            BigDecimal newTargetBalance;
            long balanceChangeTimestamp;

            synchronized (targetAccount)
            {
                if (!targetAccount.getAcceptingPayments())
                {
                    return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_ACCEPTING_PAYMENTS, amount, null, null);
                }

                if (senderBalance.compareTo(amount) < 0)
                {
                    return new MoneyTransfer(MoneyTransferResult.INSUFFICIENT_FUNDS, amount, null, null);
                }

                newSenderBalance = senderBalance.subtract(amount);
                newTargetBalance = targetAccount.getBalance().add(amount);

                if (newTargetBalance.compareTo(new BigDecimal(instance.getConfigSnapshot().getString("settings.currency.max-balance"))) > 0)
                {
                    return new MoneyTransfer(MoneyTransferResult.ABOVE_MAXIMUM_BALANCE, amount, null, null);
                }

                balanceChangeTimestamp = System.currentTimeMillis();

                targetAccount.setBalance(newTargetBalance);
                targetAccount.setLastBalanceChangeTimestamp(balanceChangeTimestamp);
            }

            transferMoneyInDatabase(senderUuid, newSenderBalance, targetAccount.getUuid(), newTargetBalance, balanceChangeTimestamp);

            return new MoneyTransfer(MoneyTransferResult.SUCCESS, amount, newSenderBalance, newTargetBalance);
        }
        catch (SQLException e)
        {
            throw new CompletionException(e);
        }
    }

    private MoneyTransfer transferMoneyBetweenUnloadedAccounts(UUID senderUuid, UUID targetUuid, BigDecimal amount)
    {
        try
        {
            BigDecimal senderBalance = getBalanceFromDatabase(senderUuid);

            if (senderBalance == null)
            {
                return new MoneyTransfer(MoneyTransferResult.SENDER_NOT_FOUND, amount, null, null);
            }

            BigDecimal targetBalance = getBalanceFromDatabase(targetUuid);

            if (targetBalance == null)
            {
                return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_FOUND, amount, null, null);
            }

            Boolean acceptingPayments = getAcceptingPaymentsFromDatabase(targetUuid);

            if (acceptingPayments == null)
            {
                return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_FOUND, amount, null, null);
            }

            if (!acceptingPayments)
            {
                return new MoneyTransfer(MoneyTransferResult.TARGET_NOT_ACCEPTING_PAYMENTS, amount, null, null);
            }

            if (senderBalance.compareTo(amount) < 0)
            {
                return new MoneyTransfer(MoneyTransferResult.INSUFFICIENT_FUNDS, amount, null, null);
            }

            BigDecimal newSenderBalance = senderBalance.subtract(amount);
            BigDecimal newTargetBalance = targetBalance.add(amount);

            if (newTargetBalance.compareTo(new BigDecimal(instance.getConfigSnapshot().getString("settings.currency.max-balance"))) > 0)
            {
                return new MoneyTransfer(MoneyTransferResult.ABOVE_MAXIMUM_BALANCE, amount, null, null);
            }

            transferMoneyInDatabase(senderUuid, newSenderBalance, targetUuid, newTargetBalance, System.currentTimeMillis());

            return new MoneyTransfer(MoneyTransferResult.SUCCESS, amount, newSenderBalance, newTargetBalance);
        }
        catch (SQLException e)
        {
            throw new CompletionException(e);
        }
    }

    /*
     * BalanceTop
     */

    private record BalanceTopFilterResult(long eligibleEntriesSeen, boolean pageComplete) {}

    public CompletableFuture<BalanceTopPage> getBalanceTop(int page)
    {
        ConfigSnapshot config = instance.getConfigSnapshot();

        int normalisedPage = Math.max(page, 1);
        int normalisedEntriesPerPage = Math.max(config.getInt("settings.balancetop.entries-per-page"), 1);

        BigDecimal minimumBalance = new BigDecimal(config.getString("settings.balancetop.min-balance"));
        boolean considerExcludePermission = config.getBoolean("settings.balancetop.consider-exclude-permission");

        return getBalanceTopPage(normalisedPage, normalisedEntriesPerPage, minimumBalance, considerExcludePermission)
                .thenCompose(entries ->
                {
                    // If the requested page exists, or page 1 itself is empty, return it
                    if (!entries.isEmpty() || normalisedPage == 1)
                    {
                        return CompletableFuture.completedFuture(new BalanceTopPage(normalisedPage, entries));
                    }

                    // The requested page was too high, so fall back to page 1
                    return getBalanceTopPage(1, normalisedEntriesPerPage, minimumBalance, considerExcludePermission).thenApply(firstPageEntries -> new BalanceTopPage(1, firstPageEntries));
                });
    }

    private CompletableFuture<List<BalanceTopEntry>> getBalanceTopPage(int page, int entriesPerPage, BigDecimal minimumBalance, boolean considerExcludePermission)
    {
        long pageStart = (long) (page - 1) * entriesPerPage;
        long pageEnd = pageStart + entriesPerPage;

        List<BalanceTopEntry> pageEntries = new ArrayList<>(entriesPerPage);

        return collectBalanceTopEntries(minimumBalance, considerExcludePermission, pageStart, pageEnd, 0L, null, pageEntries).thenApply(ignored -> List.copyOf(pageEntries));
    }

    private List<BalanceTopEntry> getBalanceTopCandidatesFromDatabase(BigDecimal minimumBalance, int limit, BalanceTopEntry after) throws SQLException
    {
        String sql;

        if (after == null)
        {
            sql = """
                SELECT uuid, balance, last_balance_change_timestamp
                FROM player_accounts
                WHERE balance >= ?
                ORDER BY balance DESC, last_balance_change_timestamp ASC, uuid ASC
                LIMIT ?;
                """;
        }
        else
        {
            sql = """
                SELECT uuid, balance, last_balance_change_timestamp
                FROM player_accounts
                WHERE balance >= ?
                  AND (
                      balance < ?
                      OR (
                          balance = ?
                          AND last_balance_change_timestamp > ?
                      )
                      OR (
                          balance = ?
                          AND last_balance_change_timestamp = ?
                          AND uuid > ?
                      )
                  )
                ORDER BY balance DESC, last_balance_change_timestamp ASC, uuid ASC
                LIMIT ?;
                """;
        }

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setLong(1, balanceToDatabaseValue(minimumBalance));

            if (after == null)
            {
                statement.setInt(2, limit);
            }
            else
            {
                long afterBalance = balanceToDatabaseValue(after.balance());

                statement.setLong(2, afterBalance);
                statement.setLong(3, afterBalance);
                statement.setLong(4, after.lastBalanceChangeTimestamp());
                statement.setLong(5, afterBalance);
                statement.setLong(6, after.lastBalanceChangeTimestamp());
                statement.setString(7, after.uuid().toString());
                statement.setInt(8, limit);
            }

            List<BalanceTopEntry> entries = new ArrayList<>();

            try (ResultSet resultSet = statement.executeQuery())
            {
                while (resultSet.next())
                {
                    entries.add(new BalanceTopEntry(UUID.fromString(resultSet.getString("uuid")), balanceFromDatabaseValue(resultSet.getLong("balance")), resultSet.getLong("last_balance_change_timestamp")));
                }
            }

            return entries;
        }
    }

    private boolean isBalanceTopEligible(UUID uuid, boolean considerExcludePermission)
    {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        // Exclude vanilla-banned players
        if (player.isBanned())
        {
            return false;
        }

        // Exclude permanently LiteBans-banned players
        if (instance.getLiteBansInstalled() && Util.isPlayerLiteBansPermanentlyBannedSync(uuid))
        {
            return false;
        }

        // Optionally exclude players with the exclusion permission
        if (considerExcludePermission && instance.getVaultPermission().playerHas(null, player, "theosiseconomy.balancetop.exclude"))
        {
            return false;
        }

        return true;
    }

    private CompletableFuture<Void> collectBalanceTopEntries(BigDecimal minimumBalance, boolean considerExcludePermission, long pageStart, long pageEnd, long eligibleEntriesSeen, BalanceTopEntry after, List<BalanceTopEntry> pageEntries)
    {
        return submitDatabaseTask(() ->
        {
            try
            {
                return getBalanceTopCandidatesFromDatabase(minimumBalance, BALANCE_TOP_BATCH_SIZE, after);
            }
            catch (SQLException e)
            {
                throw new CompletionException(e);
            }
        }).thenCompose(candidates ->
        {
            // No more qualifying accounts exist
            if (candidates.isEmpty())
            {
                return CompletableFuture.completedFuture(null);
            }

            BalanceTopEntry nextCursor = candidates.getLast();

            return submitBalanceTopFilterTask(() ->
            {
                long newEligibleEntriesSeen = eligibleEntriesSeen;

                for (BalanceTopEntry candidate : candidates)
                {
                    if (!isBalanceTopEligible(candidate.uuid(), considerExcludePermission))
                    {
                        continue;
                    }

                    if (newEligibleEntriesSeen >= pageStart && newEligibleEntriesSeen < pageEnd)
                    {
                        pageEntries.add(candidate);
                    }

                    newEligibleEntriesSeen++;

                    if (newEligibleEntriesSeen >= pageEnd)
                    {
                        return new BalanceTopFilterResult(newEligibleEntriesSeen, true);
                    }
                }

                return new BalanceTopFilterResult(newEligibleEntriesSeen, false);
            }).thenCompose(filterResult ->
            {
                if (filterResult.pageComplete())
                {
                    return CompletableFuture.completedFuture(null);
                }

                // A partial DB batch means we've reached the end
                if (candidates.size() < BALANCE_TOP_BATCH_SIZE)
                {
                    return CompletableFuture.completedFuture(null);
                }

                return collectBalanceTopEntries(minimumBalance, considerExcludePermission, pageStart, pageEnd, filterResult.eligibleEntriesSeen(), nextCursor, pageEntries);
            });
        });
    }

    public CompletableFuture<Void> refreshCachedBalanceTopEntries()
    {
        if (!balanceTopCacheRefreshInProgress.compareAndSet(false, true))
        {
            return CompletableFuture.completedFuture(null);
        }

        try
        {
            ConfigSnapshot config = instance.getConfigSnapshot();

            int entries = Math.max(config.getInt("settings.placeholders.balancetop-cache.entries"), 0);

            if (entries == 0)
            {
                cachedBalanceTopEntries = List.of();
                balanceTopCacheRefreshInProgress.set(false);

                return CompletableFuture.completedFuture(null);
            }

            return getBalanceTopPage(1, entries, new BigDecimal(config.getString("settings.balancetop.min-balance")), config.getBoolean("settings.balancetop.consider-exclude-permission")).thenAccept(balanceTopEntries ->
                    cachedBalanceTopEntries = List.copyOf(balanceTopEntries)).whenComplete((ignored, throwable) ->
                    balanceTopCacheRefreshInProgress.set(false));
        }
        catch (RuntimeException e)
        {
            balanceTopCacheRefreshInProgress.set(false);
            throw e;
        }
    }

    /*
     * Database read/write helpers
     */

    private long balanceToDatabaseValue(BigDecimal balance)
    {
        return balance.movePointRight(instance.getDecimalPlaces()).longValueExact();
    }

    private BigDecimal balanceFromDatabaseValue(long balance)
    {
        return BigDecimal.valueOf(balance, instance.getDecimalPlaces()).stripTrailingZeros();
    }

    private BigDecimal getBalanceFromDatabase(UUID uuid) throws SQLException
    {
        String sql = """
            SELECT balance
            FROM player_accounts
            WHERE uuid = ?;
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery())
            {
                if (!resultSet.next())
                {
                    return null;
                }

                return balanceFromDatabaseValue(resultSet.getLong("balance"));
            }
        }
    }

    private boolean setBalanceInDatabase(UUID uuid, BigDecimal balance, long balanceChangeTimestamp) throws SQLException
    {
        String sql = """
            UPDATE player_accounts
            SET balance = ?,
                last_balance_change_timestamp = ?
            WHERE uuid = ?;
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setLong(1, balanceToDatabaseValue(balance));
            statement.setLong(2, balanceChangeTimestamp);
            statement.setString(3, uuid.toString());

            int updatedRows = statement.executeUpdate();

            if (updatedRows < 1)
            {
                return false;
            }

            if (updatedRows > 1)
            {
                throw new SQLException("Expected to update one account's balance for " + uuid + ", but updated " + updatedRows + " rows.");
            }

            return true;
        }
    }

    private Boolean getAcceptingPaymentsFromDatabase(UUID uuid) throws SQLException
    {
        String sql = """
            SELECT accepting_payments
            FROM player_accounts
            WHERE uuid = ?;
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery())
            {
                if (!resultSet.next())
                {
                    return null;
                }

                return resultSet.getBoolean("accepting_payments");
            }
        }
    }

    private boolean setAcceptingPaymentsInDatabase(UUID uuid, boolean acceptingPayments) throws SQLException
    {
        String sql = """
            UPDATE player_accounts
            SET accepting_payments = ?
            WHERE uuid = ?;
            """;

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql))
        {
            statement.setBoolean(1, acceptingPayments);
            statement.setString(2, uuid.toString());

            int updatedRows = statement.executeUpdate();

            if (updatedRows < 1)
            {
                return false;
            }

            if (updatedRows > 1)
            {
                throw new SQLException("Expected to update one account's \"accepting_payments\" value for " + uuid + ", but updated " + updatedRows + " rows.");
            }

            return true;
        }
    }

    private void transferMoneyInDatabase(UUID senderUuid, BigDecimal senderBalance, UUID targetUuid, BigDecimal targetBalance, long balanceChangeTimestamp) throws SQLException
    {
        Connection connection = databaseManager.getConnection();

        boolean previousAutoCommit = connection.getAutoCommit();

        connection.setAutoCommit(false);

        try
        {
            if (!setBalanceInDatabase(senderUuid, senderBalance, balanceChangeTimestamp))
            {
                throw new SQLException("Sender account row does not exist for " + senderUuid + ".");
            }

            if (!setBalanceInDatabase(targetUuid, targetBalance, balanceChangeTimestamp))
            {
                throw new SQLException("Target account row does not exist for " + targetUuid + ".");
            }

            connection.commit();
        }
        catch (SQLException e)
        {
            connection.rollback();

            throw e;
        }
        finally
        {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void submitDatabaseBalanceUpdate(PlayerAccount account, BigDecimal balance, long balanceChangeTimestamp)
    {
        executeDatabaseTask(() ->
        {
            // If the loaded account's balance has changed again since this update was submitted, return, because it is now considered stale
            synchronized (account)
            {
                if (account.getBalance().compareTo(balance) != 0 || account.getLastBalanceChangeTimestamp() != balanceChangeTimestamp)
                {
                    return;
                }
            }

            UUID uuid = account.getUuid();

            try
            {
                if (!setBalanceInDatabase(uuid, balance, balanceChangeTimestamp))
                {
                    instance.getLogger().log(Level.SEVERE, "Failed to update \"balance\" in database for " + uuid + " because the account row does not exist.");
                }
            }
            catch (SQLException e)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to update \"balance\" in database for " + uuid + ".", e);
            }
        });
    }

    private void submitDatabaseAcceptingPaymentsUpdate(PlayerAccount account, boolean acceptingPayments)
    {
        executeDatabaseTask(() ->
        {
            // If the loaded account's "accepting_payments" value has changed again since this update was submitted, return, because it is now considered stale
            synchronized (account)
            {
                if (account.getAcceptingPayments() != acceptingPayments)
                {
                    return;
                }
            }

            UUID uuid = account.getUuid();

            try
            {
                if (!setAcceptingPaymentsInDatabase(uuid, acceptingPayments))
                {
                    instance.getLogger().log(Level.SEVERE, "Failed to update \"accepting_payments\" in database for " + uuid + " because the account row does not exist.");
                }
            }
            catch (SQLException e)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to update \"accepting_payments\" in database for " + uuid + ".", e);
            }
        });
    }

    /*
     * Getters
     */

    public List<BalanceTopEntry> getCachedBalanceTopEntries()
    {
        return cachedBalanceTopEntries;
    }

}