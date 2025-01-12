plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "paperweight-test-plugin"

include("paper_hooks")
include("paper_1_17_1")
include("paper_1_19_4")
include("paper_1_21_10")
