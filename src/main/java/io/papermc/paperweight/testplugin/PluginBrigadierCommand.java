package io.papermc.paperweight.testplugin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.command.VanillaCommandWrapper;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
final class PluginBrigadierCommand extends Command implements PluginIdentifiableCommand {
  private final Consumer<LiteralArgumentBuilder<CommandSourceStack>> command;
  private final Plugin plugin;

  PluginBrigadierCommand(
    final Plugin plugin,
    final String name,
    final Consumer<LiteralArgumentBuilder<CommandSourceStack>> command
  ) {
    super(name);
    this.plugin = plugin;
    this.command = command;
  }

  @Override
  public boolean execute(final CommandSender sender, final String commandLabel, final String[] args) {
    final String joined = String.join(" ", args);
    final String argsString = joined.isBlank() ? "" : " " + joined;
    ((CraftServer) Bukkit.getServer()).getServer().getCommands().performPrefixedCommand(
      VanillaCommandWrapper.getListener(sender),
      commandLabel + argsString,
      commandLabel
    );
    return true;
  }

  @Override
  public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args, final @Nullable Location location) {
    final String joined = String.join(" ", args);
    final String argsString = joined.isBlank() ? "" : joined;
    final CommandDispatcher<CommandSourceStack> dispatcher = ((CraftServer) Bukkit.getServer()).getServer().getCommands().getDispatcher();
    final ParseResults<CommandSourceStack> results = dispatcher.parse(new StringReader(alias + " " + argsString), VanillaCommandWrapper.getListener(sender));
    return dispatcher.getCompletionSuggestions(results)
      .thenApply(result -> result.getList().stream().map(Suggestion::getText).toList())
      .join();
  }

  @Override
  public Plugin getPlugin() {
    return this.plugin;
  }

  Consumer<LiteralArgumentBuilder<CommandSourceStack>> command() {
    return this.command;
  }
}
