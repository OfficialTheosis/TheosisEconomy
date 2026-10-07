package me.Short.TheosisEconomy;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.Short.TheosisEconomy.Commands.BalanceCommand;
import me.Short.TheosisEconomy.Commands.BalanceTopCommand;
import me.Short.TheosisEconomy.Commands.EconomyCommand;
import me.Short.TheosisEconomy.Commands.PayCommand;
import me.Short.TheosisEconomy.Commands.PayToggleCommand;
import me.Short.TheosisEconomy.Listeners.AsyncPlayerPreLoginListener;
import me.Short.TheosisEconomy.Listeners.PlayerConnectionCloseListener;
import me.Short.TheosisEconomy.Listeners.PlayerJoinListener;
import me.Short.TheosisEconomy.Listeners.PlayerQuitListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TheosisEconomy extends JavaPlugin
{

    // The name of the file that the activity logger will write to if configured to do so
    public static final String ACTIVITY_LOGGER_FILE_NAME = "logs.log";

    // Immutable config snapshot to allow config values to be safely read from different threads
    private volatile ConfigSnapshot configSnapshot;

    // Database manager
    private DatabaseManager databaseManager;

    // Player account manager
    private PlayerAccountManager playerAccountManager;

    // Used to easily send messages to audiences' chat/action bars using the appropriate scheduler
    private final MessageSender messageSender = new MessageSender(this);

    // Logger for monetary activity
    private final Logger activityLogger = Logger.getLogger(getName() + "-Activity");

    // File handler for the activity logger
    private FileHandler activityLoggerFileHandler;

    // Integrations/APIs
    private Economy vaultEconomy;
    private Permission vaultPermission;
    private MiniMessage miniMessage;

    // Cached names of players who have most recently been seen on the server - used for offline tab completion
    private MostRecentPlayerNamesStore mostRecentPlayerNamesStore;

    // Task that repeatedly refreshes the BalanceTop cache
    private ScheduledTask balanceTopCacheRefreshTask;

    // Instance of the LegacyComponentSerializer API
    private LegacyComponentSerializer legacyComponentSerializer;

    // The number of decimal places that the currency is configured to use
    private int decimalPlaces;

    // PlaceholderAPI
    private PlaceholderAPI placeholderApi;

    // Whether LiteBans is installed - checked when filtering BalanceTop entries
    private boolean liteBansInstalled;

    @Override
    public void onEnable()
    {
        // Save default config file if it doesn't already exist
        saveDefaultConfig();

        // Create immutable snapshot of config
        configSnapshot = ConfigSnapshot.create(getConfig());

        // Set "decimalPlaces"
        decimalPlaces = configSnapshot.getInt("settings.currency.decimal-places");
        if (decimalPlaces < 0)
        {
            getLogger().log(Level.SEVERE, "The configured number of decimal places is negative.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Set up database manager and establish database connection
        databaseManager = new DatabaseManager(this, getDataFolder().toPath().resolve("database.db").toString());
        try
        {
            databaseManager.connect();
        }
        catch (ClassNotFoundException e)
        {
            getLogger().log(Level.SEVERE, "Error invoking Class#forName(String) on the JDBC driver.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        catch (SQLException e)
        {
            getLogger().log(Level.SEVERE, "Error establishing connection to the database.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Create database tables if they don't already exist
        try
        {
            databaseManager.createTables();
        }
        catch (SQLException e)
        {
            getLogger().log(Level.SEVERE, "Error creating database tables.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Make sure all balances in the database are using the configured number of decimal places
        try
        {
            databaseManager.prepareBalanceRescale(decimalPlaces);
        }
        catch (SQLException e)
        {
            getLogger().log(Level.SEVERE, "Error preparing the database balance scale.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Set up player account manager
        playerAccountManager = new PlayerAccountManager(this, databaseManager);

        ServicesManager servicesManager = getServer().getServicesManager();

        // Register this plugin as a Vault economy provider
        vaultEconomy = new Economy(this);
        servicesManager.register(net.milkbowl.vault.economy.Economy.class, vaultEconomy, this, ServicePriority.Highest);

        // Set up integration/APIs
        vaultPermission = servicesManager.getRegistration(Permission.class).getProvider();
        miniMessage = MiniMessage.miniMessage();
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();

        PluginManager pluginManager = getServer().getPluginManager();

        // Register PlaceholderAPI
        if (pluginManager.getPlugin("PlaceholderAPI") != null)
        {
            placeholderApi = new PlaceholderAPI(this);
            placeholderApi.register();
        }

        // Get whether LiteBans is installed
        liteBansInstalled = pluginManager.getPlugin("LiteBans") != null;

        // Set up activity logger
        activityLogger.setParent(getLogger());
        activityLogger.setUseParentHandlers(configSnapshot.getBoolean("settings.activity-logging.log-console"));
        if (configSnapshot.getBoolean("settings.activity-logging.log-file"))
        {
            activityLoggerFileHandler = setupActivityLoggerFileHandler(ACTIVITY_LOGGER_FILE_NAME);
        }

        // Schedule BalanceTop cache refresh task
        balanceTopCacheRefreshTask = scheduleBalanceTopCacheRefreshTask();

        // Set "mostRecentPlayerNamesStore"
        mostRecentPlayerNamesStore = new MostRecentPlayerNamesStore(this, databaseManager, configSnapshot.getInt("settings.misc.most-recent-player-names-cache-max-size"));

        // Register event listeners
        pluginManager.registerEvents(new PlayerJoinListener(this), this);
        pluginManager.registerEvents(new PlayerQuitListener(this), this);
        pluginManager.registerEvents(new AsyncPlayerPreLoginListener(this), this);
        pluginManager.registerEvents(new PlayerConnectionCloseListener(this), this);

        // Register commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
        {
            Commands registrar = commands.registrar();

            // Basic commands
            registrar.register(configSnapshot.getString("settings.commands.paytoggle.name"), configSnapshot.getString("settings.commands.paytoggle.description"), configSnapshot.getStringList("settings.commands.paytoggle.aliases"), new PayToggleCommand(this));

            // Non-basic commands
            registrar.register(BalanceCommand.createCommand(configSnapshot.getString("settings.commands.balance.name"), this), configSnapshot.getString("settings.commands.balance.description"), configSnapshot.getStringList("settings.commands.balance.aliases"));
            registrar.register(BalanceTopCommand.createCommand(configSnapshot.getString("settings.commands.balancetop.name"), this), configSnapshot.getString("settings.commands.balancetop.description"), configSnapshot.getStringList("settings.commands.balancetop.aliases"));
            registrar.register(PayCommand.createCommand(configSnapshot.getString("settings.commands.pay.name"), this), configSnapshot.getString("settings.commands.pay.description"), configSnapshot.getStringList("settings.commands.pay.aliases"));
            registrar.register(EconomyCommand.createCommand(configSnapshot.getString("settings.commands.economy.name"), this), configSnapshot.getString("settings.commands.economy.description"), configSnapshot.getStringList("settings.commands.economy.aliases"));
        });

        // bStats
        Metrics metrics = new Metrics(this, 13836);
    }

    @Override
    public void onDisable()
    {
        // Cancel the BalanceTop cache refresh task
        if (balanceTopCacheRefreshTask != null)
        {
            balanceTopCacheRefreshTask.cancel();
        }

        // Prevent further PlayerAccountManager work and shut down its BalanceTop filter executor
        if (playerAccountManager != null)
        {
            playerAccountManager.shutdownExecutor();
        }

        // Disconnect from the database, allowing current work to finish first
        if (databaseManager != null)
        {
            databaseManager.shutdownExecutor();

            try
            {
                databaseManager.disconnect();
            }
            catch (SQLException e)
            {
                getLogger().log(Level.SEVERE, "Error closing connection to the database.", e);
            }
        }

        // Un-register this plugin as a Vault economy provider
        if (vaultEconomy != null)
        {
            getServer().getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, vaultEconomy);
        }

        // Un-register PlaceholderAPI
        if (placeholderApi != null)
        {
            placeholderApi.unregister();
        }

        // Close activity logger file handler
        if (activityLoggerFileHandler != null)
        {
            activityLogger.removeHandler(activityLoggerFileHandler);
            activityLoggerFileHandler.close();
            activityLoggerFileHandler = null;
        }
    }

    // Reload the config and data files
    public void reload()
    {
        // Reload config
        reloadConfig();

        // Create immutable snapshot of config
        configSnapshot = ConfigSnapshot.create(getConfig());

        // Set whether the activity logger should send logs to the console
        activityLogger.setUseParentHandlers(configSnapshot.getBoolean("settings.activity-logging.log-console"));

        // Set whether the activity logger should send logs to the "logs.log" file
        if (configSnapshot.getBoolean("settings.activity-logging.log-file"))
        {
            if (activityLoggerFileHandler == null)
            {
                activityLoggerFileHandler = setupActivityLoggerFileHandler(ACTIVITY_LOGGER_FILE_NAME);
            }
        }
        else
        {
            // Remove the FileHandler from the activity logger
            if (activityLoggerFileHandler != null)
            {
                activityLogger.removeHandler(activityLoggerFileHandler);
                activityLoggerFileHandler.close();
                activityLoggerFileHandler = null;
            }
        }

        // Cancel and re-schedule the BalanceTop cache refresh task
        balanceTopCacheRefreshTask.cancel();
        balanceTopCacheRefreshTask = scheduleBalanceTopCacheRefreshTask();

        // Update most recent player names store limit
        mostRecentPlayerNamesStore.setLimit(configSnapshot.getInt("settings.misc.most-recent-player-names-cache-max-size"));
    }

    // Set up file handler for the activity logger
    private FileHandler setupActivityLoggerFileHandler(String fileName)
    {
        try
        {
            FileHandler activityLoggerFileHandler = new FileHandler(getDataFolder().getAbsolutePath() + File.separator + fileName, true);
            activityLoggerFileHandler.setFormatter(new ActivityLogFormatter());
            activityLogger.addHandler(activityLoggerFileHandler);

            return activityLoggerFileHandler;
        }
        catch (IOException e)
        {
            getLogger().log(Level.SEVERE, "Error setting up file handler for the activity logger. Logs will not be saved to a file in this session.", e);

            return null;
        }
    }

    // Schedule BalanceTop refresh cache task
    private ScheduledTask scheduleBalanceTopCacheRefreshTask()
    {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, scheduledTask ->
                playerAccountManager.refreshCachedBalanceTopEntries().exceptionally(throwable ->
                {
                    getLogger().log(Level.WARNING, "Failed to refresh cached BalanceTop entries.", throwable);
                    return null;
                }), 1L, configSnapshot.getLong("settings.placeholders.balancetop-cache.refresh-interval-seconds") * 20L);
    }

    // Getter for "configSnapshot"
    public ConfigSnapshot getConfigSnapshot()
    {
        return configSnapshot;
    }

    // Getter for "playerAccountManager"
    public PlayerAccountManager getPlayerAccountManager()
    {
        return playerAccountManager;
    }

    // Getter for "messageSender"
    public MessageSender getMessageSender()
    {
        return messageSender;
    }

    // Getter for "activityLogger"
    public Logger getActivityLogger()
    {
        return activityLogger;
    }

    // Getter for "vaultPermission"
    public Permission getVaultPermission()
    {
        return vaultPermission;
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

    // Getter for "decimalPlaces"
    public int getDecimalPlaces()
    {
        return decimalPlaces;
    }

    // Getter for "liteBansInstalled"
    public boolean getLiteBansInstalled()
    {
        return liteBansInstalled;
    }

    // Getter for "mostRecentPlayerNamesStore"
    public MostRecentPlayerNamesStore getMostRecentPlayerNamesStore()
    {
        return mostRecentPlayerNamesStore;
    }

}