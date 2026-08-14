package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import me.Short.TheosisEconomy.ConfigSnapshot;
import me.Short.TheosisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.TheosisEconomy.MessageSender;
import me.Short.TheosisEconomy.MessageType;
import me.Short.TheosisEconomy.TheosisEconomy;
import me.Short.TheosisEconomy.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PayCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.pay"))

                // Send "incorrect usage" message because more arguments are required
                .executes(ctx ->
                {
                    instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                            Placeholder.component("argument_usage", Component.text("<player name> <amount>")));

                    return Command.SINGLE_SUCCESS;
                })

                // Target player argument
                .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                        .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                        {
                            if (ctx.getSource().getSender() instanceof Player senderPlayer)
                            {
                                UUID senderUuid = senderPlayer.getUniqueId();

                                String remainingLowerCase = builder.getRemainingLowerCase();

                                instance.getMostRecentPlayerNamesStore().getMostRecentPlayerNamesSnapshot().entrySet().stream()
                                        .filter(entry -> !senderUuid.equals(entry.getKey()))
                                        .map(Map.Entry::getValue)
                                        .filter(name -> Util.nameMatchesSubstring(remainingLowerCase, name.toLowerCase(Locale.ROOT)))
                                        .forEach(builder::suggest);
                            }

                            return builder.build();
                        }))

                        // Send "incorrect usage" message because more arguments are required
                        .executes(ctx ->
                        {
                            instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("<player name> <amount>")));

                            return Command.SINGLE_SUCCESS;
                        })

                        // Amount argument
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                .executes(ctx ->
                                {
                                    executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), DoubleArgumentType.getDouble(ctx, "amount"));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                ).build();
    }

    // Method to execute the command logic
    private static void executeCommandLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, double amount)
    {
        CommandSender sender = ctx.getSource().getSender();

        // If the sender is not player, return, because only players can pay money
        if (!(sender instanceof Player senderPlayer))
        {
            instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.console-cannot-use");

            return;
        }

        UUID senderUuid = senderPlayer.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        EntityScheduler senderPlayerScheduler = senderPlayer.getScheduler();

        instance.getPlayerAccountManager().transferMoney(senderUuid, targetUuid, BigDecimal.valueOf(amount)).whenComplete((moneyTransfer, throwable) ->
        {
            // If an SQL exception was thrown, log it and send a generic internal error message to the player
            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to process payment from " + senderPlayer.getName() + " (" + senderUuid + ") to " + target.getName() + " (" + targetUuid + ").", throwable);

                instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                switch (moneyTransfer.result())
                {
                    case SUCCESS ->
                    {
                        ConfigSnapshot config = instance.getConfigSnapshot();

                        String targetName = target.getName();

                        BigDecimal amountTransferred = moneyTransfer.amount();
                        BigDecimal senderResultingBalance = moneyTransfer.senderResultingBalance();
                        BigDecimal targetResultingBalanace = moneyTransfer.targetResultingBalance();

                        if (config.getBoolean("settings.logging.pay.log"))
                        {
                            instance.getActivityLogger().log(Level.INFO, config.getString("settings.logging.pay.message")
                                    .replace("<sender>", senderPlayer.getName())
                                    .replace("<sender_uuid>", senderPlayer.getUniqueId().toString())
                                    .replace("<target>", targetName != null ? targetName : targetUuid.toString())
                                    .replace("<target_uuid>", target.getUniqueId().toString())
                                    .replace("<amount>", amountTransferred.toPlainString())
                                    .replace("<sender_balance>", senderResultingBalance.toPlainString())
                                    .replace("<target_balance>", moneyTransfer.targetResultingBalance().toPlainString()));
                        }

                        Component amountTransferredFormatted = Component.text(Util.formatMoney(instance, amountTransferred));

                        MessageSender messageSender = instance.getMessageSender();

                        messageSender.sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.pay.paid-sender",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                                Placeholder.component("amount", amountTransferredFormatted),
                                Placeholder.component("balance", Component.text(Util.formatMoney(instance, senderResultingBalance))));

                        if (target instanceof Player onlineTarget)
                        {
                            messageSender.sendConfigMiniMessage(onlineTarget, MessageType.CHAT, "messages.pay.paid-target",
                                    Placeholder.component("player", senderPlayer.name()),
                                    Placeholder.component("amount", amountTransferredFormatted),
                                    Placeholder.component("balance", Component.text(Util.formatMoney(instance, targetResultingBalanace))));
                        }
                    }

                    case SAME_ACCOUNT -> instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.pay.cannot-pay-self");

                    case ZERO_OR_LESS_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.error.zero-or-less-amount");

                    case TOO_MANY_DECIMAL_PLACES_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.error.too-many-decimal-places-amount",
                            Placeholder.component("decimal_places", Component.text(instance.getDecimalPlaces())));

                    // Shouldn't be possible, because a sender who has ever been online should have an account
                    case SENDER_NOT_FOUND -> instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.error.sender-account-not-found");

                    case TARGET_NOT_FOUND ->
                    {
                        String targetName = target.getName();

                        instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.error.target-account-not-found",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));
                    }

                    case TARGET_NOT_ACCEPTING_PAYMENTS ->
                    {
                        String targetName = target.getName();

                        instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.pay.not-accepting-payments",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));
                    }

                    case INSUFFICIENT_FUNDS -> instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.pay.insufficient-funds");

                    case ABOVE_MAXIMUM_BALANCE ->
                    {
                        String targetName = target.getName();

                        instance.getMessageSender().sendConfigMiniMessage(senderPlayer, MessageType.CHAT, "messages.error.would-exceed-max-balance",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                                Placeholder.component("max_balance", Component.text(Util.formatMoney(instance, BigDecimal.valueOf(instance.getConfigSnapshot().getDouble("settings.currency.max-balance"))))));
                    }
                }
            };

            Runnable fallback = () -> Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);

            if (!senderPlayerScheduler.execute(instance, commandLogic, fallback, 0L))
            {
                fallback.run();
            }
        });
    }

}