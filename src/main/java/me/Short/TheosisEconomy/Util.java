package me.Short.TheosisEconomy;

import org.bukkit.map.MinecraftFont;

import java.math.BigDecimal;

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

}