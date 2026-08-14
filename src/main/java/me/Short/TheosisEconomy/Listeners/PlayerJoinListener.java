package me.Short.TheosisEconomy.Listeners;

import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener
{

    private final TheosisEconomy instance;

    public PlayerJoinListener(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();

        instance.getMostRecentPlayerNamesStore().add(player.getUniqueId(), player.getName());
    }

}