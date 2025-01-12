plugins {
  `my-conventions`
  id("io.papermc.paperweight.userdev")
}

dependencies {
  implementation(project(":paper_hooks"))

  paperweight.paperDevBundle("26.1.2.build.+")
  // paperweight.foliaDevBundle("26.1.2.build.+")
  // paperweight.devBundle("com.example.paperfork", "26.1.2.build.+")
}

tasks.withType<JavaCompile>().configureEach {
  // Override release for newer MC
  options.release = 25
}
