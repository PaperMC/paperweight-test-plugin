import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder

plugins {
  `java-library`
  id("net.minecrell.plugin-yml.bukkit") version "0.4.0"
  id("xyz.jpenilla.run-paper") version "1.0.3"
  id("io.papermc.paperweight.userdev") version "1.1.9-LOCAL-SNAPSHOT"
}

group = "io.papermc.paperweight"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://papermc.io/repo/repository/maven-public/")
}

dependencies {
  paperDevBundle(files("../../temp/117/Paper/build/libs/paperDevBundle-1.17.1-R0.1-SNAPSHOT.zip"))
}

runPaper {
  disablePluginJarDetection()
}

tasks {
  compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(16)
  }
  build {
    dependsOn(reobfJar)
  }

  runServer {
    minecraftVersion("1.17.1")
    pluginJars.from(reobfJar.flatMap { it.outputJar })
  }
}

bukkit {
  load = PluginLoadOrder.STARTUP
  main = "io.papermc.paperweight.testplugin.TestPlugin"
  apiVersion = "1.17"
  authors = listOf("Author")
}
