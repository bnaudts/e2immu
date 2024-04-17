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
    public static HiddenContentTypes OF_PRIMITIVE = new HiddenContentTypes(null, false, Map.of(), Map.of());
    public static HiddenContentTypes OF_OBJECT = new HiddenContentTypes(null, true, Map.of(), Map.of());

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
        this.indexToType = myTypeToIndex.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
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

    public Set<Integer> selectAll() {
        Set<Integer> set = new HashSet<>(indexToType.keySet());
        if (!forMethod()) set.addAll(hcsTypeInfo.indexToType.keySet());
        return set;
    }

    public static HiddenContentTypes compute(InspectionProvider inspectionProvider, TypeInspection typeInspection) {
        return compute(inspectionProvider, typeInspection, true);
    }

    public static HiddenContentTypes compute(HiddenContentTypes hcsTypeInfo, MethodInspection methodInspection) {
        assert hcsTypeInfo != null : "For method " + methodInspection.getMethodInfo();
        Map<NamedType, Integer> typeToIndex = methodInspection.getTypeParameters().stream()
                .collect(Collectors.toUnmodifiableMap(tp -> tp, TypeParameter::getIndex));
    /*    List<ParameterizedType> typesInParameters = methodInspection.getParameters().stream()
                .flatMap(pi -> expand(pi.parameterizedType))
                .filter(pt -> )
                .sorted()
                .toList();*/
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
            TypeInfo enclosing = typeInfo.packageNameOrEnclosingType.getRight();
            HiddenContentTypes hct = getOrCompute(inspectionProvider, enclosing, shallow);
            offset = hct.size();
            fromEnclosing = hct.indexToType.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
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
                assert typeHere != null;
                Integer indexHere = fromThis.get(typeHere.typeParameter != null ? typeHere.typeParameter : typeHere.typeInfo);
                if (indexHere != null) {
                    superTypeToIndex.put(e.getKey(), indexHere);
                }
            }
        }
    }

    private static HiddenContentTypes getOrCompute(InspectionProvider inspectionProvider, TypeInfo enclosing, boolean shallow) {
        if (enclosing.typeResolution.isSet() && enclosing.typeResolution.get().hiddenContentTypes() != null) {
            return enclosing.typeResolution.get().hiddenContentTypes();
        }
        return compute(inspectionProvider, inspectionProvider.getTypeInspection(enclosing), shallow);
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
        NamedType namedType = namedType(parameterizedType);
        if (namedType != null) {
            if (typeToIndex.containsKey(namedType)) return true;
            if (hcsTypeInfo != null) return hcsTypeInfo.contains(parameterizedType);
        }
        return false;
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

    public Map<Integer, ParameterizedType> mapTypesRecursively(InspectionProvider inspectionProvider,
                                                               ParameterizedType concreteType,
                                                               boolean thisContainsConcrete) {
        ParameterizedType formalType = concreteType.typeInfo.asParameterizedType(inspectionProvider);
        return mapTypesRecursively(inspectionProvider, concreteType, formalType, thisContainsConcrete);
    }

    public Map<Integer, ParameterizedType> mapTypesRecursively(InspectionProvider inspectionProvider,
                                                               ParameterizedType concreteType,
                                                               ParameterizedType typeInContextOfHCT,
                                                               boolean thisContainsConcrete) {
        Map<Integer, ParameterizedType> res = new LinkedHashMap<>(); // keep order, for testing
        mapTypesRecursively(inspectionProvider, concreteType, typeInContextOfHCT, thisContainsConcrete, res);
        return res;
    }

    private void mapTypesRecursively(InspectionProvider inspectionProvider,
                                     ParameterizedType concrete,
                                     ParameterizedType formal,
                                     boolean thisContainsConcrete,
                                     Map<Integer, ParameterizedType> res) {

        if (thisContainsConcrete) {
            Integer typeItself = indexOfOrNull(concrete);
            if (typeItself != null) {
                res.put(typeItself, formal);
                return;
            }
        } else {
            Integer typeItself = indexOfOrNull(formal);
            if (typeItself != null) {
                res.put(typeItself, concrete);
                return;
            }
        }
        ParameterizedType c2;
        if (formal.typeInfo != null && concrete.typeInfo == formal.typeInfo) {
            c2 = concrete;
        } else {
            c2 = concrete.concreteSuperType(inspectionProvider, formal);
        }
        assert formal.parameters.size() == concrete.parameters.size() || concrete.parameters.isEmpty();
        int i = 0;
        for (ParameterizedType fp : formal.parameters) {
            ParameterizedType cp = i >= c2.parameters.size()
                    ? inspectionProvider.getPrimitives().objectParameterizedType()
                    : c2.parameters.get(i);
            ParameterizedType cpCorrected;
            ParameterizedType fpCorrected;
            if (thisContainsConcrete) {
                cpCorrected = correctIfAssignable(inspectionProvider, cp);
                fpCorrected = fp;
            } else {
                cpCorrected = cp;
                fpCorrected = correctIfAssignable(inspectionProvider, fp);
            }
            mapTypesRecursively(inspectionProvider, cpCorrected, fpCorrected, thisContainsConcrete, res);
            i++;
        }
    }

    private ParameterizedType correctIfAssignable(InspectionProvider inspectionProvider, ParameterizedType pt) {
        if (pt.typeParameter != null && !pt.owner().getTypeInfo().equals(typeInfo)) {
            // FIXME this is one situation, there may be others; we're not checking isAssignable!
            ParameterizedType thisPt = typeInfo.asParameterizedType(inspectionProvider);
            Map<NamedType, ParameterizedType> map = pt.owner().getTypeInfo().mapInTermsOfParametersOfSubType(inspectionProvider, thisPt);
            assert map != null : "We've not verified assignable";
            ParameterizedType translated = map.get(pt.typeParameter);
            assert translated != null;
            return translated;
        }
        return pt;
    }

    /*
     The 'indices' are expressed with respect to 'this'.
     'to' is a concrete type for 'from', whose hidden content types are in 'this'.
     the resulting indices are wrt the formal type of 'to'.

     E.g. method context is the type ArrayList<EA>.new ArrayList<>(Collection<? extends EA>)
     concrete constructor call is new ArrayList<>(List<M>)

     'this' is with respect to ArrayList<EA> and the constructor, mapping EA=0
     'from' is Collection<? extends EA>, formal type Collection<EC>.
     'to' is List<M>, with formal type List<EL>. The concrete type doesn't matter here, the formal does.
     The end result is 0 -> 0: the index of EA goes to the index of EL, via the mapping

     STEP 1: compute 0=EC based on 'this', formal 'from'.
     STEP 2: compute the map EL to EC
     STEP 3: find where EL sits in 'to''s hidden content
    */

    public record IndicesAndType(LV.Indices indices, ParameterizedType type) {
    }

    public Map<LV.Indices, IndicesAndType> translateHcs(InspectionProvider inspectionProvider,
                                                        HiddenContentSelector hiddenContentSelector,
                                                        ParameterizedType from,
                                                        ParameterizedType to,
                                                        boolean fromFormalToConcrete) {
        throw new UnsupportedOperationException();
        /*
        ParameterizedType formalFrom = from.typeInfo.asParameterizedType(inspectionProvider);
        Map<Integer, ParameterizedType> map1 = mapTypesRecursively(inspectionProvider, from, formalFrom, true);
        ParameterizedType formalTo = to.typeInfo.asParameterizedType(inspectionProvider);
        Map<NamedType, ParameterizedType> map2 = formalFrom.translateMap(inspectionProvider, formalTo,
                fromFormalToConcrete);
        HiddenContentTypes toHct = to.typeInfo.typeResolution.get().hiddenContentTypes();
        return toHct.translateHcs(indices, map1, map2);*/
    }
/*
    public Map<Integer, Integer> translateHcs( Map<Integer, List<LV.Index>> indices,
                                              Map<Integer, ParameterizedType> fromTypeMap,
                                              Map<NamedType, ParameterizedType> fromToTo) {
        Map<Integer, Integer> result = new HashMap<>();
        for (int i : indices) {
            ParameterizedType ec = fromTypeMap.get(i);
            assert ec != null;
            ParameterizedType el = fromToTo.get(ec.typeParameter);
            assert el != null;
            Integer r = indexOfOrNull(el);
            assert r != null;
            result.put(i, r);
        }
        return result;
    }*/

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

    public boolean isAssignableTo(InspectionProvider inspectionProvider, NamedType namedType, int i) {
        NamedType nt = typeByIndex(i);
        if (namedType.equals(nt)) return true;
        if (nt instanceof TypeParameter tp && namedType instanceof TypeParameter tp2) {
            assert !tp.isMethodTypeParameter() && !tp2.isMethodTypeParameter() : "Not implemented";
            TypeInfo owner = tp.getOwner().getLeft();
            TypeInfo owner2 = tp2.getOwner().getLeft();
            if (owner.equals(owner2)) return tp.equals(tp2);
            ParameterizedType type = owner.asParameterizedType(inspectionProvider);
            ParameterizedType type2 = owner2.asParameterizedType(inspectionProvider);
            if (!type.isAssignableFrom(inspectionProvider, type2)) {
                return false;
            }
            // do they map onto the same type?
            Map<NamedType, ParameterizedType> map = owner2.mapInTermsOfParametersOfSubType(inspectionProvider, type);
            assert map != null;
            ParameterizedType translated = map.get(tp2);
            return translated != null && tp.equals(translated.typeParameter);
        }
        ParameterizedType formal = nt.asParameterizedType(inspectionProvider);
        ParameterizedType concrete = namedType.asParameterizedType(inspectionProvider);
        return formal.isAssignableFrom(inspectionProvider, concrete);
    }
}
