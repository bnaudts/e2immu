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

import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/*
Primary goal is to allow for a very efficient handling of hidden content types when computing HC Links.
IntegerList -> ArrayList -> List -> Collection; when given IntegerList as concrete type and Collection as formal type,
relating E=TP#0 in Collection to Integer as in IntegerList extends ArrayList<Integer> should be efficient.

Important: this information is orthogonal to mutability - immutability.
The mutable type List<E> is both extensible (it is an interface), mutable (e.g., add) and it has an unbound type
parameter. It definitely has hidden content. At the same time, some of its instances may be immutable (with
or without hidden content), e.g. List.of(...), List.copyOf(...).

 */
public class HiddenContentTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(HiddenContentTypes.class);

    public static HiddenContentTypes OF_PRIMITIVE = new HiddenContentTypes(false, Map.of(), Map.of());
    public static HiddenContentTypes OF_OBJECT = new HiddenContentTypes(true, Map.of(), Map.of());

    // FIXME
    public Set<ParameterizedType> types() {
        return typeToIndex.keySet();
    }

    public record RelationToParent(HiddenContentTypes hcs,
                                   Map<Integer, ParameterizedType> parentHcsToMyType) {
        // E(=0 in List) -> 0 (in ArrayList), follow 0->E(=0 in ArrayList) -> Integer (as in IntList extends ArrayList<Integer>)
        public RelationToParent follow(RelationToParent rtp, HiddenContentTypes hcs) {
            Map<Integer, ParameterizedType> map = new HashMap<>();
            for (Map.Entry<Integer, ParameterizedType> entry : parentHcsToMyType.entrySet()) {
                Integer inHcs = hcs.typeToIndex.get(entry.getValue());
                ParameterizedType concrete;
                if (inHcs != null) {
                    concrete = rtp.parentHcsToMyType.get(inHcs);
                } else {
                    concrete = entry.getValue();
                }
                map.put(entry.getKey(), concrete);
            }
            return new RelationToParent(hcs, map);
        }
    }

    private final boolean typeIsExtensible;
    private final ParameterizedType[] types;
    private final Map<ParameterizedType, Integer> typeToIndex;
    private final Map<TypeInfo, RelationToParent> ancestorMap;

    private HiddenContentTypes(boolean typeIsExtensible,
                               Map<ParameterizedType, Integer> typeToIndex,
                               Map<TypeInfo, RelationToParent> ancestorMap) {
        this.typeIsExtensible = typeIsExtensible;
        this.typeToIndex = typeToIndex;
        this.types = new ParameterizedType[typeToIndex.size()];
        typeToIndex.forEach((pt, i) -> types[i] = pt);
        this.ancestorMap = ancestorMap;
    }

    public static HiddenContentTypes computeShallow(AnalyserContext analyserContext,
                                                    TypeInspection typeInspection) {
        return computeShallow(analyserContext, typeInspection, false);
    }

    public static HiddenContentTypes computeShallow(AnalyserContext analyserContext,
                                                    TypeInspection typeInspection,
                                                    boolean allowRecursiveComputation) {
        TypeInfo typeInfo = typeInspection.typeInfo();
        if (typeInfo.isJavaLangObject()) return OF_OBJECT;

        Map<TypeInfo, RelationToParent> ancestorMap = new HashMap<>();
        ParameterizedType parent = typeInspection.parentClass();
        assert parent != null && parent.typeInfo != null;
        if (!parent.typeInfo.isJavaLangObject()) {
            handleExtension(analyserContext, parent, ancestorMap, allowRecursiveComputation);
        }

        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented()) {
            handleExtension(analyserContext, interfaceType, ancestorMap, allowRecursiveComputation);
        }

        Map<ParameterizedType, Integer> typeToIndex = typeInspection.typeParameters().stream()
                .collect(Collectors.toUnmodifiableMap(
                        tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE),
                        TypeParameter::getIndex));
        return new HiddenContentTypes(typeInspection.isExtensible(), typeToIndex, Map.copyOf(ancestorMap));
    }

    private static void handleExtension(AnalyserContext analyserContext,
                                        ParameterizedType parent,
                                        Map<TypeInfo, RelationToParent> ancestorMap,
                                        boolean allowRecursiveComputation) {
        HiddenContentTypes hcsParent;
        TypeInfo typeInfoParent = parent.typeInfo;
        TypeAnalysis parentAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(typeInfoParent);
        if (parentAnalysis != null) {
            hcsParent = parentAnalysis.getHiddenContentTypes();
        } else if (allowRecursiveComputation) {
            LOGGER.debug("Recursively computing HCS for {}", typeInfoParent);
            TypeInspection parentInspection = analyserContext.getTypeInspection(typeInfoParent);
            hcsParent = computeShallow(analyserContext, parentInspection, true);
        } else {
            throw new UnsupportedOperationException("Have no hidden content for " + typeInfoParent);
        }
        assert hcsParent.typeIsExtensible;
        ParameterizedType formalParentType = typeInfoParent.asParameterizedType(analyserContext);
        recursivelyFillRelationToParent(parent, formalParentType, hcsParent, ancestorMap);
    }

    /*
     parent = ArrayList<Integer>, formal parent = ArrayList<E>, we'll map 0(=E in ArrayList) -> Integer
     at the same time, we already have in ArrayList,
     parent = List<E>, formal List<E>, 0(=E in List) -> E in ArrayList

     we want to add List to the ancestor map, but now with 0(=E in List) -> Integer
     */

    private static void recursivelyFillRelationToParent(ParameterizedType parent,
                                                        ParameterizedType formalParentType,
                                                        HiddenContentTypes hcsParent,
                                                        Map<TypeInfo, RelationToParent> ancestorMap) {
        Map<Integer, ParameterizedType> parentHcsToMyType = new HashMap<>();
        int i = 0;
        for (ParameterizedType pt : parent.parameters) {
            assert i < formalParentType.parameters.size();
            ParameterizedType formalParameter = formalParentType.parameters.get(i);
            int indexInParent = hcsParent.typeToIndex.get(formalParameter);
            parentHcsToMyType.put(indexInParent, pt);
            i++;
        }
        RelationToParent rtp = new RelationToParent(hcsParent, Map.copyOf(parentHcsToMyType));
        ancestorMap.put(formalParentType.typeInfo, rtp);
        for (Map.Entry<TypeInfo, RelationToParent> entry : hcsParent.ancestorMap.entrySet()) {
            if (!ancestorMap.containsKey(entry.getKey())) {
                RelationToParent newRtp = entry.getValue().follow(rtp, hcsParent);
                ancestorMap.put(entry.getKey(), newRtp);
            } // else there could be duplicates in the hierarchy, we're assuming they have the same R2P!!
        }
    }

    public boolean hasHiddenContent() {
        return typeIsExtensible || types.length > 0;
    }

    // if T is hidden, then ? extends T is hidden as well
    public boolean contains(ParameterizedType parameterizedType) {
        if (typeToIndex.containsKey(parameterizedType)) return true;
        if (parameterizedType.typeParameter != null) {
            if (parameterizedType.wildCard != ParameterizedType.WildCard.NONE) {
                ParameterizedType withoutWildcard = parameterizedType.copyWithoutWildcard();
                return typeToIndex.containsKey(withoutWildcard);
            } else {
                // try with wildcard
                ParameterizedType withWildCard = new ParameterizedType(parameterizedType.typeParameter, parameterizedType.arrays,
                        ParameterizedType.WildCard.EXTENDS);
                return typeToIndex.containsKey(withWildCard);
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return types.length == 0;
    }

    @Override
    public String toString() {
        return typeToIndex.keySet().stream().map(ParameterizedType::printSimple).sorted().collect(Collectors.joining(", "));
    }

    public HiddenContentTypes union(HiddenContentTypes other) {
        // Set<ParameterizedType> set = new HashSet<>(types);
        //  set.addAll(other.types);
        // return new HiddenContentTypes(set);
        throw new UnsupportedOperationException();
    }

    public int size() {
        return typeToIndex.size();
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

    public RelationToParent relationToParent(TypeInfo ancestor) {
        return Objects.requireNonNull(ancestorMap.get(ancestor));
    }

    public ParameterizedType typeByIndex(int i) {
        return types[i];
    }

    public int indexOf(ParameterizedType type) {
        return typeToIndex.get(type);
    }
}
