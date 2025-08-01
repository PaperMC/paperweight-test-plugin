package my.plugin.hooks.paper_1_21_8;

import my.plugin.hooks.PaperHooks;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;

public class PaperHooks1_21_8 implements PaperHooks {
  @Override
  public void doSomething(final Logger logger) {
    logger.info(SharedConstants.getCurrentVersion().name());
  }
}
