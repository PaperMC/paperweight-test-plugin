import io.papermc.paperweight.tasks.JavaLauncherTask
import io.papermc.paperweight.tasks.RemapSources
import io.papermc.paperweight.util.MappingFormats
import io.papermc.paperweight.util.constants.DEOBF_NAMESPACE
import io.papermc.paperweight.util.constants.SPIGOT_NAMESPACE
import io.papermc.paperweight.util.deleteRecursive
import io.papermc.paperweight.util.path
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import paper.libs.org.cadixdev.mercury.Mercury
import paper.libs.org.eclipse.jdt.core.JavaCore
import java.nio.file.Files
import javax.inject.Inject

abstract class RemapPluginSources : JavaLauncherTask() {
  @get:CompileClasspath
  abstract val remapClasspath: ConfigurableFileCollection

  @get:InputDirectory
  abstract val inputSources: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:InputFile
  abstract val mappingsFile: RegularFileProperty

  @get:Input
  abstract val addExplicitThis: Property<Boolean>

  @get:Inject
  abstract val workerExecutor: WorkerExecutor

  override fun init() {
    super.init()
    addExplicitThis.convention(false)
  }

  @TaskAction
  fun run() {
    val queue = workerExecutor.processIsolation {
      forkOptions.jvmArgs("-Xmx2G")
      forkOptions.executable(launcher.get().executablePath.path.toAbsolutePath().toString())
    }

    queue.submit(RemapPluginSourcesAction::class) {
      remapClasspath.setFrom(this@RemapPluginSources.remapClasspath)
      inputSources.set(this@RemapPluginSources.inputSources)
      outputDir.set(this@RemapPluginSources.outputDir)
      mappingsFile.set(this@RemapPluginSources.mappingsFile)
      addExplicitThis.set(this@RemapPluginSources.addExplicitThis)
    }
  }

  abstract class RemapPluginSourcesAction : WorkAction<RemapPluginSourcesAction.Params> {
    interface Params : WorkParameters {
      val remapClasspath: ConfigurableFileCollection
      val inputSources: DirectoryProperty
      val outputDir: DirectoryProperty
      val mappingsFile: RegularFileProperty
      val addExplicitThis: Property<Boolean>
    }

    override fun execute() {
      val mappingSet = MappingFormats.TINY.read(
        parameters.mappingsFile.path,
        SPIGOT_NAMESPACE,
        DEOBF_NAMESPACE
      )

      val merc = Mercury()
      merc.isGracefulClasspathChecks = true
      merc.sourceCompatibility = JavaCore.VERSION_17
      merc.classPath.addAll(parameters.remapClasspath.map { it.toPath() })
      if (parameters.addExplicitThis.get()) {
        merc.processors.add(RemapSources.ExplicitThisAdder)
      }
      merc.processors.add(MercuryRemapper.create(mappingSet, false))

      parameters.outputDir.path.deleteRecursive()
      Files.createDirectories(parameters.outputDir.path)

      merc.rewrite(parameters.inputSources.path, parameters.outputDir.path)
    }
  }
}
