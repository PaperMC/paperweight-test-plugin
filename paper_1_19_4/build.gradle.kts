plugins {
  `my-conventions`
  id("io.papermc.paperweight.userdev")
}

dependencies {
  implementation(project(":paper_hooks"))

  paperweight.paperDevBundle("1.19.4-R0.1-SNAPSHOT")
}
