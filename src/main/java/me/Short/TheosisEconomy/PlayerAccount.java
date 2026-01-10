package me.Short.TheosisEconomy;

import java.math.BigDecimal;

public class PlayerAccount
{

    private BigDecimal balance;

    private boolean acceptingPayments;

    // Constructor
    public PlayerAccount(BigDecimal balance, boolean acceptingPayments)
    {
        this.balance = balance;
        this.acceptingPayments = acceptingPayments;
    }

    // Method to get a `PlayerAccountSnapshot` of this `PlayerAccount`
    public PlayerAccountSnapshot snapshot()
    {
        return new PlayerAccountSnapshot(balance, acceptingPayments);
    }

    // ----- Getters -----

    // Getter for 'balance'
    public BigDecimal getBalance()
    {
        return balance;
    }

    // Getter for 'acceptingPayments'
    public boolean getAcceptingPayments()
    {
        return acceptingPayments;
    }

    // ----- Setters -----

    // Setter for "balance"
    public void setBalance(BigDecimal balance)
    {
        this.balance = balance;
    }

    // Setter for "acceptingPayments"
    public void setAcceptingPayments(boolean acceptingPayments)
    {
        this.acceptingPayments = acceptingPayments;
    }

}