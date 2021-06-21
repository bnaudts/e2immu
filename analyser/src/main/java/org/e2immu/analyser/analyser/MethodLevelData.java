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

import org.e2immu.analyser.analyser.util.DelayDebugCollector;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.log;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public class MethodLevelData implements DelayDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);

    public static final String MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY = "mergeCausesOfContextModificationDelay";
    public static final String ENSURE_THIS_PROPERTIES = "ensureThisProperties";
    public static final String LINKS_HAVE_BEEN_ESTABLISHED = "linksHaveBeenEstablished";
    public static final String COMBINE_PRECONDITION = "combinePrecondition";

    // part of modification status for dealing with circular methods
    private final SetOnce<Boolean> callsPotentiallyCircularMethod = new SetOnce<>();

    public Boolean getCallsPotentiallyCircularMethod() {
        return callsPotentiallyCircularMethod.getOrDefaultNull();
    }

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final EventuallyFinal<Precondition> combinedPrecondition = new EventuallyFinal<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    public final FlipSwitch linksHaveBeenEstablished = new FlipSwitch();

    private final EventuallyFinal<Set<WithInspectionAndAnalysis>> causesOfContextModificationDelay = new EventuallyFinal<>();
    private final DelayDebugger delayDebugCollector = new DelayDebugCollector();

    public void addCircularCall() {
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(true);
        }
    }

    public Set<Variable> combinedPreconditionIsDelayedSet() {
        if (combinedPrecondition.isFinal()) return null;
        Precondition cp = combinedPrecondition.get();
        if (cp == null) return Set.of();
        return combinedPrecondition.get().expression().variables().stream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean causesOfContextModificationDelayIsVariable() {
        return causesOfContextModificationDelay.isVariable();
    }

    public void causesOfContextModificationDelayAddVariable(Map<WithInspectionAndAnalysis, Boolean> map, boolean allowRemoval) {
        assert causesOfContextModificationDelay.isVariable();
        if (causesOfContextModificationDelay.get() == null) {
            causesOfContextModificationDelay.setVariable(new HashSet<>());
        }
        Set<WithInspectionAndAnalysis> set = causesOfContextModificationDelay.get();
        map.forEach((k, v) -> {
            if (v) set.add(k);
            else if (allowRemoval) set.remove(k);
        });
    }

    public void causesOfContextModificationDelaySetFinal() {
        causesOfContextModificationDelay.setFinal(Set.of());
    }

    public Set<WithInspectionAndAnalysis> getCausesOfContextModificationDelay() {
        return causesOfContextModificationDelay.get();
    }

    public boolean acceptLinksHaveBeenEstablished(Predicate<WithInspectionAndAnalysis> canBeIgnored) {
        if (linksHaveBeenEstablished.isSet()) return true;
        Set<WithInspectionAndAnalysis> causes = causesOfContextModificationDelay.get();
        if (causes != null && !causes.isEmpty() && causes.stream().allMatch(canBeIgnored)) {
            log(LINKED_VARIABLES, "Accepting a limited version of linksHaveBeenEstablished to break delay cycle");
            return true;
        }
        return false;
    }

    record SharedState(StatementAnalyserResult.Builder builder,
                       EvaluationContext evaluationContext,
                       StatementAnalysis statementAnalysis,
                       String logLocation,
                       MethodLevelData previous,
                       String previousIndex,
                       StateData stateData) {
        String where(String component) {
            return statementAnalysis.methodAnalysis.getMethodInfo().fullyQualifiedName
                    + ":" + statementAnalysis.index + ":MLD:" + component;
        }

        String myStatement(String index) {
            return statementAnalysis.methodAnalysis.getMethodInfo().fullyQualifiedName + ":" + index;
        }

        String myStatement() {
            return statementAnalysis.methodAnalysis.getMethodInfo().fullyQualifiedName + ":" +
                    statementAnalysis.index;
        }
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add(MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY, this::mergeCausesOfContextModificationDelay)
            .add(ENSURE_THIS_PROPERTIES, sharedState -> ensureThisProperties())
            .add(LINKS_HAVE_BEEN_ESTABLISHED, this::linksHaveBeenEstablished)
            .add(COMBINE_PRECONDITION, this::combinePrecondition)
            .build();

    private AnalysisStatus mergeCausesOfContextModificationDelay(SharedState sharedState) {
        if (causesOfContextModificationDelay.isFinal()) return DONE;
        if (sharedState.previous != null && sharedState.previous.causesOfContextModificationDelay.isVariable()) {
            if (causesOfContextModificationDelay.get() == null) {
                causesOfContextModificationDelay.setVariable(new HashSet<>());
            }
            boolean added = causesOfContextModificationDelay.get()
                    .addAll(sharedState.previous.causesOfContextModificationDelay.get());
            assert !added || sharedState.previous.causesOfContextModificationDelay.get().stream().allMatch(cause ->
                    foundDelay(sharedState.where(MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY),
                            cause.fullyQualifiedName() + D_CAUSES_OF_CONTENT_MODIFICATION_DELAY));
        }
        sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .filter(sa -> sa.methodLevelData.causesOfContextModificationDelay.get() != null)
                .flatMap(sa -> sa.methodLevelData.causesOfContextModificationDelay.get().stream())
                .forEach(set -> {
                    if (set != null) {
                        boolean added = causesOfContextModificationDelay.get().add(set);
                        assert !added || foundDelay(sharedState.where(MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY),
                                set.fullyQualifiedName() + D_CAUSES_OF_CONTENT_MODIFICATION_DELAY);
                    }
                });
        if (causesOfContextModificationDelay.get() == null || causesOfContextModificationDelay.get().isEmpty()) {
            causesOfContextModificationDelaySetFinal();
            return DONE;
        }
        log(DELAYED, "Still have causes of context modification delay: {}",
                causesOfContextModificationDelay.get());
        return DELAYS;
    }

    public AnalysisStatus analyse(StatementAnalyser.SharedState sharedState,
                                  StatementAnalysis statementAnalysis,
                                  MethodLevelData previous,
                                  String previousIndex,
                                  StateData stateData) {
        EvaluationContext evaluationContext = sharedState.evaluationContext();
        String logLocation = statementAnalysis.location().toString();
        try {
            StatementAnalyserResult.Builder builder = sharedState.builder();
            SharedState localSharedState = new SharedState(builder, evaluationContext, statementAnalysis,
                    logLocation, previous, previousIndex, stateData);
            return analyserComponents.run(localSharedState);
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, {}", logLocation);
            throw rte;
        }
    }


    // preconditions come from the precondition expression in stateData
    // they are accumulated from the previous statement, and from all child statements
    private AnalysisStatus combinePrecondition(SharedState sharedState) {
        boolean previousDelayed = sharedState.previous != null && sharedState.previous.combinedPrecondition.isVariable();
        assert !previousDelayed || foundDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement(sharedState.previousIndex) + D_COMBINED_PRECONDITION);

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Optional<StatementAnalysis> subBlockDelay = subBlocks.stream()
                .filter(sa -> sa.methodLevelData.combinedPrecondition.isVariable()).findFirst();
        assert subBlockDelay.isEmpty() || foundDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement(subBlockDelay.get().index) + D_COMBINED_PRECONDITION);

        boolean preconditionFinal = sharedState.stateData.preconditionIsFinal();
        assert preconditionFinal || translatedDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement() + D_PRECONDITION,
                sharedState.myStatement() + D_COMBINED_PRECONDITION);

        Stream<Precondition> fromMyStateData =
                Stream.of(sharedState.stateData.getPrecondition());
        Stream<Precondition> fromPrevious = sharedState.previous != null ?
                Stream.of(sharedState.previous.combinedPrecondition.get()) : Stream.of();
        Stream<Precondition> fromBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .map(sa -> sa.methodLevelData.combinedPrecondition)
                .map(EventuallyFinal::get);
        Precondition all = Stream.concat(fromMyStateData, Stream.concat(fromBlocks, fromPrevious))
                .map(pc -> pc == null ? Precondition.empty(sharedState.evaluationContext.getPrimitives()) : pc)
                .reduce((pc1, pc2) -> pc1.combine(sharedState.evaluationContext, pc2))
                .orElse(Precondition.empty(sharedState.evaluationContext.getPrimitives()));

        boolean allDelayed = sharedState.evaluationContext.isDelayed(all.expression());

        // I wonder whether it is possible that the combination is delayed when none of the constituents are?
        assert !allDelayed || createDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement() + D_COMBINED_PRECONDITION);

        boolean delay = previousDelayed || subBlockDelay.isPresent() || !preconditionFinal || allDelayed;
        if (delay) {
            combinedPrecondition.setVariable(all);
            return DELAYS;
        }

        setFinalAllowEquals(combinedPrecondition, all);
        return DONE;
    }

    private AnalysisStatus linksHaveBeenEstablished(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        Optional<VariableInfo> delayed = sharedState.statementAnalysis.variableStream()
                .filter(vi -> !(vi.variable() instanceof This))
                // local variables that have been created, but not yet assigned/read; reject ConditionalInitialization
                .filter(vi -> !(vi.variable() instanceof LocalVariableReference)
                        || (vi.isAssigned() || vi.isRead()) && vi.isNotConditionalInitialization())
                .filter(vi -> !vi.linkedVariablesIsSet()
                        || vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.DELAY)
                .findFirst();
        if (delayed.isPresent()) {
            VariableInfo vi = delayed.get();

            assert vi.linkedVariablesIsSet() ||
                    translatedDelay(sharedState.where(LINKS_HAVE_BEEN_ESTABLISHED),
                            vi.variable().fullyQualifiedName() + "@" + sharedState.statementAnalysis.index + D_LINKED_VARIABLES_SET,
                            sharedState.myStatement() + D_LINKS_HAVE_BEEN_ESTABLISHED);
            assert vi.getProperty(VariableProperty.CONTEXT_MODIFIED) != Level.DELAY ||
                    translatedDelay(sharedState.where(LINKS_HAVE_BEEN_ESTABLISHED),
                            vi.variable().fullyQualifiedName() + "@" + sharedState.statementAnalysis.index + D_CONTEXT_MODIFIED,
                            sharedState.myStatement() + D_LINKS_HAVE_BEEN_ESTABLISHED);

            log(DELAYED, "Links have not yet been established for (findFirst) {}, statement {}",
                    delayed.get().variable().fullyQualifiedName(), sharedState.statementAnalysis.index);
            return DELAYS;
        }
        linksHaveBeenEstablished.set();
        return DONE;
    }

    /**
     * Finish odds and ends
     *
     * @return if any change happened to methodAnalysis
     */
    private AnalysisStatus ensureThisProperties() {
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(false);
        }
        return DONE;
    }

    @Override
    public boolean foundDelay(String where, String delayFqn) {
        return delayDebugCollector.foundDelay(where, delayFqn);
    }

    @Override
    public boolean translatedDelay(String where, String delayFromFqn, String newDelayFqn) {
        return delayDebugCollector.translatedDelay(where, delayFromFqn, newDelayFqn);
    }

    @Override
    public boolean createDelay(String where, String delayFqn) {
        return delayDebugCollector.createDelay(where, delayFqn);
    }

    @Override
    public Stream<DelayDebugNode> streamNodes() {
        return delayDebugCollector.streamNodes();
    }
}
