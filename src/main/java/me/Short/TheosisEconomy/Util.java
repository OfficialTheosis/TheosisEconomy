package me.Short.TheosisEconomy;

import litebans.api.Database;
import litebans.api.Entry;
import org.bukkit.map.MinecraftFont;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Util
{

    // Method to return the number of dots needed to align the end of a string after "<dots>"
    public static int getNumberOfDotsToAlign(String message, boolean isForPlayer)
    {
        if (isForPlayer)
        {
            return Math.round((130F - MinecraftFont.Font.getWidth(message.substring(0, message.indexOf("<dots>") - 1))) / 2);
        }

        return Math.round((130F - MinecraftFont.Font.getWidth(message.substring(0, message.indexOf("<dots>") - 1))) / 6) + 7;
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

    // Method to check whether a player is banned according to LiteBans
    public static CompletableFuture<Boolean> isPlayerLiteBansPermanentlyBanned(UUID uuid)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            Entry ban = Database.get().getBan(uuid, getPlayerIpFromLiteBansDatabase(uuid), null);
            return ban != null && ban.isPermanent();
        });
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
        catch (SQLException exception)
        {
            exception.printStackTrace();
        }

        return null;
    }

}