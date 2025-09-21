package me.Short.TheosisEconomy.Commands;

import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class BalancetopCommand implements TabExecutor
{

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public BalancetopCommand(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,	String label, String[] args)
    {
        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();

        Economy economy = instance.getEconomy();

        Map<String, BigDecimal> baltop = instance.getBaltop();

        if (!baltop.isEmpty())
        {
            int pageLength = config.getInt("settings.baltop.page-length");
            int pages = (int) Math.ceil((double) baltop.size() / (double) pageLength);

            // Get the specified page - set to 1 if one was not specified, or it was not a valid integer
            int specifiedPage;
            if (args.length > 0)
            {
                // Get the page from the command argument, or set to 1 if couldn't parse
                try
                {
                    specifiedPage = Integer.parseInt(args[0]);

                    // Make sure the specified page isn't below 1, and doesn't exceed the number of pages
                    if (specifiedPage < 1)
                    {
                        specifiedPage = 1;
                    }
                    else if (specifiedPage > pages)
                    {
                        specifiedPage = pages;
                    }
                }
                catch (NumberFormatException exception)
                {
                    specifiedPage = 1;
                }
            }
            else
            {
                specifiedPage = 1;
            }

            // Initial output (header)
            Component output = miniMessage.deserialize(config.getString("messages.baltop.header"),
                            Placeholder.component("page", Component.text(specifiedPage)),
                            Placeholder.component("pages", Component.text(pages)),
                            Placeholder.component("total", Component.text(economy.format(instance.getCombinedTotalBalance().doubleValue()))));

            int startPoint = (specifiedPage - 1) * pageLength;

            // Append entries to the output
            List<Entry<String, BigDecimal>> entries = new ArrayList<>(baltop.entrySet());
            for (int i = startPoint; i < startPoint + pageLength && i < entries.size(); i++)
            {
                Entry<String, BigDecimal> entry = entries.get(i);

                Player player = Bukkit.getPlayer(entry.getKey());

                String baltopEntry = config.getString(player != null && player == sender ? "messages.baltop.entry-you" : "messages.baltop.entry");

                if (baltopEntry.contains("<dots>"))
                {
                    output = output.appendNewline().append(miniMessage.deserialize(baltopEntry,
                            Placeholder.component("position", Component.text(i + 1)),
                            Placeholder.component("player", Component.text(entry.getKey())),
                            Placeholder.component("balance", Component.text(economy.format(entry.getValue().doubleValue()))),
                            Placeholder.component("dots", Component.text(new String(new char[instance.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(baltopEntry,
                                    Placeholder.component("position", Component.text(i + 1)),
                                    Placeholder.component("player", Component.text(entry.getKey())),
                                    Placeholder.component("balance", Component.text(economy.format(entry.getValue().doubleValue()))))), sender instanceof Player)]).replace("\0", ".")))));
                }
                else
                {
                    output = output.appendNewline().append(miniMessage.deserialize(baltopEntry,
                            Placeholder.component("position", Component.text(i + 1)),
                            Placeholder.component("player", Component.text(entry.getKey())),
                            Placeholder.component("balance", Component.text(economy.format(entry.getValue().doubleValue())))));
                }
            }

            // Send output
            if (sender instanceof Player)
            {
                ((Player) sender).spigot().sendMessage(instance.getBungeeComponentSerializer().serialize(output));
            }
            else
            {
                sender.sendMessage(instance.getLegacyComponentSerializer().serialize(output));
            }
        }
        else
        {
            // Send the total, along with the "no-baltop-entries" message
            if (sender instanceof Player)
            {

                ((Player) sender).spigot().sendMessage(instance.getBungeeComponentSerializer().serialize(miniMessage.deserialize(config.getString("messages.error.no-baltop-entries"),
                                Placeholder.component("total", Component.text(economy.format(instance.getCombinedTotalBalance().doubleValue()))))));
            }
            else
            {
                sender.sendMessage(instance.getLegacyComponentSerializer().serialize(miniMessage.deserialize(config.getString("messages.error.no-baltop-entries"),
                                Placeholder.component("total", Component.text(economy.format(instance.getCombinedTotalBalance().doubleValue()))))));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1)
        {
            int pages = (int) Math.ceil((double) instance.getBaltop().size() / (double) instance.getConfig().getInt("settings.baltop.page-length"));
            for (int i = 1; i < pages + 1; i++)
            {
                String suggestion = Integer.toString(i);
                if (suggestion.startsWith(args[0]))
                {
                    suggestions.add(suggestion);
                }
            }
        }

        return !suggestions.isEmpty() ? suggestions : null;
    }

}