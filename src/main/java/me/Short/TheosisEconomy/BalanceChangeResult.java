package me.Short.TheosisEconomy;

public enum BalanceChangeResult
{
    SUCCESS(null),
    ACCOUNT_NOT_FOUND("Account not found."),
    NEGATIVE_AMOUNT("Amount is negative."),
    ZERO_OR_LESS_AMOUNT("Amount is zero or less."),
    TOO_MANY_DECIMAL_PLACES_AMOUNT("Amount uses too many decimal places."),
    INSUFFICIENT_FUNDS("Insufficient funds."),
    ABOVE_MAXIMUM_BALANCE("Above maximum balance.");

    private final String errorMessage;

    BalanceChangeResult(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }
}