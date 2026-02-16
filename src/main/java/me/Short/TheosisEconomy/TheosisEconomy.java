package me.Short.TheosisEconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.Short.TheosisEconomy.Commands.BalanceCommand;
import me.Short.TheosisEconomy.Commands.BalanceTopCommand;
import me.Short.TheosisEconomy.Commands.EconomyCommand;
import me.Short.TheosisEconomy.Commands.PayCommand;
import me.Short.TheosisEconomy.Commands.PayToggleCommand;
import me.Short.TheosisEconomy.Events.BalanceTopSortEvent;
import me.Short.TheosisEconomy.Listeners.PlayerJoinListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TheosisEconomy extends JavaPlugin
{

    // Map containing snapshots of player accounts to be saved to respective JSON files on a schedule
    private Map<UUID, PlayerAccountSnapshot> dirtyPlayerAccountSnapshots;

    // Scheduler for handling the repeated saving of dirty player accounts
    private ScheduledExecutorService saveDirtyPlayerAccountsScheduler;

    // File handler for the plugin's logger
    private FileHandler logFileHandler;

    // Instance of the Gson library
    private Gson gson;

    // Instance of the Vault Economy API
    private net.milkbowl.vault.economy.Economy economy;

    // Instance of the Vault Permissions API
    private Permission permissions;

    // Cache of all players' usernames, for the purpose of offline player tab completion
    private Map<UUID, String> offlinePlayerNames;

    // Cache of all player accounts
    private Map<UUID, PlayerAccount> playerAccounts;

    // Top balances and combined total balance
    private BalanceTop balanceTop;

    // The top balances update task, so it can be cancelled in the event of a reload
    private BukkitTask updateBalanceTopTask;

    // Flag to determine whether a top balances update task is running - used to prevent tasks from being able to overlap
    private AtomicBoolean updateBalanceTopTaskRunning;

    // Instance of the MiniMessage API
    private MiniMessage miniMessage;

    // Instance of the LegacyComponentSerializer API
    private LegacyComponentSerializer legacyComponentSerializer;

    // Whether LiteBans is installed - for checking in the "updateBalanceTop" method
    private boolean liteBansInstalled;

    // Config options that may need to be retrieved in the "updateBalanceTop" method later
    private boolean balanceTopConsiderExcludePermission;
    private boolean balanceTopExcludePermanentlyBannedPlayers;
    private BigDecimal balanceTopMinBalance;

    @Override
    public void onEnable()
    {
        // Set up config.yml
        saveDefaultConfig();

        // Set "dirtyPlayerAccountSnapshots"
        dirtyPlayerAccountSnapshots = new ConcurrentHashMap<>();

        // Set "saveDirtyPlayerAccountsScheduler"
        saveDirtyPlayerAccountsScheduler = Executors.newSingleThreadScheduledExecutor();

        // Disable console logging if config.yml says to do so
        if (!getConfig().getBoolean("settings.logging.log-console"))
        {
            getLogger().setUseParentHandlers(false);
        }

        // Enable file logging to "logs.log" if config.yml says to do so
        if (getConfig().getBoolean("settings.logging.log-file"))
        {
            setupLogFileHandler();
        }

        // Create instance of Gson
        gson = new GsonBuilder().setPrettyPrinting().create();

        // Register TheosisEconomy as a Vault Economy provider
        economy = new Economy(this);
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, economy, this, ServicePriority.Highest);

        // Hook into Vault Permissions
        permissions = getServer().getServicesManager().getRegistration(Permission.class).getProvider();

        // Initial empty BalanceTop data
        balanceTop = new BalanceTop(new LinkedHashMap<>(), BigDecimal.ZERO);

        // Get instance of the MiniMessage API
        miniMessage = MiniMessage.miniMessage();

        // Get instance of the LegacyComponentSerializer API
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();

        // Get config options from config.yml here, so they don't need to be retrieved in the async "updateBalanceTop" method later
        balanceTopConsiderExcludePermission = getConfig().getBoolean("settings.balancetop.consider-exclude-permission");
        balanceTopExcludePermanentlyBannedPlayers = getConfig().getBoolean("settings.balancetop.exclude-permanently-banned-players");
        balanceTopMinBalance = new BigDecimal(getConfig().getString("settings.balancetop.min-balance"));

        // Register events
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(this), this);

        // Register commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
        {
            Commands registrar = commands.registrar();

            // Basic commands
            registrar.register(getConfig().getString("settings.commands.paytoggle.name"), getConfig().getString("settings.commands.paytoggle.description"), getConfig().getStringList("settings.commands.paytoggle.aliases"), new PayToggleCommand(this));

            // Non-basic commands
            registrar.register(BalanceCommand.createCommand(getConfig().getString("settings.commands.balance.name"), this), getConfig().getString("settings.commands.balance.description"), getConfig().getStringList("settings.commands.balance.aliases"));
            registrar.register(BalanceTopCommand.createCommand(getConfig().getString("settings.commands.balancetop.name"), this), getConfig().getString("settings.commands.balancetop.description"), getConfig().getStringList("settings.commands.balancetop.aliases"));
            registrar.register(PayCommand.createCommand(getConfig().getString("settings.commands.pay.name"), this), getConfig().getString("settings.commands.pay.description"), getConfig().getStringList("settings.commands.pay.aliases"));
            registrar.register(EconomyCommand.createCommand(getConfig().getString("settings.commands.economy.name"), this), getConfig().getString("settings.commands.economy.description"), getConfig().getStringList("settings.commands.economy.aliases"));
        });

        // Register PlaceholderAPI placeholders, if PlaceholderAPI is installed
        if (pluginManager.getPlugin("PlaceholderAPI") != null)
        {
            new PlaceholderAPI(this).register();
        }

        // Get whether LiteBans is installed
        liteBansInstalled = pluginManager.getPlugin("LiteBans") != null;

        // bStats
        Metrics metrics = new Metrics(this, 13836);

        // Cache all offline player names
        offlinePlayerNames = cacheOfflinePlayerNames();

        // Cache all players' UUIDs and their account data
        playerAccounts = cachePlayerAccounts();

        // Set "updateBalanceTopTaskRunning"
        updateBalanceTopTaskRunning = new AtomicBoolean(false);

        // Schedule repeating BalanceTop update task
        scheduleBalanceTopUpdateTask();

        // Schedule task to periodically save any dirty player accounts to JSON files
        runSaveDirtyPlayerAccountsLoop();
    }

    @Override
    public void onDisable()
    {
        // Shut down the save dirty player accounts scheduler, allowing the current task (if there is one running) to finish
        saveDirtyPlayerAccountsScheduler.shutdown();

        // Allow currently running task to finish, or force shutdown if it hasn't after 30 seconds (it should never take this long)
        try
        {
            if (!saveDirtyPlayerAccountsScheduler.awaitTermination(30, TimeUnit.SECONDS))
            {
                saveDirtyPlayerAccountsScheduler.shutdownNow();
            }
        }
        catch (InterruptedException ignored)
        {
            saveDirtyPlayerAccountsScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Ensure any remaining dirty player accounts are saved
        saveDirtyPlayerAccounts();
    }

    // Method to set up "logFileHandler" and set it to this plugin's logger
    private void setupLogFileHandler()
    {
        try
        {
            logFileHandler = new FileHandler(getDataFolder().getAbsolutePath() + "/logs.log", true);

            logFileHandler.setFormatter(new LogFormatter());

            getLogger().addHandler(logFileHandler);

        }
        catch (IOException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    // Method to cache all offline player names
    private Map<UUID, String> cacheOfflinePlayerNames()
    {
        Map<UUID, String> offlinePlayerNames = new ConcurrentHashMap<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers())
        {
            offlinePlayerNames.put(player.getUniqueId(), player.getName());
        }

        return offlinePlayerNames;
    }

    // Method to cache all player accounts from their respective JSON files in the "player-accounts" folder
    private Map<UUID, PlayerAccount> cachePlayerAccounts()
    {
        Map<UUID, PlayerAccount> playerAccounts = new HashMap<>();

        File playerAccountsFolder = new File(getDataFolder(), "player-accounts");

        // If a "player-accounts" folder doesn't exist, create one
        if (!playerAccountsFolder.exists())
        {
            playerAccountsFolder.mkdirs();
        }

        // Get a list of player account files
        File[] playerAccountFiles = playerAccountsFolder.listFiles();

        // If there are no files, return the unpopulated `playerAccounts` map
        if (playerAccountFiles == null)
        {
            return playerAccounts;
        }

        // Go through each file, read a `PlayerAccount` object from it, and put it in `PlayerAccounts`
        for (File file : playerAccountFiles)
        {
            try
            {
                UUID uuid = UUID.fromString(file.getName().replace(".json", ""));

                try (FileReader reader = new FileReader(file))
                {
                    playerAccounts.put(uuid, gson.fromJson(reader, PlayerAccount.class));
                }
            }
            catch (IllegalArgumentException exception)
            {
                getLogger().log(Level.WARNING, "Failed to load player account - invalid UUID in file name: " + file.getName());
                exception.printStackTrace();
            }
            catch (IOException | JsonIOException exception)
            {
                getLogger().log(Level.WARNING, "Failed to load player account - failed to read file: " + file.getName());
                exception.printStackTrace();
            }
        }

        return playerAccounts;
    }

    // Method to schedule a repeating BalanceTop update task
    private void scheduleBalanceTopUpdateTask()
    {
        updateBalanceTopTask = Bukkit.getScheduler().runTaskTimer(this, () ->
        {
            // Don't update BalanceTop if a task is already running
            if (updateBalanceTopTaskRunning.compareAndSet(false, true))
            {
                return;
            }

            // Create BalanceTopSortEvent instance with an initial empty HashSet of excluded players' UUIDs
            BalanceTopSortEvent balanceTopSortEvent = new BalanceTopSortEvent(new HashSet<>());

            // Call the event
            Bukkit.getServer().getPluginManager().callEvent(balanceTopSortEvent);

            // If the event was cancelled, return
            if (balanceTopSortEvent.isCancelled())
            {
                return;
            }

            // Call "updateBalanceTop", passing in the HashSet of excluded players' UUIDs
            updateBalanceTop(balanceTopSortEvent.getExcludedPlayers()).thenAccept(balanceTop ->
            {
                this.balanceTop = balanceTop;

                updateBalanceTopTaskRunning.set(false);
            });
        }, 0L, getConfig().getLong("settings.balancetop.update-task-frequency"));
    }

    // Method to repeatedly call `saveDirtyPlayerAccounts()` async
    private void runSaveDirtyPlayerAccountsLoop()
    {
        saveDirtyPlayerAccountsScheduler.scheduleWithFixedDelay(this::saveDirtyPlayerAccounts, 0, getConfig().getLong("settings.misc.data-file-save-frequency"), TimeUnit.SECONDS);
    }

    // Method to save all player accounts in `dirtyPlayerAccountSnapshots` to respective JSON files, and remove the saved accounts from `dirtyPlayerAccountSnapshots` after
    private void saveDirtyPlayerAccounts()
    {
        // If there are no dirty player account snapshots, return
        if (dirtyPlayerAccountSnapshots.isEmpty())
        {
            return;
        }

        // Save each dirty player account to a JSON file inside the "player-accounts" folder
        for (Map.Entry<UUID, PlayerAccountSnapshot> entry : new HashMap<>(dirtyPlayerAccountSnapshots).entrySet())
        {
            UUID uuid = entry.getKey();
            PlayerAccountSnapshot playerAccountSnapshot = entry.getValue();

            Path target = getDataFolder().toPath()
                    .resolve("player-accounts")
                    .resolve(uuid + ".json");

            Path temp = target.resolveSibling(target.getFileName() + ".tmp");

            try
            {
                // Write the snapshot to a temporary file
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
                {
                    gson.toJson(playerAccountSnapshot, writer);
                }

                // Atomically move the temporary file to the real JSON file
                try
                {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException ignored)
                {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }

                // Remove the snapshot from `dirtyPlayerAccountSnapshots` - only remove if same instance
                dirtyPlayerAccountSnapshots.remove(uuid, playerAccountSnapshot);
            }
            catch (IOException exception)
            {
                getLogger().log(Level.WARNING, "Failed to save player account to file: " + target);
                exception.printStackTrace();
            }
        }
    }

    // Method to update BalanceTop async
    private CompletableFuture<BalanceTop> updateBalanceTop(Set<UUID> excludedPlayers)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            Map<UUID, BigDecimal> unsortedBalances = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;

            // Get player names and their balances in no particular order, excluding banned players if config.yml says to not include them - the `Bukkit.getOfflinePlayer(uuid).isBanned()` is the only thing here that might not be safe to run async, but no issues so far in testing
            for (UUID uuid : playerAccounts.keySet())
            {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

                if (!excludedPlayers.contains(uuid) && !(balanceTopConsiderExcludePermission && permissions.playerHas(null, player, "theosiseconomy.balancetop.exclude")) && (!balanceTopExcludePermanentlyBannedPlayers || !((liteBansInstalled && Util.isPlayerLiteBansPermanentlyBanned(uuid).join()) || player.isBanned())))
                {
                    PlayerAccount account = playerAccounts.get(uuid);
                    BigDecimal balance = account.getBalance();

                    total = total.add(balance);

                    if (balance.compareTo(balanceTopMinBalance) >= 0)
                    {
                        unsortedBalances.put(uuid, balance);
                    }
                }
            }

            // Create and return sorted version of "unsortedBalances"
            LinkedHashMap<UUID, BigDecimal> topBalances = new LinkedHashMap<>();
            unsortedBalances.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry -> topBalances.put(entry.getKey(), entry.getValue()));

            return new BalanceTop(topBalances, total);
        });
    }

    // Method to reload the config and data files
    public void reload()
    {
        // Reload config.yml from disk
        reloadConfig();

        // Re-get values from config that were retrieved in "onEnable"
        balanceTopConsiderExcludePermission = getConfig().getBoolean("settings.balancetop.consider-exclude-permission");
        balanceTopExcludePermanentlyBannedPlayers = getConfig().getBoolean("settings.balancetop.exclude-permanently-banned-players");
        balanceTopMinBalance = new BigDecimal(getConfig().getString("settings.balancetop.min-balance"));

        // Set whether to send logs to console
        Logger logger = getLogger();
        if (getConfig().getBoolean("settings.logging.log-console"))
        {
            if (!logger.getUseParentHandlers())
            {
                logger.setUseParentHandlers(true);
            }
        }
        else
        {
            if (logger.getUseParentHandlers())
            {
                logger.setUseParentHandlers(false);
            }
        }

        // Set whether to send logs to the "logs.log" file
        if (getConfig().getBoolean("settings.logging.log-file"))
        {
            if (logFileHandler != null)
            {
                if (!Arrays.asList(logger.getHandlers()).contains(logFileHandler))
                {
                    logger.addHandler(logFileHandler);
                }
            }
            else
            {
                setupLogFileHandler();
            }
        }
        else
        {
            // Remove the FileHandler from the logger, if it exists
            if (logFileHandler != null)
            {
                for (Handler handler : logger.getHandlers())
                {
                    if (handler.equals(logFileHandler))
                    {
                        logger.removeHandler(logFileHandler);
                        break;
                    }
                }
            }
        }

        // Shut down the save dirty player accounts scheduler, allowing the current task (if there is one running) to finish
        saveDirtyPlayerAccountsScheduler.shutdown();

        // Allow currently running task to finish, or force shutdown if it hasn't after 30 seconds (it should never take this long)
        try
        {
            if (!saveDirtyPlayerAccountsScheduler.awaitTermination(30, TimeUnit.SECONDS))
            {
                saveDirtyPlayerAccountsScheduler.shutdownNow();
            }
        }
        catch (InterruptedException ignored)
        {
            saveDirtyPlayerAccountsScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Ensure any remaining dirty player accounts are saved
        saveDirtyPlayerAccounts();

        // Re-set "saveDirtyPlayerAccountsScheduler"
        saveDirtyPlayerAccountsScheduler = Executors.newSingleThreadScheduledExecutor();

        // Re-schedule repeating data save task
        runSaveDirtyPlayerAccountsLoop();

        // Re-cache player accounts
        playerAccounts = cachePlayerAccounts();

        // Cancel the current repeating BalanceTop update task
        updateBalanceTopTask.cancel();

        // Re-set "updateBalanceTopTaskRunning"
        updateBalanceTopTaskRunning.set(false);

        // Re-schedule repeating BalanceTop update task
        scheduleBalanceTopUpdateTask();
    }

    // ----- Getters -----

    // Getter for "dirtyPlayerAccountSnapshots"
    public Map<UUID, PlayerAccountSnapshot> getDirtyPlayerAccountSnapshots()
    {
        return dirtyPlayerAccountSnapshots;
    }

    // Getter for "economy"
    public net.milkbowl.vault.economy.Economy getEconomy()
    {
        return economy;
    }

    // Getter for ""
    public Map<UUID, String> getOfflinePlayerNames()
    {
        return offlinePlayerNames;
    }

    // Getter for "playerAccounts"
    public Map<UUID, PlayerAccount> getPlayerAccounts()
    {
        return playerAccounts;
    }

    // Getter for "balanceTop"
    public BalanceTop getBalanceTop()
    {
        return balanceTop;
    }

    // Getter for "miniMessage"
    public MiniMessage getMiniMessage()
    {
        return miniMessage;
    }

    // Getter for "legacyComponentSerializer"
    public LegacyComponentSerializer getLegacyComponentSerializer()
    {
        return legacyComponentSerializer;
    }

}