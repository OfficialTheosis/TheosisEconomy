package me.Short.TheosisEconomy.Listeners;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PlayerConnectionCloseListener implements Listener
{

    private final TheosisEconomy instance;

    public PlayerConnectionCloseListener(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerConnectionClose(PlayerConnectionCloseEvent event)
    {
        instance.getPlayerAccountManager().unloadAccount(event.getPlayerUniqueId());
    }

}