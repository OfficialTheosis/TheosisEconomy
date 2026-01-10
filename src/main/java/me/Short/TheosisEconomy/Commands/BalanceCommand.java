package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.TheosisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class BalanceCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.balance"))

                .executes(ctx ->
                {
                    // Execute command logic if no target player was specified
                    executeCommandLogic(instance, ctx, null);

                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.balance.others"))

                        // Command logic if a target player was specified
                        .executes(ctx ->
                        {
                            // Execute command logic
                            executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class));

                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
    }

    // Method to execute the command logic
    private static void executeCommandLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, @Nullable OfflinePlayer target)
    {
        final CommandSender sender = ctx.getSource().getSender();

        if (target == null)
        {
            if (!(sender instanceof Player))
            {
                sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));

                return;
            }

            target = (Player) sender;
        }

        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

        // If the target player does not have an account, return
        if (!economy.hasAccount(target))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(target != sender ? "messages.error.no-account-other" : "messages.error.no-account"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // Send message to the command sender telling them the target's balance
        sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(target != sender ? "messages.balance.their-balance" : "messages.balance.your-balance"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("balance", Component.text(economy.format(economy.getBalance(target))))));
    }

}