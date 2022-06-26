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

import static paper.libs.org.cadixdev.mercury.util.BombeBindings.convertSignature;

import paper.libs.org.cadixdev.bombe.analysis.InheritanceProvider;
import paper.libs.org.cadixdev.bombe.type.signature.FieldSignature;
import paper.libs.org.cadixdev.bombe.type.signature.MethodSignature;
import paper.libs.org.cadixdev.lorenz.MappingSet;
import paper.libs.org.cadixdev.lorenz.model.ClassMapping;
import paper.libs.org.cadixdev.lorenz.model.FieldMapping;
import paper.libs.org.cadixdev.lorenz.model.InnerClassMapping;
import paper.libs.org.cadixdev.lorenz.model.MemberMapping;
import paper.libs.org.cadixdev.lorenz.model.MethodMapping;
import paper.libs.org.cadixdev.lorenz.model.MethodParameterMapping;
import paper.libs.org.cadixdev.mercury.RewriteContext;
import paper.libs.org.cadixdev.mercury.analysis.MercuryInheritanceProvider;
import paper.libs.org.cadixdev.mercury.util.GracefulCheck;
import paper.libs.org.eclipse.jdt.core.dom.ASTNode;
import paper.libs.org.eclipse.jdt.core.dom.ASTVisitor;
import paper.libs.org.eclipse.jdt.core.dom.Block;
import paper.libs.org.eclipse.jdt.core.dom.IBinding;
import paper.libs.org.eclipse.jdt.core.dom.IMethodBinding;
import paper.libs.org.eclipse.jdt.core.dom.ITypeBinding;
import paper.libs.org.eclipse.jdt.core.dom.IVariableBinding;
import paper.libs.org.eclipse.jdt.core.dom.LambdaExpression;
import paper.libs.org.eclipse.jdt.core.dom.MethodDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.SimpleName;
import paper.libs.org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import paper.libs.org.eclipse.jdt.core.dom.VariableDeclaration;
import paper.libs.org.eclipse.jdt.internal.compiler.lookup.PackageBinding;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Remaps only methods, fields, and parameters.
 */
public class SimpleRemapperVisitor extends ASTVisitor {

    private static final String LVT_NAMES_PROPERTY = "org.cadixdev.mercury.lvtNames";
    private static final String LOCAL_VARIABLE_NAME_PROPERTY = "org.cadixdev.mercury.localVariableName";
    private static final String NEW_PARAM_NAMES_PROPERTY = "org.cadixdev.mercury.newParamNames";

    final RewriteContext context;
    final MappingSet mappings;
    private final InheritanceProvider inheritanceProvider;

    public SimpleRemapperVisitor(RewriteContext context, MappingSet mappings, boolean javadoc) {
        super(javadoc);
        this.context = context;
        this.mappings = mappings;
        this.inheritanceProvider = MercuryInheritanceProvider.get(context.getMercury());
    }

    final void updateIdentifier(SimpleName node, String newName) {
        if (!node.getIdentifier().equals(newName) && !node.isVar()) {
            this.context.createASTRewrite().set(node, SimpleName.IDENTIFIER_PROPERTY, newName, null);
        }
    }

    private void remapMethod(SimpleName node, IMethodBinding binding) {
        ITypeBinding declaringClass = binding.getDeclaringClass();
        if (GracefulCheck.checkGracefully(this.context, declaringClass)) {
            return;
        }
        final ClassMapping<?, ?> classMapping = this.mappings.getOrCreateClassMapping(declaringClass.getBinaryName());

        if (binding.isConstructor()) {
            updateIdentifier(node, classMapping.getSimpleDeobfuscatedName());
        } else {
            final MethodMapping mapping = findMethodMapping(declaringClass, binding);
            if (mapping == null) {
                return;
            }

            updateIdentifier(node, mapping.getDeobfuscatedName());
        }
    }

    private void remapField(SimpleName node, IVariableBinding binding) {
        if (!binding.isField()) {
            if (binding.isParameter()) {
                remapParameter(node, binding);
            } else {
                checkLocalVariable(node, binding);
            }

            return;
        }

        ITypeBinding declaringClass = binding.getDeclaringClass();
        if (declaringClass == null) {
            return;
        }

        ClassMapping<?, ?> classMapping = this.mappings.getClassMapping(declaringClass.getBinaryName()).orElse(null);
        if (classMapping == null) {
            return;
        }

        FieldSignature bindingSignature = convertSignature(binding);
        FieldMapping mapping = findMemberMapping(bindingSignature, classMapping, ClassMapping::computeFieldMapping);
        if (mapping == null) {
            return;
        }

        updateIdentifier(node, mapping.getDeobfuscatedName());
    }

    private MethodMapping findMethodMapping(ITypeBinding declaringClass, IMethodBinding declaringMethod) {
        final ClassMapping<?, ?> classMapping = this.mappings.getClassMapping(declaringClass.getBinaryName()).orElse(null);
        if (classMapping == null) {
            return null;
        }

        final MethodSignature methodSig = convertSignature(declaringMethod);
        MethodMapping methodMapping = findMemberMapping(methodSig, classMapping, ClassMapping::getMethodMapping);
        if (methodMapping == null) {
            classMapping.complete(this.inheritanceProvider, declaringClass);
            methodMapping = classMapping.getMethodMapping(methodSig).orElse(null);
        }

        return methodMapping;
    }

    private <T extends MemberMapping<?, ?>, M> T findMemberMapping(
        M matcher,
        ClassMapping<?, ?> classMapping,
        BiFunction<ClassMapping<?, ?>, M, Optional<? extends T>> getMapping
    ) {
        T mapping = getMapping.apply(classMapping, matcher).orElse(null);
        if (mapping != null) {
            return mapping;
        }

        if (!this.context.getMercury().isFlexibleAnonymousClassMemberLookups()) {
            return null;
        }
        return findMemberMappingAnonClass(matcher, classMapping, getMapping);
    }

    private <T extends MemberMapping<?, ?>, M> T findMemberMappingAnonClass(
        M matcher,
        ClassMapping<?, ?> classMapping,
        BiFunction<ClassMapping<?, ?>, M, Optional<? extends T>> getMapping
    ) {
        // If neither name is different then this method won't do anything
        if (Objects.equals(classMapping.getObfuscatedName(), classMapping.getDeobfuscatedName())) {
            return null;
        }
        // Anonymous classes must be inner classes
        if (!(classMapping instanceof InnerClassMapping)) {
            return null;
        }
        // Verify this is inner class is anonymous
        if (!classMapping.getObfuscatedName().chars().allMatch(Character::isDigit)) {
            return null;
        }
        ClassMapping<?, ?> parentMapping = ((InnerClassMapping) classMapping).getParent();
        if (parentMapping == null) {
            return null;
        }

        // Find a sibling anonymous class whose obfuscated name is our deobfuscated name
        ClassMapping<?, ?> otherClassMapping = parentMapping
                .getInnerClassMapping(classMapping.getDeobfuscatedName()).orElse(null);
        if (otherClassMapping != null) {
            T mapping = getMapping.apply(otherClassMapping, matcher).orElse(null);
            if (mapping != null) {
                return mapping;
            }
        }

        // Find a sibling anonymous class whose deobfuscated name is our obfuscated name
        // We have to do something a little less direct for this case
        for (InnerClassMapping innerClassMapping : parentMapping.getInnerClassMappings()) {
            if (Objects.equals(classMapping.getObfuscatedName(), innerClassMapping.getDeobfuscatedName())) {
                otherClassMapping = innerClassMapping;
                break;
            }
        }
        if (otherClassMapping == null) {
            return null;
        }
        return getMapping.apply(otherClassMapping, matcher).orElse(null);
    }

    private void remapParameter(SimpleName node, IVariableBinding binding) {
        IMethodBinding declaringMethod = binding.getDeclaringMethod();
        if (declaringMethod == null) {
            return;
        }

        int index = -1;

        ASTNode n = context.getCompilationUnit().findDeclaringNode(declaringMethod);

        if (n instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = (MethodDeclaration) n;

            @SuppressWarnings("unchecked")
            List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();

            for (int i = 0; i < parameters.size(); i++) {
                if (binding.equals(parameters.get(i).resolveBinding())) {
                    index = i;
                }
            }
        }

        if (index == -1) {
            return;
        }

        final ITypeBinding declaringClass = declaringMethod.getDeclaringClass();
        if (declaringClass == null) {
            return;
        }

        final MethodMapping methodMapping = findMethodMapping(declaringClass, declaringMethod);
        if (methodMapping == null) {
            return;
        }

        methodMapping.getParameterMapping(index).ifPresent(paramMapping -> updateIdentifier(node, paramMapping.getDeobfuscatedName()));
    }

    /**
     * Check if a local variable needs to be renamed because it conflicts with a new parameter name. This will also
     * attempt to check cases where local variables are defined in lambda expressions.
     *
     * @param node The local variable node to check
     * @param binding The variable binding corresponding to the local variable name
     */
    private void checkLocalVariable(SimpleName node, IVariableBinding binding) {
        final ASTNode bindingNode = this.context.getCompilationUnit().findDeclaringNode(binding);
        final String localVariableName = bindingNode != null ? (String) bindingNode.getProperty(LOCAL_VARIABLE_NAME_PROPERTY) : null; // modified
        if (localVariableName != null) {
            updateIdentifier(node, localVariableName);
            return;
        }

        IMethodBinding declaringMethod = binding.getDeclaringMethod();
        if (declaringMethod == null) {
            return;
        }

        if (declaringMethod.getDeclaringMember() != null) {
            // lambda method
            final LambdaExpression lambdaExpr = getLambdaMethodDeclaration(declaringMethod);
            if (lambdaExpr == null) {
                return;
            }

            // Climb out of declaring stack until we find a method which isn't a lambda
            IMethodBinding outerMethod = declaringMethod;
            while (outerMethod.getDeclaringMember() instanceof IMethodBinding) {
                outerMethod = (IMethodBinding) outerMethod.getDeclaringMember();
            }
            if (outerMethod == declaringMethod) {
                // lookup failed, nothing we can do
                return;
            }
            final ASTNode n = this.context.getCompilationUnit().findDeclaringNode(outerMethod);
            if (!(n instanceof MethodDeclaration)) {
                return;
            }
            final MethodDeclaration outerDeclaration = (MethodDeclaration) n;

            ASTNode body = lambdaExpr.getBody();
            // might be an expression
            if (!(body instanceof Block)) {
                body = null;
            }
            this.checkLocalVariableWithMappings(node, bindingNode, outerMethod, outerDeclaration, declaringMethod, (Block) body);
        } else {
            final ASTNode n = context.getCompilationUnit().findDeclaringNode(declaringMethod);
            if (!(n instanceof MethodDeclaration)) {
                return;
            }
            final MethodDeclaration methodDeclaration = (MethodDeclaration) n;

            this.checkLocalVariableWithMappings(node, bindingNode, declaringMethod, methodDeclaration, declaringMethod, methodDeclaration.getBody());
        }
    }

    /**
     * Using the given mappings and bindings, check if there are mappings for the method and if any of them conflict
     * with the given local variable name.
     *
     * @param node The local variable name to check
     * @param bindingNode The binding of the local variable declaration
     * @param binding The binding of the mapped method to check
     * @param declaration The declaration node of the mapped method to check
     * @param blockDeclaringMethod The method binding of the method which defines the {@code block}
     * @param body The method body to check for local variables
     */
    private void checkLocalVariableWithMappings(
            SimpleName node,
            ASTNode bindingNode,
            IMethodBinding binding,
            MethodDeclaration declaration,
            IMethodBinding blockDeclaringMethod,
            Block body
    ) {
        final ITypeBinding declaringClass = binding.getDeclaringClass();
        this.mappings.getClassMapping(declaringClass.getBinaryName())
                .flatMap(classMapping -> {
                    classMapping.complete(this.inheritanceProvider, declaringClass);
                    return classMapping.getMethodMapping(convertSignature(binding));
                })
                .ifPresent(methodMapping -> {
                    if (!methodMapping.getParameterMappings().isEmpty()) {
                        final Set<String> newParamNames = newParamNames(declaration, methodMapping);
                        checkLocalVariableForConflicts(node, bindingNode, blockDeclaringMethod, body, newParamNames);
                    }
                });
    }

    /**
     * Check the method's body defined by {@code methodDeclaration} to collect all local variable names in order to
     * find a suitable replacement name for {@code node} if it clashes with a name in {@code newParamNames}.
     *
     * @param node The local variable node to check
     * @param bindingNode The binding of the local variable declaration
     * @param blockDeclaringMethod The method binding of the method which defines the {@code block}
     * @param block The method body implementation to collect local variable names from
     * @param newParamNames The set of parameter names after mapping
     */
    private void checkLocalVariableForConflicts(
            SimpleName node,
            ASTNode bindingNode,
            IMethodBinding blockDeclaringMethod,
            Block block,
            Set<String> newParamNames
    ) {
        final String name = node.getIdentifier();
        if (!newParamNames.contains(name)) {
            return;
        }

        // the new param name will screw up this local variable
        final Set<String> localVariableNames = collectLocalVariableNames(blockDeclaringMethod, block);
        int counter = 1;
        String newName = name + counter;
        while (localVariableNames.contains(newName) || newParamNames.contains(newName)) {
            counter++;
            newName = name + counter;
        }

        localVariableNames.add(newName);
        bindingNode.setProperty(LOCAL_VARIABLE_NAME_PROPERTY, newName);
        updateIdentifier(node, newName);
    }

    /**
     * Find the declaration of the actual method block for the lambda method
     *
     * @param declaringMethod The method binding for the lambda method to check
     * @return The {@link MethodDeclaration} corresponding to the code block of the lambda implementation
     */
    private LambdaExpression getLambdaMethodDeclaration(IMethodBinding declaringMethod) {
        final ASTNode node = this.context.getCompilationUnit().findDeclaringNode(declaringMethod.getKey());
        if (node instanceof LambdaExpression) {
            return (LambdaExpression) node;
        }
        return null;
    }

    /**
     * Read the method body of {@code methodDeclaration} and return the set of local variable names defined inside of
     * it. The set is cached on the {@code methodDeclaration} so it is only computed once.
     *
     * @param blockDeclaringMethod The method binding of the method which defines the {@code block}
     * @param block The method body implementation to check.
     * @return The set of local variable names defined in the method body.
     */
    private Set<String> collectLocalVariableNames(IMethodBinding blockDeclaringMethod, Block block) {
        if (block == null) {
            return Collections.emptySet();
        }

        Set<String> result = checkProperty(LVT_NAMES_PROPERTY, block);
        if (result != null) {
            return result;
        }
        result = new HashSet<>();
        block.setProperty(LVT_NAMES_PROPERTY, result);

        final IVariableBinding[] synthLocals = blockDeclaringMethod.getSyntheticOuterLocals();
        for (final IVariableBinding synthLocal : synthLocals) {
            final String name = synthLocal.getName();
            if (name.startsWith("val$")) {
                result.add(name.substring(4));
            }
        }

        @SuppressWarnings("unchecked")
        final List<ASTNode> statements = (List<ASTNode>) block.statements();
        for (final ASTNode statement : statements) {
            if (!(statement instanceof VariableDeclaration)) {
                continue;
            }
            final VariableDeclaration declaration = (VariableDeclaration) statement;
            result.add(declaration.getName().getIdentifier());
        }

        return result;
    }

    /**
     * Check the parameter names defined by the {@code methodDeclaration} and apply any mappings based on the given
     * {@code mapping}. Return the list of params post-remap.
     *
     * @param methodDeclaration The method declaration to check the parameter names on.
     * @param mapping The mapping to use to determine the new parameter names
     * @return The set of parameter names after remapping them with {@code mapping}.
     */
    private Set<String> newParamNames(MethodDeclaration methodDeclaration, MethodMapping mapping) {
        Set<String> result = checkProperty(NEW_PARAM_NAMES_PROPERTY, methodDeclaration);
        if (result != null) {
            return result;
        }
        result = new HashSet<>();
        methodDeclaration.setProperty(NEW_PARAM_NAMES_PROPERTY, result);

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            final Optional<MethodParameterMapping> paramMapping = mapping.getParameterMapping(i);
            if (paramMapping.isPresent()) {
                result.add(paramMapping.get().getDeobfuscatedName());
            } else {
                result.add(parameters.get(i).getName().getIdentifier());
            }
        }

        return result;
    }

    /**
     * Check if the given node contains a {@code Set<String>} property named {@code propName} and return it if so.
     * Returns {@code null} if not.
     *
     * @param propName The name of the property
     * @param node The node to check the property on
     * @return The set stored on the node or {@code null} if empty
     */
    private static Set<String> checkProperty(String propName, ASTNode node) {
        if (node == null) {
            return null;
        }
        final Object value = node.getProperty(propName);
        if (value instanceof Set) {
            @SuppressWarnings("unchecked") final Set<String> result = (Set<String>) value;
            return result;
        }
        return null;
    }

    protected void visit(SimpleName node, IBinding binding) {
        switch (binding.getKind()) {
            case IBinding.METHOD:
                remapMethod(node, ((IMethodBinding) binding).getMethodDeclaration());
                break;
            case IBinding.VARIABLE:
                remapField(node, ((IVariableBinding) binding).getVariableDeclaration());
                break;
        }
    }

    @Override
    public final boolean visit(SimpleName node) {
        IBinding binding = node.resolveBinding();
        if (binding != null) {
            visit(node, binding);
        }
        return false;
    }

}
