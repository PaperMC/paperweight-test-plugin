package my.plugin;

import my.plugin.hooks.PaperHooks;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class MyPlugin extends JavaPlugin implements Listener {
  @Override
  public void onEnable() {
    PaperHooks.get().doSomething(this.getSLF4JLogger());
  }
}
