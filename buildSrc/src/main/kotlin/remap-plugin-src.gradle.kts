import io.papermc.paperweight.tasks.RemapJar
import io.papermc.paperweight.util.constants.DEOBF_NAMESPACE
import io.papermc.paperweight.util.constants.SPIGOT_NAMESPACE
import io.papermc.paperweight.util.registering

plugins {
  java
  id("io.papermc.paperweight.userdev")
}

tasks {
  val reverseMappings by registering<ReverseMappings> {
    inputMappings.set(layout.projectDirectory.file(".gradle/caches/paperweight/setupCache/extractDevBundle.dir/data/mojang+yarn-spigot-reobf.tiny"))
    outputMappings.set(layout.buildDirectory.file("reversed-reobf-mappings.tiny"))

    fromNs.set(DEOBF_NAMESPACE)
    toNs.set(SPIGOT_NAMESPACE)
  }

  val reobfPaperJar by tasks.registering<RemapJar> {
    // won't be runnable as we don't fixjarforreobf
    // pretty hacky but should work
    inputJar.set(layout.file(configurations.mojangMappedServerRuntime.flatMap {
      it.elements.map { elements ->
        elements.filter { element ->
          val p = element.asFile.absolutePath
          p.contains("ivyRepository") && p.contains("paper-server-userdev")
        }.single().asFile
      }
    }))
    outputJar.set(layout.buildDirectory.file("reobfPaper.jar"))
    fromNamespace.set(DEOBF_NAMESPACE)
    toNamespace.set(SPIGOT_NAMESPACE)
    mappingsFile.set(reverseMappings.flatMap { it.outputMappings })
    remapper.from(configurations.remapper)
  }

  @Suppress("unused_variable")
  val remapPluginSources by registering<RemapPluginSources> {
    remapClasspath.from(reobfPaperJar.flatMap { it.outputJar })
    remapClasspath.from(configurations.compileClasspath)
    inputSources.set(layout.projectDirectory.dir("src/main/java"))
    outputDir.set(layout.projectDirectory.dir("src/main/mojangMappedJava"))
    mappingsFile.set(reverseMappings.flatMap { it.outputMappings })
  }
}
