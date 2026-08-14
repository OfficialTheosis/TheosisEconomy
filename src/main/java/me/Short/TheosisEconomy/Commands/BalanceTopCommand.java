package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import me.Short.TheosisEconomy.BalanceTopEntry;
import me.Short.TheosisEconomy.ConfigSnapshot;
import me.Short.TheosisEconomy.MessageType;
import me.Short.TheosisEconomy.TheosisEconomy;
import me.Short.TheosisEconomy.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class BalanceTopCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.balancetop"))

                // No page number specified, so pass 1
                .executes(ctx ->
                {
                    executeCommandLogic(instance, ctx, 1);

                    return Command.SINGLE_SUCCESS;
                })

                // Page number argument
                .then(Commands.argument("page number", IntegerArgumentType.integer(1))

                        .executes(ctx ->
                        {
                            // Execute command logic if a page number was specified
                            executeCommandLogic(instance, ctx, IntegerArgumentType.getInteger(ctx, "page number"));

                            return Command.SINGLE_SUCCESS;
                        })
                ).build();
    }

    // Method to execute the command logic
    private static void executeCommandLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, int pageNumber)
    {
        CommandSender sender = ctx.getSource().getSender();

        EntityScheduler senderScheduler = sender instanceof Player senderPlayer ? senderPlayer.getScheduler() : null;

        instance.getPlayerAccountManager().getBalanceTop(pageNumber).whenComplete((balanceTopPage, throwable) ->
        {
            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to get top balances.", throwable);

                instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                List<BalanceTopEntry> entries = balanceTopPage.entries();

                // If there are no entries, return
                if (entries.isEmpty())
                {
                    instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.balancetop.no-entries");

                    return;
                }

                ConfigSnapshot config = instance.getConfigSnapshot();
                MiniMessage miniMessage = instance.getMiniMessage();

                int entriesPerPage = config.getInt("settings.balancetop.entries-per-page");

                int page = balanceTopPage.page();

                // Initial output (header)
                Component output = miniMessage.deserialize(config.getString("settings.balancetop.header-format"),
                        Placeholder.component("page", Component.text(page)));

                UUID senderUuid = sender instanceof Player senderPlayer ? senderPlayer.getUniqueId() : null;

                for (int i = 0; i < entries.size(); i++)
                {
                    BalanceTopEntry entry = entries.get(i);

                    UUID entryUuid = entry.uuid();

                    String playerName = Bukkit.getOfflinePlayer(entryUuid).getName();

                    String entryFormat = config.getString(entryUuid.equals(senderUuid) ? "settings.balancetop.entry-format-sender" : "settings.balancetop.entry-format");

                    TagResolver positionPlaceholder = Placeholder.component("position", Component.text((long) (page - 1) * entriesPerPage + i + 1));
                    TagResolver playerPlaceholder = Placeholder.component("player", Component.text(playerName != null ? playerName : entryUuid.toString()));
                    TagResolver balancePlaceholder = Placeholder.component("balance", Component.text(Util.formatMoney(instance, entry.balance())));

                    int dotsPlaceholderIndex = entryFormat.indexOf("<dots>");

                    if (dotsPlaceholderIndex != -1)
                    {
                        boolean forPlayer = senderUuid != null;

                        output = output.appendNewline().append(miniMessage.deserialize(
                                entryFormat,
                                positionPlaceholder,
                                playerPlaceholder,
                                balancePlaceholder,
                                Placeholder.component("dots", Component.text(".".repeat(Util.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(entryFormat.substring(0, dotsPlaceholderIndex),
                                        positionPlaceholder,
                                        playerPlaceholder,
                                        balancePlaceholder)), forPlayer, config.getInt(forPlayer ? "settings.balancetop.entry-dot-alignment-width.player" : "settings.balancetop.entry-dot-alignment-width.console")))))));
                    }
                    else
                    {
                        output = output.appendNewline().append(miniMessage.deserialize(entryFormat,
                                positionPlaceholder,
                                playerPlaceholder,
                                balancePlaceholder));
                    }
                }

                // Send output
                instance.getMessageSender().sendMessage(sender, MessageType.CHAT, output);
            };

            Runnable fallback = () -> Bukkit.getGlobalRegionScheduler().execute(instance, commandLogic);

            if (senderScheduler == null || !senderScheduler.execute(instance, commandLogic, fallback, 0L))
            {
                fallback.run();
            }
        });
    }

}