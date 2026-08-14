package me.Short.TheosisEconomy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class PlaceholderAPI extends PlaceholderExpansion
{

    private final TheosisEconomy instance;

    public PlaceholderAPI(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @Override
    public @NotNull String getIdentifier()
    {
        return instance.getPluginMeta().getName();
    }

    @Override
    public @NotNull String getAuthor()
    {
        List<String> authors = instance.getPluginMeta().getAuthors();

        if (authors.isEmpty())
        {
            return "";
        }

        return String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion()
    {
        return instance.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist()
    {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params)
    {
        // %theosiseconomy_accepting_payments%
        if (params.equalsIgnoreCase("accepting_payments"))
        {
            if (player == null)
            {
                return null;
            }

            Boolean acceptingPayments = instance.getPlayerAccountManager().getLoadedAccountAcceptingPayments(player.getUniqueId());

            if (acceptingPayments == null)
            {
                return null;
            }

            return Boolean.toString(acceptingPayments);
        }

        // %theosiseconomy_richest_<position>_name%
        if (Pattern.compile("richest_[1-9][0-9]*_name$", Pattern.CASE_INSENSITIVE).matcher(params).find())
        {
            try
            {
                int position = Integer.parseInt(StringUtils.replaceOnceIgnoreCase(StringUtils.replaceOnceIgnoreCase(params, "richest_", ""), "_name", ""));

                List<BalanceTopEntry> cachedBalanceTopEntries = instance.getPlayerAccountManager().getCachedBalanceTopEntries();

                if (position > cachedBalanceTopEntries.size())
                {
                    return instance.getConfigSnapshot().getString("settings.placeholders.balancetop-position-name-none");
                }

                return Bukkit.getOfflinePlayer(cachedBalanceTopEntries.get(position - 1).uuid()).getName();
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        // %theosiseconomy_richest_<position>_uuid%
        if (Pattern.compile("richest_[1-9][0-9]*_uuid$", Pattern.CASE_INSENSITIVE).matcher(params).find())
        {
            try
            {
                int position = Integer.parseInt(StringUtils.replaceOnceIgnoreCase(StringUtils.replaceOnceIgnoreCase(params, "richest_", ""), "_uuid", ""));

                List<BalanceTopEntry> cachedBalanceTopEntries = instance.getPlayerAccountManager().getCachedBalanceTopEntries();

                if (position > cachedBalanceTopEntries.size())
                {
                    instance.getConfigSnapshot().getString("settings.placeholders.balancetop-position-uuid-none");
                }

                return cachedBalanceTopEntries.get(position - 1).uuid().toString();
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        // %theosiseconomy_richest_<position>_balance%
        if (Pattern.compile("richest_[1-9][0-9]*_balance$", Pattern.CASE_INSENSITIVE).matcher(params).find())
        {
            try
            {
                int position = Integer.parseInt(StringUtils.replaceOnceIgnoreCase(StringUtils.replaceOnceIgnoreCase(params, "richest_", ""), "_balance", ""));

                List<BalanceTopEntry> cachedBalanceTopEntries = instance.getPlayerAccountManager().getCachedBalanceTopEntries();

                if (position > cachedBalanceTopEntries.size())
                {
                    return instance.getConfigSnapshot().getString("settings.placeholders.balancetop-position-balance-none");
                }

                return cachedBalanceTopEntries.get(position - 1).balance().toPlainString();
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        // %theosiseconomy_richest_<position>_balance_formatted%
        if (Pattern.compile("richest_[1-9][0-9]*_balance_formatted$", Pattern.CASE_INSENSITIVE).matcher(params).find())
        {
            try
            {
                int position = Integer.parseInt(StringUtils.replaceOnceIgnoreCase(StringUtils.replaceOnceIgnoreCase(params, "richest_", ""), "_balance_formatted", ""));

                List<BalanceTopEntry> cachedBalanceTopEntries = instance.getPlayerAccountManager().getCachedBalanceTopEntries();

                if (position > cachedBalanceTopEntries.size())
                {
                    return instance.getConfigSnapshot().getString("settings.placeholders.balancetop-position-balance_formatted-none");
                }

                return Util.formatMoney(instance, cachedBalanceTopEntries.get(position - 1).balance());
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        // %theosiseconomy_richest_<position>_entry%
        if (Pattern.compile("richest_[1-9][0-9]*_entry$", Pattern.CASE_INSENSITIVE).matcher(params).find())
        {
            try
            {
                int position = Integer.parseInt(StringUtils.replaceOnceIgnoreCase(StringUtils.replaceOnceIgnoreCase(params, "richest_", ""), "_entry", ""));

                List<BalanceTopEntry> cachedBalanceTopEntries = instance.getPlayerAccountManager().getCachedBalanceTopEntries();

                if (position > cachedBalanceTopEntries.size())
                {
                    return instance.getConfigSnapshot().getString("settings.placeholders.balancetop-position-entry-none");
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                BalanceTopEntry entry = cachedBalanceTopEntries.get(position - 1);

                UUID entryPlayerUuid = entry.uuid();

                String entryPlayerName = Bukkit.getOfflinePlayer(entryPlayerUuid).getName();

                String entryFormatted = config.getString(entryPlayerUuid.equals(player != null ? player.getUniqueId() : null) ? "settings.balancetop.entry-format-sender" : "settings.balancetop.entry-format")
                        .replace("<position>", String.format("%,d", position))
                        .replace("<player>", entryPlayerName != null ? entryPlayerName : entryPlayerUuid.toString())
                        .replace("<balance>", Util.formatMoney(instance, entry.balance()));

                int dotsPlaceholderIndex = entryFormatted.indexOf("<dots>");

                if (dotsPlaceholderIndex != -1)
                {
                    return entryFormatted.replace("<dots>", ".".repeat(Util.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(instance.getMiniMessage().deserialize(entryFormatted.substring(0, dotsPlaceholderIndex))), true, config.getInt("settings.balancetop.entry-dot-alignment-width.player"))));
                }

                return entryFormatted;
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        // %theosiseconomy_richest_<position>_entry_legacy%
        if (Pattern.compile("richest_[1-9][0-9]*_entry_legacy$", Pattern.CASE_INSENSITIVE).matcher(params).find())
        {
            try
            {
                int position = Integer.parseInt(StringUtils.replaceOnceIgnoreCase(StringUtils.replaceOnceIgnoreCase(params, "richest_", ""), "_entry_legacy", ""));

                List<BalanceTopEntry> cachedBalanceTopEntries = instance.getPlayerAccountManager().getCachedBalanceTopEntries();

                if (position > cachedBalanceTopEntries.size())
                {
                    return instance.getConfigSnapshot().getString("settings.placeholders.balancetop-position-entry-legacy-none");
                }

                ConfigSnapshot config = instance.getConfigSnapshot();

                BalanceTopEntry entry = cachedBalanceTopEntries.get(position - 1);

                UUID entryPlayerUuid = entry.uuid();

                String entryPlayerName = Bukkit.getOfflinePlayer(entryPlayerUuid).getName();

                String entryFormatted = config.getString(entryPlayerUuid.equals(player != null ? player.getUniqueId() : null) ? "settings.balancetop.entry-format-sender" : "settings.balancetop.entry-format")
                        .replace("<position>", String.format("%,d", position))
                        .replace("<player>", entryPlayerName != null ? entryPlayerName : entryPlayerUuid.toString())
                        .replace("<balance>", Util.formatMoney(instance, entry.balance()));

                int dotsPlaceholderIndex = entryFormatted.indexOf("<dots>");

                if (dotsPlaceholderIndex != -1)
                {
                    MiniMessage miniMessage = instance.getMiniMessage();

                    return instance.getLegacyComponentSerializer().serialize(miniMessage.deserialize(entryFormatted.replace("<dots>", ".".repeat(Util.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(entryFormatted.substring(0, dotsPlaceholderIndex))), true, config.getInt("settings.balancetop.entry-dot-alignment-width.player"))))));
                }

                return instance.getLegacyComponentSerializer().serialize(instance.getMiniMessage().deserialize(entryFormatted));
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }

        return null;
    }

}