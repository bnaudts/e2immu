package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;

import java.util.*;

public class LV implements Comparable<LV> {
    private static final int HC = 4;
    private static final int HC_MUTABLE = 3;
    private static final int DEPENDENT = 2;

    public static final LV LINK_STATICALLY_ASSIGNED = new LV(0, null, null, "-0-",
            CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_ASSIGNED = new LV(1, null, null, "-1-", CausesOfDelay.EMPTY,
            MultiLevel.DEPENDENT_DV);
    public static final LV LINK_DEPENDENT = new LV(DEPENDENT, null, null, "-2-", CausesOfDelay.EMPTY,
            MultiLevel.DEPENDENT_DV);

    // use of this value is severely restricted! Use in ShortestPath, ComputeLinkedVariables
    public static final LV LINK_HC_MUTABLE = new LV(HC_MUTABLE, null, null, "-3-",
            CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);

    // do not use for equality! Use LV.isCommonHC()
    public static final LV LINK_COMMON_HC = new LV(HC, null, null, "-4-", CausesOfDelay.EMPTY,
            MultiLevel.INDEPENDENT_HC_DV);
    public static final LV LINK_INDEPENDENT = new LV(5, null, null, "-5-", CausesOfDelay.EMPTY,
            MultiLevel.INDEPENDENT_DV);

    private final int value;
    private final HiddenContentSelector mine;
    private final HiddenContentSelector theirs;
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

    private LV(int value, HiddenContentSelector mine, HiddenContentSelector theirs,
               String label, CausesOfDelay causesOfDelay, DV correspondingIndependent) {
        this.value = value;
        this.mine = mine;
        this.theirs = theirs;
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

    public HiddenContentSelector mine() {
        return mine;
    }

    public HiddenContentSelector theirs() {
        return theirs;
    }

    public String label() {
        return label;
    }

    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    public static LV delay(CausesOfDelay causes) {
        assert causes.isDelayed();
        return new LV(-1, null, null, causes.label(), causes, causes);
    }

    public static LV createHC(HiddenContentSelector mine, HiddenContentSelector theirs) {
        assert mine != null && theirs != null;
        assert mine.containsMutable() == theirs.containsMutable();
        assert !mine.isNone() && !theirs.isNone()
               && !mine.isDelayed() && !theirs.isDelayed()
               && !(mine.isAll() && theirs.isAll());
        assert !mine.isAll() || theirs instanceof HiddenContentSelector.CsSet set && set.set().size() == 1;
        assert !theirs.isAll() || mine instanceof HiddenContentSelector.CsSet set && set.set().size() == 1;
        return new LV(HC, mine, theirs, mine + "-4-" + theirs, CausesOfDelay.EMPTY, MultiLevel.INDEPENDENT_HC_DV);
    }

    public static LV createDependent(HiddenContentSelector mine, HiddenContentSelector theirs) {
        if (mine == null || theirs == null || mine.isAll() && theirs.isAll() || mine.isNone() || theirs.isNone()) {
            return LINK_DEPENDENT;
        }
        assert mine.containsMutable() == theirs.containsMutable();
        assert !mine.isNone() && !theirs.isNone()
               && !mine.isDelayed() && !theirs.isDelayed()
               && !(mine.isAll() && theirs.isAll());
        assert !mine.isAll() || theirs instanceof HiddenContentSelector.CsSet set && set.set().size() == 1;
        assert !theirs.isAll() || mine instanceof HiddenContentSelector.CsSet set && set.set().size() == 1;
        return new LV(DEPENDENT, mine, theirs, mine + "-2-" + theirs, CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    }

    public LV reverse() {
        if (isDependent()) {
            return createDependent(theirs, mine);
        }
        if (isCommonHC()) {
            return createHC(theirs, mine);
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
            HiddenContentSelector mineUnion = mine.union(other.mine);
            HiddenContentSelector theirsUnion = theirs.union(other.theirs);
            return createHC(mineUnion, theirsUnion);
        }
        return this;
    }

    private boolean mineEqualsTheirs(LV other) {
        return Objects.equals(mine, other.mine) && Objects.equals(theirs, other.theirs);
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
        assert value != HC || other.value != HC || mineEqualsTheirs(other);
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
        if (value == HC && (mine.containsMutable() || theirs.containsMutable())) {
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
        return Objects.equals(mine, lv.mine)
               && Objects.equals(theirs, lv.theirs)
               && Objects.equals(causesOfDelay, lv.causesOfDelay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mine, theirs, causesOfDelay);
    }

    @Override
    public String toString() {
        return label;
    }

    public boolean containsMutable() {
        return mine != null && mine.containsMutable() || theirs != null && theirs.containsMutable();
    }

    public String minimal() {
        if (mine != null) {
            return label;
        }
        return Integer.toString(value);
    }

    public boolean isStaticallyAssignedOrAssigned() {
        return value == 0 || value == 1;
    }
}
