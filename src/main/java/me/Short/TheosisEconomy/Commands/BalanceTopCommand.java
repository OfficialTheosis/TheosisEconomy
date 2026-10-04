package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.TheosisEconomy.BalanceTopEntry;
import me.Short.TheosisEconomy.BalanceTopPage;
import me.Short.TheosisEconomy.ConfigSnapshot;
import me.Short.TheosisEconomy.MessageSender;
import me.Short.TheosisEconomy.MessageType;
import me.Short.TheosisEconomy.PlayerAccountManager;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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

    // Execute the command logic
    private static void executeCommandLogic(TheosisEconomy instance, final CommandContext<CommandSourceStack> ctx, int pageNumber)
    {
        CommandSender sender = ctx.getSource().getSender();

        PlayerAccountManager playerAccountManager = instance.getPlayerAccountManager();

        UUID senderUuid = sender instanceof Player senderPlayer ? senderPlayer.getUniqueId() : null;

        Set<UUID> activeBalanceTopRequests = playerAccountManager.getActiveBalanceTopRequests();

        // If the player already has a BalanceTop request in progress, return
        if (senderUuid != null && !activeBalanceTopRequests.add(senderUuid))
        {
            instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.balancetop.request-in-progress");

            return;
        }

        boolean senderShouldBypassRequestLimit = sender.hasPermission("theosiseconomy.balancetop.bypassrequestlimit");

        Semaphore balanceTopRequestPermits = playerAccountManager.getBalanceTopRequestPermits();

        // If there are too many simultaneous BalanceTop requests in progress, return
        if (!senderShouldBypassRequestLimit && !balanceTopRequestPermits.tryAcquire())
        {
            if (senderUuid != null)
            {
                activeBalanceTopRequests.remove(senderUuid);
            }

            instance.getMessageSender().sendConfigMiniMessage(sender, MessageType.CHAT, "messages.balancetop.too-many-simultaneous-requests-in-progress");

            return;
        }

        MessageSender messageSender = instance.getMessageSender();

        messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.balancetop.fetching");

        CompletableFuture<BalanceTopPage> getBalanceTopPageFuture;

        try
        {
            getBalanceTopPageFuture = playerAccountManager.getBalanceTop(pageNumber);
        }
        catch (RuntimeException e)
        {
            if (senderUuid != null)
            {
                activeBalanceTopRequests.remove(senderUuid);
            }

            if (!senderShouldBypassRequestLimit)
            {
                balanceTopRequestPermits.release();
            }

            instance.getLogger().log(Level.SEVERE, "Failed to start balance top page lookup.", e);

            messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

            return;
        }

        getBalanceTopPageFuture.whenComplete((balanceTopPage, throwable) ->
        {
            if (senderUuid != null)
            {
                activeBalanceTopRequests.remove(senderUuid);
            }

            if (!senderShouldBypassRequestLimit)
            {
                balanceTopRequestPermits.release();
            }

            if (throwable != null)
            {
                instance.getLogger().log(Level.SEVERE, "Failed to get top balances.", throwable);

                messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.error.internal");

                return;
            }

            Runnable commandLogic = () ->
            {
                List<BalanceTopEntry> entries = balanceTopPage.entries();

                // If there are no entries, return
                if (entries.isEmpty())
                {
                    messageSender.sendConfigMiniMessage(sender, MessageType.CHAT, "messages.balancetop.no-entries");

                    return;
                }

                ConfigSnapshot config = instance.getConfigSnapshot();
                MiniMessage miniMessage = instance.getMiniMessage();

                int page = balanceTopPage.page();

                // Initial output (header)
                Component output = miniMessage.deserialize(config.getString("settings.balancetop.header-format"),
                        Placeholder.component("page", Component.text(String.format("%,d", page))));

                for (int i = 0; i < entries.size(); i++)
                {
                    BalanceTopEntry entry = entries.get(i);

                    UUID entryUuid = entry.uuid();

                    String playerName = Bukkit.getOfflinePlayer(entryUuid).getName();

                    String entryFormat = config.getString(entryUuid.equals(senderUuid) ? "settings.balancetop.entry-format-sender" : "settings.balancetop.entry-format");

                    TagResolver positionPlaceholder = Placeholder.component("position", Component.text(String.format("%,d", balanceTopPage.startPosition() + i)));
                    TagResolver playerPlaceholder = Placeholder.component("player", Component.text(playerName != null ? playerName : entryUuid.toString()));
                    TagResolver balancePlaceholder = Placeholder.component("balance", Component.text(Util.formatMoney(instance, entry.balance())));

                    int dotsPlaceholderIndex = entryFormat.indexOf("<dots>");

                    if (dotsPlaceholderIndex != -1)
                    {
                        boolean forPlayer = senderUuid != null;

                        output = output.appendNewline().append(miniMessage.deserialize(entryFormat, positionPlaceholder, playerPlaceholder, balancePlaceholder,
                                Placeholder.component("dots", Component.text(".".repeat(Util.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(entryFormat.substring(0, dotsPlaceholderIndex), positionPlaceholder, playerPlaceholder, balancePlaceholder)), forPlayer, config.getInt(forPlayer ? "settings.balancetop.entry-dot-alignment-width.players" : "settings.balancetop.entry-dot-alignment-width.console")))))));
                    }
                    else
                    {
                        output = output.appendNewline().append(miniMessage.deserialize(entryFormat, positionPlaceholder, playerPlaceholder, balancePlaceholder));
                    }
                }

                // Send output
                messageSender.sendMessage(sender, MessageType.CHAT, output);
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