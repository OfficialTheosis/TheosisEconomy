package me.Short.TheosisEconomy;

import java.math.BigDecimal;

public final class PlayerAccountSnapshot
{

    private final BigDecimal balance;
    private final boolean acceptingPayments;

    // Constructor
    public PlayerAccountSnapshot(BigDecimal balance, boolean acceptingPayments)
    {
        this.balance = balance;
        this.acceptingPayments = acceptingPayments;
    }

}
