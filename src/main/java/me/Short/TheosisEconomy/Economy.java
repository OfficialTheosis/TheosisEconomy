package me.Short.TheosisEconomy;

import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class Economy implements net.milkbowl.vault.economy.Economy
{

    // EconomyResponse error messages
    private static final String ERROR_INSUFFICIENT_FUNDS = "Insufficient funds.";
    private static final String ERROR_WOULD_EXCEED_MAX_BALANCE = "Would exceed the configured maximum balance.";
    private static final String ERROR_TOO_MANY_DECIMAL_PLACES = "Too many decimal places.";
    private static final String ERROR_NOT_GREATER_THAN_ZERO = "Amount is not greater than zero.";

    // Instance of "TheosisEconomy"
    private TheosisEconomy instance;

    // Constructor
    public Economy(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    @Override
    public boolean isEnabled()
    {
        return instance.isEnabled();
    }

    @Override
    public String getName()
    {
        return instance.getName();
    }

    @Override
    public boolean hasBankSupport()
    {
        return false;
    }

    @Override
    public int fractionalDigits()
    {
        return instance.getConfig().getInt("settings.currency.decimal-places");
    }

    @Override
    public String format(double amount)
    {
        int fractionalDigits = fractionalDigits();

        // Determine decimal format based on the configured number of decimal places ("fractionalDigits")
        DecimalFormat df;
        if (fractionalDigits > 0 && amount % 1 != 0)
        {
            df = new DecimalFormat("#,##0." + StringUtils.repeat("0", fractionalDigits));
        }
        else
        {
            df = new DecimalFormat("#,##0");
        }

        // Return formatted output, applying decimal format to the amount
        FileConfiguration config = instance.getConfig();
        return config.getString("settings.currency.format")
                .replace("<amount>", df.format(amount))
                .replace("<name>", amount == 1D ? currencyNameSingular() : currencyNamePlural());
    }

    @Override
    public String currencyNamePlural()
    {
        return instance.getConfig().getString("settings.currency.name-plural");
    }

    @Override
    public String currencyNameSingular()
    {
        return instance.getConfig().getString("settings.currency.name-singular");
    }

    @Override
    public boolean hasAccount(OfflinePlayer player)
    {
        return instance.getPlayerAccounts().containsKey(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName)
    {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player)
    {
        return instance.getPlayerAccounts().get(player.getUniqueId()).getBalance().doubleValue();
    }

    @Override
    public double getBalance(OfflinePlayer player, String world)
    {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount)
    {
        return instance.getPlayerAccounts().get(player.getUniqueId()).getBalance().compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount)
    {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount)
    {
        UUID uuid = player.getUniqueId();

        BigDecimal currentBalance = instance.getPlayerAccounts().get(uuid).getBalance();
        BigDecimal bdAmount = instance.round(BigDecimal.valueOf(amount)).stripTrailingZeros();
        double bdAmountDoubleValue = bdAmount.doubleValue();

        if (bdAmount.compareTo(BigDecimal.ZERO) > 0) // If the amount is greater than 0...
        {
            if (bdAmount.scale() <= fractionalDigits()) // If the amount is within the decimal place scale of the configured amount...
            {
                if (has(player, bdAmountDoubleValue))
                {
                    PlayerAccount account = instance.getPlayerAccounts().get(uuid);

                    BigDecimal resultingBalance = currentBalance.subtract(bdAmount);

                    // Update the player's balance
                    account.setBalance(resultingBalance);

                    // Mark for saving
                    instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

                    // Log the change to the console if config.yml says to do so
                    FileConfiguration config = instance.getConfig();
                    if (config.getBoolean("settings.logging.vault-withdraw-success.log"))
                    {
                        instance.getLogger().log(Level.INFO, config.getString("settings.logging.vault-withdraw-success.message")
                                .replace("<player>", player.getName())
                                .replace("<uuid>", uuid.toString())
                                .replace("<amount>", bdAmount.toPlainString())
                                .replace("<balance>", resultingBalance.toPlainString()));
                    }

                    return new EconomyResponse(bdAmountDoubleValue, resultingBalance.doubleValue(), ResponseType.SUCCESS, null);
                }

                // Log the failure to the console if config.yml says to do so
                FileConfiguration config = instance.getConfig();
                if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
                {
                    instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                            .replace("<player>", player.getName())
                            .replace("<uuid>", uuid.toString())
                            .replace("<amount>", bdAmount.toPlainString())
                            .replace("<error_message>", ERROR_INSUFFICIENT_FUNDS));
                }

                return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_INSUFFICIENT_FUNDS);
            }

            // Log the failure to the console if config.yml says to do so
            FileConfiguration config = instance.getConfig();
            if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                        .replace("<player>", player.getName())
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_TOO_MANY_DECIMAL_PLACES));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_TOO_MANY_DECIMAL_PLACES);
        }

        // Log the failure to the console if config.yml says to do so
        FileConfiguration config = instance.getConfig();
        if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
        {
            instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                    .replace("<player>", player.getName())
                    .replace("<uuid>", uuid.toString())
                    .replace("<amount>", bdAmount.toPlainString())
                    .replace("<error_message>", ERROR_NOT_GREATER_THAN_ZERO));
        }

        return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_NOT_GREATER_THAN_ZERO);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player,	String worldName, double amount)
    {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount)
    {
        UUID uuid = player.getUniqueId();

        BigDecimal currentBalance = instance.getPlayerAccounts().get(uuid).getBalance();
        BigDecimal bdAmount = instance.round(BigDecimal.valueOf(amount)).stripTrailingZeros();
        double bdAmountDoubleValue = bdAmount.doubleValue();

        if (bdAmount.compareTo(BigDecimal.ZERO) > 0) // If the amount is greater than 0...
        {
            if (bdAmount.scale() <= fractionalDigits()) // If the amount is within the decimal place scale of the configured amount...
            {
                FileConfiguration config = instance.getConfig();

                BigDecimal resultingBalance = currentBalance.add(bdAmount);

                if (resultingBalance.compareTo(new BigDecimal(config.getString("settings.currency.max-balance"))) <= 0) // If "resultingBalance" is less than or equal to the max balance...
                {
                    PlayerAccount account = instance.getPlayerAccounts().get(uuid);

                    // Update the player's balance
                    account.setBalance(resultingBalance);

                    // Mark for saving
                    instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

                    // Log the change to the console if config.yml says to do so
                    if (config.getBoolean("settings.logging.vault-deposit-success.log"))
                    {
                        instance.getLogger().log(Level.INFO, config.getString("settings.logging.vault-deposit-success.message")
                                .replace("<player>", player.getName())
                                .replace("<uuid>", uuid.toString())
                                .replace("<amount>", bdAmount.toPlainString())
                                .replace("<balance>", resultingBalance.toPlainString()));
                    }

                    return new EconomyResponse(bdAmountDoubleValue, resultingBalance.doubleValue(), ResponseType.SUCCESS, null);
                }

                // Log the failure to the console if config.yml says to do so
                if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
                {
                    instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                            .replace("<player>", player.getName())
                            .replace("<uuid>", uuid.toString())
                            .replace("<amount>", bdAmount.toPlainString())
                            .replace("<error_message>", ERROR_WOULD_EXCEED_MAX_BALANCE));
                }

                return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_WOULD_EXCEED_MAX_BALANCE);
            }

            // Log the failure to the console if config.yml says to do so
            FileConfiguration config = instance.getConfig();
            if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                        .replace("<player>", player.getName())
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_TOO_MANY_DECIMAL_PLACES));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_TOO_MANY_DECIMAL_PLACES);
        }

        // Log the failure to the console if config.yml says to do so
        FileConfiguration config = instance.getConfig();
        if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
        {
            instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                    .replace("<player>", player.getName())
                    .replace("<uuid>", uuid.toString())
                    .replace("<amount>", bdAmount.toPlainString())
                    .replace("<error_message>", ERROR_NOT_GREATER_THAN_ZERO));
        }

        return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_NOT_GREATER_THAN_ZERO);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player)
    {
        BigDecimal defaultBalance = new BigDecimal(instance.getConfig().getString("settings.currency.default-balance")).stripTrailingZeros();

        if (defaultBalance.compareTo(BigDecimal.ZERO) >= 0 && defaultBalance.scale() <= fractionalDigits() && defaultBalance.compareTo(new BigDecimal(instance.getConfig().getString("settings.currency.max-balance"))) <= 0)
        {
            UUID uuid = player.getUniqueId();
            PlayerAccount account = new PlayerAccount(defaultBalance, true, player.getName());

            // Add account to the cache
            instance.getPlayerAccounts().put(uuid, account);

            // Mark for saving
            instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

            return true;
        }

        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName)
    {
        return createPlayerAccount(player);
    }

    @Override
    public EconomyResponse createBank(String name, String player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse deleteBank(String name)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankBalance(String name)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankHas(String name, double amount)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public List<String> getBanks()
    {
        return null;
    }

    // ----- Getters for error messages -----
    public static String getErrorInsufficientFunds()
    {
        return ERROR_INSUFFICIENT_FUNDS;
    }

    public static String getErrorWouldExceedMaxBalance()
    {
        return ERROR_WOULD_EXCEED_MAX_BALANCE;
    }

    public static String getErrorTooManyDecimalPlaces()
    {
        return ERROR_TOO_MANY_DECIMAL_PLACES;
    }

    public static String getErrorNotGreaterThanZero()
    {
        return ERROR_NOT_GREATER_THAN_ZERO;
    }

    // ----- Deprecated Economy methods -----

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasAccount(String playerName)
    {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasAccount(String playerName, String worldName)
    {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public double getBalance(String playerName)
    {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public double getBalance(String playerName, String world)
    {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean has(String playerName, double amount)
    {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean has(String playerName, String worldName, double amount)
    {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount)
    {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount)
    {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount)
    {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount)
    {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean createPlayerAccount(String playerName)
    {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean createPlayerAccount(String playerName, String worldName)
    {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

}