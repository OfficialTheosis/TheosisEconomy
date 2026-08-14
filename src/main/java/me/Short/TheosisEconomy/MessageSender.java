package me.Short.TheosisEconomy;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MessageSender
{

    private final TheosisEconomy instance;

    public MessageSender(TheosisEconomy instance)
    {
        this.instance = instance;
    }

    public void sendMessage(Audience audience, MessageType messageType, Component message)
    {
        audience.forEachAudience(target ->
        {
            Runnable sendMessageLogic = () ->
            {
                switch (messageType)
                {
                    case CHAT -> target.sendMessage(message);
                    case ACTION_BAR -> target.sendActionBar(message);
                }
            };

            if (target instanceof Player player)
            {
                if (Bukkit.isOwnedByCurrentRegion(player))
                {
                    sendMessageLogic.run();
                }
                else
                {
                    player.getScheduler().execute(instance, sendMessageLogic, null, 0L);
                }
            }
            else
            {
                if (Bukkit.isGlobalTickThread())
                {
                    sendMessageLogic.run();
                }
                else
                {
                    Bukkit.getGlobalRegionScheduler().execute(instance, sendMessageLogic);
                }
            }
        });
    }

    public void sendMiniMessage(Audience audience, MessageType messageType, String miniMessage)
    {
        sendMessage(audience, messageType, instance.getMiniMessage().deserialize(miniMessage));
    }

    public void sendMiniMessage(Audience audience, MessageType messageType, String miniMessage, TagResolver... tagResolvers)
    {
        sendMessage(audience, messageType, instance.getMiniMessage().deserialize(miniMessage, tagResolvers));
    }

    public void sendConfigMiniMessage(Audience audience, MessageType messageType, String configMessagePath)
    {
        sendMiniMessage(audience, messageType, instance.getConfigSnapshot().getString(configMessagePath));
    }

    public void sendConfigMiniMessage(Audience audience, MessageType messageType, String configMessagePath, TagResolver... tagResolvers)
    {
        sendMiniMessage(audience, messageType, instance.getConfigSnapshot().getString(configMessagePath), tagResolvers);
    }

}