/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.analyser.methodanalysercomponent.StaticModifier;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);

    private final TypeContext typeContext;
    private final ParameterAnalyser parameterAnalyser;
    private final ComputeLinking computeLinking;

    public MethodAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
        this.parameterAnalyser = new ParameterAnalyser(typeContext);
        this.computeLinking = new ComputeLinking(typeContext, parameterAnalyser);
    }

    public void check(MethodInfo methodInfo) {
        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(methodInfo, Independent.class, typeContext.independent.get());
        check(methodInfo, NotModified.class, typeContext.notModified.get());

        if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
            check(methodInfo, NotNull.class, typeContext.notNull.get());
            check(methodInfo, Fluent.class, typeContext.fluent.get());
            check(methodInfo, Identity.class, typeContext.identity.get());
            CheckConstant.checkConstantForMethods(typeContext, methodInfo);
        }
        methodInfo.methodAnalysis.unusedLocalVariables.visit((lv, b) -> {
            if (b)
                typeContext.addMessage(Message.Severity.ERROR, "In method " + methodInfo.fullyQualifiedName() +
                        ", local variable " + lv.name + " is not used");
        });

        methodInfo.methodInspection.get().parameters.forEach(parameterAnalyser::check);
    }

    private void check(MethodInfo methodInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Method " + methodInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @" + annotation.getTypeName()));
    }

    public boolean analyse(MethodInfo methodInfo, VariableProperties methodProperties) {
        List<Statement> statements = methodInfo.methodInspection.get().methodBody.get().statements;
        if (!statements.isEmpty()) {
            return analyseMethod(methodInfo, methodProperties, statements);
        }
        return false;
    }

    // return when there have been changes
    private boolean analyseMethod(MethodInfo methodInfo, VariableProperties methodProperties, List<Statement> statements) {
        boolean changes = false;

        log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());

        if (!methodInfo.methodAnalysis.numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            CreateNumberedStatements.recursivelyCreateNumberedStatements(statements, indices, numberedStatements, new SideEffectContext(typeContext, methodInfo));
            methodInfo.methodAnalysis.numberedStatements.set(ImmutableList.copyOf(numberedStatements));
            changes = true;
        }
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            methodProperties.create(parameterInfo, new VariableValue(parameterInfo));
        }
        if (analyseFlow(methodInfo, methodProperties)) changes = true;
        return changes;
    }

    private boolean analyseFlow(MethodInfo methodInfo, VariableProperties methodProperties) {
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;

            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeLinking.computeVariablePropertiesOfMethod(numberedStatements, methodInfo, methodProperties))
                changes = true;
            if (methodIsIndependent(methodInfo, methodAnalysis)) changes = true;
            if (StaticModifier.computeStaticMethodCallsOnly(methodInfo, methodAnalysis, numberedStatements))
                changes = true;

            long returnStatements = numberedStatements.stream().filter(ns -> ns.statement instanceof ReturnStatement).count();
            if (returnStatements > 0) {
                if (methodIsIdentity(returnStatements, numberedStatements, methodAnalysis)) changes = true;
                if (methodIsFluent(returnStatements, numberedStatements, methodAnalysis)) changes = true;
                if (methodIsNotNull(returnStatements, numberedStatements, methodInfo, methodAnalysis)) changes = true;
                if (methodIsConstant(returnStatements, numberedStatements, methodInfo, methodAnalysis)) changes = true;
            }

            if (!methodInfo.isConstructor) {
                if (methodInfo.isStatic) {
                    if (methodCreatesObjectOfSelf(numberedStatements, methodInfo, methodAnalysis)) changes = true;
                }
                StaticModifier.detectMissingStaticStatement(typeContext, methodInfo, methodAnalysis);
                if (methodIsNotModified(methodInfo, methodAnalysis)) changes = true;
            }
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    // part of @UtilityClass computation in the type analyser
    private boolean methodCreatesObjectOfSelf(List<NumberedStatement> numberedStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.createObjectOfSelf.isSet()) {
            boolean createSelf = numberedStatements.stream().flatMap(ns -> Statement.findExpressionRecursivelyInStatements(ns.statement, NewObject.class))
                    .anyMatch(no -> no.parameterizedType.typeInfo == methodInfo.typeInfo);
            log(UTILITY_CLASS, "Is {} a static non-constructor method that creates self? {}", methodInfo.fullyQualifiedName(), createSelf);
            methodAnalysis.createObjectOfSelf.set(createSelf);
            return true;
        }
        return false;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    private boolean methodIsConstant(long returnStatements, List<NumberedStatement> numberedStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.singleReturnValue.isSet()) {
            Value value;
            if (returnStatements == 1) {
                value = numberedStatements.stream()
                        .filter(ns -> ns.returnValue.isSet())
                        .map(ns -> ns.returnValue.get())
                        .findAny().orElse(UnknownValue.NO_VALUE);
            } else {
                value = new Instance(methodInfo.returnType());
            }
            if (value != UnknownValue.NO_VALUE) {
                methodAnalysis.singleReturnValue.set(value);
                AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
                methodAnalysis.annotations.put(constantAnnotation, true);
                log(CONSTANT, "Added @Constant annotation on {}", methodInfo.fullyQualifiedName());
                return true;
            }
        }
        return false;
    }

    private boolean methodIsNotNull(long returnStatements, List<NumberedStatement> numberedStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        boolean notNull = numberedStatements.stream().filter(ns -> ns.returnsNotNull.isSet() && ns.returnsNotNull.get() == Boolean.TRUE)
                .count() == returnStatements;
        if (notNull && !methodAnalysis.annotations.isSet(typeContext.notNull.get())) {
            methodAnalysis.annotations.put(typeContext.notNull.get(), true);
            log(NOT_NULL, "Set @NotNull on {}", methodInfo.fullyQualifiedName());
            return true;
        }
        boolean notNullFalse = numberedStatements.stream().anyMatch(ns -> ns.returnsNotNull.isSet() && Boolean.FALSE == ns.returnsNotNull.get());
        if (notNullFalse && !methodAnalysis.annotations.isSet(typeContext.notNull.get())) {
            methodAnalysis.annotations.put(typeContext.notNull.get(), false);
            log(NOT_NULL, "Set NOT @NotNull on {}", methodInfo.fullyQualifiedName());
            return true;
        }
        return false;
    }

    private boolean methodIsFluent(long returnStatements, List<NumberedStatement> numberedStatements, MethodAnalysis methodAnalysis) {
        boolean fluent = numberedStatements.stream().filter(ns -> onReturnStatement(ns,
                e -> ReturnStatement.isFluent(typeContext, e) == Boolean.TRUE))
                .count() == returnStatements;
        if (fluent && !methodAnalysis.annotations.isSet(typeContext.fluent.get())) {
            methodAnalysis.annotations.put(typeContext.fluent.get(), true);
            log(ANALYSER, "Set @Fluent");
            return true;
        }
        boolean fluentFalse = numberedStatements.stream().anyMatch(ns -> onReturnStatement(ns,
                e -> ReturnStatement.isFluent(typeContext, e) == Boolean.FALSE));
        if (fluentFalse && !methodAnalysis.annotations.isSet(typeContext.fluent.get())) {
            methodAnalysis.annotations.put(typeContext.fluent.get(), false);
            log(ANALYSER, "Set NOT @Fluent");
            return true;
        }
        return false;
    }

    private boolean methodIsIdentity(long returnStatements, List<NumberedStatement> numberedStatements, MethodAnalysis methodAnalysis) {
        boolean identity = numberedStatements.stream().filter(ns -> onReturnStatement(ns,
                e -> ReturnStatement.isIdentity(typeContext, e) == Boolean.TRUE))
                .count() == returnStatements;
        if (identity && !methodAnalysis.annotations.isSet(typeContext.identity.get())) {
            methodAnalysis.annotations.put(typeContext.identity.get(), true);
            log(ANALYSER, "Set @Identity");
            return true;
        }
        boolean identityFalse = numberedStatements.stream().anyMatch(ns -> onReturnStatement(ns,
                e -> ReturnStatement.isIdentity(typeContext, e) == Boolean.FALSE));
        if (identityFalse && !methodAnalysis.annotations.isSet(typeContext.identity.get())) {
            methodAnalysis.annotations.put(typeContext.identity.get(), false);
            log(ANALYSER, "Set NOT @Identity");
            return true;
        }
        return false;
    }

    private boolean methodIsNotModified(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.annotations.isSet(typeContext.notModified.get())) {
            Boolean isAllParametersNotModified = methodInfo.isAllParametersNotModified(typeContext);
            if (isAllParametersNotModified == null) {
                log(NOT_MODIFIED, "Method {}: Not deciding on @NotModified yet, delaying because of parameters",
                        methodInfo.fullyQualifiedName());
                return false;
            }
            boolean isNotModified;
            if (!isAllParametersNotModified) {
                log(NOT_MODIFIED, "Method {} cannot be @NotModified: some parameters are not @NotModified",
                        methodInfo.fullyQualifiedName());
                isNotModified = false;
            } else {
                // second step, check that no fields are modified
                if (!methodAnalysis.linksComputed.isSet()) {
                    log(NOT_MODIFIED, "Method {}: Not deciding on @NotModified yet, delaying because linking not computed");
                }
                isNotModified = methodAnalysis.contentModifications
                        .stream()
                        .filter(e -> e.getKey() instanceof FieldReference)
                        .noneMatch(Map.Entry::getValue);
                if (isNotModified) {
                    log(NOT_MODIFIED, "Mark method {} as @NotModified", methodInfo.fullyQualifiedName());
                } else {
                    log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications",
                            methodInfo.fullyQualifiedName());
                }
            }
            methodAnalysis.annotations.put(typeContext.notModified.get(), isNotModified);
            return true;
        }
        return false;
    }

    // relies on the @Linked annotation for non-constructors, does a useful computation on constructors
    private boolean methodIsIndependent(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.annotations.isSet(typeContext.independent.get())) return false;
        Boolean mark = null;
        if (methodInfo.isConstructor) {
            List<FieldInfo> fields = methodInfo.typeInfo.typeInspection.get().fields;
            if (methodAnalysis.linksComputed.isSet()) {
                mark = fields.stream().allMatch(f -> Collections.disjoint(f.fieldAnalysis.variablesLinkedToMe.get(),
                        methodInfo.methodInspection.get().parameters));
            }
        } else {
            if (methodInfo.returnType().isPrimitiveOrStringNotVoid()) mark = true;
            if (methodInfo.returnType().isEffectivelyImmutable(typeContext) == Boolean.TRUE) mark = true;
            Boolean linked = methodAnalysis.annotations.getOtherwiseNull(typeContext.linked.get());
            if (linked != null) mark = !linked;
        }
        if (mark != null) {
            methodAnalysis.annotations.put(typeContext.independent.get(), mark);
            log(INDEPENDENT, "Mark method {} " + (mark ? "" : "not ") + "independent",
                    methodInfo.fullyQualifiedName());
        } else {
            log(INDEPENDENT, "Delaying @Independent on {}", methodInfo.fullyQualifiedName());
        }
        return mark != null;
    }

    // helper
    private static Boolean onReturnStatement(NumberedStatement ns, Predicate<Expression> predicate) {
        if (ns.statement instanceof ReturnStatement) {
            ReturnStatement ret = (ReturnStatement) ns.statement;
            return predicate.test(ret.expression);
        }
        return false;
    }
}
