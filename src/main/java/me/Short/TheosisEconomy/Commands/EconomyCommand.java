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
import java.util.UUID;
import java.util.logging.Level;

public class EconomyCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy"))

                // Send the sender a message containing information about the command
                .executes(ctx ->
                {
                    instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.economy.help");

                    return Command.SINGLE_SUCCESS;
                })

                // Set sub-command
                .then(Commands.literal("set")

                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.set"))

                        // Send "incorrect usage" message because more arguments are required
                        .executes(ctx ->
                        {
                            instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("set <player name> <amount>")));

                            return Command.SINGLE_SUCCESS;
                        })

                        // Target player argument
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                // Send "incorrect usage" message because more arguments are required
                                .executes(ctx ->
                                {
                                    instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("set <player name> <amount>")));

                                    return Command.SINGLE_SUCCESS;
                                })

                                // Amount argument
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeSetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), DoubleArgumentType.getDouble(ctx, "amount"));

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                // Give sub-command
                .then(Commands.literal("give")

                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.give"))

                        // Send "incorrect usage" message because more arguments are required
                        .executes(ctx ->
                        {
                            instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("give <player name> <amount>")));

                            return Command.SINGLE_SUCCESS;
                        })

                        // Target player argument
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                // Send "incorrect usage" message because more arguments are required
                                .executes(ctx ->
                                {
                                    instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("give <player name> <amount>")));

                                    return Command.SINGLE_SUCCESS;
                                })

                                // Amount argument
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeGiveLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), DoubleArgumentType.getDouble(ctx, "amount"));

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                // Take sub-command
                .then(Commands.literal("take")

                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.take"))

                        // Send "incorrect usage" message because more arguments are required
                        .executes(ctx ->
                        {
                            instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("take <player name> <amount>")));

                            return Command.SINGLE_SUCCESS;
                        })

                        // Target player argument
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                // Send "incorrect usage" message because more arguments are required
                                .executes(ctx ->
                                {
                                    instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("take <player name> <amount>")));

                                    return Command.SINGLE_SUCCESS;
                                })

                                // Amount argument
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeTakeLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), DoubleArgumentType.getDouble(ctx, "amount"));

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                // Reset sub-command
                .then(Commands.literal("reset")

                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.reset"))

                        // Send "incorrect usage" message because more arguments are required
                        .executes(ctx ->
                        {
                            instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.error.incorrect-usage",
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("reset <player name>")));

                            return Command.SINGLE_SUCCESS;
                        })

                        // Target player argument
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    executeResetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                // Reload sub-command
                .then(Commands.literal("reload")

                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.reload"))

                        .executes(ctx ->
                        {
                            executeReloadLogic(instance, ctx);

                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
    }

    // Execute the logic for the "set" sub-command
    private static void executeSetLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, double amount)
    {
        CommandSender sender = ctx.getSource().getSender();

        UUID targetUuid = target.getUniqueId();

        EntityScheduler senderScheduler = sender instanceof Player senderPlayer ? senderPlayer.getScheduler() : null;

        instance.getPlayerAccountManager().setBalance(targetUuid, BigDecimal.valueOf(amount)).whenComplete((balanceChange, throwable) ->
        {
            String targetName = target.getName();

            // If an SQL exception was thrown, log it and send a generic internal error message to the player
            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to set balance of " + (targetName != null ? targetName : targetUuid.toString()) + "(" + targetUuid + ").", throwable);

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                switch (balanceChange.result())
                {
                    case SUCCESS ->
                    {
                        ConfigSnapshot config = instance.getConfigSnapshot();

                        BigDecimal resultingBalance = balanceChange.resultingBalance();

                        // Log
                        if (config.getBoolean("settings.activity-logging.economy-set.log"))
                        {
                            instance.getActivityLogger().log(Level.INFO, config.getString("settings.activity-logging.economy-set.message")
                                    .replace("<player>", targetName != null ? targetName : targetUuid.toString())
                                    .replace("<uuid>", targetUuid.toString())
                                    .replace("<balance>", resultingBalance.toPlainString()));
                        }

                        MessageSender messageSender = instance.getMessageSender();

                        Component resultingBalanceFormatted = Component.text(Util.formatMoney(instance, resultingBalance));

                        // Send message to the command sender
                        messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.set.balance-set-sender",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                                Placeholder.component("amount", resultingBalanceFormatted));

                        // Send message to the target player if online
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null)
                        {
                            messageSender.sendConfigMiniMessage(onlineTarget, MessageType.CHAT, "messages.economy.set.balance-set-target",
                                    Placeholder.component("amount", resultingBalanceFormatted));
                        }
                    }

                    case ACCOUNT_NOT_FOUND -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.target-account-not-found",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    case NEGATIVE_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.negative-amount");

                    case TOO_MANY_DECIMAL_PLACES_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.too-many-decimal-places-amount",
                            Placeholder.component("decimal_places", Component.text(instance.getDecimalPlaces())));

                    case ABOVE_MAXIMUM_BALANCE -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.would-exceed-max-balance",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                            Placeholder.component("max_balance", Component.text(Util.formatMoney(instance, BigDecimal.valueOf(instance.getConfigSnapshot().getDouble("settings.currency.max-balance"))))));
                }
            };

            Runnable fallback = () -> Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);

            if (senderScheduler == null || !senderScheduler.execute(instance, commandLogic, fallback, 0L))
            {
                fallback.run();
            }
        });
    }

    // Execute the logic for the "give" sub-command
    private static void executeGiveLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, double amount)
    {
        CommandSender sender = ctx.getSource().getSender();

        UUID targetUuid = target.getUniqueId();

        EntityScheduler senderScheduler = sender instanceof Player senderPlayer ? senderPlayer.getScheduler() : null;

        instance.getPlayerAccountManager().addToBalance(targetUuid, BigDecimal.valueOf(amount)).whenComplete((balanceChange, throwable) ->
        {
            String targetName = target.getName();

            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to set balance of " + (targetName != null ? targetName : targetUuid.toString()) + "(" + targetUuid + ").", throwable);

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                switch (balanceChange.result())
                {
                    case SUCCESS ->
                    {
                        ConfigSnapshot config = instance.getConfigSnapshot();

                        BigDecimal bdAmount = balanceChange.amount();
                        BigDecimal resultingBalance = balanceChange.resultingBalance();

                        // Log
                        if (config.getBoolean("settings.activity-logging.economy-give.log"))
                        {
                            instance.getActivityLogger().log(Level.INFO, config.getString("settings.activity-logging.economy-give.message")
                                    .replace("<player>", targetName != null ? targetName : targetUuid.toString())
                                    .replace("<uuid>", targetUuid.toString())
                                    .replace("<amount>", bdAmount.toPlainString())
                                    .replace("<balance>", resultingBalance.toPlainString()));
                        }

                        MessageSender messageSender = instance.getMessageSender();

                        Component amountFormatted = Component.text(Util.formatMoney(instance, bdAmount));
                        Component resultingBalanceFormatted = Component.text(Util.formatMoney(instance, resultingBalance));

                        // Send message to the command sender
                        messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.give.money-given-sender",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                                Placeholder.component("amount", amountFormatted),
                                Placeholder.component("balance", resultingBalanceFormatted));

                        // Send message to the target player if online
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null)
                        {
                            messageSender.sendConfigMiniMessage(onlineTarget, MessageType.CHAT, "messages.economy.give.money-given-target",
                                    Placeholder.component("amount", amountFormatted),
                                    Placeholder.component("balance", resultingBalanceFormatted));
                        }
                    }

                    case ACCOUNT_NOT_FOUND -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.target-account-not-found",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    case ZERO_OR_LESS_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.zero-or-less-amount");

                    case TOO_MANY_DECIMAL_PLACES_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.too-many-decimal-places-amount",
                            Placeholder.component("decimal_places", Component.text(instance.getDecimalPlaces())));

                    case ABOVE_MAXIMUM_BALANCE -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.would-exceed-max-balance",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                            Placeholder.component("max_balance", Component.text(Util.formatMoney(instance, BigDecimal.valueOf(instance.getConfigSnapshot().getDouble("settings.currency.max-balance"))))));
                }
            };

            Runnable fallback = () -> Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);

            if (senderScheduler == null || !senderScheduler.execute(instance, commandLogic, fallback, 0L))
            {
                fallback.run();
            }
        });
    }

    // Execute the logic for the "take" sub-command
    private static void executeTakeLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, double amount)
    {
        CommandSender sender = ctx.getSource().getSender();

        UUID targetUuid = target.getUniqueId();

        EntityScheduler senderScheduler = sender instanceof Player senderPlayer ? senderPlayer.getScheduler() : null;

        instance.getPlayerAccountManager().subtractFromBalance(targetUuid, BigDecimal.valueOf(amount)).whenComplete((balanceChange, throwable) ->
        {
            String targetName = target.getName();

            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to set balance of " + (targetName != null ? targetName : targetUuid.toString()) + "(" + targetUuid + ").", throwable);

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                switch (balanceChange.result())
                {
                    case SUCCESS ->
                    {
                        ConfigSnapshot config = instance.getConfigSnapshot();

                        BigDecimal bdAmount = balanceChange.amount();
                        BigDecimal resultingBalance = balanceChange.resultingBalance();

                        // Log
                        if (config.getBoolean("settings.activity-logging.economy-take.log"))
                        {
                            instance.getActivityLogger().log(Level.INFO, config.getString("settings.activity-logging.economy-take.message")
                                    .replace("<player>", targetName != null ? targetName : targetUuid.toString())
                                    .replace("<uuid>", targetUuid.toString())
                                    .replace("<amount>", bdAmount.toPlainString())
                                    .replace("<balance>", resultingBalance.toPlainString()));
                        }

                        MessageSender messageSender = instance.getMessageSender();

                        Component amountFormatted = Component.text(Util.formatMoney(instance, bdAmount));
                        Component resultingBalanceFormatted = Component.text(Util.formatMoney(instance, resultingBalance));

                        // Send message to the command sender
                        messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.take.money-taken-sender",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                                Placeholder.component("amount", amountFormatted),
                                Placeholder.component("balance", resultingBalanceFormatted));

                        // Send message to the target player if online
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null)
                        {
                            messageSender.sendConfigMiniMessage(onlineTarget, MessageType.CHAT, "messages.economy.take.money-taken-target",
                                    Placeholder.component("amount", amountFormatted),
                                    Placeholder.component("balance", resultingBalanceFormatted));
                        }
                    }

                    case ACCOUNT_NOT_FOUND -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.target-account-not-found",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    case ZERO_OR_LESS_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.zero-or-less-amount");

                    case TOO_MANY_DECIMAL_PLACES_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.too-many-decimal-places-amount",
                            Placeholder.component("decimal_places", Component.text(instance.getDecimalPlaces())));

                    case INSUFFICIENT_FUNDS -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.take.insufficient-funds",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                            Placeholder.component("amount", Component.text(Util.formatMoney(instance, balanceChange.amount()))));
                }
            };

            Runnable fallback = () -> Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);

            if (senderScheduler == null || !senderScheduler.execute(instance, commandLogic, fallback, 0L))
            {
                fallback.run();
            }
        });
    }

    // Execute the logic for the "reset" sub-command
    private static void executeResetLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target)
    {
        ConfigSnapshot config = instance.getConfigSnapshot();

        CommandSender sender = ctx.getSource().getSender();

        UUID targetUuid = target.getUniqueId();

        EntityScheduler senderScheduler = sender instanceof Player senderPlayer ? senderPlayer.getScheduler() : null;

        instance.getPlayerAccountManager().setBalance(targetUuid, BigDecimal.valueOf(config.getDouble("settings.currency.default-balance"))).whenComplete((balanceChange, throwable) ->
        {
            String targetName = target.getName();

            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to set balance of " + (targetName != null ? targetName : targetUuid.toString()) + "(" + targetUuid + ").", throwable);

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                switch (balanceChange.result())
                {
                    case SUCCESS ->
                    {
                        BigDecimal resultingBalance = balanceChange.resultingBalance();

                        // Log
                        if (config.getBoolean("settings.activity-logging.economy-reset.log"))
                        {
                            instance.getActivityLogger().log(Level.INFO, config.getString("settings.activity-logging.economy-reset.message")
                                    .replace("<player>", targetName != null ? targetName : targetUuid.toString())
                                    .replace("<uuid>", targetUuid.toString())
                                    .replace("<balance>", resultingBalance.toPlainString()));
                        }

                        MessageSender messageSender = instance.getMessageSender();

                        String resultingBalanceFormatted = Util.formatMoney(instance, resultingBalance);

                        // Send message to the command sender
                        messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.reset.balance-reset-sender",
                                Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())),
                                Placeholder.component("balance", Component.text(resultingBalanceFormatted)));

                        // Send message to the target player if online
                        Player onlineTarget = target.getPlayer();
                        if (onlineTarget != null)
                        {
                            messageSender.sendConfigMiniMessage(onlineTarget, MessageType.CHAT, "messages.economy.reset.balance-reset-target",
                                    Placeholder.component("balance", Component.text(resultingBalanceFormatted)));
                        }
                    }

                    case ACCOUNT_NOT_FOUND -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.target-account-not-found",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    case NEGATIVE_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.reset.negative-default-balance",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    case TOO_MANY_DECIMAL_PLACES_AMOUNT -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.reset.too-many-decimal-places-default-balance",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));

                    case ABOVE_MAXIMUM_BALANCE -> instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.economy.reset.default-balance-exceeds-max-balance",
                            Placeholder.component("target", Component.text(targetName != null ? targetName : targetUuid.toString())));
                }
            };

            Runnable fallback = () -> Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);

            if (senderScheduler == null || !senderScheduler.execute(instance, commandLogic, fallback, 0L))
            {
                fallback.run();
            }
        });
    }

    // Execute the logic for the "reload" sub-command
    private static void executeReloadLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx)
    {
        Bukkit.getGlobalRegionScheduler().execute(instance, () ->
        {
            instance.reload();

            instance.getMessageSender().sendConfigMiniMessage(ctx.getSource().getSender(), MessageType.CHAT, "messages.economy.reload");
        });
    }

}