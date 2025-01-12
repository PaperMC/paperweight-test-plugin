import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml
import xyz.jpenilla.runpaper.task.RunServer

plugins {
  `my-conventions`
  id("io.papermc.paperweight.userdev") version "2.0.0-beta.18" apply false
  id("xyz.jpenilla.run-paper") version "3.0.0-beta.1" // Adds runServer task for testing
  id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.3.0" // Generates plugin.yml based on the Gradle config
  id("com.gradleup.shadow") version "8.3.8"
}

java.disableAutoTargetJvm() // Allow consuming JVM 21 projects (i.e. paper_1_21_8) even though our release is 17

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")

  implementation(project(":paper_hooks"))

  // Shade the reobf variant
  runtimeOnly(project(":paper_1_17_1", configuration = "reobf"))
  runtimeOnly(project(":paper_1_19_4", configuration = "reobf"))

  // For Paper 1.20.5+, we don't need to use the reobf variant.
  // If you still support spigot, you will need to use the reobf variant,
  // and remove the Mojang-mapped metadata from the manifest below.
  runtimeOnly(project(":paper_1_21_8"))
}

tasks.assemble {
  dependsOn(tasks.shadowJar)
}

tasks.jar {
  manifest.attributes(
    "paperweight-mappings-namespace" to "mojang",
  )
}

// Configure plugin.yml generation
// - name, version, and description are inherited from the Gradle project.
bukkitPluginYaml {
  main = "my.plugin.MyPlugin"
  load = BukkitPluginYaml.PluginLoadOrder.STARTUP
  authors.add("Author")
  apiVersion = "1.17"
}

tasks.runServer {
  minecraftVersion("1.21.8")
}

tasks.register("run1_17_1", RunServer::class) {
  minecraftVersion("1.17.1")
  pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
  runDirectory = layout.projectDirectory.dir("run1_17_1")
  systemProperties["Paper.IgnoreJavaVersion"] = true
}

tasks.register("run1_19_4", RunServer::class) {
  minecraftVersion("1.19.4")
  pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
  runDirectory = layout.projectDirectory.dir("run1_19_4")
  systemProperties["Paper.IgnoreJavaVersion"] = true
}
