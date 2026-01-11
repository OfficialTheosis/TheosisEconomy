package me.Short.TheosisEconomy.Events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;

public class PlayerPayPlayerEvent extends Event implements Cancellable
{
    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;

    // The player who is sending the money
    private Player sender;

    // The player who the money is being sent to
    private OfflinePlayer recipient;

    // The amount of money being sent
    private BigDecimal amount;

    public PlayerPayPlayerEvent(Player sender, OfflinePlayer recipient, BigDecimal amount)
    {
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
    }

    @Override
    public boolean isCancelled()
    {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel)
    {
        this.cancelled = cancel;
    }

    @Override
    public @NonNull HandlerList getHandlers()
    {
        return handlers;
    }

    public static HandlerList getHandlerList()
    {
        return handlers;
    }

    // ----- Getters -----

    // Getter for 'sender'
    public Player getSender()
    {
        return sender;
    }

    // Getter for 'recipient'
    public OfflinePlayer getRecipient()
    {
        return recipient;
    }

    // Getter for 'amount'
    public BigDecimal getAmount()
    {
        return amount;
    }

    // ----- Setters -----

    // Setter for "sender"
    public void setSender(Player sender)
    {
        this.sender = sender;
    }

    // Setter for "recipient"
    public void setRecipient(OfflinePlayer recipient)
    {
        this.recipient = recipient;
    }

    // Setter for "amount"
    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }
}