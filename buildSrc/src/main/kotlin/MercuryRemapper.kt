import modified.mercury.RemapperVisitor
import modified.mercury.SimpleRemapperVisitor
import paper.libs.org.cadixdev.lorenz.MappingSet
import paper.libs.org.cadixdev.mercury.RewriteContext
import paper.libs.org.cadixdev.mercury.SourceRewriter
import java.util.Objects

class MercuryRemapper private constructor(mappings: MappingSet, simple: Boolean, javadoc: Boolean) : SourceRewriter {
	private val mappings: MappingSet
	private val simple: Boolean
	private val javadoc: Boolean

	init {
		this.mappings = Objects.requireNonNull(mappings, "mappings")
		this.simple = simple
		this.javadoc = javadoc
	}

	override fun getFlags(): Int = SourceRewriter.FLAG_RESOLVE_BINDINGS

	override fun rewrite(context: RewriteContext) {
		context.compilationUnit.accept(if (simple) SimpleRemapperVisitor(context, mappings, this.javadoc) else RemapperVisitor(context, mappings, this.javadoc))
	}

	companion object {
		fun create(mappings: MappingSet): SourceRewriter {
			return MercuryRemapper(mappings, false, true)
		}

		fun create(mappings: MappingSet, javadoc: Boolean): SourceRewriter {
			return MercuryRemapper(mappings, false, javadoc)
		}

		fun createSimple(mappings: MappingSet): SourceRewriter {
			return MercuryRemapper(mappings, true, true)
		}

		fun createSimple(mappings: MappingSet, javadoc: Boolean): SourceRewriter {
			return MercuryRemapper(mappings, true, javadoc)
		}
	}
}
