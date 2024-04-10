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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.support.Either;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Important: this information is orthogonal to mutability - immutability.
The mutable type List<E> is both extensible (it is an interface), mutable (e.g., add) and it has an unbound type
parameter. It definitely has hidden content. At the same time, some of its instances may be immutable (with
or without hidden content), e.g. List.of(...), List.copyOf(...).
 */
public class HiddenContentTypes {
    public static HiddenContentTypes OF_PRIMITIVE = new HiddenContentTypes(null, false, Map.of());
    public static HiddenContentTypes OF_OBJECT = new HiddenContentTypes(null, true, Map.of());

    private final TypeInfo typeInfo;
    private final boolean typeIsExtensible;
    private final int startOfMethodParameters;

    private final HiddenContentTypes hcsTypeInfo;
    private final MethodInfo methodInfo;

    private final Map<ParameterizedType, Integer> typeToIndex;
    private final Map<Integer, ParameterizedType> indexToType;

    private HiddenContentTypes(TypeInfo typeInfo,
                               boolean typeIsExtensible,
                               Map<ParameterizedType, Integer> typeToIndex) {
        this.typeInfo = typeInfo;
        this.typeIsExtensible = typeIsExtensible;
        this.typeToIndex = typeToIndex;
        this.indexToType = typeToIndex.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
        startOfMethodParameters = typeToIndex.size();
        methodInfo = null;
        hcsTypeInfo = null;
    }

    private HiddenContentTypes(HiddenContentTypes hcsTypeInfo,
                               MethodInfo methodInfo,
                               Map<ParameterizedType, Integer> typeToIndexIn) {
        this.typeIsExtensible = hcsTypeInfo.typeIsExtensible;
        this.hcsTypeInfo = hcsTypeInfo;
        this.typeInfo = hcsTypeInfo.typeInfo;
        this.methodInfo = methodInfo;
        assert typeInfo == methodInfo.typeInfo
                : "HCS typeInfo = " + typeInfo + ", method type info = " + methodInfo.typeInfo;
        this.startOfMethodParameters = hcsTypeInfo.startOfMethodParameters;
        Map<Integer, ParameterizedType> i2t = new HashMap<>();
        Map<ParameterizedType, Integer> t2i = new HashMap<>();
        for (Map.Entry<ParameterizedType, Integer> entry : typeToIndexIn.entrySet()) {
            ParameterizedType pt = entry.getKey().copyWithoutWildcard();
            if (!hcsTypeInfo.typeToIndex.containsKey(pt)) {
                int i = startOfMethodParameters + entry.getValue();
                i2t.put(i, pt);
                t2i.put(pt, i);
            }
        }
        indexToType = Map.copyOf(i2t);
        typeToIndex = Map.copyOf(t2i);
    }

    public boolean forMethod() {
        return methodInfo != null;
    }

    public HiddenContentSelector selectAll() {
        if (forMethod()) return hcsTypeInfo.selectAll();
        return typeToIndex.isEmpty()
                ? HiddenContentSelector.None.INSTANCE
                : new HiddenContentSelector.CsSet(indexToType.keySet());
    }

    public static HiddenContentTypes compute(TypeInspection typeInspection) {
        return compute(typeInspection, true);
    }

    public static HiddenContentTypes compute(HiddenContentTypes hcsTypeInfo, MethodInspection methodInspection) {
        assert hcsTypeInfo != null : "For method " + methodInspection.getMethodInfo();
        Map<ParameterizedType, Integer> typeToIndex = methodInspection.getTypeParameters().stream()
                .collect(Collectors.toUnmodifiableMap(
                        tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE),
                        TypeParameter::getIndex));
    /*    List<ParameterizedType> typesInParameters = methodInspection.getParameters().stream()
                .flatMap(pi -> expand(pi.parameterizedType))
                .filter(pt -> )
                .sorted()
                .toList();*/
        return new HiddenContentTypes(hcsTypeInfo, methodInspection.getMethodInfo(), typeToIndex);
    }

    // private static Stream<ParameterizedType> expand(ParameterizedType pt) {

    //  }

    public static HiddenContentTypes compute(TypeInspection typeInspection, boolean shallow) {
        TypeInfo typeInfo = typeInspection.typeInfo();
        //if (typeInfo.isJavaLangObject()) return OF_OBJECT;

        Set<TypeParameter> typeParametersInFields;
        if (shallow) {
            typeParametersInFields = typeInspection.typeParameters().stream().collect(Collectors.toUnmodifiableSet());
        } else {
            typeParametersInFields = typeInspection.fields().stream()
                    .flatMap(fi -> typeParameterStream(fi.type))
                    .collect(Collectors.toUnmodifiableSet());
        }
        Map<ParameterizedType, Integer> typeToIndex = typeParametersInFields.stream()
                .collect(Collectors.toUnmodifiableMap(
                        tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE),
                        TypeParameter::getIndex));
        return new HiddenContentTypes(typeInfo, typeInspection.isExtensible(), typeToIndex);
    }

    private static Stream<TypeParameter> typeParameterStream(ParameterizedType type) {
        if (type.isTypeParameter()) return Stream.of(type.typeParameter);
        return type.parameters.stream().flatMap(HiddenContentTypes::typeParameterStream);
    }

    public boolean hasHiddenContent() {
        return typeIsExtensible || size() > 0;
    }

    // if T is hidden, then ? extends T is hidden as well! all wildcards have been stripped.
    public boolean contains(ParameterizedType parameterizedType) {
        if (typeToIndex.containsKey(parameterizedType)) return true;
        if (parameterizedType.typeParameter != null) {
            if (parameterizedType.wildCard != ParameterizedType.WildCard.NONE) {
                ParameterizedType withoutWildcard = parameterizedType.copyWithoutWildcard();
                if (typeToIndex.containsKey(withoutWildcard)) return true;
            }
        }
        if (hcsTypeInfo != null) return hcsTypeInfo.contains(parameterizedType);
        return false;
    }

    public boolean isEmpty() {
        return (hcsTypeInfo == null || hcsTypeInfo.isEmpty()) && typeToIndex.isEmpty();
    }

    @Override
    public String toString() {
        String s = hcsTypeInfo == null ? "" : hcsTypeInfo.toString() + " - ";
        String l = hcsTypeInfo == null ? typeInfo.simpleName : methodInfo.name;
        return s + l + ":" + sortedTypes();
    }

    public int size() {
        return (hcsTypeInfo == null ? 0 : hcsTypeInfo.size()) + typeToIndex.size();
    }

    /*
    make a translation map based on pt2, and translate from formal to concrete.

    If types contains E=formal type parameter of List<E>, and pt = List<T>, we want
    to return a HiddenContentTypes containing T instead of E
     */
    public HiddenContentTypes translate(InspectionProvider inspectionProvider, ParameterizedType pt) {
        Map<NamedType, ParameterizedType> map = pt.initialTypeParameterMap(inspectionProvider);
        Set<ParameterizedType> newTypes = typeToIndex.keySet().stream()
                .map(t -> translate(inspectionProvider, pt, map, t))
                .collect(Collectors.toUnmodifiableSet());
        // return new HiddenContentTypes(newTypes);
        throw new UnsupportedOperationException();
    }

    private ParameterizedType translate(InspectionProvider inspectionProvider,
                                        ParameterizedType pt,
                                        Map<NamedType, ParameterizedType> map,
                                        ParameterizedType t) {
        if (map.isEmpty() && t.isTypeParameter() && t.isAssignableFrom(inspectionProvider, pt)) {
            return pt;
        }
        return t.applyTranslation(inspectionProvider.getPrimitives(), map);
    }

    public ParameterizedType typeByIndex(int i) {
        return indexToType.get(i);
    }

    public int indexOf(ParameterizedType type) {
        return indexOfOrNull(type);
    }

    public int indexOf(TypeParameter typeParameter) {
        return typeParameter.getIndex();
    }

    public Integer indexOfOrNull(ParameterizedType type) {
        if (type.typeParameter != null) {
            return type.typeParameter.getIndex();
        }
        return typeToIndex.get(type);
    }

    public Set<ParameterizedType> types() {
        return typeToIndex.keySet();
    }

    public String sortedTypes() {
        String s = forMethod() ? hcsTypeInfo.sortedTypes() + " - " : "";
        return s + typeToIndex.keySet().stream()
                .map(ParameterizedType::printSimple).sorted().collect(Collectors.joining(", "));
    }

    public static HiddenContentTypes from(ParameterizedType formalTargetType) {
        TypeInfo targetTi;
        if (formalTargetType.typeParameter != null) {
            Either<TypeInfo, MethodInfo> owner = formalTargetType.typeParameter.getOwner();
            targetTi = owner.isLeft() ? owner.getLeft() : owner.getRight().typeInfo;
        } else if (formalTargetType.typeInfo != null) {
            targetTi = formalTargetType.typeInfo;
        } else {
            return OF_OBJECT;
        }
        return targetTi.typeResolution.get().hiddenContentTypes();
    }
}
