package me.Short.TheosisEconomy.Listeners;

import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

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
        UUID uuid = player.getUniqueId();

        // If the player's account is somehow not loaded, kick them
        if (!instance.getPlayerAccountManager().isAccountLoaded(uuid))
        {
            player.kick(instance.getMiniMessage().deserialize(instance.getConfigSnapshot().getString("messages.error.could-not-create-or-load-account")));

            return;
        }

        instance.getMostRecentPlayerNamesStore().add(uuid, player.getName());
    }

}