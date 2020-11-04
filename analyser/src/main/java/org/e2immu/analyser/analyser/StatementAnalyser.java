/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.config.EvaluationResultVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.StatementAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.annotation.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser implements HasNavigationData<StatementAnalyser> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatementAnalyser.class);
    public static final String ANALYSE_METHOD_LEVEL_DATA = "analyseMethodLevelData";
    public static final String STEP_2 = "step2"; // for(X x: xs)
    public static final String STEP_3 = "step3"; // int i=3;
    public static final String STEP_4 = "step4"; // main evaluation
    public static final String STEP_9 = "step9"; // recursive

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final AnalyserContext analyserContext;
    public final NavigationData<StatementAnalyser> navigationData = new NavigationData<>();

    // shared state over the different analysers
    private ConditionManager localConditionManager;
    private AnalysisStatus analysisStatus;
    private AnalyserComponents<String, SharedState> analyserComponents;
    // if true, we should ignore errors on the condition in the next iterations
    // if(x == null) throw ... causes x to become @NotNull; in the next iteration, x==null cannot happen,
    // which would cause an error; it is this error that is eliminated
    private boolean ignoreErrorsOnCondition;

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index,
                              boolean inSyncBlock) {
        this.analyserContext = Objects.requireNonNull(analyserContext);
        this.myMethodAnalyser = Objects.requireNonNull(methodAnalyser);
        this.statementAnalysis = new StatementAnalysis(analyserContext.getPrimitives(),
                methodAnalyser.methodAnalysis, statement, parent, index, inSyncBlock);
    }

    public static StatementAnalyser recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd,
            boolean inSyncBlock) {
        Objects.requireNonNull(myMethodAnalyser);
        Objects.requireNonNull(myMethodAnalyser.methodAnalysis);

        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
        }
        StatementAnalyser first = null;
        StatementAnalyser previous = null;
        for (Statement statement : statements) {
            String iPlusSt = indices.isEmpty() ? "" + statementIndex : indices + "." + statementIndex;
            StatementAnalyser statementAnalyser = new StatementAnalyser(analyserContext, myMethodAnalyser, statement, parent, iPlusSt, inSyncBlock);
            if (previous != null) {
                previous.statementAnalysis.navigationData.next.set(Optional.of(statementAnalyser.statementAnalysis));
                previous.navigationData.next.set(Optional.of(statementAnalyser));
            }
            previous = statementAnalyser;
            if (first == null) first = statementAnalyser;

            int blockIndex = 0;
            List<StatementAnalyser> blocks = new ArrayList<>();
            List<StatementAnalysis> analysisBlocks = new ArrayList<>();

            Structure structure = statement.getStructure();
            boolean newInSyncBlock = inSyncBlock || statement instanceof SynchronizedStatement;

            if (structure.haveStatements()) {
                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                        statementAnalyser.statementAnalysis, structure.getStatements(),
                        iPlusSt + "." + blockIndex, true, newInSyncBlock);
                blocks.add(subStatementAnalyser);
                analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser,
                            statementAnalyser.statementAnalysis, structure.getStatements(),
                            iPlusSt + "." + blockIndex, true, newInSyncBlock);
                    blocks.add(subStatementAnalyser);
                    analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                    blockIndex++;
                }
            }
            statementAnalyser.statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
            statementAnalyser.navigationData.blocks.set(ImmutableList.copyOf(blocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.statementAnalysis.navigationData.next.set(Optional.empty());
            previous.navigationData.next.set(Optional.empty());
        }
        return first;

    }

    record PreviousAndFirst(StatementAnalyser previous, StatementAnalyser first) {
    }

    /**
     * Main recursive method, which follows the navigation chain for all statements in a block. When called from the method analyser,
     * it loops over the statements of the method.
     *
     * @param iteration           the current iteration
     * @param forwardAnalysisInfo information from the level above
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    public StatementAnalyserResult analyseAllStatementsInBlock(int iteration, ForwardAnalysisInfo forwardAnalysisInfo) {
        try {
            // skip all the statements that are already in the DONE state...
            PreviousAndFirst previousAndFirst = goToFirstStatementToAnalyse();
            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();

            if (previousAndFirst.first == null) {
                // nothing left to analyse
                return builder.setAnalysisStatus(DONE).build();
            }

            StatementAnalyser previousStatement = previousAndFirst.previous;
            StatementAnalyser statementAnalyser = previousAndFirst.first;
            do {
                boolean wasReplacement;
                if (analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
                    wasReplacement = false;
                } else {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, forwardAnalysisInfo.conditionManager());
                    // first attempt at detecting a transformation
                    wasReplacement = checkForPatterns(evaluationContext);
                    statementAnalyser = statementAnalyser.followReplacements();
                }
                StatementAnalysis previousStatementAnalysis = previousStatement == null ? null : previousStatement.statementAnalysis;
                StatementAnalyserResult result = statementAnalyser.analyseSingleStatement(iteration,
                        wasReplacement, previousStatementAnalysis, forwardAnalysisInfo);
                builder.add(result);
                previousStatement = statementAnalyser;

                statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            } while (statementAnalyser != null);
            return builder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception while analysing block {} of: {}", index(), myMethodAnalyser.methodInfo.fullyQualifiedName());
            throw rte;
        }
    }

    @Override
    public NavigationData<StatementAnalyser> getNavigationData() {
        return navigationData;
    }

    @Override
    public StatementAnalyser followReplacements() {
        if (navigationData.replacement.isSet()) {
            return navigationData.replacement.get().followReplacements();
        }
        return this;
    }

    @Override
    public StatementAnalyser lastStatement() {
        return followReplacements().navigationData.next.get().map(sa -> {
            // TODO add a check for unreachable statements, returning "this"
            StatementAnalyser last = sa.lastStatement();
            return last;
        }).orElse(this);
    }

    @Override
    public String index() {
        return statementAnalysis.index;
    }

    @Override
    public Statement statement() {
        return statementAnalysis.statement;
    }

    @Override
    public StatementAnalysis parent() {
        return statementAnalysis.parent;
    }

    @Override
    public void wireNext(StatementAnalyser newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
        statementAnalysis.navigationData.next.set(newStatement == null ? Optional.empty() : Optional.of(newStatement.statementAnalysis));
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalyser> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(evaluationContext.getAnalyserContext(),
                evaluationContext.getCurrentMethod(),
                parent(), statements, startIndex, false, statementAnalysis.inSyncBlock);
    }

    private PreviousAndFirst goToFirstStatementToAnalyse() {
        StatementAnalyser statementAnalyser = followReplacements();
        StatementAnalyser previous = null;
        while (statementAnalyser != null && statementAnalyser.analysisStatus == DONE) {
            previous = statementAnalyser;
            statementAnalyser = statementAnalyser.navigationData.next.get().orElse(null);
            if (statementAnalyser != null) {
                statementAnalyser = statementAnalyser.followReplacements();
            }
        }
        return new PreviousAndFirst(previous, statementAnalyser);
    }

    private boolean checkForPatterns(EvaluationContext evaluationContext) {
        PatternMatcher<StatementAnalyser> patternMatcher = analyserContext.getPatternMatcher();
        if (!analyserContext.getConfiguration().analyserConfiguration.skipTransformations) {
            MethodInfo methodInfo = myMethodAnalyser.methodInfo;
            return patternMatcher.matchAndReplace(methodInfo, this, evaluationContext);
        }
        return false;
    }

    private Location getLocation() {
        return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
    }

    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    record SharedState(EvaluationContext evaluationContext, StatementAnalyserResult.Builder builder) {
    }

    /**
     * @param iteration      the iteration
     * @param wasReplacement boolean, to ensure that the effect of a replacement warrants continued analysis
     * @param previous       null if there was no previous statement in this block
     * @return the combination of a list of all modifications to be done to parameters, methods, and an AnalysisStatus object.
     * Once the AnalysisStatus reaches DONE, this particular block is not analysed again.
     */
    private StatementAnalyserResult analyseSingleStatement(int iteration,
                                                           boolean wasReplacement,
                                                           StatementAnalysis previous,
                                                           ForwardAnalysisInfo forwardAnalysisInfo) {
        if (analysisStatus == null) {
            assert localConditionManager == null : "expected null localConditionManager";

            statementAnalysis.initialise(previous);

            analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                    .add("checkUnreachableStatement", sharedState -> checkUnreachableStatement(previous,
                            forwardAnalysisInfo.execution()))

                    .add("singleStatementAnalysisSteps", sharedState -> singleStatementAnalysisSteps(sharedState, previous, forwardAnalysisInfo))
                    .add("tryToFreezeDependencyGraph", sharedState -> tryToFreezeDependencyGraph())
                    .add("analyseFlowData", sharedState -> statementAnalysis.flowData.analyse(this, previous,
                            forwardAnalysisInfo.execution()))

                    .add("checkFluent", this::checkFluent)
                    .add("checkNotNullEscapes", this::checkNotNullEscapes)
                    .add("checkSizeEscapes", this::checkSizeEscapes)
                    .add("checkPrecondition", this::checkPrecondition)
                    .add("copyPrecondition", sharedState -> statementAnalysis.stateData.copyPrecondition(this,
                            previous, sharedState.evaluationContext))
                    .add(ANALYSE_METHOD_LEVEL_DATA, sharedState -> statementAnalysis.methodLevelData.analyse(sharedState, statementAnalysis,
                            previous == null ? null : previous.methodLevelData,
                            statementAnalysis.stateData))

                    .add("checkUnusedReturnValue", sharedState -> checkUnusedReturnValue())
                    .add("checkUselessAssignments", sharedState -> checkUselessAssignments())
                    .add("checkUnusedLocalVariables", sharedState -> checkUnusedLocalVariables())
                    .build();
        } else {
            statementAnalysis.updateStatements(analyserContext, myMethodAnalyser.methodInfo, previous);
        }
        boolean startOfNewBlock = previous == null;
        localConditionManager = startOfNewBlock ? forwardAnalysisInfo.conditionManager() : previous.stateData.getConditionManager();

        StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, localConditionManager);
        SharedState sharedState = new SharedState(evaluationContext, builder);
        AnalysisStatus overallStatus = analyserComponents.run(sharedState);

        StatementAnalyserResult result = sharedState.builder()
                .addMessages(statementAnalysis.messages.stream())
                .setAnalysisStatus(overallStatus)
                .combineAnalysisStatus(wasReplacement ? PROGRESS : DONE).build();
        analysisStatus = result.analysisStatus;

        visitStatementVisitors(statementAnalysis.index, result, sharedState);

        log(ANALYSER, "Returning from statement {} of {} with analysis status {}", statementAnalysis.index,
                myMethodAnalyser.methodInfo.name, analysisStatus);
        return result;
    }

    private AnalysisStatus tryToFreezeDependencyGraph() {
        if (statementAnalysis.dependencyGraph.isFrozen()) return DONE;
        AtomicBoolean haveDelays = new AtomicBoolean();
        statementAnalysis.dependencyGraph.visit((v, list) -> {
            statementAnalysis.assertVariableExists(v);

            VariableInfo variableInfo = statementAnalysis.find(analyserContext, v);
            if (variableInfo != null && variableInfo.hasNoValue()) haveDelays.set(true);
        });
        if (haveDelays.get()) return DELAYS;
        statementAnalysis.dependencyGraph.freeze();
        return DONE;
    }

    private void visitStatementVisitors(String statementId, StatementAnalyserResult result, SharedState sharedState) {
        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVariableVisitors) {
            statementAnalysis.variables.stream()
                    .map(Map.Entry::getValue)
                    .forEach(variableInfoContainer -> statementAnalyserVariableVisitor.visit(
                            new StatementAnalyserVariableVisitor.Data(
                                    sharedState.evaluationContext.getIteration(),
                                    sharedState.evaluationContext,
                                    myMethodAnalyser.methodInfo,
                                    statementId,
                                    variableInfoContainer.current().name,
                                    variableInfoContainer.current().variable,
                                    variableInfoContainer.current().getValue(),
                                    variableInfoContainer.current().properties,
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor :
                analyserContext.getConfiguration().debugConfiguration.statementAnalyserVisitors) {
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            result,
                            sharedState.evaluationContext.getIteration(),
                            sharedState.evaluationContext,
                            myMethodAnalyser.methodInfo,
                            statementAnalysis,
                            statementAnalysis.index,
                            statementAnalysis.stateData.getConditionManager().condition,
                            statementAnalysis.stateData.getConditionManager().state,
                            analyserComponents.getStatusesAsMap(),
                            ignoreErrorsOnCondition));
        }
    }


    // executed only once per statement; we're assuming that the flowData are computed correctly
    private AnalysisStatus checkUnreachableStatement(StatementAnalysis previousStatementAnalysis,
                                                     FlowData.Execution execution) {
        if (statementAnalysis.flowData.computeGuaranteedToBeReachedReturnUnreachable(statementAnalysis.primitives,
                previousStatementAnalysis, execution, localConditionManager.state) &&
                !statementAnalysis.inErrorState(Message.UNREACHABLE_STATEMENT)) {
            statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNREACHABLE_STATEMENT));
        }
        return DONE;
    }

    private boolean isEscapeAlwaysExecutedInCurrentBlock() {
        InterruptsFlow bestAlways = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean escapes = bestAlways == InterruptsFlow.ESCAPE;
        if (escapes) {
            return statementAnalysis.flowData.guaranteedToBeReachedInCurrentBlock.get() == FlowData.Execution.ALWAYS;
        }
        return false;
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
     */
    private AnalysisStatus apply(EvaluationResult evaluationResult, StatementAnalysis statementAnalysis, int level, String step) {
        // state changes get composed into one big operation, applied, and the result is set
        // the condition is copied from the evaluation context
        Function<Value, Value> composite = evaluationResult.getStateChangeStream()
                .reduce(v -> v, (f1, f2) -> v -> f2.apply(f1.apply(v)));
        Value reducedState = composite.apply(localConditionManager.state);
        if (reducedState != localConditionManager.state) {
            localConditionManager = new ConditionManager(localConditionManager.condition, reducedState);
        }

        // all modifications get applied
        evaluationResult.getModificationStream().forEach(mod -> mod.accept(level));

        evaluationResult.getValueChangeStream().forEach(e -> {
            Variable variable = e.getKey();
            Value value = e.getValue();
            VariableInfo variableInfo = statementAnalysis.findForWriting(analyserContext, variable, level);
            if (!variableInfo.value.isSet() && value != NO_VALUE) {
                log(ANALYSER, "Write value {} to variable {}", value, variable.fullyQualifiedName());
                variableInfo.value.set(value);
            }
            if (!variableInfo.stateOnAssignment.isSet() && reducedState != NO_VALUE) {
                variableInfo.stateOnAssignment.set(reducedState);
            }
        });

        AnalysisStatus status = evaluationResult.value == NO_VALUE ? DELAYS : DONE;

        if (status == DONE && !statementAnalysis.methodLevelData.internalObjectFlows.isFrozen()) {
            boolean delays = false;
            for (ObjectFlow objectFlow : evaluationResult.getObjectFlowStream().collect(Collectors.toSet())) {
                if (objectFlow.isDelayed()) {
                    delays = true;
                } else if (!statementAnalysis.methodLevelData.internalObjectFlows.contains(objectFlow)) {
                    statementAnalysis.methodLevelData.internalObjectFlows.add(objectFlow);
                }
            }
            if (delays) {
                status = DELAYS;
            } else {
                statementAnalysis.methodLevelData.internalObjectFlows.freeze();
            }
        }

        // debugging...
        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration().debugConfiguration.evaluationResultVisitors) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.iteration, step,
                    myMethodAnalyser.methodInfo, statementAnalysis.index, evaluationResult));
        }

        return status;
        // IMPROVE check that AddOnceSet is the right data structure
    }


    // whatever that has not been picked up by the notNull and the size escapes
    // + preconditions by calling other methods with preconditions!

    private AnalysisStatus checkPrecondition(SharedState sharedState) {
        if (isEscapeAlwaysExecutedInCurrentBlock() && !statementAnalysis.stateData.precondition.isSet()) {
            EvaluationResult er = statementAnalysis.stateData.getConditionManager().escapeCondition(sharedState.evaluationContext);
            Value precondition = er.value;
            if (precondition != UnknownValue.EMPTY) {
                boolean atLeastFieldOrParameterInvolved = precondition.variables().stream()
                        .anyMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference);
                if (atLeastFieldOrParameterInvolved) {
                    log(VARIABLE_PROPERTIES, "Escape with precondition {}", precondition);

                    statementAnalysis.stateData.precondition.set(precondition);

                    if (!ignoreErrorsOnCondition) {
                        log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                        ignoreErrorsOnCondition = true;
                    }
                }
            }
        }
        return DONE;
    }

    private AnalysisStatus checkNotNullEscapes(SharedState sharedState) {
        if (isEscapeAlwaysExecutedInCurrentBlock()) {
            Set<Variable> nullVariables = statementAnalysis.stateData.getConditionManager()
                    .findIndividualNullInCondition(sharedState.evaluationContext, true);
            for (Variable nullVariable : nullVariables) {
                log(VARIABLE_PROPERTIES, "Escape with check not null on {}", nullVariable.fullyQualifiedName());
                ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser((ParameterInfo) nullVariable).parameterAnalysis;
                sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL));

                // as a context property, at the highest level (AFTER summary, but we're simply increasing)
                statementAnalysis.addProperty(analyserContext, VariableInfoContainer.LEVEL_4_SUMMARY,
                        nullVariable, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                if (!ignoreErrorsOnCondition) {
                    log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                    ignoreErrorsOnCondition = true;
                }
            }
        }
        return DONE;
    }

    private AnalysisStatus checkSizeEscapes(SharedState sharedState) {
        if (isEscapeAlwaysExecutedInCurrentBlock()) {
            Map<Variable, Value> individualSizeRestrictions = statementAnalysis.stateData
                    .getConditionManager().findIndividualSizeRestrictionsInCondition(sharedState.evaluationContext);
            for (Map.Entry<Variable, Value> entry : individualSizeRestrictions.entrySet()) {
                ParameterInfo parameterInfo = (ParameterInfo) entry.getKey();
                Value negated = NegatedValue.negate(sharedState.evaluationContext, entry.getValue());
                log(VARIABLE_PROPERTIES, "Escape with check on size on {}: {}", parameterInfo.fullyQualifiedName(), negated);
                int sizeRestriction = negated.encodedSizeRestriction(sharedState.evaluationContext);
                if (sizeRestriction > 0) { // if the complement is a meaningful restriction
                    ParameterAnalysisImpl.Builder parameterAnalysis = myMethodAnalyser.getParameterAnalyser(parameterInfo).parameterAnalysis;
                    sharedState.builder.add(parameterAnalysis.new SetProperty(VariableProperty.SIZE, sizeRestriction));

                    statementAnalysis.addProperty(analyserContext, VariableInfoContainer.LEVEL_4_SUMMARY,
                            parameterInfo, VariableProperty.SIZE, sizeRestriction);
                    if (!ignoreErrorsOnCondition) {
                        log(VARIABLE_PROPERTIES, "Disable errors on if-statement");
                        ignoreErrorsOnCondition = true;
                    }
                }
            }
        }
        return DONE;
    }

    private AnalysisStatus checkFluent(SharedState sharedState) {
        VariableInfo variableInfo = findReturnAsVariableForWriting();
        int fluent = variableInfo.getProperty(VariableProperty.FLUENT);
        if (fluent != Level.DELAY) return DONE;
        Value value = variableInfo.getValue();
        if (value == NO_VALUE) return DELAYS;
        boolean isFluent = value instanceof VariableValue vv && vv.variable instanceof This thisVar && thisVar.typeInfo == myMethodAnalyser.myTypeAnalyser.typeInfo;
        variableInfo.properties.put(VariableProperty.FLUENT, isFluent ? Level.TRUE : Level.FALSE);
        return DONE;
    }

    private VariableInfo findReturnAsVariableForWriting() {
        String fqn = myMethodAnalyser.methodInfo.fullyQualifiedName();
        return statementAnalysis.findForWriting(fqn, VariableInfoContainer.LEVEL_2_EVALUATION);
    }

    /**
     * Situations:     for(X x: xs) { ... } and catch(Exception e) { ... }
     * We create the local variable not at the level of the statement, but that of the first statement in the block
     * <p>
     * TODO raise an error when the block is empty
     */

    private LocalVariableReference step1_localVariableInForOrCatch(Structure structure,
                                                                   boolean inCatch,
                                                                   boolean assignedInLoop) {
        LocalVariableReference lvr;
        if (structure.localVariableCreation != null) {
            lvr = new LocalVariableReference(structure.localVariableCreation, List.of());
            StatementAnalysis firstStatementInBlock = firstStatementFirstBlock();
            final int l1 = VariableInfoContainer.LEVEL_1_INITIALISER;
            VariableInfo variableInfo;
            if (inCatch) {
                firstStatementInBlock.addProperty(analyserContext, l1, lvr, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
                variableInfo = firstStatementInBlock.addProperty(analyserContext, l1, lvr, VariableProperty.READ, Level.READ_ASSIGN_ONCE);
            } else if (assignedInLoop) {
                variableInfo = firstStatementInBlock.addProperty(analyserContext, l1, lvr, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
            } else {
                variableInfo = firstStatementInBlock.find(analyserContext, lvr); // "touch" it
            }
            variableInfo.writeValue(new VariableValue(lvr));
        } else {
            lvr = null;
        }
        return lvr;
    }

    /**
     * Situations: normal variable creation:
     * <ul>
     * <li>String s = "xxx";
     * <li>the first component of the classical for loop: for(int i=0; i<..., i++)
     * <li>the variables created inside the try-with-resources
     * </ul>
     * <p>
     * <p>
     * Note that steps 1 and 2 are mutually exclusive, both can use LEVEL_1_INITIALISER
     */
    private AnalysisStatus step2_localVariableCreation(EvaluationContext evaluationContext, Structure structure, boolean assignedInLoop) {
        boolean haveDelays = false;
        StatementAnalysis saToCreate = structure.createVariablesInsideBlock ? firstStatementFirstBlock() : statementAnalysis;
        for (Expression initialiser : structure.initialisers) {
            if (initialiser instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr = new LocalVariableReference((lvc).localVariable, List.of());
                // the NO_VALUE here becomes the initial (and reset) value, which should not be a problem because variables
                // introduced here should not become "reset" to an initial value; they'll always be assigned one
                if (assignedInLoop) {
                    saToCreate.addProperty(analyserContext, VariableInfoContainer.LEVEL_1_INITIALISER, lvr, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                } else {
                    saToCreate.find(analyserContext, lvr); // "touch" it
                }
            }
            try {
                EvaluationResult result = initialiser.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                AnalysisStatus status = apply(result, saToCreate, VariableInfoContainer.LEVEL_1_INITIALISER, STEP_2);
                if (status == DELAYS) haveDelays = true;

                Value value = result.value;
                if (value == NO_VALUE) haveDelays = true;
                // initialisers size 1 means expression as statement, local variable creation
                if (structure.initialisers.size() == 1 && value != null && value != NO_VALUE && !saToCreate.stateData.valueOfExpression.isSet()) {
                    saToCreate.stateData.valueOfExpression.set(value);
                }

                // IMPROVE move to a place where *every* assignment is verified (assignment-basics)
                // are we in a loop (somewhere) assigning to a variable that already exists outside that loop?
                if (initialiser instanceof Assignment assignment) {
                    int levelLoop = saToCreate.stepsUpToLoop();
                    if (levelLoop >= 0) {
                        int levelAssignmentTarget = saToCreate.levelAtWhichVariableIsDefined(analyserContext, assignment.variableTarget);
                        if (levelAssignmentTarget >= levelLoop) {
                            saToCreate.addProperty(analyserContext, VariableInfoContainer.LEVEL_1_INITIALISER,
                                    assignment.variableTarget, VariableProperty.ASSIGNED_IN_LOOP, Level.TRUE);
                        }
                    }
                }
            } catch (RuntimeException rte) {
                LOGGER.warn("Failed to evaluate initialiser expression in statement {} (created in {})",
                        statementAnalysis.index, saToCreate.index);
                throw rte;
            }
        }
        return haveDelays ? DELAYS : DONE;
    }

    private StatementAnalysis firstStatementFirstBlock() {
        return navigationData.blocks.get().get(0).statementAnalysis;
    }

    // for(int i=0 (step2); i<3 (step4); i++ (step3))
    // TODO check if we really still must do this in the beginning rather than the end?
    private AnalysisStatus step3_updaters(EvaluationContext evaluationContext, Structure structure) {
        boolean haveDelays = false;
        for (Expression updater : structure.updaters) {
            EvaluationResult result = updater.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            AnalysisStatus status = apply(result, statementAnalysis, VariableInfoContainer.LEVEL_3_UPDATER, STEP_3);
            if (status == DELAYS) haveDelays = true;
        }
        return haveDelays ? DELAYS : DONE;
    }

    private Value step4_evaluationOfMainExpression(EvaluationContext evaluationContext, Structure structure) {
        if (structure.expression == EmptyExpression.EMPTY_EXPRESSION) {
            return null;
        }
        try {
            EvaluationResult result = structure.expression.evaluate(evaluationContext, structure.forwardEvaluationInfo);
            AnalysisStatus status = apply(result, statementAnalysis, VariableInfoContainer.LEVEL_2_EVALUATION, STEP_4);
            if (status == DELAYS) return NO_VALUE;

            // the evaluation system should be pretty good at always returning NO_VALUE when a NO_VALUE has been encountered
            Value value = result.value;
            if (value != NO_VALUE && !statementAnalysis.stateData.valueOfExpression.isSet()) {
                statementAnalysis.stateData.valueOfExpression.set(value);
            }
            return value;
        } catch (RuntimeException rte) {
            LOGGER.warn("Failed to evaluate expression in statement {}", statementAnalysis.index);
            throw rte;
        }
    }

    private void step5_forEachNotNullCheck(EvaluationContext evaluationContext, Value value, LocalVariableReference localVariableReference) {
        if (statementAnalysis.statement instanceof ForEachStatement && value != null) {
            ArrayValue arrayValue;
            if (((arrayValue = value.asInstanceOf(ArrayValue.class)) != null) &&
                    arrayValue.values.stream().allMatch(evaluationContext::isNotNull0)) {
                StatementAnalysis firstStatementInBlock = firstStatementFirstBlock();
                firstStatementInBlock.addProperty(analyserContext, VariableInfoContainer.LEVEL_2_EVALUATION,
                        localVariableReference, VariableProperty.NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL);
            }
        }
    }

    private AnalysisStatus step6_return(Value value, EvaluationContext evaluationContext) {
        if (!(statementAnalysis.statement instanceof ReturnStatement) || myMethodAnalyser.methodInfo.isVoid()
                || myMethodAnalyser.methodInfo.isConstructor) {
            return DONE;
        }
        if (value == null) throw new UnsupportedOperationException("??");
        if (value == NO_VALUE) return DELAYS;

        VariableInfo variableInfo = findReturnAsVariableForWriting();
        variableInfo.value.set(value);
        // properties need properly copying from value into "properties"
        // TODO
        return DONE;
    }

    private boolean step7_detectErrorsIfElseSwitchFor(Value value, EvaluationContext evaluationContext) {
        if (statementAnalysis.statement instanceof IfElseStatement || statementAnalysis.statement instanceof SwitchStatement) {
            Objects.requireNonNull(value);

            Value previousConditional = localConditionManager.condition;
            Value combinedWithCondition = localConditionManager.evaluateWithCondition(evaluationContext, value);
            Value combinedWithState = localConditionManager.evaluateWithState(evaluationContext, value);

            // we have no idea which of the 2 remains
            boolean noEffect = combinedWithCondition != NO_VALUE && combinedWithState != NO_VALUE &&
                    (combinedWithCondition.equals(previousConditional) || combinedWithState.isConstant())
                    || combinedWithCondition.isConstant();

            if (noEffect && !ignoreErrorsOnCondition && !statementAnalysis.inErrorState(Message.CONDITION_EVALUATES_TO_CONSTANT)) {
                statementAnalysis.ensure(Message.newMessage(evaluationContext.getLocation(), Message.CONDITION_EVALUATES_TO_CONSTANT));
                ignoreErrorsOnCondition = true; // so that we don't repeat this error
            }
            return true;
        }

        if (value != null && statementAnalysis.statement instanceof ForEachStatement) {
            int size = evaluationContext.getProperty(value, VariableProperty.SIZE);
            if (size == Level.SIZE_EMPTY && !statementAnalysis.inErrorState(Message.EMPTY_LOOP)) {
                statementAnalysis.ensure(Message.newMessage(evaluationContext.getLocation(), Message.EMPTY_LOOP));
            }
        }
        return false;
    }

    private AnalysisStatus step8_primaryBlock(EvaluationContext evaluationContext,
                                              Value value,
                                              Structure structure,
                                              StatementAnalyser startOfFirstBlock,
                                              StatementAnalyserResult.Builder builder) {
        boolean statementsExecutedAtLeastOnce = structure.statementsExecutedAtLeastOnce.test(value, evaluationContext);
        FlowData.Execution execution = statementAnalysis.flowData.execution(statementsExecutedAtLeastOnce);
        ConditionManager newLocalConditionManager = structure.expressionIsCondition ? localConditionManager.addCondition(evaluationContext, value)
                : localConditionManager;
        StatementAnalyserResult recursiveResult = startOfFirstBlock.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                new ForwardAnalysisInfo(execution, newLocalConditionManager, false));
        builder.add(recursiveResult);
        return recursiveResult.analysisStatus;
    }

    private Value step9_evaluateSubExpression(EvaluationContext evaluationContext,
                                              Expression expression,
                                              ForwardEvaluationInfo forwardEvaluationInfo,
                                              List<Value> conditions) {

        Value valueForSubStatement;
        if (EmptyExpression.DEFAULT_EXPRESSION == expression) { // else, default:
            Primitives primitives = evaluationContext.getPrimitives();
            if (conditions.isEmpty()) {
                valueForSubStatement = BoolValue.createTrue(primitives);
            } else {
                Value[] negated = conditions.stream().map(c -> NegatedValue.negate(evaluationContext, c)).toArray(Value[]::new);
                valueForSubStatement = new AndValue(primitives, ObjectFlow.NO_FLOW).append(evaluationContext, negated);
            }
        } else if (EmptyExpression.FINALLY_EXPRESSION == expression || EmptyExpression.EMPTY_EXPRESSION == expression) {
            valueForSubStatement = null;
        } else {
            // real expression
            EvaluationResult result = expression.evaluate(evaluationContext, forwardEvaluationInfo);
            valueForSubStatement = result.value;
            // there cannot be any assignment in these sub-expressions, nor variables, ...
            AnalysisStatus status = apply(result, statementAnalysis, VariableInfoContainer.LEVEL_2_EVALUATION, STEP_9);
            if (status == DELAYS) {
                return NO_VALUE;
            }
        }
        return valueForSubStatement;
    }

    private AnalysisStatus step10_evaluateSubBlock(EvaluationContext evaluationContext,
                                                   StatementAnalyserResult.Builder builder,
                                                   Structure structure,
                                                   int count,
                                                   Value value,
                                                   Value valueForSubStatement) {
        boolean statementsExecutedAtLeastOnce = structure.statementsExecutedAtLeastOnce.test(value, evaluationContext);
        FlowData.Execution execution = statementAnalysis.flowData.execution(statementsExecutedAtLeastOnce);

        StatementAnalyser subStatementStart = navigationData.blocks.get().get(count);
        boolean inCatch = structure.localVariableCreation != null;
        StatementAnalyserResult result = subStatementStart.analyseAllStatementsInBlock(evaluationContext.getIteration(),
                new ForwardAnalysisInfo(execution, localConditionManager.addCondition(evaluationContext, valueForSubStatement), inCatch));
        builder.add(result);
        return result.analysisStatus;
    }

    private AnalysisStatus singleStatementAnalysisSteps(SharedState sharedState,
                                                        StatementAnalysis previous,
                                                        ForwardAnalysisInfo forwardAnalysisInfo) {
        Structure structure = statementAnalysis.statement.getStructure();
        EvaluationContext evaluationContext = sharedState.evaluationContext;

        // STEP 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e)
        boolean assignedInLoop = statementAnalysis.statement instanceof LoopStatement;
        LocalVariableReference theLocalVariableReference = step1_localVariableInForOrCatch(structure,
                forwardAnalysisInfo.inCatch(), assignedInLoop);

        // STEP 2: More local variables in try-resources, for-loop, expression as statement (normal local variables)
        AnalysisStatus analysisStatus = step2_localVariableCreation(sharedState.evaluationContext, structure, assignedInLoop);


        // STEP 3: updaters (only in the classic for statement, and the parameters of an explicit constructor invocation this(...)
        // for now, we put these BEFORE the main evaluation + the main block. One of the two should read the value that is being updated
        AnalysisStatus status3 = step3_updaters(evaluationContext, structure);
        analysisStatus = analysisStatus.combine(status3);


        // STEP 4: evaluation of the main expression of the statement (if the statement has such a thing)
        final Value value = step4_evaluationOfMainExpression(evaluationContext, structure);
        AnalysisStatus status4 = value == NO_VALUE ? DELAYS : DONE;
        analysisStatus = analysisStatus.combine(status4);

        // STEP 5: not-null check on forEach (see step 1)
        step5_forEachNotNullCheck(evaluationContext, value, theLocalVariableReference);


        // STEP 6: ReturnStatement, prepare parts of method level data
        AnalysisStatus status6 = step6_return(value, evaluationContext);
        analysisStatus = analysisStatus.combine(status6);

        // STEP 7: detect some errors for some statements
        boolean isSwitchOrIfElse = step7_detectErrorsIfElseSwitchFor(value, evaluationContext);

        // STEP 8: the primary block, if it's there
        // the primary block has an expression in case of "if", "while", and "synchronized"
        // in the first two cases, we'll treat the expression as a condition

        List<StatementAnalyser> startOfBlocks = navigationData.blocks.get();
        List<Value> conditions = new ArrayList<>();
        conditions.add(value);
        int start;

        StatementAnalyserResult.Builder builder = sharedState.builder;
        if (structure.haveNonEmptyBlock()) {
            StatementAnalyser startOfFirstBlock = startOfBlocks.get(0);
            AnalysisStatus status8 = step8_primaryBlock(evaluationContext, value, structure, startOfFirstBlock, builder);
            analysisStatus = analysisStatus.combine(status8);
            start = 1;
        } else {
            start = 0;
        }

        // PART 8: other conditions, including the else, switch entries, catch clauses

        for (int count = start; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements.get(count - start);

            // PART 9: evaluate the sub-expression; this can be done in the current evaluation context
            // (only real evaluation for Java 14 conditionals in switch)
            // valueForSubStatement will become the new condition

            Value valueForSubStatement = step9_evaluateSubExpression(evaluationContext, subStatements.expression,
                    subStatements.forwardEvaluationInfo, conditions);
            AnalysisStatus status9 = value == NO_VALUE ? DELAYS : DONE;
            analysisStatus = analysisStatus.combine(status9);
            if (valueForSubStatement != null && valueForSubStatement != NO_VALUE) {
                conditions.add(valueForSubStatement);
            } // else "finally", empty expression (is that possible?)

            // PART 10: create subContext, add parameters of sub statements, execute

            AnalysisStatus status10 = step10_evaluateSubBlock(evaluationContext, builder, subStatements, count, value,
                    valueForSubStatement);
            analysisStatus = analysisStatus.combine(status10);
        }

        List<StatementAnalyser> lastStatements = startOfBlocks.stream().map(StatementAnalyser::lastStatement).collect(Collectors.toList());
        statementAnalysis.copyBackLocalCopies(lastStatements, previous);
        if (!lastStatements.isEmpty()) {

            if (isSwitchOrIfElse) {
                // compute the escape situation of the sub-blocks
                List<Boolean> escapes = lastStatements.stream()
                        .map(sa -> sa.statementAnalysis.flowData.interruptStatus() == FlowData.Execution.ALWAYS)
                        .collect(Collectors.toList());
                Value addToStateAfterStatement = statementAnalysis.statement.addToStateAfterStatement(evaluationContext, conditions, escapes);
                if (addToStateAfterStatement != UnknownValue.EMPTY) {
                    localConditionManager = localConditionManager.addToState(evaluationContext, addToStateAfterStatement);
                    log(VARIABLE_PROPERTIES, "Continuing beyond default condition with conditional", addToStateAfterStatement);
                }
            }
        }

        // FINALLY, set the state

        if (localConditionManager.notInDelayedState() && !statementAnalysis.stateData.conditionManager.isSet()) {
            statementAnalysis.stateData.conditionManager.set(localConditionManager);
        }

        return analysisStatus;
    }

    /**
     * We recognize the following situations, looping over the local variables:
     * <ul>
     *     <li>NYR + CREATED at the same level</li>
     *     <li>NYR + local variable created higher up + EXIT (return stmt, anything beyond the level of the CREATED)</li>
     *     <li>NYR + escape</li>
     * </ul>
     */
    private AnalysisStatus checkUselessAssignments() {
        InterruptsFlow bestAlwaysInterrupt = statementAnalysis.flowData.bestAlwaysInterrupt();
        boolean alwaysInterrupts = bestAlwaysInterrupt != InterruptsFlow.NO;
        boolean atEndOfBlock = navigationData.next.get().isEmpty();
        if (atEndOfBlock || alwaysInterrupts) {
            // we run at the local level
            List<String> toRemove = new ArrayList<>();
            statementAnalysis.variableStream().forEach(variableInfo -> {
                int assigned = variableInfo.getProperty(VariableProperty.ASSIGNED);
                int read = variableInfo.getProperty(VariableProperty.READ);
                if (assigned >= Level.TRUE && read <= assigned) {
                    boolean notAssignedInLoop = variableInfo.getProperty(VariableProperty.ASSIGNED_IN_LOOP) != Level.TRUE;
                    // IMPROVE at some point we will do better than "notAssignedInLoop"
                    boolean isLocalAndLocalToThisBlock = statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name);
                    boolean useless = bestAlwaysInterrupt == InterruptsFlow.ESCAPE || notAssignedInLoop && (
                            variableInfo.variable.isLocal() && (alwaysInterrupts || isLocalAndLocalToThisBlock));
                    if (useless) {
                        statementAnalysis.ensure(Message.newMessage(getLocation(), Message.USELESS_ASSIGNMENT, variableInfo.name));
                        if (!isLocalAndLocalToThisBlock) toRemove.add(variableInfo.name);
                    }
                }
            });
            if (!toRemove.isEmpty()) {
                log(VARIABLE_PROPERTIES, "Removing local info for variables {}", toRemove);
                statementAnalysis.removeAllVariables(toRemove);
            }
        }
        return DONE;
    }

    private AnalysisStatus checkUnusedLocalVariables() {
        if (navigationData.next.get().isEmpty()) {
            // at the end of the block, check for variables created in this block
            // READ is set in the first iteration, so there is no reason to expect delays
            statementAnalysis.variableStream().forEach(variableInfo -> {
                if (statementAnalysis.isLocalVariableAndLocalToThisBlock(variableInfo.name)
                        && variableInfo.getProperty(VariableProperty.READ) < Level.READ_ASSIGN_ONCE) {
                    statementAnalysis.ensure(Message.newMessage(getLocation(), Message.UNUSED_LOCAL_VARIABLE, variableInfo.name));
                }
            });
        }
        return DONE;
    }

    /*
     * Can be delayed
     */
    private AnalysisStatus checkUnusedReturnValue() {
        if (statementAnalysis.statement instanceof ExpressionAsStatement eas && eas.expression instanceof MethodCall) {
            MethodCall methodCall = (MethodCall) (((ExpressionAsStatement) statementAnalysis.statement).expression);
            if (Primitives.isVoid(methodCall.methodInfo.returnType())) return DONE;
            MethodAnalysis methodAnalysis = getMethodAnalysis(methodCall.methodInfo);
            int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
            if (identity == Level.DELAY) return DELAYS;
            if (identity == Level.TRUE) return DONE;
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.FALSE) {
                statementAnalysis.ensure(Message.newMessage(getLocation(), Message.IGNORING_RESULT_OF_METHOD_CALL,
                        methodCall.getMethodInfo().fullyQualifiedName()));
            }
        }
        return DONE;
    }

    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
    }

    public List<StatementAnalyser> lastStatementsOfSubBlocks() {
        return navigationData.blocks.get().stream().map(StatementAnalyser::lastStatement).collect(Collectors.toList());
    }


    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
        }

        @Override
        public Set<String> allUnqualifiedVariableNames() {
            return statementAnalysis.allUnqualifiedVariableNames(getCurrentType().typeInfo);
        }

        @Override
        public int getIteration() {
            return iteration;
        }

        @Override
        public TypeAnalyser getCurrentType() {
            return myMethodAnalyser.myTypeAnalyser;
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return myMethodAnalyser;
        }

        @Override
        public MethodAnalysis getCurrentMethodAnalysis() {
            return myMethodAnalyser.methodAnalysis;
        }

        @Override
        public ObjectFlow getObjectFlow(Variable variable) {
            return null;
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return StatementAnalyser.this;
        }

        @Override
        public Location getLocation() {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
        }

        @Override
        public Location getLocation(Expression expression) {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index, expression);
        }

        @Override
        public EvaluationContext child(Value condition) {
            return new EvaluationContextImpl(iteration, conditionManager.addCondition(this, condition));
        }

        @Override
        public int getProperty(Value value, VariableProperty variableProperty) {

            // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
            if (value instanceof VariableValue) {
                Variable variable = ((VariableValue) value).variable;
                if (VariableProperty.NOT_NULL == variableProperty && notNullAccordingToConditionManager(variable)) {
                    return MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                            statementAnalysis.getProperty(analyserContext, variable, variableProperty));
                }
                if (VariableProperty.SIZE == variableProperty) {
                    Value sizeRestriction = conditionManager.individualSizeRestrictions(this).get(variable);
                    if (sizeRestriction != null) {
                        return sizeRestriction.encodedSizeRestriction(this);
                    }
                }
                return statementAnalysis.getProperty(analyserContext, variable, variableProperty);
            }
            /*
            // the following situation occurs when the state contains, e.g., not (null == map.get(a)),
            // and we need to evaluate the NOT_NULL property in the return transfer value in StatementAnalyser
            // See TestSMapList; also, same situation, we may have a conditional value, and the condition will decide...
            // TODO smells like dedicated code
            if (!(value.isInstanceOf(ConstantValue.class)) && conditionManager.haveNonEmptyState() && !conditionManager.inErrorState()) {
                if (VariableProperty.NOT_NULL == variableProperty) {
                    if(value instanceof VariableValue variableValue) {
                        if(notNullAccordingToConditionManager(variableValue.variable)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                    }
                    int notNull = notNullAccordingToConditionManager(value);
                    if (notNull != Level.DELAY) return notNull;
                }
                // TODO add SIZE support?
            }

             */
            // redirect to Value.getProperty()
            // this is the only usage of this method; all other evaluation of a Value in an evaluation context
            // must go via the current method
            return value.getProperty(this, variableProperty);

        }

        private boolean notNullAccordingToConditionManager(Variable variable) {
            Set<Variable> notNullVariablesInState = conditionManager.findIndividualNullInState(this, false);
            if (notNullVariablesInState.contains(variable)) return true;
            Set<Variable> notNullVariablesInCondition = conditionManager.findIndividualNullInCondition(this, false);
            return notNullVariablesInCondition.contains(variable);
        }


        /**
         * Conversion from state to notNull numeric property value, combined with the existing state
         *
         * @param value any non-constant, non-variable value, to be combined with the current state
         * @return the numeric VariableProperty.NOT_NULL value
         */
        private int notNullAccordingToConditionManager(Value value) {
            if (conditionManager.state == UnknownValue.EMPTY || ConditionManager.isDelayed(conditionManager.state))
                return Level.DELAY;

            // action: if we add value == null, and nothing changes, we know it is true, we rely on value.getProperty
            // if the whole thing becomes false, we know it is false, which means we can return Level.TRUE
            Value equalsNull = EqualsValue.equals(this, NullValue.NULL_VALUE, value, ObjectFlow.NO_FLOW);
            Value boolValueFalse = BoolValue.createFalse(getPrimitives());
            if (equalsNull.equals(boolValueFalse)) return MultiLevel.EFFECTIVELY_NOT_NULL;
            Value withCondition = conditionManager.combineWithState(this, equalsNull);
            if (withCondition.equals(boolValueFalse)) return MultiLevel.EFFECTIVELY_NOT_NULL; // we know != null
            if (withCondition.equals(equalsNull)) return MultiLevel.NULLABLE; // we know == null was already there
            return Level.DELAY;
        }

        @Override
        public boolean isNotNull0(Value value) {
            return MultiLevel.isEffectivelyNotNull(getProperty(value, VariableProperty.NOT_NULL));
        }

        @Override
        public Value currentValue(Variable variable) {
            VariableInfo vi = statementAnalysis.find(analyserContext, variable);
            return vi.getValue();
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            Value currentValue = currentValue(variable);
            if (currentValue instanceof VariableValue) {
                return statementAnalysis.find(analyserContext, variable).getProperty(variableProperty);
            }
            return currentValue.getProperty(this, variableProperty);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return Stream.empty(); // TODO
        }

        @Override
        public Set<Variable> linkedVariables(Value value) {
            assert value != null;
            if (value instanceof VariableValue variableValue) {
                TypeInfo typeInfo = variableValue.variable.parameterizedType().bestTypeInfo();
                boolean notSelf = typeInfo != getCurrentType().typeInfo;
                if (notSelf) {
                    VariableInfo variableInfo = statementAnalysis.find(analyserContext, variableValue.variable);
                    int immutable = variableInfo.getProperty(VariableProperty.IMMUTABLE);
                    if (immutable == MultiLevel.DELAY) return null;
                    if (MultiLevel.isE2Immutable(immutable)) return Set.of();
                }
                return Set.of(variableValue.variable);
            }
            return value.linkedVariables(this);
        }
    }

    public class SetProperty implements StatementAnalysis.StatementAnalysisModification {
        public final Variable variable;
        public final VariableProperty property;
        public final int value;

        public SetProperty(Variable variable, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = variable;
        }

        @Override
        public void accept(Integer minLevel) {
            VariableInfo variableInfo = statementAnalysis.find(analyserContext, variable);
            int current = variableInfo.getProperty(property);
            if (current < value) {
                variableInfo.setProperty(property, value);
            }

            Value currentValue = variableInfo.getValue();
            VariableValue valueWithVariable;
            if ((valueWithVariable = currentValue.asInstanceOf(VariableValue.class)) == null) return;
            Variable other = valueWithVariable.variable;
            if (!variable.equals(other)) {
                statementAnalysis.addProperty(analyserContext, minLevel, other, property, value);
            }
        }

        @Override
        public String toString() {
            return "SetProperty{" +
                    "variable=" + variable +
                    ", property=" + property +
                    ", value=" + value +
                    '}';
        }
    }


    public class RaiseErrorMessage implements StatementAnalysis.StatementAnalysisModification {
        private final Message message;

        public RaiseErrorMessage(Message message) {
            this.message = message;
        }

        @Override
        public void accept(Integer minLevel) {
            statementAnalysis.ensure(message);
        }

        @Override
        public String toString() {
            return "RaiseErrorMessage{" +
                    "message=" + message +
                    '}';
        }
    }


    public class ErrorAssigningToFieldOutsideType implements StatementAnalysis.StatementAnalysisModification {
        private final FieldInfo fieldInfo;
        private final Location location;

        public ErrorAssigningToFieldOutsideType(FieldInfo fieldInfo, Location location) {
            this.fieldInfo = fieldInfo;
            this.location = location;
        }

        @Override
        public void accept(Integer minLevel) {
            statementAnalysis.ensure(Message.newMessage(location, Message.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE));
        }

        @Override
        public String toString() {
            return "ErrorAssigningToFieldOutsideType{" +
                    "fieldInfo=" + fieldInfo +
                    ", location=" + location +
                    '}';
        }
    }

    public class ParameterShouldNotBeAssignedTo implements StatementAnalysis.StatementAnalysisModification {
        private final ParameterInfo parameterInfo;
        private final Location location;

        public ParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo, Location location) {
            this.parameterInfo = parameterInfo;
            this.location = location;
        }

        @Override
        public void accept(Integer minLevel) {
            statementAnalysis.ensure(Message.newMessage(location, Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        @Override
        public String toString() {
            return "ParameterShouldNotBeAssignedTo{" +
                    "parameterInfo=" + parameterInfo +
                    ", location=" + location +
                    '}';
        }
    }

    public class MarkAssigned implements StatementAnalysis.StatementAnalysisModification {
        public final Variable variable;
        public final Value stateOnAssignment;

        public MarkAssigned(Variable variable, Value stateOnAssignment) {
            this.variable = variable;
            this.stateOnAssignment = stateOnAssignment;
        }

        @Override
        public void accept(Integer minLevel) {
            statementAnalysis.find(analyserContext, variable).markAssigned(stateOnAssignment);
        }

        @Override
        public String toString() {
            return "MarkAssigned{" +
                    "variable=" + variable +
                    '}';
        }
    }

    public class MarkRead implements StatementAnalysis.StatementAnalysisModification {
        public final Variable variable;

        public MarkRead(Variable variable) {
            this.variable = variable;
        }

        @Override
        public void accept(Integer minLevel) {
            statementAnalysis.find(analyserContext, variable).markRead();
        }

        @Override
        public String toString() {
            return "MarkRead{" +
                    "variable=" + variable +
                    '}';
        }
    }

    public class LinkVariable implements StatementAnalysis.StatementAnalysisModification {
        public final Variable variable;
        public final List<Variable> to;

        public LinkVariable(Variable variable, Set<Variable> to) {
            this.variable = variable;
            this.to = ImmutableList.copyOf(to);
        }

        @Override
        public void accept(Integer minLevel) {
            statementAnalysis.assertVariableExists(variable);
            to.forEach(statementAnalysis::assertVariableExists);
            if (!statementAnalysis.dependencyGraph.isFrozen()) {
                statementAnalysis.dependencyGraph.addNode(variable, to);
            }
        }

        @Override
        public String toString() {
            return "LinkVariable{" +
                    "variable=" + variable +
                    ", to=" + to +
                    '}';
        }
    }

}
