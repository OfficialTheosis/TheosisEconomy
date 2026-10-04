package me.Short.TheosisEconomy;

import java.util.List;

public record BalanceTopPage(int page, long startPosition, List<BalanceTopEntry> entries) {}