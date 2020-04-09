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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * all the fields are deeply immutable or in the case of TypeInfo, eventually immutable.
 */
@NotNull
@NullNotAllowed
public class TypeInspection extends Inspection {
    // the type that this inspection object belongs to
    public final TypeInfo typeInfo;
    public final boolean hasBeenDefined;

    // when this type is an inner or nested class of an enclosing class
    public final Either<String, TypeInfo> packageNameOrEnclosingType;

    public final TypeNature typeNature;

    public final ParameterizedType parentClass;

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    public final List<MethodInfo> constructors;
    public final List<MethodInfo> methods;
    public final List<FieldInfo> fields;
    public final List<TypeModifier> modifiers;
    public final List<TypeInfo> subTypes;
    public final List<TypeParameter> typeParameters;
    public final List<ParameterizedType> interfacesImplemented;

    public final SetOnceMap<MethodInfo, Set<MethodInfo>> overloads = new SetOnceMap<>();
    public final SetOnce<List<TypeInfo>> superTypes = new SetOnce<>();
    public final TypeModifier access;

    private TypeInspection(boolean hasBeenDefined,
                           TypeInfo typeInfo,
                           Either<String, TypeInfo> packageNameOrEnclosingType,
                           TypeNature typeNature,
                           List<TypeParameter> typeParameters,
                           ParameterizedType parentClass,
                           List<ParameterizedType> interfacesImplemented,
                           List<MethodInfo> constructors,
                           List<MethodInfo> methods,
                           List<FieldInfo> fields,
                           List<TypeModifier> modifiers,
                           List<TypeInfo> subTypes,
                           List<AnnotationExpression> annotations) {
        super(annotations);
        this.packageNameOrEnclosingType = packageNameOrEnclosingType;
        this.parentClass = parentClass;
        this.interfacesImplemented = interfacesImplemented;
        this.typeParameters = typeParameters;
        this.typeInfo = typeInfo;
        this.typeNature = typeNature;
        this.methods = methods;
        this.constructors = constructors;
        this.fields = fields;
        this.modifiers = modifiers;
        this.subTypes = subTypes;
        if (typeNature == TypeNature.INTERFACE || typeNature == TypeNature.PRIMITIVE
                || typeNature == TypeNature.ANNOTATION) this.hasBeenDefined = false;
        else {
            this.hasBeenDefined = hasBeenDefined;
        }
        if (modifiers.contains(TypeModifier.PUBLIC)) access = TypeModifier.PUBLIC;
        else if (modifiers.contains(TypeModifier.PROTECTED)) access = TypeModifier.PROTECTED;
        else if (modifiers.contains(TypeModifier.PRIVATE)) access = TypeModifier.PRIVATE;
        else access = TypeModifier.PACKAGE;
    }

    public boolean isClass() {
        return typeNature == TypeNature.CLASS;
    }

    public Stream<MethodInfo> constructorAndMethodStream() {
        return Stream.concat(constructors.stream(), methods.stream());
    }

    public Iterable<MethodInfo> methodsAndConstructors() {
        return Iterables.concat(methods, constructors);
    }

    public TypeInspection copy(List<AnnotationExpression> alternativeAnnotations) {
        return new TypeInspection(hasBeenDefined, typeInfo, packageNameOrEnclosingType, typeNature, typeParameters,
                parentClass, interfacesImplemented, constructors, methods, fields, modifiers, subTypes,
                ImmutableList.copyOf(alternativeAnnotations));
    }

    public static class TypeInspectionBuilder implements BuilderWithAnnotations<TypeInspectionBuilder> {
        private String packageName;
        private TypeInfo enclosingType;
        private TypeNature typeNature = TypeNature.CLASS;
        private final List<MethodInfo> methods = new ArrayList<>();
        private final List<MethodInfo> constructors = new ArrayList<>();
        private final List<FieldInfo> fields = new ArrayList<>();
        private final List<TypeModifier> modifiers = new ArrayList<>();
        private final List<TypeInfo> subTypes = new ArrayList<>();
        private final List<AnnotationExpression> annotations = new ArrayList<>();
        private final List<TypeParameter> typeParameters = new ArrayList<>();
        private ParameterizedType parentClass;
        private final List<ParameterizedType> interfacesImplemented = new ArrayList<>();

        public TypeInspectionBuilder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public TypeInspectionBuilder setEnclosingType(TypeInfo enclosingType) {
            this.enclosingType = enclosingType;
            return this;
        }

        public TypeInspectionBuilder setTypeNature(TypeNature typeNature) {
            this.typeNature = typeNature;
            return this;
        }

        public TypeInspectionBuilder setParentClass(ParameterizedType parentClass) {
            this.parentClass = parentClass;
            return this;
        }

        public TypeInspectionBuilder addField(FieldInfo fieldInfo) {
            fields.add(fieldInfo);
            return this;
        }

        public TypeInspectionBuilder addTypeModifier(TypeModifier modifier) {
            modifiers.add(modifier);
            return this;
        }

        public TypeInspectionBuilder addConstructor(MethodInfo methodInfo) {
            constructors.add(methodInfo);
            return this;
        }

        public TypeInspectionBuilder addMethod(MethodInfo methodInfo) {
            methods.add(methodInfo);
            return this;
        }

        public TypeInspectionBuilder addSubType(TypeInfo typeInfo) {
            subTypes.add(typeInfo);
            return this;
        }

        public TypeInspectionBuilder addTypeParameter(TypeParameter typeParameter) {
            typeParameters.add(typeParameter);
            return this;
        }

        public TypeInspectionBuilder addInterfaceImplemented(ParameterizedType parameterizedType) {
            interfacesImplemented.add(parameterizedType);
            return this;
        }

        @Override
        public TypeInspectionBuilder addAnnotation(AnnotationExpression annotation) {
            annotations.add(annotation);
            return this;
        }

        public TypeInspection build(boolean hasBeenDefined, TypeInfo typeInfo) {
            Objects.requireNonNull(typeNature);
            if (parentClass == null) {
                parentClass = ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT;
            } else {
                Objects.requireNonNull(parentClass);
            }
            Either<String, TypeInfo> packageNameOrEnclosingType = packageName == null ? Either.right(enclosingType) : Either.left(packageName);
            return new TypeInspection(
                    hasBeenDefined,
                    typeInfo,
                    packageNameOrEnclosingType,
                    typeNature,
                    ImmutableList.copyOf(typeParameters),
                    parentClass,
                    ImmutableList.copyOf(interfacesImplemented),
                    ImmutableList.copyOf(constructors),
                    ImmutableList.copyOf(methods),
                    ImmutableList.copyOf(fields),
                    ImmutableList.copyOf(modifiers),
                    ImmutableList.copyOf(subTypes),
                    ImmutableList.copyOf(annotations));
        }
    }

}
