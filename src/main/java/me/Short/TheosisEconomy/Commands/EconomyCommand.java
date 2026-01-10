package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.TheosisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.TheosisEconomy.PlayerAccount;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;

@NullMarked
public class EconomyCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy"))

                .executes(ctx ->
                {
                    // Send the sender a message containing information about the command
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.help")));

                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.literal("set")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.set"))

                        .executes(ctx ->
                        {
                            // Send the sender a message showing the command usage of this branch
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("set <player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    // Send the sender a message showing the command usage of this branch
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("set <player name> <amount>"))));

                                    return Command.SINGLE_SUCCESS;
                                })

                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeSetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)));

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                .then(Commands.literal("give")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.give"))

                        .executes(ctx ->
                        {
                            // Send the sender a message showing the command usage of this branch
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("give <player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    // Send the sender a message showing the command usage of this branch
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("give <player name> <amount>"))));

                                    return Command.SINGLE_SUCCESS;
                                })

                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeGiveLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)));

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                .then(Commands.literal("take")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.take"))

                        .executes(ctx ->
                        {
                            // Send the sender a message showing the command usage of this branch
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("take <player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    // Send the sender a message showing the command usage of this branch
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("take <player name> <amount>"))));

                                    return Command.SINGLE_SUCCESS;
                                })

                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeTakeLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)));

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                .then(Commands.literal("reset")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.reset"))

                        .executes(ctx ->
                        {
                            // Send the sender a message showing the command usage of this branch
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("reset <player name>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    executeResetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(Commands.literal("reload")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.economy.reload"))

                        .executes(ctx ->
                        {
                            executeReloadLogic(instance, ctx);

                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
    }

    // Method to execute the logic for the "set" sub-command
    private static void executeSetLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

        // If the target player does not have an account, return
        if (!economy.hasAccount(target))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        int decimalPlaces = economy.fractionalDigits();

        // If the amount uses more decimal places than the configured amount, return
        if (amount.scale() > decimalPlaces)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(decimalPlaces))));

            return;
        }

        FileConfiguration config = instance.getConfig();

        // If the amount is greater than the configured maximum balance, return
        if (amount.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(config.getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        MiniMessage miniMessage = instance.getMiniMessage();

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        // Set the player's balance
        account.setBalance(amount);

        // Mark for saving
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String amountFormatted = economy.format(amount.doubleValue());

        // Log the change to the console if config.yml says to do so
        if (config.getBoolean("settings.logging.balance-set.log"))
        {
            instance.getLogger().log(Level.INFO, config.getString("settings.logging.balance-set.message")
                    .replace("<player>", target.getName())
                    .replace("<uuid>", uuid.toString())
                    .replace("<amount>", amount.toPlainString()));
        }

        // Send message to the target player, if online
        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.economy.set.balance-set-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        // Send message to the command sender
        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.set.balance-set-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the logic for the "give" sub-command
    private static void executeGiveLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

        // If the target player does not have an account, return
        if (!economy.hasAccount(target))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        int decimalPlaces = economy.fractionalDigits();

        // If the amount uses more decimal places than the configured amount, return
        if (amount.scale() > decimalPlaces)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(decimalPlaces))));

            return;
        }

        // Try to deposit the amount to the target
        EconomyResponse depositPlayerResponse = economy.depositPlayer(target, amount.doubleValue());

        // If the deposit was unsuccessful, return
        if (!depositPlayerResponse.transactionSuccess())
        {
            String errorMessage = depositPlayerResponse.errorMessage;

            if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorWouldExceedMaxBalance()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                        Placeholder.component("target", Component.text(target.getName()))));
            }
            else if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorTooManyDecimalPlaces()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                        Placeholder.component("amount", Component.text(amount.toPlainString())),
                        Placeholder.component("decimal_places", Component.text(economy.fractionalDigits()))));
            }
            else if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorNotGreaterThanZero()))
            {
                ctx.getSource().getSender().sendMessage(instance.getConfig().getString("messages.error.not-greater-than-zero-amount"));
            }
            else // This should never be able to happen
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>An unknown error occurred when depositing money.</red>"));
            }

            return;
        }

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();

        String amountFormatted = economy.format(depositPlayerResponse.amount);

        // Send message to the target player, if online
        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.economy.give.money-given-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        // Send message to the command sender
        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.give.money-given-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the logic for the "take" sub-command
    private static void executeTakeLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

        // If the target player does not have an account, return
        if (!economy.hasAccount(target))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        int decimalPlaces = economy.fractionalDigits();

        // If the amount uses more decimal places than the configured amount, return
        if (amount.scale() > decimalPlaces)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(decimalPlaces))));

            return;
        }

        double amountAsDouble = amount.doubleValue(); // For passing into the Economy methods, as they only take doubles

        // Try to withdraw the amount from the target
        EconomyResponse withdrawPlayerResponse = economy.withdrawPlayer(target, amountAsDouble);

        if (!withdrawPlayerResponse.transactionSuccess())
        {
            String errorMessage = withdrawPlayerResponse.errorMessage;

            if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorInsufficientFunds()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds-other"),
                        Placeholder.component("target", Component.text(target.getName())),
                        Placeholder.component("amount", Component.text(economy.format(amountAsDouble)))));
            }
            else if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorTooManyDecimalPlaces()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                        Placeholder.component("amount", Component.text(amount.toPlainString())),
                        Placeholder.component("decimal_places", Component.text(economy.fractionalDigits()))));
            }
            else if (errorMessage.equals(me.Short.TheosisEconomy.Economy.getErrorNotGreaterThanZero()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            }
            else // This should never be able to happen
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>An unknown error occurred when withdrawing money.</red>"));
            }

            return;
        }

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();

        String amountFormatted = economy.format(withdrawPlayerResponse.amount);

        // Send message to the target player, if online
        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.economy.take.money-taken-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        // Send message to the command sender
        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.take.money-taken-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the logic for the "reset" sub-command
    private static void executeResetLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();

        // If the target player does not have an account, return
        if (!economy.hasAccount(target))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        BigDecimal defaultBalance = new BigDecimal(instance.getConfig().getString("settings.currency.default-balance")).stripTrailingZeros();

        // If the default balance is negative, return
        if (defaultBalance.compareTo(BigDecimal.ZERO) < 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.negative-default-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // If the default balance uses more decimal places than the configured amount, return
        if (defaultBalance.scale() > economy.fractionalDigits())
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-default-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        FileConfiguration config = instance.getConfig();

        // If the default balance is greater than the configured maximum balance, return
        if (defaultBalance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(config.getString("messages.error.default-balance-exceeds-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        MiniMessage miniMessage = instance.getMiniMessage();

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        // Set the player's balance
        account.setBalance(defaultBalance);

        // Mark for saving
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String defaultBalanceFormatted = economy.format(defaultBalance.doubleValue());

        // Log the change to the console if config.yml says to do so
        if (config.getBoolean("settings.logging.balance-reset.log"))
        {
            instance.getLogger().log(Level.INFO, config.getString("settings.logging.balance-reset.message")
                    .replace("<player>", target.getName())
                    .replace("<uuid>", uuid.toString())
                    .replace("<default_balance>", defaultBalance.toPlainString()));
        }

        // Send message to the target player, if online
        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.economy.reset.balance-reset-target"),
                    Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
        }

        // Send message to the command sender
        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.reset.balance-reset-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
    }

    // Method to execute the logic for the "reload" sub-command
    private static void executeReloadLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx)
    {
        instance.reload();

        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.reload")));
    }

}