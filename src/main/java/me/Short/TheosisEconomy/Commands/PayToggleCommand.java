package me.Short.TheosisEconomy.Commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.Short.TheosisEconomy.PlayerAccount;
import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public class PayToggleCommand implements BasicCommand
{

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public PayToggleCommand(TheosisEconomy mainInstance)
    {
        this.instance = mainInstance;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args)
    {
        CommandSender sender = commandSourceStack.getSender();

        // If the sender is not a player, return, because books can only be opened for players
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));

            return;
        }

        // If the player does not have an account, return
        if (!instance.getEconomy().hasAccount((player)))
        {
            player.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account")));

            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        if (account.getAcceptingPayments())
        {
            // Toggle the setting in the player's account
            account.setAcceptingPayments(false);

            // Mark for saving
            instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

            // Send message
            player.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.paytoggle.disabled")));
        }
        else
        {
            // Toggle the setting in the player's account
            account.setAcceptingPayments(true);

            // Mark for saving
            instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

            // Send message
            player.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.paytoggle.enabled")));
        }
    }

    @Override
    public @Nullable String permission()
    {
        return "theosiseconomy.command.paytoggle";
    }
}