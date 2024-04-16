package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.stream.Collectors;

public class LV implements Comparable<LV> {
    public static final int ALL = -1;

    public static final Links NO_LINKS = new Links(Map.of());

    public record Links(Map<Integer, Link> map) implements DijkstraShortestPath.Connection {
        @Override
        public Links next(DijkstraShortestPath.Connection current) {
            throw new UnsupportedOperationException("NYI"); // FIXME implement
        }

        public Links ensureMutable(boolean b) {
            if (map.isEmpty()) return this;
            Map<Integer, Link> res = new HashMap<>();
            boolean change = false;
            for (Map.Entry<Integer, Link> entry : map.entrySet()) {
                Link link;
                if (b != entry.getValue().mutable) {
                    link = new Link(entry.getValue().to, b);
                    change = true;
                } else {
                    link = entry.getValue();
                }
                res.put(entry.getKey(), link);
            }
            return change ? new Links(Map.copyOf(res)) : this;
        }

        public HiddenContentSelector mine() {
            if (map.isEmpty()) return HiddenContentSelector.None.INSTANCE;
            Link allLink = map.get(ALL);
            if (allLink != null) {
                return allLink.mutable ? HiddenContentSelector.All.MUTABLE_INSTANCE : HiddenContentSelector.All.INSTANCE;
            }
            Map<Integer, Boolean> res = map.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                    e -> e.getValue().mutable));
            return new HiddenContentSelector.CsSet(res);
        }

        public HiddenContentSelector theirs() {
            if (map.isEmpty()) return HiddenContentSelector.None.INSTANCE;
            Map<Integer, Boolean> res = map.values().stream().collect(Collectors.toUnmodifiableMap(l -> l.to, l -> l.mutable));
            Boolean allMutable = res.get(ALL);
            if (allMutable != null) {
                return allMutable ? HiddenContentSelector.All.MUTABLE_INSTANCE : HiddenContentSelector.All.INSTANCE;
            }
            return new HiddenContentSelector.CsSet(res);
        }

        public Links theirsToTheirs(Links links) {
        }

        public Links mineToTheirs(Links links) {
        }

        public Links reverse() {
            if (map.isEmpty()) return this;
            Map<Integer, Link> map = new HashMap<>();
            for (Map.Entry<Integer, Link> e : this.map.entrySet()) {
                map.put(e.getValue().to, new Link(e.getKey(), e.getValue().mutable));
            }
            return new Links(Map.copyOf(map));
        }
    }

    public record Link(int to, boolean mutable) {
    }

    private static final int DELAY = -1;
    private static final int HC = 4;
    private static final int HC_MUTABLE = 3;
    private static final int DEPENDENT = 2;

    public static final LV LINK_STATICALLY_ASSIGNED = new LV(0, NO_LINKS, "-0-",
            CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_ASSIGNED = new LV(1, NO_LINKS, "-1-", CausesOfDelay.EMPTY,
            MultiLevel.DEPENDENT_DV);
    public static final LV LINK_DEPENDENT = new LV(DEPENDENT, NO_LINKS, "-2-", CausesOfDelay.EMPTY,
            MultiLevel.DEPENDENT_DV);

    // use of this value is severely restricted! Use in ShortestPath, ComputeLinkedVariables
    public static final LV LINK_HC_MUTABLE = new LV(HC_MUTABLE, NO_LINKS, "-3-",
            CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);

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

    public boolean isHCMutable() {
        return HC_MUTABLE == value;
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
        for (Map.Entry<Integer, Link> e : links.map.entrySet()) {
            boolean mutable = e.getValue().mutable;
            boolean fromIsAll = e.getKey() < 0;
            String f = (fromIsAll ? "*" : "" + e.getKey()) + (mutable ? "M" : "");
            boolean toIsAll = e.getValue().to < 0;
            String t = (toIsAll ? "*" : "" + e.getValue().to) + (mutable ? "M" : "");
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

    public static Links matchingLinks(HiddenContentSelector from, HiddenContentSelector to) {
        if (from.isAll()) {
            if (to instanceof HiddenContentSelector.CsSet set) {
                assert set.set().size() == 1;
                return new Links(Map.of(ALL, new Link(set.set().stream().findFirst().orElseThrow(),
                        from.containsMutable())));
            } else throw new UnsupportedOperationException();
        }
        if (to.isAll()) {
            if (from instanceof HiddenContentSelector.CsSet set) {
                assert set.set().size() == 1;
                int i = set.set().stream().findFirst().orElseThrow();
                return new Links(Map.of(i, new Link(ALL, from.containsMutable())));
            } else throw new UnsupportedOperationException();
        }
        if (from instanceof HiddenContentSelector.CsSet set) {
            Map<Integer, Link> res = new HashMap<>();
            for (Map.Entry<Integer, Boolean> entry : set.getMap().entrySet()) {
                res.put(entry.getKey(), new Link(entry.getKey(), entry.getValue()));
            }
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
        assert !isHCMutable() : "Internal use only";
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
        Map<Integer, Link> res = new HashMap<>(links.map);
        for (Map.Entry<Integer, Link> e : other.map.entrySet()) {
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
        return links.map.size() == 1 && links.map.containsKey(ALL);
    }

    public boolean theirsIsAll() {
        return links.map.size() == 1 && links.map.values().stream().findFirst().orElseThrow().to == ALL;
    }
}
