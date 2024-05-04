package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.function.IntFunction;
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
    public static final int UNSPECIFIED = -2;
    public static final Index UNSPECIFIED_INDEX = new Index(List.of(UNSPECIFIED));
    public static final Indices UNSPECIFIED_INDICES = new Indices(Set.of(UNSPECIFIED_INDEX));

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
            return findInFormal(inspectionProvider, type, 0, true);
        }

        public ParameterizedType find(InspectionProvider inspectionProvider, ParameterizedType type) {
            return findInFormal(inspectionProvider, type, 0, false);
        }

        private ParameterizedType findInFormal(InspectionProvider inspectionProvider, ParameterizedType type,
                                               int pos,
                                               boolean switchToFormal) {
            if (type.parameters.isEmpty()) {
                // no generics, so substitute "Object"
                if (type.typeInfo != null) {
                    HiddenContentTypes hct = type.typeInfo.typeResolution.get().hiddenContentTypes();
                    NamedType byIndex = hct.typeByIndex(pos);
                    if (byIndex != null) {
                        return byIndex.asParameterizedType(inspectionProvider);
                    }
                }
                return inspectionProvider.getPrimitives().objectParameterizedType();
            }
            int index = list.get(pos);
            if (pos == list.size() - 1) {
                assert type.typeInfo != null;
                ParameterizedType formal = switchToFormal ? type.typeInfo.asParameterizedType(inspectionProvider) : type;
                assert index < formal.parameters.size();
                return formal.parameters.get(index);
            }
            ParameterizedType nextType = type.parameters.get(index);
            assert nextType != null;
            return findInFormal(inspectionProvider, nextType, pos + 1, switchToFormal);
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

        public Index dropFirst() {
            assert list.size() > 1;
            return new Index(list.subList(1, list.size()));
        }

        public Index takeFirst() {
            assert list.size() > 1;
            return new Index(List.of(list.get(0)));
        }

        public Index prefix(int index) {
            return new Index(Stream.concat(Stream.of(index), list.stream()).toList());
        }

        public Integer single() {
            return list.size() == 1 ? list.get(0) : null;
        }

        public Index map(IntFunction<Integer> intFunction) {
            int index = list.get(0);
            return new Index(Stream.concat(Stream.of(intFunction.apply(index)), list.stream().skip(1)).toList());
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

        public ParameterizedType find(InspectionProvider inspectionProvider, ParameterizedType type) {
            // in theory, they all should map to the same type... so we pick one
            Index first = set.stream().findFirst().orElseThrow();
            return first.find(inspectionProvider, type);
        }

        public LV.Indices allOccurrencesOf(InspectionProvider inspectionProvider, ParameterizedType where) {
            Index first = set.stream().findFirst().orElseThrow();
            ParameterizedType what = first.find(inspectionProvider, where);
            return allOccurrencesOf(what, where);
        }

        public static LV.Indices allOccurrencesOf(ParameterizedType what, ParameterizedType where) {
            Set<LV.Index> set = new TreeSet<>();
            allOccurrencesOf(what, where, set, new Stack<>());
            if (set.isEmpty()) return null;
            return new LV.Indices(set);
        }

        private static void allOccurrencesOf(ParameterizedType what, ParameterizedType where, Set<LV.Index> set, Stack<Integer> pos) {
            if (what.equals(where)) {
                LV.Index index = new LV.Index(List.copyOf(pos));
                set.add(index);
                return;
            }
            int i = 0;
            for (ParameterizedType pt : where.parameters) {
                pos.push(i);
                allOccurrencesOf(what, pt, set, pos);
                pos.pop();
                i++;
            }
        }

        public boolean containsSize2Plus() {
            return set.stream().anyMatch(i -> i.list.size() > 1);
        }

        public Indices size2PlusDropOne() {
            return new Indices(set.stream().filter(i -> i.list.size() > 1)
                    .map(Index::dropFirst)
                    .collect(Collectors.toCollection(TreeSet::new)));
        }

        public Indices first() {
            return new Indices(set.stream().filter(i -> i.list.size() > 1)
                    .map(Index::takeFirst)
                    .collect(Collectors.toCollection(TreeSet::new)));
        }

        public Indices prefix(int index) {
            Set<Index> newSet = set.stream().map(i -> i.prefix(index)).collect(Collectors.toUnmodifiableSet());
            return new Indices(newSet);
        }

        public Integer single() {
            return set.stream().findFirst().map(Index::single).orElse(null);
        }

        public Indices map(IntFunction<Integer> intFunction) {
            return new Indices(set.stream().map(index -> index.map(intFunction)).collect(Collectors.toUnmodifiableSet()));
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
                        res.merge(entry.getKey(), newLink, Link::merge);
                    }
                } else {
                    Link allLink = this.map.get(ALL_INDICES);
                    if (allLink != null) {
                        // start again from *
                        boolean mutable = entry.getValue().mutable || allLink.mutable;
                        Link newLink = mutable == allLink.mutable ? allLink : new Link(allLink.to, true);
                        res.merge(entry.getKey(), newLink, Link::merge);
                    }
                }
            }
            if (res.isEmpty()) return null;
            return new Links(res);
        }

        @Override
        public DijkstraShortestPath.Connection merge(DijkstraShortestPath.Connection connection) {
            Links other = (Links) connection;
            Map<Indices, Link> res = new HashMap<>(map);
            other.map.forEach((k, v) -> res.merge(k, v, Link::merge));
            return new Links(res);
        }

        public Links theirsToTheirs(Links links) {
            Map<Indices, Link> res = new HashMap<>();
            map.forEach((thisFrom, thisTo) -> {
                Link link = links.map.get(thisTo.to);
                assert link != null;
                res.put(thisTo.to, link);
            });
            return new Links(res);
        }

        // use thisTo.to as the intermediary
        public Links mineToTheirs(Links links) {
            Map<Indices, Link> res = new HashMap<>();
            map.forEach((thisFrom, thisTo) -> {
                Link link = links.map.get(thisTo.to);
                assert link != null;
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

        public Link merge(Link l2) {
            return new Link(to.merge(l2.to), mutable || l2.mutable);
        }

        public Link prefixTheirs(int index) {
            return new Link(to.prefix(index), mutable);
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
    Assume, for now, that mutable == false.
     */
    public static Links matchingLinks(HiddenContentSelector from, HiddenContentSelector to) {
        Map<Indices, Link> res = new HashMap<>();
        for (Map.Entry<Integer, Indices> entry : from.getMap().entrySet()) {
            int hctIndex = entry.getKey();
            Indices indicesInTo = to.getMap().get(hctIndex);
            if (indicesInTo != null) {
                Indices indicesInFrom = entry.getValue();
                res.put(indicesInFrom, new Link(indicesInTo, false));
            }
        }
        assert !res.isEmpty();
        return new Links(Map.copyOf(res));
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
            // IMPORTANT: the union only "compacts" on the "to" side for now, see Linking_1A.f9m()
            Links union = union(other.links);
            return createHC(union);
        }
        return this;
    }

    private Links union(Links other) {
        Map<Indices, Link> res = new HashMap<>(links.map);
        for (Map.Entry<Indices, Link> e : other.map.entrySet()) {
            res.merge(e.getKey(), e.getValue(), Link::merge);
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
        // no correction for the "M" links in independent_hc down to dependent!
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

    public LV prefixMine(int index) {
        if (isDelayed() || isStaticallyAssignedOrAssigned()) return this;
        if (links.map.isEmpty()) return this;
        Map<Indices, Link> newMap = links.map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().prefix(index), Map.Entry::getValue));
        Links newLinks = new Links(newMap);
        return isCommonHC() ? LV.createHC(newLinks) : LV.createDependent(newLinks);
    }

    public LV prefixTheirs(int index) {
        if (isDelayed() || isStaticallyAssignedOrAssigned()) return this;
        if (links.map.isEmpty()) return this;
        Map<Indices, Link> newMap = links.map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().prefixTheirs(index)));
        Links newLinks = new Links(newMap);
        return isCommonHC() ? LV.createHC(newLinks) : LV.createDependent(newLinks);
    }

    public LV changeToHc() {
        if (value == DEPENDENT) {
            return createHC(links);
        }
        return this;
    }
}
