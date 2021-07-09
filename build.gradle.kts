import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import xyz.jpenilla.runpaper.task.RunServerTask

plugins {
  `java-library`
  id("io.papermc.paperweight.userdev") version "1.1.9-LOCAL-SNAPSHOT"
  id("xyz.jpenilla.run-paper") version "1.0.3"
  id("net.minecrell.plugin-yml.bukkit") version "0.4.0"
}

group = "io.papermc.paperweight"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenLocal() {
    content { includeGroup("io.papermc.paper") }
  }
  mavenCentral()
  maven("https://papermc.io/repo/repository/maven-public/")
}

dependencies {
  paperweightDevBundle("1.17.1-R0.1-SNAPSHOT")
}

runPaper {
  disablePluginJarDetection()
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

  // Task to run obfuscated/production server and plugin
  runServer {
    minecraftVersion("1.17.1")
    pluginJars.from(reobfJar.flatMap { it.outputJar })
  }

  // Task to run mojang mapped/dev server and plugin
  register<RunServerTask>("runMojangMappedServer") {
    minecraftVersion("1.17.1")
    pluginJars.from(jar.flatMap { it.archiveFile })
    paperclip(layout.file(configurations.mojangMappedServer.map { it.singleFile }))
  }
}

bukkit {
  load = BukkitPluginDescription.PluginLoadOrder.STARTUP
  main = "io.papermc.paperweight.testplugin.TestPlugin"
  apiVersion = "1.17"
  authors = listOf("Author")
}
