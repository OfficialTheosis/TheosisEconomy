package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.TheosisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.TheosisEconomy.MessageType;
import me.Short.TheosisEconomy.TheosisEconomy;
import me.Short.TheosisEconomy.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

public class BalanceCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, final TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.balance"))

                .executes(ctx ->
                {
                    executeCommandLogic(instance, ctx.getSource().getSender(), null);

                    return Command.SINGLE_SUCCESS;
                })

                // Target player argument
                .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.balance.others"))

                        .executes(ctx ->
                        {
                            executeCommandLogic(instance, ctx.getSource().getSender(), ctx.getArgument("target player", OfflinePlayer.class));

                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
    }

    // Execute the command logic
    private static void executeCommandLogic(TheosisEconomy instance, CommandSender sender, OfflinePlayer target)
    {
        if (target == null)
        {
            if (!(sender instanceof Player))
            {
                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.console-cannot-use");

                return;
            }

            target = (Player) sender;
        }

        UUID targetUuid = target.getUniqueId();
        String targetName = target.getName();

        instance.getPlayerAccountManager().getBalance(targetUuid).whenComplete((balance, throwable) ->
        {
            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to get balance of " + targetName + "(" + targetUuid + ").", throwable);

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                // If the balance is null, it means the target player does not have an account, so return
                if (balance == null)
                {
                    instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.target-account-not-found",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    return;
                }

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, sender instanceof Player senderPlayer && senderPlayer.getUniqueId().equals(targetUuid) ? "messages.balance.your-balance" : "messages.balance.their-balance",
                        Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                        Placeholder.component("balance", Component.text(Util.formatMoney(instance, balance))));
            };

            if (sender instanceof Player senderPlayer)
            {
                senderPlayer.getScheduler().execute(instance, commandLogic, null, 1L);
            }
            else
            {
                Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);
            }
        });
    }

}