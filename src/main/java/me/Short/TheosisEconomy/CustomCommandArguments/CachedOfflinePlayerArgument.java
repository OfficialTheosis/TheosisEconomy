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
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class CachedOfflinePlayerArgument implements CustomArgumentType<OfflinePlayer, PlayerSelectorArgumentResolver>
{

    // Instance of "TheosisEconomy"
    private static TheosisEconomy instance;

    // Constructor
    public CachedOfflinePlayerArgument(TheosisEconomy instance)
    {
        CachedOfflinePlayerArgument.instance = instance;
    }

    private static final SimpleCommandExceptionType ERROR_BAD_SOURCE = new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text("The source needs to be a CommandSourceStack.")));

    private static final DynamicCommandExceptionType ERROR_NOT_CACHED = new DynamicCommandExceptionType(specifiedName ->
            MessageComponentSerializer.message().serialize(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-joined-before"),
                    Placeholder.component("name", Component.text(specifiedName.toString())))));

    @Override
    public OfflinePlayer parse(StringReader reader)
    {
        throw new UnsupportedOperationException("This method will never be called.");
    }

    @Override
    public <S> OfflinePlayer parse(StringReader reader, S source) throws CommandSyntaxException
    {
        if (!(source instanceof CommandSourceStack))
        {
            throw ERROR_BAD_SOURCE.create();
        }

        // Get the name that was specified in the command argument
        String remaining = reader.getRemaining();
        String specifiedName = remaining.contains(" ") ? remaining.substring(0, remaining.indexOf(" ")) : remaining;

        // Manually advance the cursor, since we are not calling the native argument type's parse method which would normally do this
        reader.setCursor(reader.getCursor() + specifiedName.length());

        // Get target player if cached
        final OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(specifiedName);

        // Throw error if a target player was not found (i.e. no target player cached by the specified name)
        if (target == null)
        {
            throw ERROR_NOT_CACHED.create(specifiedName);
        }

        return target;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder)
    {
        instance.getOfflinePlayerNames().values().stream()
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