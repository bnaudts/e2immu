package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
variable m               variable n
Map<A,List<B>> --- 2 --- Map<X,List<C>>
links: 0->[0], 1.0->[1.0]

Map<A,List<A>> --- 2 --- Map<List<X>,X>
links: [0,1.0] -> [0.1,1]

[0]->[0] list.subList()
[0]->*  list.get()

 */
public class LV implements Comparable<LV> {
    public static final int ALL = -1;
    public static final Index ALL_INDEX = new Index(List.of(ALL));
    public static final Indices ALL_INDICES = new Indices(Set.of(ALL_INDEX));

    // for testing...
    public static final Indices INDICES_0 = new Indices(0);
    public static final Indices INDICES_1 = new Indices(1);
    public static final Links NO_LINKS = new Links(Map.of());

    public record Index(List<Integer> list) implements Comparable<Index> {
        public static Index createZeroes(int arrays) {
            List<Integer> list = new ArrayList<>(arrays);
            for (int i = 0; i < arrays; i++) list.add(0);
            return new Index(List.copyOf(list));
        }

        @Override
        public int compareTo(Index o) {
            return ListUtil.compare(list, o.list);
        }

        @Override
        public String toString() {
            return list.stream().map(Object::toString).collect(Collectors.joining("."));
        }

        /*
         extract type given the indices. Switch to formal for the last index in the list only!
         Map.Entry<A,B>, with index 0, will return K in Map.Entry
         Set<Map.Entry<A,B>>, with indices 0.1, will return V in Map.Entry.
         */
        public ParameterizedType findInFormal(InspectionProvider inspectionProvider, ParameterizedType type) {
            return findInFormal(inspectionProvider, type, 0);
        }

        private ParameterizedType findInFormal(InspectionProvider inspectionProvider, ParameterizedType type, int pos) {
            if (type.parameters.isEmpty()) {
                // no generics, so substitute "Object"
                return inspectionProvider.getPrimitives().objectParameterizedType();
            }
            int index = list.get(pos);
            if (pos == list.size() - 1) {
                assert type.typeInfo != null;
                ParameterizedType formal = type.typeInfo.asParameterizedType(inspectionProvider);
                return formal.parameters.get(index);
            }
            ParameterizedType nextType = type.parameters.get(index);
            assert nextType != null;
            return findInFormal(inspectionProvider, nextType, pos + 1);
        }

        public Index replaceLast(int v) {
            if (list.get(list.size() - 1) == v) return this;
            return new Index(Stream.concat(list.stream().limit(list.size() - 1), Stream.of(v)).toList());
        }

        public int countSequentialZeros() {
            int cnt = 0;
            for (int i : list) {
                if (i != 0) return -1;
                cnt++;
            }
            return cnt;
        }
    }

    // important: as soon as there are multiple elements, use a TreeSet!!
    public record Indices(Set<Index> set) implements Comparable<Indices> {
        public Indices {
            assert set != null && !set.isEmpty() && (set.size() == 1 || set instanceof TreeSet);
            assert set.size() == 1 || !set.contains(ALL_INDEX);
        }

        public Indices(int i) {
            this(Set.of(new Index(List.of(i))));
        }

        @Override
        public String toString() {
            return set.stream().map(Object::toString).collect(Collectors.joining(";"));
        }

        @Override
        public int compareTo(Indices o) {
            Iterator<Index> mine = set.iterator();
            Iterator<Index> theirs = o.set.iterator();
            while (mine.hasNext()) {
                if (!theirs.hasNext()) return 1;
                int c = mine.next().compareTo(theirs.next());
                if (c != 0) return c;
            }
            if (theirs.hasNext()) return -1;
            return 0;
        }

        public Indices merge(Indices indices) {
            return new Indices(Stream.concat(set.stream(), indices.set.stream())
                    .collect(Collectors.toCollection(TreeSet::new)));
        }

        public ParameterizedType findInFormal(InspectionProvider inspectionProvider, ParameterizedType type) {
            // in theory, they all should map to the same type... so we pick one
            Index first = set.stream().findFirst().orElseThrow();
            return first.findInFormal(inspectionProvider, type);
        }
    }

    public record Links(Map<Indices, Link> map) implements DijkstraShortestPath.Connection {
        public Links(int from, int to) {
            this(Map.of(from == ALL ? LV.ALL_INDICES : new LV.Indices(Set.of(new LV.Index(List.of(from)))),
                    new LV.Link(new LV.Indices(Set.of(new LV.Index(List.of(to)))), false)));
        }

        /*
        this method, together with allowModified(), is key to the whole linking + modification process of
        ComputeLinkedVariables.
         */
        @Override
        public Links next(DijkstraShortestPath.Connection current) {
            if (current == NO_LINKS || this == NO_LINKS) {
                return this;
            }
            Map<Indices, Link> res = new HashMap<>();
            for (Map.Entry<Indices, Link> entry : ((Links) current).map.entrySet()) {
                Indices middle = entry.getValue().to;
                Link link = this.map.get(middle);
                if (link != null) {
                    boolean fromAllToAll = entry.getKey().equals(LV.ALL_INDICES) && link.to.equals(LV.ALL_INDICES);
                    if (!fromAllToAll) {
                        boolean middleIsAll = middle.equals(ALL_INDICES);
                        boolean mutable = !middleIsAll && entry.getValue().mutable && link.mutable;
                        Link newLink = mutable == link.mutable ? link : new Link(link.to, false);
                        res.put(entry.getKey(), newLink);
                    }
                } else {
                    Link allLink = this.map.get(ALL_INDICES);
                    if (allLink != null) {
                        // start again from *
                        boolean mutable = entry.getValue().mutable || allLink.mutable;
                        Link newLink = mutable == allLink.mutable ? allLink : new Link(allLink.to, true);
                        res.put(entry.getKey(), newLink);
                    }
                }
            }
            if (res.isEmpty()) return null;
            return new Links(res);
        }

        public Links theirsToTheirs(Links links) {
            Map<Indices, Link> res = new HashMap<>();
            map.forEach((thisFrom, thisTo) -> {
                Link link = links.map.get(thisTo.to);
                res.put(thisTo.to, link);
            });
            return new Links(res);
        }

        // use thisTo.to as the intermediary
        public Links mineToTheirs(Links links) {
            Map<Indices, Link> res = new HashMap<>();
            map.forEach((thisFrom, thisTo) -> {
                Link link = links.map.get(thisTo.to);
                res.put(thisFrom, link);
            });
            return new Links(res);
        }

        public Links reverse() {
            if (map.isEmpty()) return this;
            Map<Indices, Link> map = new HashMap<>();
            for (Map.Entry<Indices, Link> e : this.map.entrySet()) {
                map.put(e.getValue().to, new Link(e.getKey(), e.getValue().mutable));
            }
            return new Links(Map.copyOf(map));
        }
    }

    public record Link(Indices to, boolean mutable) {
        public Link correctTo(Map<Indices, Indices> correctionMap) {
            return new Link(correctionMap.getOrDefault(to, to), mutable);
        }
    }

    private static final int DELAY = -1;
    private static final int HC = 4;
    private static final int DEPENDENT = 2;

    public static final LV LINK_STATICALLY_ASSIGNED = new LV(0, NO_LINKS, "-0-",
            CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_ASSIGNED = new LV(1, NO_LINKS, "-1-", CausesOfDelay.EMPTY,
            MultiLevel.DEPENDENT_DV);
    public static final LV LINK_DEPENDENT = new LV(DEPENDENT, NO_LINKS, "-2-", CausesOfDelay.EMPTY,
            MultiLevel.DEPENDENT_DV);

    // do not use for equality! Use LV.isCommonHC()
    public static final LV LINK_COMMON_HC = new LV(HC, NO_LINKS, "-4-", CausesOfDelay.EMPTY,
            MultiLevel.INDEPENDENT_HC_DV);
    public static final LV LINK_INDEPENDENT = new LV(5, NO_LINKS, "-5-", CausesOfDelay.EMPTY,
            MultiLevel.INDEPENDENT_DV);

    private final int value;

    public boolean haveLinks() {
        return !links.map.isEmpty();
    }


    private final Links links;
    private final String label;
    private final CausesOfDelay causesOfDelay;
    private final DV correspondingIndependent;

    public boolean isDependent() {
        return DEPENDENT == value;
    }

    public boolean isCommonHC() {
        return HC == value;
    }

    private LV(int value, Links links, String label, CausesOfDelay causesOfDelay, DV correspondingIndependent) {
        this.value = value;
        this.links = links;
        this.label = Objects.requireNonNull(label);
        assert !label.isBlank();
        this.causesOfDelay = Objects.requireNonNull(causesOfDelay);
        this.correspondingIndependent = correspondingIndependent;
    }

    public static LV initialDelay() {
        return delay(DelayFactory.initialDelay());
    }

    public int value() {
        return value;
    }

    public Links links() {
        return links;
    }

    public String label() {
        return label;
    }

    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    public static LV delay(CausesOfDelay causes) {
        assert causes.isDelayed();
        return new LV(DELAY, NO_LINKS, causes.label(), causes, causes);
    }

    private static String createLabel(Links links, int hc) {
        List<String> from = new ArrayList<>();
        List<String> to = new ArrayList<>();
        int countAll = 0;
        for (Map.Entry<Indices, Link> e : links.map.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            boolean mutable = e.getValue().mutable;
            boolean fromIsAll = e.getKey().equals(ALL_INDICES);
            String f = (fromIsAll ? "*" : "" + e.getKey()) + (mutable ? "M" : "");
            Indices i = e.getValue().to;
            assert i != null;
            boolean toIsAll = i.equals(ALL_INDICES);
            String t = (toIsAll ? "*" : "" + i) + (mutable ? "M" : "");
            assert !(fromIsAll && toIsAll);
            from.add(f);
            to.add(t);
            countAll += (fromIsAll || toIsAll) ? 1 : 0;
        }
        assert countAll <= 1;
        assert from.size() == to.size();
        assert hc != HC || !from.isEmpty();
        return String.join(",", from) + "-" + hc + "-" + String.join(",", to);
    }


    public static LV createHC(Links links) {
        return new LV(HC, links, createLabel(links, HC), CausesOfDelay.EMPTY, MultiLevel.INDEPENDENT_HC_DV);
    }

    public static LV createDependent(Links links) {
        return new LV(DEPENDENT, links, createLabel(links, DEPENDENT), CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    }

    /*
    go from hidden content selectors to an actual Links object, in the method context.
     */
    public static Links matchingLinks(HiddenContentSelector from, HiddenContentSelector to) {
        if (from.isAll()) {
            if (to instanceof HiddenContentSelector.CsSet set) {
                assert set.set().size() == 1;
                // hidden content to a number of indices, locations in the type
                Map.Entry<Integer, Indices> indexMapEntry = set.getMap().entrySet().stream().findFirst().orElseThrow();
                return new Links(Map.of(ALL_INDICES, new Link(indexMapEntry.getValue(), false)));
            } else throw new UnsupportedOperationException();
        }
        if (to.isAll()) {
            if (from instanceof HiddenContentSelector.CsSet set) {
                assert set.set().size() == 1;
                Map.Entry<Integer, Indices> indexMapEntry = set.getMap().entrySet().stream().findFirst().orElseThrow();
                return new Links(Map.of(ALL_INDICES, new Link(indexMapEntry.getValue(), false)));
            } else throw new UnsupportedOperationException();
        }
        if (from instanceof HiddenContentSelector.CsSet setFrom && to instanceof HiddenContentSelector.CsSet setTo) {
            Map<Indices, Link> res = new HashMap<>();
            for (Map.Entry<Integer, Indices> entry : setFrom.getMap().entrySet()) {
                Indices list = setTo.getMap().get(entry.getKey());
                if (list != null) {
                    res.put(entry.getValue(), new Link(list, false));
                }
            }
            assert !res.isEmpty();
            return new Links(Map.copyOf(res));
        } else throw new UnsupportedOperationException();
    }


    public LV reverse() {
        if (isDependent()) {
            return createDependent(links.reverse());
        }
        if (isCommonHC()) {
            return createHC(links.reverse());
        }
        return this;
    }

    public boolean isDelayed() {
        return causesOfDelay.isDelayed();
    }

    public boolean le(LV other) {
        return value <= other.value;
    }

    public boolean lt(LV other) {
        return value < other.value;
    }

    public boolean ge(LV other) {
        return value >= other.value;
    }

    public LV min(LV other) {
        if (isDelayed()) {
            if (other.isDelayed()) {
                return delay(causesOfDelay.merge(other.causesOfDelay));
            }
            return this;
        }
        if (other.isDelayed()) return other;
        if (value > other.value) return other;
        if (isCommonHC() && other.isCommonHC()) {
            Links union = union(other.links);
            return createHC(union);
        }
        return this;
    }

    private Links union(Links other) {
        Map<Indices, Link> res = new HashMap<>(links.map);
        for (Map.Entry<Indices, Link> e : other.map.entrySet()) {
            res.putIfAbsent(e.getKey(), e.getValue());
        }
        return new Links(Map.copyOf(res));
    }

    private boolean sameLinks(LV other) {
        return links.equals(other.links);
    }

    public LV max(LV other) {
        if (isDelayed()) {
            if (other.isDelayed()) {
                return delay(causesOfDelay.merge(other.causesOfDelay));
            }
            return this;
        }
        if (other.isDelayed()) return other;
        if (value < other.value) return other;
        assert value != HC || other.value != HC || sameLinks(other);
        return this;
    }

    public boolean isDone() {
        return causesOfDelay.isDone();
    }

    @Override
    public int compareTo(LV o) {
        return value - o.value;
    }

    public DV toIndependent() {
        if (value == HC && (containsMutable())) {
            return MultiLevel.DEPENDENT_DV;
        }
        return correspondingIndependent;
    }

    public boolean isInitialDelay() {
        return causesOfDelay().isInitialDelay();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LV lv = (LV) o;
        if (value != lv.value) return false;
        return Objects.equals(links, lv.links)
               && Objects.equals(causesOfDelay, lv.causesOfDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, links, causesOfDelay);
    }

    @Override
    public String toString() {
        return label;
    }

    public boolean containsMutable() {
        return links.map.values().stream().anyMatch(l -> l.mutable);
    }

    public String minimal() {
        if (!links.map.isEmpty()) {
            return label;
        }
        return Integer.toString(value);
    }

    public boolean isStaticallyAssignedOrAssigned() {
        return value == 0 || value == 1;
    }

    public boolean mineIsAll() {
        return links.map.size() == 1 && links.map.containsKey(ALL_INDICES);
    }

    public boolean theirsIsAll() {
        return links.map.size() == 1 && links.map.values().stream().findFirst().orElseThrow().to.equals(ALL_INDICES);
    }

    /*
    modifications travel the -4- links ONLY when the link is *M--4--xx
     */
    public boolean allowModified() {
        if (value != HC) return true;
        if (links.map.size() == 1) {
            Map.Entry<Indices, Link> entry = links.map.entrySet().stream().findFirst().orElseThrow();
            return entry.getValue().mutable && ALL_INDICES.equals(entry.getKey());
        }
        return false;
    }


    public LV correctTo(Map<Indices, Indices> correctionMap) {
        if (links.map.isEmpty()) return this;
        boolean isHc = isCommonHC();
        Map<Indices, Link> updatedMap = links.map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().correctTo(correctionMap)));
        Links updatedLinks = new Links(updatedMap);
        return isHc ? createHC(updatedLinks) : createDependent(updatedLinks);
    }
}
