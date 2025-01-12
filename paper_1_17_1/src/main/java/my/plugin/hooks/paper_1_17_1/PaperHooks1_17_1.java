package my.plugin.hooks.paper_1_17_1;

import my.plugin.hooks.PaperHooks;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;

public class PaperHooks1_17_1 implements PaperHooks {
  @Override
  public void doSomething(final Logger logger) {
    logger.info(SharedConstants.getCurrentVersion().getName());
  }
}
