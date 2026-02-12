package me.Short.TheosisEconomy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class BalanceTop
{

    // Ordered map of players and their balances from richest to poorest
    private final Map<UUID, BigDecimal> topBalances;

    // All players' balances added together
    private final BigDecimal combinedTotalBalance;

    // Constructor
    public BalanceTop(Map<UUID, BigDecimal> topBalances, BigDecimal combinedTotalBalance)
    {
        this.topBalances = topBalances;
        this.combinedTotalBalance = combinedTotalBalance;
    }

    // Getter for "topBalances"
    public Map<UUID, BigDecimal> getTopBalances()
    {
        return topBalances;
    }

    // Getter for "combinedTotalBalance"
    public BigDecimal getCombinedTotalBalance()
    {
        return combinedTotalBalance;
    }

}