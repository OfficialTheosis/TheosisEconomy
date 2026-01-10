package me.Short.TheosisEconomy.CustomCommandArguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.Short.TheosisEconomy.TheosisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class OnlineVisiblePlayerArgument implements CustomArgumentType<Player, PlayerSelectorArgumentResolver>
{

    // Instance of "TheosisEconomy"
    private static TheosisEconomy instance;

    // Constructor
    public OnlineVisiblePlayerArgument(TheosisEconomy instance)
    {
        OnlineVisiblePlayerArgument.instance = instance;
    }

    private static final SimpleCommandExceptionType ERROR_BAD_SOURCE = new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text("The source needs to be a CommandSourceStack.")));

    private static final DynamicCommandExceptionType ERROR_NOT_ONLINE_OR_VISIBLE = new DynamicCommandExceptionType(specifiedName ->
            MessageComponentSerializer.message().serialize(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-online"),
                    Placeholder.component("name", Component.text(specifiedName.toString())))));

    @Override
    public Player parse(StringReader reader)
    {
        throw new UnsupportedOperationException("This method will never be called.");
    }

    @Override
    public <S> Player parse(StringReader reader, S source) throws CommandSyntaxException
    {
        if (!(source instanceof CommandSourceStack stack))
        {
            throw ERROR_BAD_SOURCE.create();
        }

        // Get the name that was specified in the command argument - needs to be done before parsing, because parsing advances the reader's cursor
        String remaining = reader.getRemaining();
        String specifiedName = remaining.contains(" ") ? remaining.substring(0, remaining.indexOf(" ")) : remaining;

        // Try to resolve a target player - if an exception is thrown, throw our own exception instead so the error message is custom
        final Player target;
        try
        {
            target = getNativeType().parse(reader).resolve(stack).getFirst();
        }
        catch (CommandSyntaxException exception)
        {
            throw ERROR_NOT_ONLINE_OR_VISIBLE.create(specifiedName);
        }

        // If the sender cannot see the player, throw an identical exception to what would be thrown if the player was not online
        if (stack.getSender() instanceof Player sender && !sender.canSee(target))
        {
            throw ERROR_NOT_ONLINE_OR_VISIBLE.create(specifiedName);
        }

        return target;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder)
    {
        Bukkit.getOnlinePlayers().stream()
                .filter(target -> context.getSource() instanceof CommandSourceStack stack && stack.getSender() instanceof Player sender && sender.canSee(target))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    @Override
    public ArgumentType<PlayerSelectorArgumentResolver> getNativeType()
    {
        return ArgumentTypes.player();
    }

}