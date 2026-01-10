package me.Short.TheosisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.TheosisEconomy.TheosisEconomy;
import me.Short.TheosisEconomy.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class BalanceTopCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, TheosisEconomy instance)
    {
        return Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("theosiseconomy.command.balancetop"))

                // Command logic if no page number was specified
                .executes(ctx ->
                {
                    // Execute command logic if no page number was specified
                    executeCommandLogic(instance, ctx, 1);

                    return Command.SINGLE_SUCCESS;
                })

                // Specified page number argument
                .then(Commands.argument("page number", IntegerArgumentType.integer())

                        // Send all valid page numbers as suggestions
                        .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                        {
                            for (int i = 1; i <= calculateBaltopPages(instance); i++)
                            {
                                if (Integer.toString(i).startsWith(builder.getRemaining()))
                                {
                                    builder.suggest(i);
                                }
                            }

                            return builder.build();
                        }))

                        // Command logic if a page number was specified
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
        final CommandSender sender = ctx.getSource().getSender();

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();

        Economy economy = instance.getEconomy();

        Map<UUID, BigDecimal> baltop = instance.getBaltop();

        // If the baltop is empty, tell the command sender, and return
        if (baltop.isEmpty())
        {
            sender.sendMessage(miniMessage.deserialize(config.getString("messages.error.no-baltop-entries"),
                    Placeholder.component("total", Component.text(economy.format(instance.getCombinedTotalBalance().doubleValue())))));

            return;
        }

        int pageLength = config.getInt("settings.baltop.page-length");
        int pages = calculateBaltopPages(instance);

        // Make sure the specified page isn't below 1, and doesn't exceed the number of pages
        if (pageNumber < 1)
        {
            pageNumber = 1;
        }
        else if (pageNumber > pages)
        {
            pageNumber = pages;
        }

        // Initial output (header)
        Component output = miniMessage.deserialize(config.getString("messages.baltop.header"),
                Placeholder.component("page", Component.text(pageNumber)),
                Placeholder.component("pages", Component.text(pages)),
                Placeholder.component("total", Component.text(economy.format(instance.getCombinedTotalBalance().doubleValue()))));

        int startPoint = (pageNumber - 1) * pageLength;

        // Append entries to the output
        List<Map.Entry<UUID, BigDecimal>> entries = new ArrayList<>(baltop.entrySet());
        for (int i = startPoint; i < startPoint + pageLength && i < entries.size(); i++)
        {
            Map.Entry<UUID, BigDecimal> entry = entries.get(i);

            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());

            String baltopEntry = config.getString(player == sender ? "messages.baltop.entry-you" : "messages.baltop.entry");

            if (baltopEntry.contains("<dots>"))
            {
                output = output.appendNewline().append(miniMessage.deserialize(baltopEntry,
                        Placeholder.component("position", Component.text(i + 1)),
                        Placeholder.component("player", Component.text(player.getName())),
                        Placeholder.component("balance", Component.text(economy.format(entry.getValue().doubleValue()))),
                        Placeholder.component("dots", Component.text(new String(new char[Util.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(baltopEntry,
                                Placeholder.component("position", Component.text(i + 1)),
                                Placeholder.component("player", Component.text(player.getName())),
                                Placeholder.component("balance", Component.text(economy.format(entry.getValue().doubleValue()))))), sender instanceof Player)]).replace("\0", ".")))));
            }
            else
            {
                output = output.appendNewline().append(miniMessage.deserialize(baltopEntry,
                        Placeholder.component("position", Component.text(i + 1)),
                        Placeholder.component("player", Component.text(player.getName())),
                        Placeholder.component("balance", Component.text(economy.format(entry.getValue().doubleValue())))));
            }
        }

        // Send output
        sender.sendMessage(output);
    }

    // Method to calculate the number of pages in the baltop
    private static int calculateBaltopPages(TheosisEconomy instance)
    {
        return (int) Math.ceil((double) instance.getBaltop().size() / (double) instance.getConfig().getInt("settings.baltop.page-length"));
    }

}