package my.plugin.hooks.paper_26_1_2;

import my.plugin.hooks.PaperHooks;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;

public class PaperHooks26_1_2 implements PaperHooks {
  @Override
  public void doSomething(final Logger logger) {
    logger.info(SharedConstants.getCurrentVersion().name());
  }
}
