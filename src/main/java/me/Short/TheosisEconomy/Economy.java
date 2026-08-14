package me.Short.TheosisEconomy;

import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Level;

public class Economy implements net.milkbowl.vault.economy.Economy
{

    private final TheosisEconomy instance;

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
        return instance.getDecimalPlaces();
    }

    @Override
    public String format(double amount)
    {
        return Util.formatMoney(instance, BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural()
    {
        return instance.getConfigSnapshot().getString("settings.currency.name-plural");
    }

    @Override
    public String currencyNameSingular()
    {
        return instance.getConfigSnapshot().getString("settings.currency.name-singular");
    }

    @Override
    public boolean hasAccount(OfflinePlayer player)
    {
        // Return whether an account for this player is loaded - checking for an account's existence would require database I/O or caching *all* of them in memory
        return instance.getPlayerAccountManager().isAccountLoaded(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName)
    {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player)
    {
        // Only loaded accounts can be queried without performing database I/O
        BigDecimal balance = instance.getPlayerAccountManager().getLoadedAccountBalance(player.getUniqueId());

        return balance != null ? balance.doubleValue() : 0D;
    }

    @Override
    public double getBalance(OfflinePlayer player, String world)
    {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount)
    {
        // Only loaded accounts can be queried without performing database I/O
        BigDecimal balance = instance.getPlayerAccountManager().getLoadedAccountBalance(player.getUniqueId());

        return balance != null && balance.compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount)
    {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount)
    {
        ConfigSnapshot config = instance.getConfigSnapshot();

        BalanceChange balanceChange = instance.getPlayerAccountManager().subtractFromLoadedAccountBalance(player.getUniqueId(), BigDecimal.valueOf(amount));

        BalanceChangeResult balanceChangeResult = balanceChange.result();

        BigDecimal bdAmount = balanceChange.amount();

        BigDecimal resultingBalance = balanceChange.resultingBalance();

        if (balanceChangeResult == BalanceChangeResult.SUCCESS)
        {
            if (config.getBoolean("settings.logging.vault-withdraw-success.log"))
            {
                instance.getActivityLogger().log(Level.INFO, config.getString("settings.logging.vault-withdraw-success.message")
                        .replace("<player>", player.getName())
                        .replace("<uuid>", player.getUniqueId().toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<balance>", resultingBalance.toPlainString()));
            }

            return new EconomyResponse(bdAmount.doubleValue(), resultingBalance.doubleValue(), ResponseType.SUCCESS, null);
        }

        if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
        {
            instance.getActivityLogger().log(Level.INFO, config.getString("settings.logging.vault-withdraw-fail.message")
                    .replace("<player>", player.getName())
                    .replace("<uuid>", player.getUniqueId().toString())
                    .replace("<amount>", bdAmount.toPlainString())
                    .replace("<error_message>", balanceChangeResult.getErrorMessage()));
        }

        return new EconomyResponse(bdAmount.doubleValue(), resultingBalance != null ? resultingBalance.doubleValue() : 0D, ResponseType.FAILURE, balanceChangeResult.getErrorMessage());
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player,	String worldName, double amount)
    {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount)
    {
        ConfigSnapshot config = instance.getConfigSnapshot();

        BalanceChange balanceChange = instance.getPlayerAccountManager().addToLoadedAccountBalance(player.getUniqueId(), BigDecimal.valueOf(amount));

        BalanceChangeResult balanceChangeResult = balanceChange.result();

        BigDecimal bdAmount = balanceChange.amount();

        BigDecimal resultingBalance = balanceChange.resultingBalance();

        if (balanceChangeResult == BalanceChangeResult.SUCCESS)
        {
            if (config.getBoolean("settings.logging.vault-deposit-success.log"))
            {
                instance.getActivityLogger().log(Level.INFO, config.getString("settings.logging.vault-deposit-success.message")
                        .replace("<player>", player.getName())
                        .replace("<uuid>", player.getUniqueId().toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<balance>", resultingBalance.toPlainString()));
            }

            return new EconomyResponse(bdAmount.doubleValue(), resultingBalance.doubleValue(), ResponseType.SUCCESS, null);
        }

        if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
        {
            instance.getActivityLogger().log(Level.INFO, config.getString("settings.logging.vault-deposit-fail.message")
                    .replace("<player>", player.getName())
                    .replace("<uuid>", player.getUniqueId().toString())
                    .replace("<amount>", bdAmount.toPlainString())
                    .replace("<error_message>", balanceChangeResult.getErrorMessage()));
        }

        return new EconomyResponse(bdAmount.doubleValue(), resultingBalance != null ? resultingBalance.doubleValue() : 0D, ResponseType.FAILURE, balanceChangeResult.getErrorMessage());
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player)
    {
        // Account creation happens on player pre-login and requires database I/O
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
        return List.of();
    }

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