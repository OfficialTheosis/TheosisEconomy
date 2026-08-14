package me.Short.TheosisEconomy;

import java.math.BigDecimal;
import java.util.UUID;

public class PlayerAccount
{

    private final UUID uuid;

    private BigDecimal balance;

    private long lastBalanceChangeTimestamp;

    private boolean acceptingPayments;

    public PlayerAccount(UUID uuid, BigDecimal balance, long lastBalanceChangeTimestamp, boolean acceptingPayments)
    {
        this.uuid = uuid;
        this.balance = balance;
        this.lastBalanceChangeTimestamp = lastBalanceChangeTimestamp;
        this.acceptingPayments = acceptingPayments;
    }

    public UUID getUuid()
    {
        return uuid;
    }

    public synchronized BigDecimal getBalance()
    {
        return balance;
    }

    public synchronized BigDecimal addToBalance(BigDecimal amount)
    {
        balance = balance.add(amount);

        return balance;
    }

    public synchronized BigDecimal subtractFromBalance(BigDecimal amount)
    {
        balance = balance.subtract(amount);

        return balance;
    }

    public synchronized void setBalance(BigDecimal balance)
    {
        this.balance = balance;
    }

    public synchronized long getLastBalanceChangeTimestamp()
    {
        return lastBalanceChangeTimestamp;
    }

    public synchronized void setLastBalanceChangeTimestamp(long lastBalanceChangeTimestamp)
    {
        this.lastBalanceChangeTimestamp = lastBalanceChangeTimestamp;
    }

    public synchronized boolean getAcceptingPayments()
    {
        return acceptingPayments;
    }

    public synchronized void setAcceptingPayments(boolean acceptingPayments)
    {
        this.acceptingPayments = acceptingPayments;
    }

}