package io.papermc.paperweight.testplugin;

import net.minecraft.SharedConstants;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class TestPlugin extends JavaPlugin implements Listener {
  @Override
  public void onEnable() {
    this.getServer().getPluginManager().registerEvents(this, this);

    this.getLogger().info(SharedConstants.getCurrentVersion().id());
  }
}
