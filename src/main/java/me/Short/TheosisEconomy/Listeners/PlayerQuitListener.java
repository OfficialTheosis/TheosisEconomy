package me.Short.TheosisEconomy.Listeners;

import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class PlayerQuitListener implements Listener
{

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public PlayerQuitListener(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cache the player's username
        Map<UUID, String> mostRecentPlayerNames = instance.getMostRecentPlayerNames();
        mostRecentPlayerNames.remove(uuid);
        mostRecentPlayerNames.put(uuid, player.getName());
    }

}