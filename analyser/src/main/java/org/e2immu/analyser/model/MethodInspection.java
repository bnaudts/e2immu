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

package org.e2immu.analyser.model;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@NotNull
public class MethodInspection extends Inspection {

    public final String fullyQualifiedName;
    public final String distinguishingName;

    public final MethodInfo methodInfo; // backlink, container... will become contextclass+immutable eventually
    public final ParameterizedType returnType; // ContextClass
    //@Immutable(after="??")
    public final FirstThen<BlockStmt, Block> methodBody;

    //@Immutable(level = 2, after="MethodAnalyzer.analyse()")
    //@Immutable
    public final List<ParameterInfo> parameters;
    //@Immutable
    public final List<MethodModifier> modifiers;

    //@Immutable
    public final List<TypeParameter> typeParameters;
    //@Immutable
    public final List<ParameterizedType> exceptionTypes;

    // if our type implements a number of interfaces, then the method definitions in these interfaces
    // that this method implements, are represented in this variable
    // this is used to check inherited annotations on methods
    //@Immutable
    public final List<MethodInfo> implementationOf;

    private MethodInspection(MethodInfo methodInfo,
                             List<MethodModifier> modifiers,
                             List<ParameterInfo> parameters,
                             ParameterizedType returnType,
                             List<AnnotationExpression> annotations,
                             List<TypeParameter> typeParameters,
                             List<ParameterizedType> exceptionTypes,
                             List<MethodInfo> implementationOf,
                             FirstThen<BlockStmt, Block> methodBody) {
        super(annotations);
        this.modifiers = modifiers;
        this.methodInfo = methodInfo;
        this.parameters = parameters;
        this.returnType = returnType;
        this.typeParameters = typeParameters;
        this.methodBody = methodBody;
        this.exceptionTypes = exceptionTypes;
        this.implementationOf = implementationOf;
        fullyQualifiedName = methodInfo.typeInfo.fullyQualifiedName + "." + methodInfo.name + "(" + parameters.stream()
                .map(p -> p.parameterizedType.stream(p.parameterInspection.get().varArgs))
                .collect(Collectors.joining(",")) + ")";
        distinguishingName = methodInfo.typeInfo.fullyQualifiedName + "." + methodInfo.name + "(" + parameters.stream()
                .map(p -> p.parameterizedType.distinguishingStream(p.parameterInspection.get().varArgs))
                .collect(Collectors.joining(",")) + ")";
    }

    public MethodInspection copy(List<AnnotationExpression> alternativeAnnotations) {
        return new MethodInspection(methodInfo, modifiers, parameters, returnType,
                ImmutableList.copyOf(alternativeAnnotations), typeParameters, exceptionTypes, implementationOf, methodBody);
    }

    public boolean haveCodeBlock() {
        return methodBody.isSet() && !methodBody.get().statements.isEmpty() ||
                !methodBody.isSet() && !methodBody.getFirst().getStatements().isEmpty();
    }

    @Container(builds = MethodInspection.class)
    public static class MethodInspectionBuilder implements BuilderWithAnnotations<MethodInspectionBuilder> {
        private final List<ParameterInfo> parameters = new ArrayList<>();
        private final List<MethodModifier> modifiers = new ArrayList<>();
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private final List<MethodInfo> implementationsOf = new ArrayList<>();
        private BlockStmt block;
        private Block alreadyKnown;
        private final List<ParameterizedType> exceptionTypes = new ArrayList<>();
        private ParameterizedType returnType;

        @Fluent
        public MethodInspectionBuilder setReturnType(ParameterizedType returnType) {
            this.returnType = returnType;
            return this;
        }

        @Fluent
        public MethodInspectionBuilder setReturnType(@NotNull TypeInfo returnType) {
            this.returnType = returnType.asParameterizedType();
            return this;
        }

        @Fluent
        public MethodInspectionBuilder setBlock(BlockStmt block) {
            this.block = block;
            return this;
        }

        @Fluent
        public MethodInspectionBuilder setBlock(Block alreadyKnown) {
            this.alreadyKnown = alreadyKnown;
            return this;
        }

        @Fluent
        public MethodInspectionBuilder addParameter(@NotNull ParameterInfo parameterInfo) {
            parameters.add(parameterInfo);
            return this;
        }

        @Fluent
        public MethodInspectionBuilder addModifier(@NotNull MethodModifier methodModifier) {
            modifiers.add(methodModifier);
            return this;
        }

        @Fluent
        public MethodInspectionBuilder addExceptionType(@NotNull ParameterizedType exceptionType) {
            exceptionTypes.add(exceptionType);
            return this;
        }

        @Fluent
        public MethodInspectionBuilder addTypeParameter(@NotNull TypeParameter typeParameter) {
            typeParameters.add(typeParameter);
            if (!typeParameter.isMethodTypeParameter()) throw new IllegalArgumentException();
            return this;
        }

        @Fluent
        @Override
        public MethodInspectionBuilder addAnnotation(@NotNull AnnotationExpression annotation) {
            annotations.add(annotation);
            return this;
        }

        @NotModified
        @NotNull
        public MethodInspection build(MethodInfo methodInfo) {
            if (methodInfo.isConstructor) {
                returnType = ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            } else {
                Objects.requireNonNull(returnType);
            }
            FirstThen<BlockStmt, Block> methodBody = new FirstThen<>(block != null ? block : new BlockStmt());
            if (alreadyKnown != null) methodBody.set(alreadyKnown);
            for (TypeParameter typeParameter : typeParameters) {
                if (typeParameter.owner.isRight() && typeParameter.owner.getRight() != methodInfo) {
                    throw new UnsupportedOperationException("I cannot have type parameters owned by another method!");
                }
            }
            for (ParameterInfo parameterInfo : parameters) {
                if (parameterInfo.parameterizedType.typeParameter != null
                        && parameterInfo.parameterizedType.typeParameter.isMethodTypeParameter()
                        && parameterInfo.parameterizedType.typeParameter.owner.getRight() != methodInfo) {
                    throw new UnsupportedOperationException("I cannot have parameters of a type being a type parameter owned by another method!");
                }
            }
            return new MethodInspection(methodInfo,
                    ImmutableList.copyOf(modifiers),
                    ImmutableList.copyOf(parameters),
                    returnType,
                    ImmutableList.copyOf(annotations),
                    ImmutableList.copyOf(typeParameters),
                    ImmutableList.copyOf(exceptionTypes),
                    ImmutableList.copyOf(implementationsOf),
                    methodBody
            );
        }
    }
}
