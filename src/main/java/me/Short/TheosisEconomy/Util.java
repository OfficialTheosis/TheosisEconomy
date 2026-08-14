package me.Short.TheosisEconomy;

import com.google.common.base.CharMatcher;
import litebans.api.Database;
import litebans.api.Entry;
import org.bukkit.map.MinecraftFont;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Util
{

    private static final CharMatcher matchSplitter = CharMatcher.anyOf("._/");

    // Method to check if a player's username matches a given substring - replicated from vanilla client behaviour: https://mcsrc.dev/1/26.2/net/minecraft/commands/SharedSuggestionProvider#L271
    public static boolean nameMatchesSubstring(String pattern, String input)
    {
        int index = 0;

        while (!input.startsWith(pattern, index))
        {
            int indexOfSplitter = matchSplitter.indexIn(input, index);

            if (indexOfSplitter < 0)
            {
                return false;
            }

            index = indexOfSplitter + 1;
        }

        return true;
    }

    // Method to return the number of dots needed to align the end of the dot sequence
    public static int getNumberOfDotsToAlign(String textBeforeDots, boolean forPlayer, int alignmentWidth)
    {
        if (forPlayer)
        {
            return Math.max(0, Math.round((alignmentWidth - MinecraftFont.Font.getWidth(textBeforeDots)) / 2F));
        }

        return Math.max(0, alignmentWidth - textBeforeDots.length());
    }

    // Method to format an amount of money as per config, and apply comma separation
    public static String formatMoney(TheosisEconomy instance, BigDecimal amount)
    {
        ConfigSnapshot config = instance.getConfigSnapshot();

        amount = amount.stripTrailingZeros();

        int decimalPlaces = instance.getDecimalPlaces();

        return config.getString("settings.currency.format")
                .replace("<amount>", (decimalPlaces > 0 && amount.scale() > 0 ? "%,." + decimalPlaces + "f" : "%,.0f").formatted(amount))
                .replace("<name>", amount.abs().compareTo(BigDecimal.ONE) == 0 ? config.getString("settings.currency.name-singular") : config.getString("settings.currency.name-plural"));
    }

    // Method to round a value to the number of decimal places that the currency is configured to use
    public static BigDecimal round(BigDecimal value, int decimalPlaces, RoundingMode mode)
    {
        if (mode == RoundingMode.ROUND_NEAREST)
        {
            return value.setScale(decimalPlaces, java.math.RoundingMode.HALF_UP);
        }

        if (mode == RoundingMode.ROUND_UP)
        {
            return value.setScale(decimalPlaces, java.math.RoundingMode.UP);
        }

        if (mode == RoundingMode.ROUND_DOWN)
        {
            return value.setScale(decimalPlaces, java.math.RoundingMode.DOWN);
        }

        return value;
    }

    // Method to check whether a player is banned according to LiteBans - only call off the main thread
    public static boolean isPlayerLiteBansPermanentlyBannedSync(UUID uuid)
    {
        Entry ban = Database.get().getBan(uuid, getPlayerIpFromLiteBansDatabase(uuid), null);

        return ban != null && ban.isPermanent();
    }

    // Method to get a player's most recent IP address according to LiteBans' database - only call off the main thread
    public static String getPlayerIpFromLiteBansDatabase(UUID uuid)
    {
        try (PreparedStatement preparedStatement = Database.get().prepareStatement("SELECT ip FROM {history} WHERE uuid=? ORDER BY date DESC LIMIT 1"))
        {
            preparedStatement.setString(1, uuid.toString());

            try (ResultSet resultSet = preparedStatement.executeQuery())
            {
                if (resultSet.next())
                {
                    return resultSet.getString(1);
                }
            }
        }
        catch (SQLException ignored)
        {
            return null;
        }

        return null;
    }

}