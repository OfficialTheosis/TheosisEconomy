package me.Short.TheosisEconomy.Listeners;

import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
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
            player.kick(Component.text("Your economy account could not be loaded, or one could not be created for you. Please try again."));

            return;
        }

        instance.getMostRecentPlayerNamesStore().add(uuid, player.getName());
    }

}