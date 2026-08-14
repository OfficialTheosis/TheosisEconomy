package me.Short.TheosisEconomy.Commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.Short.TheosisEconomy.MessageType;
import me.Short.TheosisEconomy.TheosisEconomy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public class PayToggleCommand implements BasicCommand
{

    private final TheosisEconomy instance;

    public PayToggleCommand(TheosisEconomy mainInstance)
    {
        this.instance = mainInstance;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args)
    {
        CommandSender sender = commandSourceStack.getSender();

        // If the sender is not a player, return, because only players can toggle whether they want to receive payments
        if (!(sender instanceof Player player))
        {
            instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.console-cannot-use");

            return;
        }

        Boolean newAcceptingPaymentsState = instance.getPlayerAccountManager().toggleLoadedAccountAcceptingPayments(player.getUniqueId());

        // If the state is somehow null, that means the sender's account is somehow not loaded, so return
        if (newAcceptingPaymentsState == null)
        {
            instance.getMessageSender().sendConfigMiniMessage(player, MessageType.CHAT, "messages.error.sender-account-not-found");

            return;
        }

        // Send confirmation message
        instance.getMessageSender().sendConfigMiniMessage(player, MessageType.CHAT, newAcceptingPaymentsState ? "messages.paytoggle.enabled" : "messages.paytoggle.disabled");
    }

    @Override
    public @Nullable String permission()
    {
        return "theosiseconomy.command.paytoggle";
    }
}