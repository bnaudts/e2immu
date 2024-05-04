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
    public static HiddenContentTypes OF_PRIMITIVE = new HiddenContentTypes(null, false, Map.of(), Map.of());
    public static final int UNSPECIFIED_EXTENSION = -2; // extension of the type itself, only if typeIsExtensible == true

    private final TypeInfo typeInfo;
    private final boolean typeIsExtensible;
    private final int startOfMethodParameters;

    private final HiddenContentTypes hcsTypeInfo;
    private final MethodInfo methodInfo;

    private final Map<NamedType, Integer> typeToIndex;
    private final Map<Integer, NamedType> indexToType;

    private HiddenContentTypes(TypeInfo typeInfo,
                               boolean typeIsExtensible,
                               Map<NamedType, Integer> myTypeToIndex,
                               Map<NamedType, Integer> superTypeToIndex) {
        this.typeInfo = typeInfo;
        this.typeIsExtensible = typeIsExtensible;
        Map<NamedType, Integer> combined = new HashMap<>(myTypeToIndex);
        combined.putAll(superTypeToIndex);
        this.typeToIndex = Map.copyOf(combined);
        try {
            this.indexToType = myTypeToIndex.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
        } catch (IllegalStateException ise) {

            throw ise;
        }
        startOfMethodParameters = myTypeToIndex.size();
        methodInfo = null;
        hcsTypeInfo = null;
    }

    private HiddenContentTypes(HiddenContentTypes hcsTypeInfo,
                               MethodInfo methodInfo,
                               Map<NamedType, Integer> typeToIndexIn) {
        this.typeIsExtensible = hcsTypeInfo.typeIsExtensible;
        this.hcsTypeInfo = hcsTypeInfo;
        this.typeInfo = hcsTypeInfo.typeInfo;
        this.methodInfo = methodInfo;
        assert typeInfo == methodInfo.typeInfo
                : "HCS typeInfo = " + typeInfo + ", method type info = " + methodInfo.typeInfo;
        this.startOfMethodParameters = hcsTypeInfo.startOfMethodParameters;
        Map<Integer, NamedType> i2t = new HashMap<>();
        Map<NamedType, Integer> t2i = new HashMap<>();
        for (Map.Entry<NamedType, Integer> entry : typeToIndexIn.entrySet()) {
            NamedType nt = entry.getKey();
            if (!hcsTypeInfo.typeToIndex.containsKey(nt)) {
                int i = startOfMethodParameters + entry.getValue();
                i2t.put(i, nt);
                t2i.put(nt, i);
            }
        }
        indexToType = Map.copyOf(i2t);
        typeToIndex = Map.copyOf(t2i);
    }

    public boolean forMethod() {
        return methodInfo != null;
    }

    // accessible, not accessible

    public static HiddenContentTypes compute(InspectionProvider inspectionProvider, TypeInspection typeInspection) {
        return compute(inspectionProvider, typeInspection, true);
    }

    public static HiddenContentTypes compute(HiddenContentTypes hcsTypeInfo, MethodInspection methodInspection) {
        assert hcsTypeInfo != null : "For method " + methodInspection.getMethodInfo();

        Map<NamedType, Integer> typeToIndex = new HashMap<>();
        int max = 0;
        for (TypeParameter tp : methodInspection.getTypeParameters()) {
            typeToIndex.put(tp, tp.getIndex());
            max = Math.max(max, tp.getIndex());
        }
        // are any of the parameter's type's a type parameter, not yet used in the fields? See resolve.Method_15
        for (ParameterInfo pi : methodInspection.getParameters()) {
            TypeParameter tp = pi.parameterizedType.typeParameter;
            if (tp != null && tp.getOwner().isLeft() && !hcsTypeInfo.typeToIndex.containsKey(tp)) {
                typeToIndex.put(tp, ++max);
            }
        }
        return new HiddenContentTypes(hcsTypeInfo, methodInspection.getMethodInfo(), typeToIndex);
    }

    // private static Stream<ParameterizedType> expand(ParameterizedType pt) {

    //  }

    public static HiddenContentTypes compute(InspectionProvider inspectionProvider,
                                             TypeInspection typeInspection,
                                             boolean shallow) {
        TypeInfo typeInfo = typeInspection.typeInfo();

        int offset;
        Map<NamedType, Integer> fromEnclosing;
        if (typeInspection.isInnerClass()) {
            HiddenContentTypes hct;
            MethodInfo enclosingMethod = typeInspection.enclosingMethod();
            Stream<Map.Entry<Integer, NamedType>> indexToTypeStream;
            if (enclosingMethod != null) {
                hct = getOrCompute(inspectionProvider, enclosingMethod, shallow);
                indexToTypeStream = Stream.concat(hct.hcsTypeInfo.indexToType.entrySet().stream(),
                        hct.indexToType.entrySet().stream());
            } else {
                TypeInfo enclosing = typeInfo.packageNameOrEnclosingType.getRight();
                hct = getOrCompute(inspectionProvider, enclosing, shallow);
                indexToTypeStream = hct.indexToType.entrySet().stream();
            }
            offset = hct.size();
            fromEnclosing = indexToTypeStream.collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
        } else {
            offset = 0;
            fromEnclosing = Map.of();
        }

        Set<TypeParameter> typeParametersInFields;
        if (shallow) {
            typeParametersInFields = typeInspection.typeParameters().stream().collect(Collectors.toUnmodifiableSet());
        } else {
            typeParametersInFields = typeInspection.fields().stream()
                    .flatMap(fi -> typeParameterStream(fi.type))
                    .collect(Collectors.toUnmodifiableSet());
        }
        Map<NamedType, Integer> fromThis = typeParametersInFields.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> tp.getIndex() + offset, (tp1, tp2) -> tp1, HashMap::new));
        fromThis.putAll(fromEnclosing);

        Map<NamedType, Integer> superTypeToIndex = new HashMap<>();
        if (typeInspection.parentClass() != null && !typeInspection.parentClass().isJavaLangObject()) {
            addFromSuperType(inspectionProvider, typeInspection.parentClass(), shallow, typeInfo, fromThis,
                    superTypeToIndex);
        }
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented()) {
            addFromSuperType(inspectionProvider, interfaceType, shallow, typeInfo, fromThis, superTypeToIndex);
        }
        /*
         Finally, we add hidden content types from extensible fields without type parameters.

         NOTE: Linking to extensible fields with type parameters is done at the level of those type parameters ONLY
         in the current implementation, which does not allow for the combination of ALL and CsSet.
         */
        if (!shallow) {
            for (FieldInfo f : typeInspection.fields()) {
                if (!f.type.isTypeParameter()) {
                    TypeInfo bestType = Objects.requireNonNullElse(f.type.bestTypeInfo(inspectionProvider),
                            inspectionProvider.getPrimitives().objectTypeInfo());
                    TypeInspection bestTypeInspection = inspectionProvider.getTypeInspection(bestType);
                    if (bestTypeInspection.typeParameters().isEmpty() && bestTypeInspection.isExtensible()) {
                        int index = fromThis.size();
                        fromThis.putIfAbsent(bestType, index);
                    }
                }
            }
        }
        return new HiddenContentTypes(typeInfo, typeInspection.isExtensible(), Map.copyOf(fromThis),
                Map.copyOf(superTypeToIndex));
    }

    private static void addFromSuperType(InspectionProvider inspectionProvider,
                                         ParameterizedType superType,
                                         boolean shallow,
                                         TypeInfo typeInfo,
                                         Map<NamedType, Integer> fromThis,
                                         Map<NamedType, Integer> superTypeToIndex) {
        HiddenContentTypes hctParent = getOrCompute(inspectionProvider, superType.typeInfo, shallow);
        if (!hctParent.isEmpty()) {
            Map<NamedType, ParameterizedType> fromMeToParent = superType.initialTypeParameterMap(inspectionProvider);
            assert fromMeToParent != null;
            // the following include all recursively computed
            for (Map.Entry<NamedType, Integer> e : hctParent.typeToIndex.entrySet()) {
                NamedType typeInParent = hctParent.indexToType.get(e.getValue()); // this step is necessary for recursively computed...
                ParameterizedType typeHere = fromMeToParent.get(typeInParent);
                if (typeHere != null) {
                    NamedType namedTypeHere = typeHere.typeParameter != null ? typeHere.typeParameter : typeHere.typeInfo;
                    Integer indexHere = fromThis.get(namedTypeHere);
                    if (indexHere == null && !shallow) {
                        // no field with this type, but we must still add it, as it exists in the parent
                        // if shallow, it has already been added, there is no check on fields
                        indexHere = fromThis.size();
                        fromThis.put(namedTypeHere, indexHere);
                    }
                    if (indexHere != null) {
                        superTypeToIndex.put(e.getKey(), indexHere);
                    }
                } // see e.g. resolve.Constructor_2
            }
        }
    }

    private static HiddenContentTypes getOrCompute(InspectionProvider inspectionProvider, TypeInfo enclosing, boolean shallow) {
        if (enclosing.typeResolution.isSet() && enclosing.typeResolution.get().hiddenContentTypes() != null) {
            return enclosing.typeResolution.get().hiddenContentTypes();
        }
        return compute(inspectionProvider, inspectionProvider.getTypeInspection(enclosing), shallow);
    }

    private static HiddenContentTypes getOrCompute(InspectionProvider inspectionProvider, MethodInfo enclosing, boolean shallow) {
        if (enclosing.methodResolution.isSet() && enclosing.methodResolution.get().hiddenContentTypes() != null) {
            return enclosing.methodResolution.get().hiddenContentTypes();
        }
        HiddenContentTypes hctTypeInfo = getOrCompute(inspectionProvider, enclosing.typeInfo, shallow);
        return compute(hctTypeInfo, inspectionProvider.getMethodInspection(enclosing));
    }

    private static Stream<TypeParameter> typeParameterStream(ParameterizedType type) {
        if (type.isTypeParameter()) return Stream.of(type.typeParameter);
        return type.parameters.stream().flatMap(HiddenContentTypes::typeParameterStream);
    }

    public boolean hasHiddenContent() {
        return typeIsExtensible || size() > 0;
    }

    private static NamedType namedType(ParameterizedType type) {
        if (type.typeParameter != null) return type.typeParameter;
        if (type.typeInfo != null) return type.typeInfo;
        return null;
    }

    public boolean isEmpty() {
        return (hcsTypeInfo == null || hcsTypeInfo.isEmpty()) && indexToType.isEmpty();
    }

    @Override
    public String toString() {
        String s = hcsTypeInfo == null ? "" : hcsTypeInfo + " - ";
        String l = hcsTypeInfo == null ? typeInfo.simpleName : methodInfo.name;
        return s + l + ":" + indexToType.values().stream()
                .map(NamedType::simpleName).sorted().collect(Collectors.joining(", "));
    }

    public int size() {
        return (hcsTypeInfo == null ? 0 : hcsTypeInfo.size()) + indexToType.size();
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
                                        NamedType t) {
        if (map.isEmpty() && t instanceof TypeParameter tp && tp.toParameterizedType()
                .isAssignableFrom(inspectionProvider, pt)) {
            return pt;
        }
        //  return t.applyTranslation(inspectionProvider.getPrimitives(), map);
        throw new UnsupportedOperationException();
    }

    public NamedType typeByIndex(int i) {
        NamedType here = indexToType.get(i);
        if (here != null) return here;
        if (hcsTypeInfo != null) {
            return hcsTypeInfo.indexToType.get(i);
        }
        return null;
    }

    public int indexOf(ParameterizedType type) {
        return indexOfOrNull(type);
    }

    public int indexOf(NamedType namedType) {
        Integer here = typeToIndex.get(namedType);
        if (here != null) return here;
        if (hcsTypeInfo != null) return hcsTypeInfo.typeToIndex.get(namedType);
        throw new UnsupportedOperationException("Expected " + namedType + " to be known");
    }

    public Integer indexOfOrNull(ParameterizedType type) {
        NamedType namedType = namedType(type);
        if (namedType == null) return null;
        Integer here = typeToIndex.get(namedType);
        if (here != null) return here;
        if (hcsTypeInfo != null) return hcsTypeInfo.typeToIndex.get(namedType);
        return null;
    }

    public Collection<NamedType> types() {
        return indexToType.values();
    }

    public String sortedTypes() {
        String s = forMethod() ? hcsTypeInfo.sortedTypes() + " - " : "";
        return s + indexToType.values().stream()
                .map(NamedType::simpleName).sorted().collect(Collectors.joining(", "));
    }

    public Map<Integer, Integer> mapMethodToTypeIndices(ParameterizedType parameterizedType) {
        // FIXME this is not a good implementation
        Map<Integer, Integer> result = new HashMap<>();
        for (int i : indexToType.keySet()) {
            result.put(i, i - startOfMethodParameters);
        }
        return Map.copyOf(result);
    }

    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    public boolean isExtensible(Integer single) {
        if (single == null) return false;
        NamedType nt = typeByIndex(single);
        return nt instanceof TypeInfo;
    }

    /*
     The hidden content selector's hct indices (the keys in the map) are computed with respect to 'this'.
     They map to indices (the values in the map) which exist in 'from'.

     'to' is a concrete type for 'from'. We'll map the indices of the selector to indices wrt the formal
     type of to, also attaching the concrete types at those indices.

     E.g. method context is the type ArrayList<EA>.new ArrayList<>(Collection<? extends EA>)
     concrete constructor call is new ArrayList<>(List<M>)

     'this' is with respect to ArrayList<EA> and the constructor, mapping EA=0 (and EL=0, EC=0 for List, Collection)
     'from' is Collection<? extends EA>, formal type Collection<EC>.
     The hidden content selector is 0=0.

     'to' is List<M>, with formal type List<EL>.
     The result maps 'indices' 0 to the combination of "M" and indices 0.
    */

    // FIXME move to HCS, and use the HCT of the HCS
    public record IndicesAndType(LV.Indices indices, ParameterizedType type) {
    }

    public Map<LV.Indices, IndicesAndType> translateHcs(InspectionProvider inspectionProvider,
                                                        HiddenContentSelector hiddenContentSelector,
                                                        ParameterizedType from,
                                                        ParameterizedType to) {
        if (hiddenContentSelector.isNone()) return Map.of();
        Map<LV.Indices, ParameterizedType> map1 = hiddenContentSelector.extract(inspectionProvider, from);
        Map<LV.Indices, IndicesAndType> result = new HashMap<>();
        for (Map.Entry<LV.Indices, ParameterizedType> entry1 : map1.entrySet()) {
            IndicesAndType iat;
            if (from.arrays > 0 && hiddenContentSelector.selectArrayElement(from.arrays)) {
                LV.Indices indices = new LV.Indices(Set.of(LV.Index.createZeroes(from.arrays)));
                iat = new IndicesAndType(indices, to);
            } else if (from.typeParameter != null || from.equals(to)) {
                iat = new IndicesAndType(entry1.getKey(), to);
            } else {
                iat = findAll(inspectionProvider, entry1.getKey(), entry1.getValue(), from, to);
            }
            result.put(entry1.getKey(), iat);
        }
        return Map.copyOf(result);
    }

    /*
    if what is a type parameter, is will be with respect to the formal type of that level of generics.

    what == the result of 'ptInFrom in from' translated to ('to' == where)

    Given what=EL, with where==List<String>, return 0,String
    Given what=K in Map.Entry, with where = Set<Map.Entry<A,B>>, return 0.0,A

    If from=Set<Set<K>>, and we extract K at 0.0
    If to=Collection<ArrayList<String>>, we'll have to return 0.0, String
     */
    private static IndicesAndType findAll(InspectionProvider inspectionProvider,
                                          LV.Indices indices,
                                          ParameterizedType ptInFrom,
                                          ParameterizedType from,
                                          ParameterizedType to) {
        // it does not matter with which index we start
        LV.Index index = indices.set().stream().findFirst().orElseThrow();
        IndicesAndType res = findAll(inspectionProvider, index, 0, ptInFrom, from, to);
        // but once we have found it, we must make sure that we return all occurrences
        assert res.indices.set().size() == 1;
        assert res.type != null;
        LV.Indices findAll = LV.Indices.allOccurrencesOf(res.type, to);
        return new IndicesAndType(findAll, res.type);
    }


    private static IndicesAndType findAll(InspectionProvider inspectionProvider,
                                          LV.Index index,
                                          int pos,
                                          ParameterizedType ptFrom,
                                          ParameterizedType from,
                                          ParameterizedType to) {
        int atPos = index.list().get(pos);
        if (pos == index.list().size() - 1) {
            // the last entry
            assert from.typeInfo != null;
            ParameterizedType formalFrom = from.typeInfo.asParameterizedType(inspectionProvider);
            assert formalFrom.parameters.get(atPos).equals(ptFrom);
            if (formalFrom.typeInfo == to.typeInfo) {
                ParameterizedType concrete;
                if (atPos >= to.parameters.size()) {
                    // type parameters are missing, we'd expect <> so that they get filled in automatically
                    concrete = inspectionProvider.getPrimitives().objectParameterizedType();
                } else {
                    concrete = to.parameters.get(atPos);
                }
                return new IndicesAndType(new LV.Indices(Set.of(index)), concrete);
            }
            ParameterizedType formalTo = to.typeInfo.asParameterizedType(inspectionProvider);
            Map<NamedType, ParameterizedType> map1;
            if (formalFrom.isAssignableFrom(inspectionProvider, formalTo)) {
                map1 = to.typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, formalFrom);
            } else {
                map1 = from.typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, formalTo);
            }
            assert map1 != null;
            ParameterizedType ptTo = map1.get(ptFrom.namedType());
            assert ptTo != null;
            int iTo = to.typeInfo.typeResolution.get().hiddenContentTypes().indexOf(ptTo);
            LV.Index indexTo = index.replaceLast(iTo);
            LV.Indices indicesTo = new LV.Indices(Set.of(indexTo));
            Map<NamedType, ParameterizedType> map2 = to.initialTypeParameterMap(inspectionProvider);
            ParameterizedType concreteTypeTo = map2.get(ptTo.namedType());
            assert concreteTypeTo != null;
            return new IndicesAndType(indicesTo, concreteTypeTo);
        }
        if (from.typeInfo == to.typeInfo) {
            ParameterizedType inFrom = from.parameters.get(atPos);
            ParameterizedType inTo = to.parameters.get(atPos);
            return findAll(inspectionProvider, index, pos + 1, ptFrom, inFrom, inTo);
        }
        throw new UnsupportedOperationException();
    }

    public HiddenContentTypes getHcsTypeInfo() {
        return hcsTypeInfo;
    }

    public Map<Integer, NamedType> getIndexToType() {
        return indexToType;
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    public Map<NamedType, Integer> getTypeToIndex() {
        return typeToIndex;
    }

    public Stream<Map.Entry<NamedType, Integer>> typesOfExtensibleFields() {
        return typeToIndex.entrySet().stream().filter(e -> e.getKey() instanceof TypeInfo);
    }

    public boolean isTypeIsExtensible() {
        return typeIsExtensible;
    }
}
