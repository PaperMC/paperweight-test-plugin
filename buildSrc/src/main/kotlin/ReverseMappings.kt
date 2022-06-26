import io.papermc.paperweight.util.MappingFormats
import io.papermc.paperweight.util.constants.DEOBF_NAMESPACE
import io.papermc.paperweight.util.constants.SPIGOT_NAMESPACE
import io.papermc.paperweight.util.path
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class ReverseMappings : io.papermc.paperweight.tasks.BaseTask() {
	@get:InputFile
	abstract val inputMappings: RegularFileProperty

	@get:OutputFile
	abstract val outputMappings: RegularFileProperty

	@get:Input
	abstract val fromNs: Property<String>

	@get:Input
	abstract val toNs: Property<String>

	@TaskAction
	fun run() {
		val mappingSet = MappingFormats.TINY.read(
			inputMappings.path,
			fromNs.get(),
			toNs.get()
		).reverse()

		Files.deleteIfExists(outputMappings.path)
		Files.createDirectories(outputMappings.path.parent)
		MappingFormats.TINY.write(
			mappingSet,
			outputMappings.path,
			toNs.get(),
			fromNs.get()
		)
	}
}
