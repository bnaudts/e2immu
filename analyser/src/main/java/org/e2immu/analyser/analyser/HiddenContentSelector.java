package org.e2immu.analyser.analyser;

import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.stream.Collectors;

// integers represent type parameters, as result of HC.typeParameters()
public abstract sealed class HiddenContentSelector implements DijkstraShortestPath.Connection
        permits HiddenContentSelector.All,
        HiddenContentSelector.None,
        HiddenContentSelector.CsSet,
        HiddenContentSelector.Delayed {

    public abstract HiddenContentSelector union(HiddenContentSelector other);

    public boolean isNone() {
        return false;
    }

    public boolean isAll() {
        return false;
    }

    public Set<Integer> set() {
        throw new UnsupportedOperationException();
    }

    public CausesOfDelay causesOfDelay() {
        return CausesOfDelay.EMPTY;
    }

    public boolean isDelayed() {
        return causesOfDelay().isDelayed();
    }

    public static final class Delayed extends HiddenContentSelector {
        private final CausesOfDelay causesOfDelay;

        public Delayed(CausesOfDelay causesOfDelay) {
            this.causesOfDelay = causesOfDelay;
        }

        @Override
        public HiddenContentSelector union(HiddenContentSelector other) {
            return new Delayed(causesOfDelay.merge(other.causesOfDelay()));
        }

        @Override
        public CausesOfDelay causesOfDelay() {
            return causesOfDelay;
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection required) {
            return true; // always follow!
        }
    }

    public static final class All extends HiddenContentSelector {
        private final boolean mutable;

        public static final HiddenContentSelector INSTANCE = new All(false);
        public static final HiddenContentSelector MUTABLE_INSTANCE = new All(true);

        private All(boolean mutable) {
            this.mutable = mutable;
        }

        public boolean isMutable() {
            return mutable;
        }

        @Override
        public boolean isAll() {
            return true;
        }

        @Override
        public HiddenContentSelector union(HiddenContentSelector other) {
            if (other instanceof All all) {
                assert mutable == all.mutable;
                return this;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection required) {
            return false;
        }

        @Override
        public String toString() {
            return mutable ? "*M" : "*";
        }
    }

    public static final class None extends HiddenContentSelector {

        public static final HiddenContentSelector INSTANCE = new None();

        private None() {
        }

        @Override
        public boolean isNone() {
            return true;
        }

        @Override
        public HiddenContentSelector union(HiddenContentSelector other) {
            return other;
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection required) {
            return true;
        }

        @Override
        public String toString() {
            return "X";
        }
    }

    public static final class CsSet extends HiddenContentSelector {

        // to boolean 'mutable'
        private final Map<Integer, Boolean> map;

        public CsSet(Set<Integer> set) {
            assert set != null && !set.isEmpty() && set.stream().allMatch(i -> i >= 0);
            this.map = set.stream().collect(Collectors.toUnmodifiableMap(s -> s, s -> false));
        }

        public CsSet(Map<Integer, Boolean> map) {
            assert map != null && !map.isEmpty() && map.keySet().stream().allMatch(i -> i >= 0);
            this.map = Map.copyOf(map);
        }

        public static HiddenContentSelector selectTypeParameter(int i) {
            return new CsSet(Map.of(i, false));
        }

        public static HiddenContentSelector selectTypeParameters(int... is) {
            return new CsSet(Arrays.stream(is).boxed().collect(Collectors.toUnmodifiableMap(i -> i, i -> false)));
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection other) {
            if (other instanceof None) throw new UnsupportedOperationException();
            return !(other instanceof All) && Collections.disjoint(map.keySet(), ((CsSet) other).map.keySet());
        }

        @Override
        public String toString() {
            return map.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(e -> e.getKey() + (e.getValue() ? "M" : ""))
                    .collect(Collectors.joining(",", "<", ">"));
        }

        public Set<Integer> set() {
            return map.keySet();
        }

        public Map<Integer, Boolean> getMap() {
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
        public HiddenContentSelector union(HiddenContentSelector other) {
            assert !(other instanceof All);
            if (other instanceof None) return this;
            Map<Integer, Boolean> map = new HashMap<>(this.map);
            map.putAll(((CsSet) other).map);
            assert !map.isEmpty();
            return new CsSet(map);
        }
    }
}
