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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Convention for spotting delays:

1. at assignment level: no delays, never
2. at dependent, independent1 level: add the variable, with DELAYED_VALUE
 */
public class LinkedVariables implements Comparable<LinkedVariables> {

    private final Map<Variable, DV> variables;

    private LinkedVariables(Map<Variable, DV> variables) {
        assert variables != null;
        this.variables = variables;
        assert variables.values().stream().noneMatch(dv -> dv == DV.FALSE_DV || dv == MultiLevel.INDEPENDENT_1_DV);
    }

    // never use .equals() here, marker
    public static final LinkedVariables NOT_YET_SET = new LinkedVariables(Map.of());
    // use .equals, not a marker
    public static final LinkedVariables EMPTY = new LinkedVariables(Map.of());

    public static DV fromIndependentToLinkedVariableLevel(DV independent) {
        if (independent.isDelayed()) return independent;
        if (MultiLevel.INDEPENDENT_DV.equals(independent)) return LinkedVariables.LINK_NONE;
        if (MultiLevel.DEPENDENT_DV.equals(independent)) return LinkedVariables.LINK_DEPENDENT;
        return LINK_INDEPENDENT1;
    }

    public static DV fromImmutableToLinkedVariableLevel(DV immutable) {
        if (immutable.isDelayed()) return immutable;
        // REC IMM -> NO_LINKING
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return LinkedVariables.LINK_NONE;
        int level = MultiLevel.level(immutable);
        if (level == 0) return LINK_DEPENDENT;
        return LINK_INDEPENDENT1;
    }

    public static DV fromLinkedVariableToIndependent(DV linked) {
        int value = linked.value();
        if (value <= 2) return LINK_DEPENDENT;
        return LINK_INDEPENDENT1;
    }

    public boolean isDelayed() {
        if (this == NOT_YET_SET) return true;
        return variables.values().stream().anyMatch(DV::isDelayed);
    }

    public static final DV LINK_STATICALLY_ASSIGNED = new NoDelay(0, "statically_assigned");
    public static final DV LINK_ASSIGNED = new NoDelay(1, "assigned");
    public static final DV LINK_DEPENDENT = new NoDelay(2, "dependent");
    public static final DV LINK_INDEPENDENT1 = new NoDelay(3, "independent1");
    public static final DV LINK_NONE = new NoDelay(MultiLevel.MAX_LEVEL, "no");

    public static LinkedVariables of(Variable variable, DV value) {
        return new LinkedVariables(Map.of(variable, value));
    }

    public static LinkedVariables of(Map<Variable, DV> map) {
        return new LinkedVariables(Map.copyOf(map));
    }

    public static LinkedVariables of(Variable var1, DV v1, Variable var2, DV v2) {
        return new LinkedVariables(Map.of(var1, v1, var2, v2));
    }

    public static boolean isNotIndependent(DV assignedOrLinked) {
        return assignedOrLinked.ge(LINK_STATICALLY_ASSIGNED) && assignedOrLinked.lt(LINK_NONE);
    }

    public static boolean isAssigned(DV level) {
        return level.equals(LINK_STATICALLY_ASSIGNED) || level.equals(LINK_ASSIGNED);
    }

    public LinkedVariables mergeDelay(LinkedVariables other, DV whenMissing) {
        assert whenMissing.isDelayed();
        HashMap<Variable, DV> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            DV inMap = map.get(v);
            if (inMap == null) {
                map.put(v, whenMissing);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                DV merged = inMap.equals(LINK_STATICALLY_ASSIGNED) ? LINK_STATICALLY_ASSIGNED : whenMissing;
                map.put(v, merged);
            }
        });
        return of(map);
    }

    /*
    goal of the 'minimum' parameter:

    x = new X(b); expression b has linked variables b,0 when b is a variable expression
    the variable independent gives the independence of the first parameter of the constructor X(b)

    if DEPENDENT, then x is linked in an accessible way to b (maybe it stores b); max(0,0)=0
    if INDEPENDENT_1, then changes to b may imply changes to the hidden content of x; max(0,1)=1

    if the expression is e.g. c,1, as in new B(c)

    if DEPENDENT, then x is linked in an accessible way to an object b which is content linked to c
    changes to c have an impact on the hidden content of b, which is linked in an accessible way
    (leaving the hidden content hidden?) to x  max(1,0)=1
    if INDEPENDENT_1, then x is hidden content linked to b is hidden content linked to c, max(1,1)=1
     */
    public LinkedVariables merge(LinkedVariables other, DV minimum) {
        if (this == NOT_YET_SET || other == NOT_YET_SET) return NOT_YET_SET;

        HashMap<Variable, DV> map = new HashMap<>(variables);
        other.variables.forEach((v, i) -> {
            DV newValue = minimum == DV.MIN_INT_DV ? i : i.max(minimum);
            DV inMap = map.get(v);
            if (inMap == null) {
                map.put(v, newValue);
            } else {
                // once 0, always 0 (we do not accept delays on 0!)
                DV merged = newValue.equals(LINK_STATICALLY_ASSIGNED) || inMap.equals(LINK_STATICALLY_ASSIGNED)
                        ? LINK_STATICALLY_ASSIGNED : newValue.min(inMap);
                map.put(v, merged);
            }
        });
        return of(map);
    }

    public LinkedVariables merge(LinkedVariables other) {
        return merge(other, DV.MIN_INT_DV); // no effect
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    @Override
    public String toString() {
        if (this == NOT_YET_SET) return "NOT_YET_SET";
        if (this == EMPTY || variables.isEmpty()) return "";
        return variables.entrySet().stream()
                .map(e -> e.getKey().minimalOutput() + ":" + e.getValue().value())
                .sorted()
                .collect(Collectors.joining(","));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedVariables that = (LinkedVariables) o;
        return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
        return variables.hashCode();
    }

    public boolean contains(Variable variable) {
        return variables.containsKey(variable);
    }

    public Stream<Map.Entry<Variable, DV>> stream() {
        return variables.entrySet().stream();
    }

    public LinkedVariables minimum(DV minimum) {
        if (this == NOT_YET_SET) return NOT_YET_SET;
        return of(variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> minimum.max(e.getValue()))));
    }

    public Stream<Variable> variablesAssignedOrDependent() {
        return variables.entrySet().stream()
                .filter(e -> isAssignedOrLinked(e.getValue()))
                .map(Map.Entry::getKey);
    }

    public Stream<Variable> variablesAssigned() {
        return variables.entrySet().stream()
                .filter(e -> isAssigned(e.getValue()))
                .map(Map.Entry::getKey);
    }

    public Stream<Variable> independent1Variables() {
        return variables.entrySet().stream()
                .filter(e -> e.getValue().gt(LINK_DEPENDENT))
                .map(Map.Entry::getKey);
    }

    public LinkedVariables translate(TranslationMap translationMap) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        var translatedVariables = variables.entrySet().stream()
                .collect(Collectors.toMap(e -> translationMap.translateVariable(e.getKey()), Map.Entry::getValue, DV::min));
        if (translatedVariables.equals(variables)) return this;
        return of(translatedVariables);
    }

    public LinkedVariables changeToDelay(DV delay) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        assert delay.isDelayed();
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().equals(LINK_STATICALLY_ASSIGNED) ? LINK_STATICALLY_ASSIGNED : delay.min(e.getValue())));
        return of(map);
    }

    public LinkedVariables remove(Set<Variable> reassigned) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .filter(e -> !reassigned.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }

    public LinkedVariables remove(Predicate<Variable> remove) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .filter(e -> !remove.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return of(map);
    }

    public LinkedVariables changeNonStaticallyAssignedToDelay(DV delay) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        assert delay.isDelayed();
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    DV lv = e.getValue();
                    return LINK_STATICALLY_ASSIGNED.equals(lv) ? LINK_STATICALLY_ASSIGNED : delay.max(lv);
                }));
        return of(map);
    }

    public LinkedVariables changeAllTo(DV value) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        Map<Variable, DV> map = variables.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> value));
        return of(map);
    }

    public DV value(Variable variable) {
        return variables.get(variable);
    }

    /*
    we prune a linked variables map, based on immutable values.
    if the source is @ERImmutable, then there cannot be linked; but the same holds for the targets!
     */
    public LinkedVariables removeIncompatibleWithImmutable(Variable source,
                                                           Predicate<Variable> myself,
                                                           Function<Variable, DV> computeImmutable,
                                                           Function<Variable, DV> immutableCanBeIncreasedByTypeParameters,
                                                           Function<Variable, DV> computeImmutableHiddenContent,
                                                           Function<Variable, DV> computeTransparent) {
        if (isEmpty() || this == NOT_YET_SET) return this;
        DV sourceImmutable = computeImmutable.apply(source);
        if (sourceImmutable.isDelayed()) {
            return changeToDelay(sourceImmutable); // but keep the 0
        }
        DV sourceTransparent = computeTransparent.apply(source);
        if (sourceTransparent.isDelayed()) {
            return changeToDelay(sourceTransparent);
        }

        Map<Variable, DV> adjustedSource;
        if (!variables.isEmpty() && sourceImmutable.ge(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV)) {
            // level 2+ -> remove all @Dependent
            boolean recursivelyImmutable = sourceImmutable.equals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV);
            adjustedSource = variables.entrySet().stream()
                    .filter(e -> recursivelyImmutable ? e.getValue().le(LINK_ASSIGNED) :
                            !e.getValue().equals(LINK_DEPENDENT))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            adjustedSource = variables;
        }
        Map<Variable, DV> result = new HashMap<>();
        for (Map.Entry<Variable, DV> entry : adjustedSource.entrySet()) {
            DV linkLevel = entry.getValue();
            Variable target = entry.getKey();
            if (myself.test(target) || linkLevel.equals(LINK_STATICALLY_ASSIGNED) || sourceTransparent.valueIsTrue()) {
                result.put(target, linkLevel);
            } else {
                DV targetImmutable = computeImmutable.apply(target);
                if (targetImmutable.isDelayed()) {
                    result.put(target, targetImmutable);
                } else if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(targetImmutable)) {
                    // targetImmutable is @ERImmutable; only assignments kept
                    if (linkLevel.le(LINK_ASSIGNED)) {
                        result.put(target, linkLevel);
                    }
                } else {
                    if (linkLevel.le(LINK_DEPENDENT)) {
                        result.put(target, linkLevel);
                    } else { // INDEPENDENT1+
                        /*
                         so we may have a mutable object linked at content level to the variable
                         e.g. this.set = new HashSet<>(inputSet)

                         if this set is a set of recursively immutable objects, the linking disappears
                         if the set contains level 2 immutable objects, the linking should go to a higher level than
                         independent_1, but we're not worried about that right now
                         */
                        DV transparent = computeTransparent.apply(target);
                        if (transparent.isDelayed()) {
                            result.put(target, transparent);
                        } else if (transparent.valueIsTrue()) {
                            result.put(target, LINK_INDEPENDENT1);
                        } else {
                            DV canIncrease = immutableCanBeIncreasedByTypeParameters.apply(target);
                            if (canIncrease.isDelayed()) {
                                result.put(target, canIncrease);
                            } else if (canIncrease.valueIsTrue()) {
                                DV immutableHidden = computeImmutableHiddenContent.apply(target);
                                if (immutableHidden.isDelayed()) {
                                    result.put(target, immutableHidden);
                                } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableHidden)) {
                                    result.put(target, LINK_INDEPENDENT1);
                                }
                            } else {
                                result.put(target, LINK_INDEPENDENT1);
                            }
                        }
                    }
                }
            }
        }
        return of(result);
    }

    public static boolean isAssignedOrLinked(DV dependent) {
        return dependent.ge(LINK_STATICALLY_ASSIGNED) && dependent.le(LINK_DEPENDENT);
    }

    public static final CausesOfDelay NOT_YET_SET_DELAY = DelayFactory.createDelay(Location.NOT_YET_SET,
            CauseOfDelay.Cause.LINKING);

    public CausesOfDelay causesOfDelay() {
        if (this == NOT_YET_SET) {
            return NOT_YET_SET_DELAY;
        }
        return variables.values().stream()
                .map(DV::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    public Map<Variable, DV> variables() {
        return variables;
    }

    public boolean isDone() {
        if (this == NOT_YET_SET) return false;
        return causesOfDelay().isDone();
    }

    @Override
    public int compareTo(LinkedVariables o) {
        return Properties.compareMaps(variables, o.variables);
    }

    private List<Variable> staticallyAssigned() {
        return variables.entrySet().stream()
                .filter(e -> e.getValue().equals(LINK_STATICALLY_ASSIGNED))
                .map(Map.Entry::getKey).sorted().toList();
    }

    public boolean identicalStaticallyAssigned(LinkedVariables linkedVariables) {
        return staticallyAssigned().equals(linkedVariables.staticallyAssigned());
    }

    public LinkedVariables nonDelayedPart() {
        if (isEmpty() || this == NOT_YET_SET) return this;
        return of(variables.entrySet().stream()
                .filter(e -> e.getValue().isDone())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public Set<Variable> directAssignmentVariables() {
        if (isEmpty() || this == NOT_YET_SET) return Set.of();
        return variables.entrySet().stream().filter(e -> e.getValue().equals(LINK_STATICALLY_ASSIGNED))
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
}
