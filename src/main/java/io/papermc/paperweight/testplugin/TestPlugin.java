package io.papermc.paperweight.testplugin;

import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_18_R1.CraftServer;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.players;

public final class TestPlugin extends JavaPlugin implements Listener {
  @Override
  public void onEnable() {
    this.getServer().getPluginManager().registerEvents(this, this);
    ((CraftServer) this.getServer()).getServer().vanillaCommandDispatcher.getDispatcher().register(
      literal("paperweight")
        .requires(stack -> stack.hasPermission(stack.getServer().getOperatorUserPermissionLevel()))
        .then(argument("players", players())
          .executes(ctx -> {
            final Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
            for (final ServerPlayer player : players) {
              player.sendMessage(
                new TextComponent("Hello from Paperweight test plugin!")
                  .withStyle(ChatFormatting.ITALIC, ChatFormatting.GREEN)
                  .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/paperweight @a"))),
                Util.NIL_UUID
              );
            }
            return players.size();
          }))
    );
  }
}
