package me.Short.TheosisEconomy.Listeners;

import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class AsyncPlayerPreLoginListener implements Listener
{

    private final TheosisEconomy instance;

    public AsyncPlayerPreLoginListener(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event)
    {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED)
        {
            return;
        }

        try
        {
            instance.getPlayerAccountManager().loadOrCreateAccount(event.getUniqueId(), event.getName());
        }
        catch (CompletionException e)
        {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, instance.getMiniMessage().deserialize(instance.getConfigSnapshot().getString("messages.error.could-not-create-or-load-account")));

            instance.getLogger().log(Level.SEVERE, "Failed to load or create account for " + event.getName() + " (" + event.getUniqueId() + ").", e.getCause());
        }
    }

}