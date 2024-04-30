package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.stream.Collectors;

// integers represent type parameters, as result of HC.typeParameters()
public abstract sealed class HiddenContentSelector
        permits HiddenContentSelector.All,
        HiddenContentSelector.None,
        HiddenContentSelector.CsSet,
        HiddenContentSelector.Delayed {

    public boolean isNone() {
        return false;
    }

    public boolean isAll() {
        return false;
    }

    public Set<Integer> set() {
        return Set.of();
    }

    public CausesOfDelay causesOfDelay() {
        return CausesOfDelay.EMPTY;
    }

    public boolean isDelayed() {
        return causesOfDelay().isDelayed();
    }

    public Map<LV.Indices, ParameterizedType> extract(InspectionProvider inspectionProvider, ParameterizedType type) {
        throw new UnsupportedOperationException();
    }

    public boolean selectArrayElement(int arrays) {
        return false;
    }

    public HiddenContentSelector correct(Map<Integer, Integer> mapMethodHCTIndexToTypeHCTIndex) {
        return this;
    }

    public static final class Delayed extends HiddenContentSelector {
        private final CausesOfDelay causesOfDelay;

        public Delayed(CausesOfDelay causesOfDelay) {
            this.causesOfDelay = causesOfDelay;
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return causesOfDelay;
        }
    }

    public HiddenContentTypes hiddenContentTypes() {
        return null;
    }

    public static final class All extends HiddenContentSelector {
        private final int hiddenContentIndex;
        private final HiddenContentTypes hiddenContentTypes;

        public All(HiddenContentTypes hiddenContentTypes, int hiddenContentIndex) {
            this.hiddenContentIndex = hiddenContentIndex;
            this.hiddenContentTypes = hiddenContentTypes;
        }

        @Override
        public boolean isAll() {
            return true;
        }

        public int getHiddenContentIndex() {
            return hiddenContentIndex;
        }

        @Override
        public String toString() {
            return "*";
        }

        @Override
        public Map<LV.Indices, ParameterizedType> extract(InspectionProvider inspectionProvider, ParameterizedType type) {
            return Map.of(LV.ALL_INDICES, type);
        }

        @Override
        public HiddenContentTypes hiddenContentTypes() {
            return hiddenContentTypes;
        }

        @Override
        public Set<Integer> set() {
            return Set.of(hiddenContentIndex);
        }

        @Override
        public HiddenContentSelector correct(Map<Integer, Integer> mapMethodHCTIndexToTypeHCTIndex) {
            int mapped = mapMethodHCTIndexToTypeHCTIndex.getOrDefault(hiddenContentIndex, hiddenContentIndex);
            return mapped == hiddenContentIndex ? this: new All(hiddenContentTypes, mapped);
        }
    }

    public static final class None extends HiddenContentSelector {

        public static final None INSTANCE = new None();

        private None() {
        }

        @Override
        public boolean isNone() {
            return true;
        }

        @Override
        public String toString() {
            return "X";
        }
    }

    public static final class CsSet extends HiddenContentSelector {
        /*
        map key: the index in HCT
        map value: how to extract from the type
         */
        private final Map<Integer, LV.Indices> map;
        private final HiddenContentTypes hiddenContentTypes;

        public CsSet(HiddenContentTypes hiddenContentTypes, Map<Integer, LV.Indices> map) {
            this.map = map;
            this.hiddenContentTypes = hiddenContentTypes;
        }

        @Override
        public HiddenContentTypes hiddenContentTypes() {
            return hiddenContentTypes;
        }

        // for testing
        public static HiddenContentSelector selectTypeParameter(HiddenContentTypes hiddenContentTypes, int i) {
            return new CsSet(hiddenContentTypes, Map.of(i, new LV.Indices(i)));
        }

        @Override
        public String toString() {
            return map.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> print(e.getKey(), e.getValue()))
                    .collect(Collectors.joining(","));
        }

        private static String print(int i, LV.Indices indices) {
            String is = indices.toString();
            String iToString = "" + i;
            if (is.equals(iToString)) return iToString;
            return iToString + "=" + is;
        }

        public Set<Integer> set() {
            return map.keySet();
        }

        public Map<Integer, LV.Indices> getMap() {
            return map;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CsSet csSet = (CsSet) o;
            return Objects.equals(map, csSet.map);
        }

        @Override
        public int hashCode() {
            return Objects.hash(map);
        }

        @Override
        public Map<LV.Indices, ParameterizedType> extract(InspectionProvider inspectionProvider, ParameterizedType type) {
            return map.values().stream().collect(Collectors.toUnmodifiableMap(i -> i,
                    i -> i.findInFormal(inspectionProvider, type)));
        }

        @Override
        public boolean selectArrayElement(int arrays) {
            if (map.size() == 1) {
                LV.Indices indices = map.get(0);
                return indices.set().size() == 1
                       && indices.set().stream().findFirst().orElseThrow().countSequentialZeros() == arrays;
            }
            return false;
        }

        @Override
        public HiddenContentSelector correct(Map<Integer, Integer> mapMethodHCTIndexToTypeHCTIndex) {
            Map<Integer, LV.Indices> newMap = map.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    e -> mapMethodHCTIndexToTypeHCTIndex.getOrDefault(e.getKey(), e.getKey()),
                    Map.Entry::getValue, (i1, i2) -> i1));
            return new CsSet(hiddenContentTypes, newMap);
        }
    }

    /*
     Take in a type, and return the hidden content components of this type, with respect to the hidden content types
     of the current type or method.
     Set<Map.Entry<K, V>> will return the indices of K and V mapped to their position in the formal type,
     i.e., 0 -> 0.0, 1 -> 0.1
     Collection<V> will return 1 -> 0

     in the following context:
     static <X> X method(...) { Supplier<X> s = new Supplier<>() { ... }}
     the anonymous type $1 has no direct type parameters, but its enclosing method does. We'll replace $2 by
     Supplier<X>.
     */
    public static HiddenContentSelector selectAll(HiddenContentTypes hiddenContentTypes,
                                                  ParameterizedType typeIn) {
        assert hiddenContentTypes != null;
        boolean haveArrays = typeIn.arrays > 0;
        ParameterizedType type = typeIn.copyWithoutArrays();
        Integer index = hiddenContentTypes.indexOfOrNull(type);

        if (type.isTypeParameter()) {
            assert index != null;
            if (haveArrays) {
                return new CsSet(hiddenContentTypes, Map.of(index, new LV.Indices(index)));
            }
            return new All(hiddenContentTypes, index);
        }
        if (type.typeInfo == null) {
            // ?, equivalent to ? extends Object; 0 for now
            return new All(hiddenContentTypes, 0);
        }
        if (type.arrays > 0) {
            // assert type.parameters.isEmpty(); // not doing the combination
            return None.INSTANCE;
        }
        Map<Integer, LV.Indices> map;
        if (type.typeInfo.equals(hiddenContentTypes.getTypeInfo())) {
            map = hiddenContentTypes.selectAll().stream()
                    .collect(Collectors.toUnmodifiableMap(i -> i, i -> {
                        return new LV.Indices(i);
                    }));
        } else {
            map = new HashMap<>();
            recursivelyCollectHiddenContentParameters(hiddenContentTypes, type, new Stack<>(), map);
        }
        if (map.isEmpty()) {
            return None.INSTANCE;
        }
        return new CsSet(hiddenContentTypes, map);
    }

    private static void recursivelyCollectHiddenContentParameters(HiddenContentTypes hiddenContentTypes,
                                                                  ParameterizedType type,
                                                                  Stack<Integer> prefix,
                                                                  Map<Integer, LV.Indices> map) {
        Integer index = hiddenContentTypes.indexOfOrNull(type.copyWithoutArrays());
        if (index != null && type.parameters.isEmpty()) {
            map.merge(index, new LV.Indices(Set.of(new LV.Index(List.copyOf(prefix)))), LV.Indices::merge);
        } else {
            int i = 0;
            for (ParameterizedType parameter : type.parameters) {
                prefix.push(i);
                recursivelyCollectHiddenContentParameters(hiddenContentTypes, parameter, prefix, map);
                prefix.pop();
                i++;
            }
        }
    }
}
