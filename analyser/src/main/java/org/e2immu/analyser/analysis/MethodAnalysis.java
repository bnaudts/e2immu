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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.analyser.util.ParSeq;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface MethodAnalysis extends Analysis {

    @NotNull
    MethodInfo getMethodInfo();

    /**
     * @return null when not (yet) decided
     */
    Expression getSingleReturnValue();

    default Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider, boolean complainIfNotAnalyzed) {
        return Set.of();
    }

    /**
     * @return null when the method is not defined (has no statements)
     */
    default StatementAnalysis getFirstStatement() {
        return null;
    }

    @NotNull(content = true)
    List<ParameterAnalysis> getParameterAnalyses();

    /**
     * @return null when the method is not defined (has no statements)
     */
    default StatementAnalysis getLastStatement() {
        return getLastStatement(false);
    }

    default StatementAnalysis getLastStatement(boolean excludeThrows) {
        throw new UnsupportedOperationException();
    }

    // the value here (size will be one)
    Precondition getPreconditionForEventual();

    /**
     * @return never null; can be delayed
     */
    default Eventual getEventual() {
        throw new UnsupportedOperationException();
    }

    // ************* object flow

    default Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
        return null;
    }

    default Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
        return null;
    }

    // ************** PRECONDITION

    /**
     * @return delayed See org.e2immu.analyser.analysis.StateData#setPrecondition(org.e2immu.analyser.analyser.Precondition, boolean)
     * for a description of the conventions.
     */
    @NotNull
    Precondition getPrecondition();

    /**
     * @return post-conditions, in no particular order.
     */
    @NotNull
    Set<PostCondition> getPostConditions();

    /*
    Many throw and assert statements find their way into a pre- or post-condition.
    Some, however, do not. We register them here.
     */
    @NotNull
    Set<String> indicesOfEscapesNotInPreOrPostConditions();

    CommutableData getCommutableData();

    default MethodLevelData methodLevelData() {
        StatementAnalysis last = getLastStatement();
        if (last == null) return null; // there is no last statement --> there are no statements
        return last.methodLevelData();
    }

    default DV getMethodProperty(Property property) {
        return switch (property) {
            case MODIFIED_METHOD_ALT_TEMP -> modifiedMethodOrTempModifiedMethod();
            case CONTAINER, IMMUTABLE, IMMUTABLE_BREAK, NOT_NULL_EXPRESSION, TEMP_MODIFIED_METHOD, MODIFIED_METHOD,
                    FLUENT, IDENTITY, IGNORE_MODIFICATIONS, INDEPENDENT, CONSTANT, STATIC_SIDE_EFFECTS ->
                    getPropertyFromMapDelayWhenAbsent(property);
            case FINALIZER -> getPropertyFromMapNeverDelay(property);
            default -> throw new PropertyException(Analyser.AnalyserIdentification.METHOD, property);
        };
    }

    // NOTE: this should be private, but JavaParser 3.25.9 complains
    default DV modifiedMethodOrTempModifiedMethod() {
        if (getMethodInfo().methodResolution.get().partOfCallCycle()) {
            return getPropertyFromMapDelayWhenAbsent(Property.TEMP_MODIFIED_METHOD);
        }
        return getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_METHOD);
    }

    /*
     not a default implementation, because we want no dependency on DelayFactory; hence, 2 implementations and a static
     */
    DV valueFromOverrides(AnalysisProvider analysisProvider, Property property);

    default boolean eventualIsSet() {
        return true;
    }

    Eventual NOT_EVENTUAL = new Eventual(CausesOfDelay.EMPTY, Set.of(), false, null, null);

    CausesOfDelay eventualStatus();

    CausesOfDelay preconditionStatus();

    static Eventual delayedEventual(CausesOfDelay causes) {
        return new Eventual(causes, Set.of(), false, null, null);
    }

    // associated with the @Commutable annotation
    ParSeq<ParameterInfo> getParallelGroups();

    default boolean hasParallelGroups() {
        ParSeq<ParameterInfo> parSeq = getParallelGroups();
        return parSeq != null && parSeq.containsParallels();
    }

    <X extends Comparable<? super X>> List<X> sortAccordingToParallelGroupsAndNaturalOrder(List<X> parameterExpressions);

    default String postConditionsSortedToString() {
        return getPostConditions().stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    default int pad() {
        return getFirstStatement().index().length();
    }

    record Eventual(CausesOfDelay causesOfDelay,
                    Set<FieldInfo> fields,
                    boolean mark,
                    Boolean after, // null for a @Mark without @Only
                    Boolean test) { // true for isSet (before==false, after==true), false for !isSet (before==true, after==false), null for absent

        public Eventual {
            assert !fields.isEmpty() || !mark && after == null && test == null;
        }

        public Eventual(Set<FieldInfo> fields, boolean mark, Boolean after, Boolean test) {
            this(CausesOfDelay.EMPTY, fields, mark, after, test);
        }

        @Override
        public String toString() {
            if (causesOfDelay.isDelayed()) {
                return "[DelayedEventual:" + causesOfDelay + "]";
            }
            if (mark) return "@Mark: " + fields;
            if (test != null) return "@TestMark: " + (test ? "" : "!") + fields;
            if (after == null) {
                if (this == NOT_EVENTUAL) return "NOT_EVENTUAL";
                throw new UnsupportedOperationException();
            }
            return "@Only " + (after ? "after" : "before") + ": " + fields;
        }

        public String markLabel() {
            return fields.stream().map(f -> f.name).sorted().collect(Collectors.joining(","));
        }

        public boolean consistentWith(Eventual other) {
            return fields.equals(other.fields);
        }

        public boolean isOnly() {
            assert causesOfDelay.isDone();
            return !mark && test == null;
        }

        public boolean isMark() {
            assert causesOfDelay.isDone();
            return mark;
        }

        public boolean isTestMark() {
            assert causesOfDelay.isDone();
            return test != null;
        }
    }

    default List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo) {
        StatementAnalysis lastStatement = getLastStatement();
        return lastStatement == null ? List.of() : getLastStatement().latestInfoOfVariablesReferringTo(fieldInfo);
    }

    default List<VariableInfo> getFieldAsVariableAssigned(FieldInfo fieldInfo) {
        List<VariableInfo> result = new ArrayList<>();
        for (VariableInfo vi : getFieldAsVariable(fieldInfo)) {
            if (vi.isAssigned()) {
                VariableInfo accept;
                if (vi.isDelayed()
                    && vi.getValue().causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY,
                        c -> c.variableIsField(fieldInfo) && getMethodInfo().equals(c.location().getInfo().getMethodInfo()))) {
                    String latestAssignment = vi.getAssignmentIds().getLatestAssignment();
                    StatementAnalysis saOfAssignment = getFirstStatement().navigateTo(StringUtil.stripStage(latestAssignment));
                    VariableInfoContainer vic = saOfAssignment.findOrNull(vi.variable());
                    Stage stage = Stage.from(StringUtil.stage(latestAssignment));
                    VariableInfo viOfAssignment = vic.best(stage);
                    if (viOfAssignment.valueIsSet()) {
                        accept = viOfAssignment;
                    } else {
                        accept = vi; // stay where we are
                    }
                } else {
                    accept = vi;
                }
                // see External_15: we ignore self-assignments (copy.s = this.s) because (a) they do not contribute
                // and (b) they cause delay loops. IMPROVE: more complicated, silly constructs like
                // copy.t = this.s; copy.s = this.t;  will not be caught by this.
                IsVariableExpression ive;
                if ((ive = accept.getValue().asInstanceOf(IsVariableExpression.class)) == null
                    || !(ive.variable() instanceof FieldReference fr)
                    || !fieldInfo.equals(fr.fieldInfo())
                    // accept this.f = <f:f>, as <f:f> is the initial value
                    || fr.scope().equals(((FieldReference) accept.variable()).scope())) {
                    result.add(accept);
                }
            }
        }
        return result;
    }

    void markFirstIteration();

    boolean hasBeenAnalysedUpToIteration0();

    FieldInfo getSetField();

    record GetSetEquivalent(MethodInfo methodInfo, Set<ParameterInfo> convertToGetSet) {
    }

    GetSetEquivalent getSetEquivalent();

    /*
    the hidden content selector of the return value
     */
    HiddenContentSelector getHiddenContentSelector();
}
