package me.Short.TheosisEconomy;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceTopEntry(UUID uuid, BigDecimal balance, long lastBalanceChangeTimestamp) {}