package me.Short.TheosisEconomy;

import java.math.BigDecimal;

public record MoneyTransfer(MoneyTransferResult result, BigDecimal amount, BigDecimal senderResultingBalance, BigDecimal targetResultingBalance) {}