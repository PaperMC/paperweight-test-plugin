/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package modified.mercury;

import static paper.libs.org.cadixdev.mercury.util.BombeBindings.isPackagePrivate;

import paper.libs.org.cadixdev.lorenz.MappingSet;
import paper.libs.org.cadixdev.lorenz.model.ClassMapping;
import paper.libs.org.cadixdev.lorenz.model.InnerClassMapping;
import paper.libs.org.cadixdev.lorenz.model.Mapping;
import paper.libs.org.cadixdev.lorenz.model.TopLevelClassMapping;
import paper.libs.org.cadixdev.mercury.RewriteContext;
import paper.libs.org.cadixdev.mercury.jdt.rewrite.imports.ImportRewrite;
import paper.libs.org.cadixdev.mercury.util.GracefulCheck;
import paper.libs.org.eclipse.jdt.core.dom.AST;
import paper.libs.org.eclipse.jdt.core.dom.ASTNode;
import paper.libs.org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.AnnotatableType;
import paper.libs.org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.EnumDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.IBinding;
import paper.libs.org.eclipse.jdt.core.dom.IDocElement;
import paper.libs.org.eclipse.jdt.core.dom.ITypeBinding;
import paper.libs.org.eclipse.jdt.core.dom.ImportDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.Javadoc;
import paper.libs.org.eclipse.jdt.core.dom.Modifier;
import paper.libs.org.eclipse.jdt.core.dom.Name;
import paper.libs.org.eclipse.jdt.core.dom.NameQualifiedType;
import paper.libs.org.eclipse.jdt.core.dom.PackageDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.QualifiedName;
import paper.libs.org.eclipse.jdt.core.dom.QualifiedType;
import paper.libs.org.eclipse.jdt.core.dom.SimpleName;
import paper.libs.org.eclipse.jdt.core.dom.SimpleType;
import paper.libs.org.eclipse.jdt.core.dom.TagElement;
import paper.libs.org.eclipse.jdt.core.dom.TypeDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import paper.libs.org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import paper.libs.org.eclipse.jdt.internal.compiler.lookup.PackageBinding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RemapperVisitor extends SimpleRemapperVisitor {

    private final ImportRewrite importRewrite;
    private final Deque<ImportContext> importStack = new ArrayDeque<>();
    private final String simpleDeobfuscatedName;

    public RemapperVisitor(RewriteContext context, MappingSet mappings, boolean javadoc) {
        super(context, mappings, javadoc);

        this.importRewrite = context.createImportRewrite();
        importRewrite.setUseContextToFilterImplicitImports(true);

        TopLevelClassMapping primary = mappings.getTopLevelClassMapping(context.getQualifiedPrimaryType()).orElse(null);
        if (primary != null) {
            context.setPackageName(primary.getDeobfuscatedPackage().replace('/', '.'));
            this.importRewrite.setImplicitPackageName(context.getPackageName());

            this.simpleDeobfuscatedName = primary.getSimpleDeobfuscatedName();
            context.setPrimaryType(simpleDeobfuscatedName);

            List<String> implicitTypes = new ArrayList<>();
            String simpleObfuscatedName = primary.getSimpleObfuscatedName();

            @SuppressWarnings("unchecked")
            List<AbstractTypeDeclaration> types = context.getCompilationUnit().types();
            for (AbstractTypeDeclaration type : types) {
                String name = type.getName().getIdentifier();
                if (name.equals(simpleObfuscatedName)) {
                    implicitTypes.add(simpleDeobfuscatedName);
                } else {
                    implicitTypes.add(mappings.getTopLevelClassMapping(context.getPackageName() + '.' + name)
                        .map(Mapping::getSimpleDeobfuscatedName)
                        .orElse(name));
                }
            }
            this.importRewrite.setImplicitTypes(implicitTypes);
        } else {
            this.simpleDeobfuscatedName = null;
        }
    }

    private void remapType(SimpleName node, ITypeBinding binding) {
        if (binding.isTypeVariable() || GracefulCheck.checkGracefully(this.context, binding)) {
            return;
        }

        if (binding.getBinaryName() == null) {
            throw new IllegalStateException("Binary name for binding " + binding.getQualifiedName() + " is null. Did you forget to add a library to the classpath?");
        }

        ClassMapping<?, ?> mapping = this.mappings.computeClassMapping(binding.getBinaryName()).orElse(null);

        if (node.getParent() instanceof AbstractTypeDeclaration
                || node.getParent() instanceof QualifiedType
                || node.getParent() instanceof NameQualifiedType
                || binding.isLocal()) {
            if (mapping != null) {
                updateIdentifier(node, mapping.getSimpleDeobfuscatedName());
            }
            return;
        }

        String qualifiedName = (mapping != null ? mapping.getFullDeobfuscatedName().replace('/', '.') : binding.getBinaryName()).replace('$', '.');
        String newName = this.importRewrite.addImport(qualifiedName, this.importStack.peek());

        if (!node.getIdentifier().equals(newName) && !node.isVar()) {
            if (newName.indexOf('.') == -1) {
                this.context.createASTRewrite().set(node, SimpleName.IDENTIFIER_PROPERTY, newName, null);
            } else {
                // Qualified name
                this.context.createASTRewrite().replace(node, node.getAST().newName(newName), null);
            }
        }
    }

    private void remapQualifiedType(QualifiedName node, ITypeBinding binding) {
        String binaryName = binding.getBinaryName();
        if (binaryName == null) {
            if (this.context.getMercury().isGracefulClasspathChecks() || this.context.getMercury().isGracefulJavadocClasspathChecks() && GracefulCheck.isJavadoc(node)) {
                return;
            }
            throw new IllegalStateException("No binary name for " + binding.getQualifiedName());
        }
        TopLevelClassMapping mapping = this.mappings.getTopLevelClassMapping(binaryName).orElse(null);

        if (mapping == null) {
            return;
        }

        String newName = mapping.getDeobfuscatedName().replace('/', '.');
        if (binaryName.equals(newName)) {
            return;
        }

        this.context.createASTRewrite().replace(node, node.getAST().newName(newName), null);
    }

    private void remapInnerType(QualifiedName qualifiedName, ITypeBinding outerClass) {
        final String binaryName = outerClass.getBinaryName();
        if (binaryName == null) {
            if (this.context.getMercury().isGracefulClasspathChecks()) {
                return;
            }
            throw new IllegalStateException("No binary name for " + outerClass.getQualifiedName());
        }

        ClassMapping<?, ?> outerClassMapping = this.mappings.computeClassMapping(binaryName).orElse(null);
        if (outerClassMapping == null) {
            return;
        }

        SimpleName node = qualifiedName.getName();
        InnerClassMapping mapping = outerClassMapping.getInnerClassMapping(node.getIdentifier()).orElse(null);
        if (mapping == null) {
            return;
        }

        updateIdentifier(node, mapping.getDeobfuscatedName());
    }

    @Override
    protected void visit(SimpleName node, IBinding binding) {
        switch (binding.getKind()) {
            case IBinding.TYPE:
                remapType(node, (ITypeBinding) binding);
                break;
            case IBinding.METHOD:
            case IBinding.VARIABLE:
                super.visit(node, binding);
                break;
            case IBinding.PACKAGE:
                // This is ignored because it should be covered by separate handling
                // of QualifiedName (for full-qualified class references),
                // PackageDeclaration and ImportDeclaration
            default:
                throw new IllegalStateException("Unhandled binding: " + binding.getClass().getSimpleName() + " (" + binding.getKind() + ')');
        }
    }

    @Override
    public boolean visit(final TagElement tag) {
        // We don't want to visit the names of some Javadoc tags, since they can't be remapped.
        if (TagElement.TAG_LINK.equals(tag.getTagName())) {
            // With a @link tag, the first fragment will be a name
            if (tag.fragments().size() >= 1) {
                final Object fragment = tag.fragments().get(0);

                // A package might be a SimpleName (test), or a QualifiedName (test.test)
                if (fragment instanceof Name) {
                    final Name name = (Name) fragment;
                    final IBinding binding = name.resolveBinding();

                    if (binding != null) {
                        // We can't remap packages, so don't visit package names
                        if (binding.getKind() == IBinding.PACKAGE) {
                            return false;
                        }
                    }
                }
            }
        }

        return super.visit(tag);
    }

    @Override
    public boolean visit(QualifiedName node) {
        IBinding binding = node.resolveBinding();
        if (binding == null) {
            if (this.context.getMercury().isGracefulClasspathChecks()) {
                return false;
            }
            throw new IllegalStateException("No binding for qualified name node " + node.getFullyQualifiedName());
        }

        if (binding.getKind() != IBinding.TYPE) {
            // Unpack the qualified name and remap method/field and type separately
            return true;
        }

        Name qualifier = node.getQualifier();
        IBinding qualifierBinding = qualifier.resolveBinding();
        switch (qualifierBinding.getKind()) {
            case IBinding.PACKAGE:
                // Remap full qualified type
                remapQualifiedType(node, (ITypeBinding) binding);
                break;
            case IBinding.TYPE:
                // Remap inner type separately
                remapInnerType(node, (ITypeBinding) qualifierBinding);

                // Remap the qualifier
                qualifier.accept(this);
                break;
            default:
                throw new IllegalStateException("Unexpected qualifier binding: " + binding.getClass().getSimpleName() + " (" + binding.getKind() + ')');
        }

        return false;
    }

    @Override
    public boolean visit(NameQualifiedType node) {
        // Annotated inner class -> com.package.Outer.@NonNull Inner
        // existing mechanisms will handle
        final IBinding qualBinding = node.getQualifier().resolveBinding();
        if (qualBinding != null && qualBinding.getKind() == IBinding.TYPE) {
            return true;
        }

        ITypeBinding binding = node.getName().resolveTypeBinding();
        if (binding == null) {
            if (this.context.getMercury().isGracefulClasspathChecks()) {
                return false;
            }
            throw new IllegalStateException("No binding for qualified name node " + node.getName());
        }

        final ClassMapping<?, ?> classMapping = this.mappings.computeClassMapping(binding.getBinaryName()).orElse(null);
        if (classMapping == null) {
            return false;
        }

        // qualified -> default package (test.@NonNull ObfClass -> @NonNull Core):
        final String deobfPackage = classMapping.getDeobfuscatedPackage();
        final ASTRewrite rewrite = this.context.createASTRewrite();
        if (deobfPackage == null || deobfPackage.isEmpty()) {
            // if we have annotations, those need to be moved to a new SimpleType node
            final ASTNode nameNode;
            if (node.isAnnotatable() && !node.annotations().isEmpty()) {
                final SimpleType type = node.getName().getAST().newSimpleType((Name) rewrite.createCopyTarget(node.getName()));
                transferAnnotations(node, type);
                nameNode = type;
            } else {
                nameNode = node.getName();
            }
            rewrite.replace(node, nameNode, null);
        } else {
            // qualified -> other qualified:
            rewrite.set(node, NameQualifiedType.QUALIFIER_PROPERTY, node.getAST().newName(deobfPackage.replace('/', '.')), null);
        }
        node.getName().accept(this);

        return false;
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        String currentPackage = node.getName().getFullyQualifiedName();

        if (this.context.getPackageName().isEmpty()) {
            // remove package declaration if remapped to root package
            this.context.createASTRewrite().remove(node, null);
        } else if (!currentPackage.equals(this.context.getPackageName())) {
            this.context.createASTRewrite().replace(node.getName(), node.getAST().newName(this.context.getPackageName()), null);
        }

        return false;
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        if (node.isStatic()) {
            // Remap class/member reference separately
            return true;
        }

        IBinding binding = node.resolveBinding();
        if (binding != null) {
            switch (binding.getKind()) {
                case IBinding.TYPE:
                    ITypeBinding typeBinding = (ITypeBinding) binding;
                    String name = typeBinding.getBinaryName();
                    if (name == null) {
                        if (this.context.getMercury().isGracefulClasspathChecks()) {
                            return false;
                        }
                        throw new IllegalStateException("No binary name for " + typeBinding.getQualifiedName() + ". Did you add the library to the classpath?");
                    }

                    ClassMapping<?, ?> mapping = this.mappings.computeClassMapping(name).orElse(null);
                    if (mapping != null && !name.equals(mapping.getFullDeobfuscatedName().replace('/', '.'))) {
                        this.importRewrite.removeImport(typeBinding.getQualifiedName());
                    } else if (this.simpleDeobfuscatedName != null && this.simpleDeobfuscatedName.equals(typeBinding.getName())) {
                        this.importRewrite.removeImport(typeBinding.getQualifiedName());
                    }

                    break;
            }
        }
        return false;
    }

    private void pushImportContext(ITypeBinding binding) {
        ImportContext context = new ImportContext(this.importRewrite.getDefaultImportRewriteContext(), this.importStack.peek());
        collectImportContext(context, binding);
        this.importStack.push(context);
    }

    private void collectImportContext(ImportContext context, ITypeBinding binding) {
        if (binding == null) {
            return;
        }

        // Names from inner classes
        for (ITypeBinding inner : binding.getDeclaredTypes()) {
            if (GracefulCheck.checkGracefully(this.context, inner)) {
                continue;
            }

            int modifiers = inner.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                // Inner type must be declared in this compilation unit
                if (this.context.getCompilationUnit().findDeclaringNode(inner) == null) {
                    continue;
                }
            }

            ClassMapping<?, ?> mapping = this.mappings.computeClassMapping(inner.getBinaryName()).orElse(null);

            if (isPackagePrivate(modifiers)) {
                // Must come from the same package
                String packageName = mapping != null ? mapping.getDeobfuscatedPackage() : inner.getPackage().getName();
                if (!packageName.replace('/', '.').equals(this.context.getPackageName().replace('/', '.'))) {
                    continue;
                }
            }

            String simpleName;
            String qualifiedName;
            if (mapping != null) {
                simpleName = mapping.getSimpleDeobfuscatedName();
                qualifiedName = mapping.getFullDeobfuscatedName().replace('/', '.').replace('$', '.');
            } else {
                simpleName = inner.getName();
                qualifiedName = inner.getBinaryName().replace('$', '.');
            }

            if (!context.conflicts.contains(simpleName)) {
                String current = context.implicit.putIfAbsent(simpleName, qualifiedName);
                if (current != null && !current.equals(qualifiedName)) {
                    context.implicit.remove(simpleName);
                    context.conflicts.add(simpleName);
                }
            }
        }

        // Inherited names
        collectImportContext(context, binding.getSuperclass());
        for (ITypeBinding parent : binding.getInterfaces()) {
            collectImportContext(context, parent);
        }
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        pushImportContext(node.resolveBinding());
        return true;
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
        pushImportContext(node.resolveBinding());
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        pushImportContext(node.resolveBinding());
        return true;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        pushImportContext(node.resolveBinding());
        return true;
    }

    @Override
    public void endVisit(AnnotationTypeDeclaration node) {
        this.importStack.pop();
    }

    @Override
    public void endVisit(AnonymousClassDeclaration node) {
        this.importStack.pop();
    }

    @Override
    public void endVisit(EnumDeclaration node) {
        this.importStack.pop();
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        this.importStack.pop();
    }

    private void transferAnnotations(final AnnotatableType oldNode, final AnnotatableType newNode) {
        // we don't support type annotations, ignore
        if (newNode.getAST().apiLevel() < AST.JLS8) {
            return;
        }
        if (oldNode.annotations().isEmpty()) {
            // none to transfer
            return;
        }

        // transfer and visit
        final ListRewrite rewrite = this.context.createASTRewrite().getListRewrite(newNode, newNode.getAnnotationsProperty());
        for (Object annotation : oldNode.annotations()) {
            final ASTNode annotationNode = (ASTNode) annotation;
            annotationNode.accept(this);
            rewrite.insertLast(annotationNode, null);
        }
    }

    private static class ImportContext extends ImportRewrite.ImportRewriteContext {
        private final ImportRewrite.ImportRewriteContext defaultContext;
        final Map<String, String> implicit;
        final Set<String> conflicts;

        ImportContext(ImportRewrite.ImportRewriteContext defaultContext, ImportContext parent) {
            this.defaultContext = defaultContext;
            if (parent != null) {
                this.implicit = new HashMap<>(parent.implicit);
                this.conflicts = new HashSet<>(parent.conflicts);
            } else {
                this.implicit = new HashMap<>();
                this.conflicts = new HashSet<>();
            }
        }

        @Override
        public int findInContext(String qualifier, String name, int kind) {
            int result = this.defaultContext.findInContext(qualifier, name, kind);
            if (result != RES_NAME_UNKNOWN) {
                return result;
            }

            if (kind == KIND_TYPE) {
                String current = implicit.get(name);
                if (current != null) {
                    return current.equals(qualifier + '.' + name) ? RES_NAME_FOUND : RES_NAME_CONFLICT;
                }

                if (conflicts.contains(name)) {
                    return RES_NAME_CONFLICT;  // TODO
                }
            }

            return RES_NAME_UNKNOWN;
        }
    }

}
