package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.TheosisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.TheosisEconomy.Events.PlayerPayPlayerEvent;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class PayCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.pay"))

                .executes(ctx ->
                {
                    // Send the sender a message showing the command usage of this branch
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                            Placeholder.component("argument_usage", Component.text("<player name> <amount>"))));

                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                        .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                        {
                            if (ctx.getSource().getSender() instanceof Player senderPlayer)
                            {
                                instance.getOfflinePlayerNames().values().stream()
                                        .filter(name -> !name.equals(senderPlayer.getName()) && name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                        .forEach(builder::suggest);
                            }
                            else
                            {
                                instance.getOfflinePlayerNames().values().stream()
                                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                        .forEach(builder::suggest);
                            }

                            return builder.build();
                        }))

                        .executes(ctx ->
                        {
                            // Send the sender a message showing the command usage of this branch
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("<player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                .executes(ctx ->
                                {
                                    executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                ).build();
    }

    // Method to execute the command logic
    private static void executeCommandLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        final CommandSender sender = ctx.getSource().getSender();

        // If the sender is not player, return, because only players can pay money
        if (!(sender instanceof Player senderPlayer))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));

            return;
        }

        // If the target player is the sender, return, because players cannot pay themselves
        if (target == sender)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.cannot-pay-yourself")));

            return;
        }

        Economy economy = instance.getEconomy();

        // If the sender does not have an account, return
        if (!economy.hasAccount(senderPlayer))
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account")));

            return;
        }

        // If the target player does not have an account, return
        if (!economy.hasAccount(target))
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // If the target player is not accepting payments, return
        if (!instance.getPlayerAccounts().get(target.getUniqueId()).getAcceptingPayments())
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-accepting-payments"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // Call PlayerPayPlayerEvent event
        PlayerPayPlayerEvent playerPayPlayerEvent = new PlayerPayPlayerEvent(senderPlayer, target, amount);
        Bukkit.getServer().getPluginManager().callEvent(playerPayPlayerEvent);

        // If the event is cancelled, return
        if (playerPayPlayerEvent.isCancelled())
        {
            return;
        }

        // Reassign variables in case a plugin listening for the event changed them
        senderPlayer = playerPayPlayerEvent.getSender();
        target = playerPayPlayerEvent.getRecipient();
        amount = playerPayPlayerEvent.getAmount();

        double amountAsDouble = amount.doubleValue(); // For passing into the Economy methods, as they only take doubles

        // If the sender does not have enough money to pay the amount specified, return
        if (!economy.has(senderPlayer, amountAsDouble))
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds"),
                    Placeholder.component("amount", Component.text(economy.format(amountAsDouble)))));

            return;
        }

        // Try to withdraw the amount from the sender
        EconomyResponse withdrawPlayerResponse = economy.withdrawPlayer(senderPlayer, amountAsDouble);

        // If the withdrawal was unsuccessful, return
        if (!withdrawPlayerResponse.transactionSuccess())
        {
            String errorMessage = withdrawPlayerResponse.errorMessage;

            if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorTooManyDecimalPlaces()))
            {
                senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                        Placeholder.component("amount", Component.text(amount.toPlainString())),
                        Placeholder.component("decimal_places", Component.text(economy.fractionalDigits()))));
            }
            else if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorNotGreaterThanZero()))
            {
                senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            }
            else if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorInsufficientFunds())) // This should never be the case, since we already checked to make sure the sender has enough money
            {
                senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds"),
                        Placeholder.component("amount", Component.text(economy.format(amountAsDouble)))));
            }
            else // This should also never be able to happen
            {
                senderPlayer.sendMessage(instance.getMiniMessage().deserialize("<red>An unknown error occurred when withdrawing money.</red>"));
            }

            return;
        }

        // Try to deposit the amount to the target
        EconomyResponse depositPlayerResponse = economy.depositPlayer(target, amountAsDouble);

        // If the deposit was unsuccessful, deposit the amount back to the sender's balance, and return
        if (!depositPlayerResponse.transactionSuccess())
        {
            // Deposit the amount back into the sender's account
            economy.depositPlayer(senderPlayer, amountAsDouble);

            // Send error message - this should be the only possible error, since the other errors were ruled out due to the withdrawal being successful
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();

        String amountFormatted = economy.format(amountAsDouble);

        // Send message to the target player, if online
        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.pay.paid-target"),
                    Placeholder.component("player", Component.text(senderPlayer.getName())),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        // Send message to the command sender
        senderPlayer.sendMessage(miniMessage.deserialize(config.getString("messages.pay.paid-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

}