package io.papermc.paperweight.testplugin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.command.VanillaCommandWrapper;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;
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
  public Plugin getPlugin() {
    return this.plugin;
  }

  Consumer<LiteralArgumentBuilder<CommandSourceStack>> command() {
    return this.command;
  }
}
