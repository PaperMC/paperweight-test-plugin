plugins {
  `my-conventions`
  id("io.papermc.paperweight.userdev")
}

dependencies {
  implementation(project(":paper_hooks"))

  paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
  // paperweight.foliaDevBundle("1.21.4-R0.1-SNAPSHOT")
  // paperweight.devBundle("com.example.paperfork", "1.21.4-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
  // Override release for newer MC
  options.release = 21
}
