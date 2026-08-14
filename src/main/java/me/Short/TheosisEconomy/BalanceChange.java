package me.Short.TheosisEconomy;

import java.math.BigDecimal;

public record BalanceChange(BalanceChangeResult result, BigDecimal amount, BigDecimal resultingBalance) {}