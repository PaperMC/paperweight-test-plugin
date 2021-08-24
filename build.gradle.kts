import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
  `java-library`
  id("io.papermc.paperweight.userdev") version "1.1.11"
  id("xyz.jpenilla.run-paper") version "1.0.4" // Adds runServer and runMojangMappedServer tasks for testing
  id("net.minecrell.plugin-yml.bukkit") version "0.4.0"
}

group = "io.papermc.paperweight"
version = "1.0.0-SNAPSHOT"
description = "Test plugin for paperweight-userdev"

dependencies {
  paperDevBundle("1.17.1-R0.1-SNAPSHOT")
}

tasks {
  // Run reobfJar on build
  build {
    dependsOn(reobfJar)
  }

  compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(16)
  }
  javadoc {
    options.encoding = Charsets.UTF_8.name()
  }
  processResources {
    filteringCharset = Charsets.UTF_8.name()
  }
}

bukkit {
  load = BukkitPluginDescription.PluginLoadOrder.STARTUP
  main = "io.papermc.paperweight.testplugin.TestPlugin"
  apiVersion = "1.17"
  authors = listOf("Author")
}
