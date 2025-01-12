plugins {
  `my-conventions`
  id("io.papermc.paperweight.userdev")
}

dependencies {
  implementation(project(":paper_hooks"))

  paperweight.paperDevBundle("1.17.1-R0.1-SNAPSHOT")
}
