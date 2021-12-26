/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * all the fields are deeply immutable or in the case of TypeInfo, eventually immutable.
 */
public interface TypeInspection extends Inspection {
    // the type that this inspection object belongs to
    TypeInfo typeInfo();

    TypeNature typeNature();

    ParameterizedType parentClass();

    //@Immutable(level = 2, after="TypeAnalyser.analyse()")
    List<MethodInfo> constructors();

    List<MethodInfo> methods();

    List<FieldInfo> fields();

    Set<TypeModifier> modifiers();

    List<TypeInfo> subTypes();

    List<TypeParameter> typeParameters();

    List<ParameterizedType> interfacesImplemented();

    TypeModifier access();

    Inspector inspector();

    /**
     * Returns the types permitted to extend from this type.
     *
     * @return The types permitted to extend from this type. Note that this list is not empty
     * if and only if the type is sealed.
     */
    List<TypeInfo> permittedWhenSealed();

    default boolean isFunctionalInterface() {
        if (typeNature() != TypeNature.INTERFACE) {
            return false;
        }
        return getAnnotations().stream().anyMatch(ann -> Primitives.isFunctionalInterfaceAnnotation(ann.typeInfo()));
    }

    default boolean onlyHasPrivateMethods() {
        return Stream.concat(methodStream(Methods.THIS_TYPE_ONLY),
                        typeInfo().typeResolution.get().superTypesExcludingJavaLangObject().stream()
                                .flatMap(t -> methodStream(Methods.THIS_TYPE_ONLY)))
                .allMatch(MethodInfo::isPrivate);
    }

    enum Methods {

        THIS_TYPE_ONLY(false, false, null),
        THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM(false, false, null),
        THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM(false, false, null),
        INCLUDE_SUBTYPES(true, false, THIS_TYPE_ONLY),
        INCLUDE_SUPERTYPES(false, true, THIS_TYPE_ONLY);

        Methods(boolean recurseIntoSubTypes, boolean recurseIntoSuperTypes, Methods nonRecursiveVariant) {
            this.recurseIntoSubTypes = recurseIntoSubTypes;
            this.recurseIntoSuperTypes = recurseIntoSuperTypes;
            this.nonRecursiveVariant = nonRecursiveVariant;
        }

        final boolean recurseIntoSubTypes;
        final boolean recurseIntoSuperTypes;
        final Methods nonRecursiveVariant;
    }

    default Stream<MethodInfo> methodsAndConstructors(Methods methodsMode) {
        return Stream.concat(constructorStream(methodsMode), methodStream(methodsMode));
    }

    default Stream<MethodInfo> methodStream(Methods methodsMode) {
        if (methodsMode.recurseIntoSubTypes) {
            return Stream.concat(nonRecursiveMethodStream(methodsMode.nonRecursiveVariant),
                    subTypes().stream()
                            .filter(subType -> subType.typeInspection.isSet())
                            .flatMap(subType -> subType.typeInspection.get().methodStream(methodsMode)));
        }
        if (methodsMode.recurseIntoSuperTypes) {
            return Stream.concat(nonRecursiveMethodStream(methodsMode.nonRecursiveVariant),
                    typeInfo().typeResolution.get().superTypesExcludingJavaLangObject().stream()
                            .flatMap(superType -> superType.typeInspection.get()
                                    .nonRecursiveMethodStream(methodsMode.nonRecursiveVariant)));
        }
        return nonRecursiveMethodStream(methodsMode);
    }

    private Stream<MethodInfo> nonRecursiveMethodStream(Methods methodsMode) {
        if (methodsMode == Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM) return methods().stream();
        return Stream.concat(methods().stream(),
                methodsInFieldInitializers(methodsMode != Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_ARTIFICIAL_SAM));
    }

    default Stream<MethodInfo> constructorStream(Methods methodsMode) {
        if (methodsMode.recurseIntoSubTypes) {
            return Stream.concat(constructors().stream(), subTypes().stream()
                    .filter(subType -> subType.typeInspection.isSet())
                    .flatMap(subType -> subType.typeInspection.get(subType.fullyQualifiedName).constructorStream(methodsMode)));
        }
        if (methodsMode.recurseIntoSuperTypes) {
            return Stream.concat(constructors().stream(),
                    typeInfo().typeResolution.get().superTypesExcludingJavaLangObject().stream()
                            .flatMap(superType -> superType.typeInspection.get().constructors().stream()));
        }
        return constructors().stream();
    }

    default Iterable<MethodInfo> methods(Methods methodsMode) {
        return () -> methodStream(methodsMode).iterator();
    }

    default List<MethodInfo> methodsAndConstructors() {
        return ListUtil.concatImmutable(methods(), constructors());
    }

    default Stream<MethodInfo> methodsInFieldInitializers(boolean alsoArtificial) {
        return fields().stream()
                .filter(fieldInfo -> fieldInfo.fieldInspection.get().fieldInitialiserIsSet())
                .map(fieldInfo -> fieldInfo.fieldInspection.get().getFieldInitialiser())
                .filter(initialiser -> initialiser.implementationOfSingleAbstractMethod() != null && (alsoArtificial || !initialiser.artificial()))
                .map(FieldInspection.FieldInitialiser::implementationOfSingleAbstractMethod);
    }

    TypeInspectionImpl.InspectionState getInspectionState();

    default int countNonStaticNonDefaultMethods(InspectionProvider inspectionProvider) {
        int sum = (int) methodStream(Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> {
                    MethodInspection inspection = inspectionProvider.getMethodInspection(m);
                    return !inspection.isStatic() && !inspection.isDefault();
                }).count();
        for (ParameterizedType superInterface : interfacesImplemented()) {
            TypeInspection typeInspectionOfSuperType = inspectionProvider.getTypeInspection(superInterface.typeInfo);
            sum += typeInspectionOfSuperType.countNonStaticNonDefaultMethods(inspectionProvider);
        }
        return sum;
    }

    /**
     * This is the starting place to compute all types that are referred to in any way.
     *
     * @return a map of all types referenced, with the boolean indicating explicit reference somewhere
     */
    default UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parentClass() == null ? UpgradableBooleanMap.of() : parentClass().typesReferenced(true),
                typeInfo().packageNameOrEnclosingType.isRight() && !isStatic() && !isInterface() ?
                        UpgradableBooleanMap.of(typeInfo().packageNameOrEnclosingType.getRight(), false) :
                        UpgradableBooleanMap.of(),
                interfacesImplemented().stream().flatMap(i -> i.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()),
                getAnnotations().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                //ti.subTypes.stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                        .flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                fields().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                subTypes().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
        );
    }

    default boolean isStatic() {
        if (typeInfo().packageNameOrEnclosingType.isLeft()) return true; // independent type
        return typeNature() != TypeNature.CLASS || modifiers().contains(TypeModifier.STATIC); // static sub type
    }

    default boolean isInterface() {
        return typeNature() == TypeNature.INTERFACE;
    }

    default boolean isEnum() {
        return typeNature() == TypeNature.ENUM;
    }

    default boolean isRecord() {
        return typeNature() == TypeNature.RECORD;
    }

    default boolean isAnnotation() {
        return typeNature() == TypeNature.ANNOTATION;
    }

    default boolean isSealed() {
        return !permittedWhenSealed().isEmpty();
    }


    default Set<ParameterizedType> typesOfFieldsMethodsConstructors(InspectionProvider inspectionProvider) {
        // this type
        Set<ParameterizedType> typesOfFields = fields().stream()
                .map(fieldInfo -> fieldInfo.type).collect(Collectors.toCollection(HashSet::new));
        typesOfFields.addAll(typesOfMethodsAndConstructors(inspectionProvider));
        return typesOfFields;
    }

    default Set<ParameterizedType> typesOfMethodsAndConstructors(InspectionProvider inspectionProvider) {
        Set<ParameterizedType> result = new HashSet<>();
        for (MethodInfo methodInfo : methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodInfo.returnType());
            }
            for (ParameterInfo parameterInfo : inspectionProvider.getMethodInspection(methodInfo).getParameters()) {
                result.add(parameterInfo.parameterizedType);
            }
        }
        return result;
    }

    static String createStaticBlockMethodName(int identifier) {
        return "$staticBlock$" + identifier;
    }

    default MethodInfo findStaticBlock(int i) {
        String name = createStaticBlockMethodName(i);
        return methods().stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    default Stream<MethodInfo> staticBlocksRecursively(InspectionProvider inspectionProvider) {
        Stream<MethodInfo> mine = methods().stream().filter(m -> inspectionProvider.getMethodInspection(m).isStaticBlock());
        Stream<MethodInfo> subTypes = subTypes().stream()
                .flatMap(st -> inspectionProvider.getTypeInspection(st).staticBlocksRecursively(inspectionProvider));
        return Stream.concat(mine, subTypes);
    }

    default Stream<List<MethodInfo>> staticBlocksPerType(InspectionProvider inspectionProvider) {
        List<MethodInfo> mine = methods().stream().filter(m -> inspectionProvider.getMethodInspection(m).isStaticBlock()).toList();
        Stream<List<MethodInfo>> subTypes = subTypes().stream()
                .flatMap(st -> inspectionProvider.getTypeInspection(st).staticBlocksPerType(inspectionProvider));
        return Stream.concat(Stream.of(mine), subTypes);
    }
}
