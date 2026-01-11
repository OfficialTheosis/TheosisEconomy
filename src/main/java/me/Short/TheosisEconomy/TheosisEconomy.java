package me.Short.TheosisEconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import litebans.api.Database;
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
import org.apache.commons.lang3.tuple.Pair;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

    // Instance of the current save task scheduled to run in the future
    private ScheduledFuture<?> scheduledSaveTask;

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

    // Baltop
    private Map<UUID, BigDecimal> baltop;

    // Combined total of all players' balances - updated in "updateBaltop"
    private BigDecimal combinedTotalBalance;

    // ID for the baltop update task, so it can be cancelled in the event of a reload
    private int updateBaltopTaskId;

    // Instance of the MiniMessage API
    private MiniMessage miniMessage;

    // Instance of the LegacyComponentSerializer API
    private LegacyComponentSerializer legacyComponentSerializer;

    // Whether LiteBans is installed - for checking in the "updateBaltop" method
    private boolean liteBansInstalled;

    // Config options that may need to be retrieved in the "updateBaltop" method later
    private boolean baltopConsiderExcludePermission;
    private boolean baltopExcludeBannedPlayers;
    private BigDecimal baltopMinBalance;

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

        // Set initial "baltop" value to an empty linked hashmap
        baltop = new LinkedHashMap<>();

        // Set initial "combinedTotalBalance" to 0
        combinedTotalBalance = BigDecimal.ZERO;

        // Get instance of the MiniMessage API
        miniMessage = MiniMessage.miniMessage();

        // Get instance of the LegacyComponentSerializer API
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();

        // Get config options from config.yml here, so they don't need to be retrieved in the async "updateBaltop" method later
        baltopConsiderExcludePermission = getConfig().getBoolean("settings.baltop.consider-exclude-permission");
        baltopExcludeBannedPlayers = getConfig().getBoolean("settings.baltop.exclude-banned-players");
        baltopMinBalance = new BigDecimal(getConfig().getString("settings.baltop.min-balance"));

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

        // Schedule repeating baltop update task
        scheduleBaltopUpdateTask();

        // Schedule task to periodically save any dirty player accounts to JSON files
        scheduledSaveTask = saveDirtyPlayerAccountsScheduler.schedule(this::runSaveDirtyPlayerAccountsLoop, getConfig().getLong("settings.player-accounts-save-frequency"), TimeUnit.SECONDS);
    }

    @Override
    public void onDisable()
    {
        // Cancel async dirty player account saving task, since we're going to save here manually
        scheduledSaveTask.cancel(false);

        // Save dirty player accounts to JSON files
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

    // Method to cache all player accounts from their respective JSON files in the "player-accounts" folder - also migrates any files in the old "data.yml" file if it exists
    private Map<UUID, PlayerAccount> cachePlayerAccounts()
    {
        Map<UUID, PlayerAccount> playerAccounts = new HashMap<>();

        File playerAccountsFolder = new File(getDataFolder(), "player-accounts");

        // If a "player-accounts" folder doesn't exist, create one
        if (!playerAccountsFolder.exists())
        {
            playerAccountsFolder.mkdirs();
        }

        // If there is an old "data.yml" file, migrate all player accounts to separate JSON files
        File dataFile = new File(getDataFolder(), "data.yml");
        if (dataFile.exists())
        {
            getLogger().log(Level.INFO, "Old \"data.yml\" file found. Migrating player data to JSON files...");

            ConfigurationSection data = YamlConfiguration.loadConfiguration(dataFile).getConfigurationSection("players");

            // If there is data, create a `PlayerAccountSnapshot` based on it, add it to `dirtyPlayerAccountSnapshots`, then save the dirty player accounts to respective JSON files
            if (data != null)
            {
                // Add snapshots to `dirtyPlayerAccountSnapshots`
                for (String uuid : data.getKeys(false))
                {
                    dirtyPlayerAccountSnapshots.put(UUID.fromString(uuid), new PlayerAccountSnapshot(new BigDecimal(data.getString(uuid + ".balance")), data.getBoolean(uuid + ".acceptingPayments")));
                }

                // Save
                saveDirtyPlayerAccounts();
            }

            // Delete the "data.yml" file
            if (dataFile.delete())
            {
                getLogger().log(Level.INFO, "The old \"data.yml\" file has been deleted.");
            }
            else
            {
                getLogger().log(Level.INFO, "Failed to delete the old \"data.yml\" file.");
            }

            getLogger().log(Level.INFO, "Migration complete.");
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

    // Method to schedule the repeating baltop update task
    private void scheduleBaltopUpdateTask()
    {
        // Call "updateBaltop" on a schedule (frequency defined in config.yml) and set "baltop" and "combinedTotalBalance" to its results
        updateBaltopTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () ->
        {
            // Create BalanceTopSortEvent instance with an initial empty HashSet of excluded players' UUIDs
            BalanceTopSortEvent balanceTopSortEvent = new BalanceTopSortEvent(new HashSet<>());

            // Call the event
            Bukkit.getServer().getPluginManager().callEvent(balanceTopSortEvent);

            // Call "updateBaltop", passing in the HashSet of excluded players' UUIDs, if the event was NOT cancelled
            if (!balanceTopSortEvent.isCancelled())
            {
                updateBaltop(balanceTopSortEvent.getExcludedPlayers()).thenAccept(pair ->
                {
                    baltop = pair.getLeft();
                    combinedTotalBalance = pair.getRight();
                });
            }
        }, 0L, getConfig().getLong("settings.baltop.update-task-frequency"));
    }

    // Method to repeatedly call `saveDirtyPlayerAccounts()` async
    private void runSaveDirtyPlayerAccountsLoop()
    {
        CompletableFuture.runAsync(this::saveDirtyPlayerAccounts)
                .thenRunAsync(() -> scheduledSaveTask = saveDirtyPlayerAccountsScheduler.schedule(this::runSaveDirtyPlayerAccountsLoop, getConfig().getLong("settings.misc.data-file-save-frequency"), TimeUnit.SECONDS), saveDirtyPlayerAccountsScheduler);
    }

    // Method to save all player accounts in `dirtyPlayerAccountSnapshots` to respective JSON files, and remove the saved accounts from `dirtyPlayerAccountSnapshots` after
    private void saveDirtyPlayerAccounts()
    {
        if (!dirtyPlayerAccountSnapshots.isEmpty())
        {
            // Snapshot of `dirtyPlayerAccountSnapshots`
            Map<UUID, PlayerAccountSnapshot> dirtyPlayerAccountSnapshotsSnapshot = new HashMap<>(dirtyPlayerAccountSnapshots);

            // Save each dirty player account to a JSON file inside the "player-accounts" folder
            for (Map.Entry<UUID, PlayerAccountSnapshot> entry : dirtyPlayerAccountSnapshotsSnapshot.entrySet())
            {
                String fileName = getDataFolder().getAbsolutePath() + File.separator + "player-accounts" + File.separator + entry.getKey() + ".json";

                try (Writer writer = new FileWriter(fileName))
                {
                    // Write the `PlayerAccountSnapshot` to a JSON file with the name `fileName`
                    gson.toJson(entry.getValue(), writer);
                }
                catch (IOException exception)
                {
                    getLogger().log(Level.WARNING, "Failed to save player account to file: " + fileName);
                    exception.printStackTrace();
                    continue;
                }

                // Remove the entry from `dirtyPlayerAccountSnapshots`, since the player's account has now been saved
                dirtyPlayerAccountSnapshots.remove(entry.getKey());
            }
        }
    }

    // Method to update baltop async
    private CompletableFuture<Pair<Map<UUID, BigDecimal>, BigDecimal>> updateBaltop(Set<UUID> excludedPlayers)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            Map<UUID, BigDecimal> unsortedBaltop = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;

            // Get player names and their balances in no particular order, excluding banned players if config.yml says to not include them - the `Bukkit.getOfflinePlayer(uuid).isBanned()` is the only thing here that might not be safe to run async, but no issues so far in testing
            for (UUID uuid : playerAccounts.keySet())
            {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

                if (!excludedPlayers.contains(uuid) && !(baltopConsiderExcludePermission && permissions.playerHas(null, player, "theosiseconomy.balancetop.exclude")) && (!baltopExcludeBannedPlayers || !((liteBansInstalled && Database.get().isPlayerBanned(uuid, null)) || player.isBanned())))
                {
                    PlayerAccount account = playerAccounts.get(uuid);
                    BigDecimal balance = account.getBalance();

                    total = total.add(balance);

                    if (balance.compareTo(baltopMinBalance) >= 0)
                    {
                        unsortedBaltop.put(uuid, balance);
                    }
                }
            }

            // Create and return sorted version of "unsortedBaltop"
            LinkedHashMap<UUID, BigDecimal> sortedBaltop = new LinkedHashMap<>();
            unsortedBaltop.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry -> sortedBaltop.put(entry.getKey(), entry.getValue()));

            return Pair.of(sortedBaltop, total);
        });
    }

    // Method to reload the config and data files
    public void reload()
    {
        BukkitScheduler scheduler = Bukkit.getScheduler();

        // Reload config.yml from disk
        reloadConfig();

        // Re-get values from config that were retrieved in "onEnable"
        baltopConsiderExcludePermission = getConfig().getBoolean("settings.baltop.consider-exclude-permission");
        baltopExcludeBannedPlayers = getConfig().getBoolean("settings.baltop.exclude-banned-players");
        baltopMinBalance = new BigDecimal(getConfig().getString("settings.baltop.min-balance"));

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

        // Cancel async dirty player account saving task
        scheduledSaveTask.cancel(false);

        // Save dirty player accounts to JSON files
        saveDirtyPlayerAccounts();

        // Re-schedule repeating data save task
        scheduledSaveTask = saveDirtyPlayerAccountsScheduler.schedule(this::runSaveDirtyPlayerAccountsLoop, getConfig().getLong("settings.player-accounts-save-frequency"), TimeUnit.SECONDS);

        // Re-cache player accounts
        playerAccounts = cachePlayerAccounts();

        // Cancel the current repeating baltop update task
        scheduler.cancelTask(updateBaltopTaskId);

        // Re-schedule repeating baltop update task
        scheduleBaltopUpdateTask();
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

    // Getter for "baltop"
    public Map<UUID, BigDecimal> getBaltop()
    {
        return baltop;
    }

    // Getter for "combinedTotalBalance"
    public BigDecimal getCombinedTotalBalance()
    {
        return combinedTotalBalance;
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