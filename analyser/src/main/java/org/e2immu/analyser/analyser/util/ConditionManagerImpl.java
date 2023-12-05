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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;

/*
condition = the condition in the parent statement that leads to this block. Default: true

state = the cumulative state in the current block, before execution of the statement (level 1-2, not 3).
The state is carried over to the next statement unless there is some interrupt in the flow (break, return, throw...)

precondition = the cumulative precondition of the method.
In SAI.analyseSingleStatement, the cumulative precondition from MethodLevelData is added via
ConditionManagerHelper.makeLocalConditionManager.

In a recursion of inline conditionals, the state remains true, and the condition equals the condition of each inline.
Default value: true

Concerning delays: only condition and state are recursively combined, precondition is not.
 */
public record ConditionManagerImpl(Expression condition,
                                   Set<Variable> conditionVariables,
                                   Expression state,
                                   Set<Variable> stateVariables,
                                   Precondition precondition,
                                   Set<Variable> ignore,
                                   ConditionManager parent) implements ConditionManager {

    private static final Set<Variable> NO_VARS = Set.of();

    private static final ConditionManagerImpl SPECIAL = new ConditionManagerImpl();

    public static final int LIMIT_ON_COMPLEXITY = 200;

    private ConditionManagerImpl() {
        this(UnknownExpression.forSpecial(), NO_VARS, UnknownExpression.forSpecial(), NO_VARS,
                new Precondition(UnknownExpression.forSpecial(), List.of()), NO_VARS, null);
    }

    public ConditionManagerImpl {
        checkBooleanOrUnknown(Objects.requireNonNull(condition));
        checkVariables(condition, Objects.requireNonNull(conditionVariables));
        checkBooleanOrUnknown(Objects.requireNonNull(state));
        checkVariables(state, Objects.requireNonNull(stateVariables));
        Objects.requireNonNull(precondition);
        Objects.requireNonNull(ignore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionManagerImpl that = (ConditionManagerImpl) o;
        return condition.equals(that.condition)
                && state.equals(that.state)
                && precondition.equals(that.precondition)
                && ignore.equals(that.ignore)
                && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, state, precondition, ignore, parent);
    }

    // there can be more, but all the expression's variables should be included
    private static void checkVariables(Expression e, Set<Variable> ev) {
        assert ev.containsAll(e.variables());
    }

    public boolean isDelayed() {
        return condition.isDelayed() || state.isDelayed() || precondition.expression().isDelayed()
                || (parent != null && parent.isDelayed());
    }

    public boolean isReasonForDelay(Variable variable) {
        return state.causesOfDelay().contains(variable)
                || condition.causesOfDelay().contains(variable)
                || precondition.expression().causesOfDelay().contains(variable)
                || (parent != null && parent().isReasonForDelay(variable));
    }

    /*
    EMPTY -> some value, no clue which one, we'll never know
    NO_VALUE -> delay
     */
    private static void checkBooleanOrUnknown(Expression v) {
        if (!v.isUnknown() && v.returnType().isNotBooleanOrBoxedBoolean()) {
            throw new UnsupportedOperationException("Need an unknown or boolean value in the condition manager; got " + v
                    + " with return type " + v.returnType());
        }
    }

    public static ConditionManagerImpl initialConditionManager(Primitives primitives) {
        BooleanConstant TRUE = new BooleanConstant(primitives, true);
        return new ConditionManagerImpl(TRUE, NO_VARS, TRUE, NO_VARS, Precondition.empty(TRUE), NO_VARS, null);
    }

    public static ConditionManagerImpl impossibleConditionManager(Primitives primitives) {
        BooleanConstant FALSE = new BooleanConstant(primitives, true);
        return new ConditionManagerImpl(FALSE, NO_VARS, FALSE, NO_VARS, new Precondition(FALSE, List.of()), NO_VARS,
                null);
    }

    /*
    adds a new layer (parent this)
    Used in CompanionAnalyser, ComputingMethodAnalyser, FieldAnalyser
     */
    public ConditionManagerImpl newAtStartOfNewBlock(Primitives primitives, Expression condition,
                                                     Set<Variable> conditionVariables, Precondition precondition) {
        return new ConditionManagerImpl(condition, conditionVariables, new BooleanConstant(primitives, true),
                NO_VARS, precondition, NO_VARS, this);
    }

    /*
    we guarantee a parent so that the condition counts!
    Used in: StatementAnalyserImpl.analyseAllStatementsInBlock
     */
    public ConditionManagerImpl newCondition(EvaluationResult context, Expression switchCondition,
                                             Set<Variable> conditionVariables) {
        return new ConditionManagerImpl(combine(context, condition, switchCondition),
                combine(this.conditionVariables, conditionVariables), state, stateVariables, precondition, NO_VARS,
                this);
    }

    public ConditionManagerImpl newConditionCompute(EvaluationResult context, Expression switchCondition) {
        if (switchCondition.isBoolValueTrue()) {
            // add a new layer
            return new ConditionManagerImpl(condition, conditionVariables, state, stateVariables, precondition, NO_VARS, this);
        }
        Set<Variable> computedConditionVariables = switchCondition.variableStream().collect(Collectors.toUnmodifiableSet());
        return new ConditionManagerImpl(switchCondition, computedConditionVariables, state, stateVariables,
                precondition, NO_VARS, this);
    }

    @Override
    public ConditionManager replaceConditionComputeEmptyState(EvaluationResult context, Expression newCondition) {
        Set<Variable> computedConditionVariables;
        if (newCondition.isBooleanConstant()) {
            computedConditionVariables = Set.of();
        } else {
            computedConditionVariables = newCondition.variableStream().collect(Collectors.toUnmodifiableSet());
        }
        BooleanConstant TRUE = new BooleanConstant(context.getPrimitives(), true);
        return new ConditionManagerImpl(newCondition, computedConditionVariables, TRUE, Set.of(),
                precondition, NO_VARS, parent);
    }

    @Override
    public ConditionManager withStateCompute(EvaluationResult context, Expression newState) {
        Set<Variable> computedStateVariables;
        if (newState.isBooleanConstant()) {
            computedStateVariables = Set.of();
        } else {
            computedStateVariables = newState.variableStream().collect(Collectors.toUnmodifiableSet());
        }
        return new ConditionManagerImpl(condition, conditionVariables, newState, computedStateVariables,
                precondition, NO_VARS, parent);
    }

    /*
        adds a new layer (parent this)
        Widely used, mostly in SASubBlocks to create the CM of the ExecutionOfBlock objects
        */
    public ConditionManagerImpl newAtStartOfNewBlockDoNotChangePrecondition(Primitives primitives,
                                                                            Expression condition,
                                                                            Set<Variable> conditionVariables) {
        return new ConditionManagerImpl(condition, conditionVariables, new BooleanConstant(primitives, true),
                NO_VARS, precondition, NO_VARS, this);
    }

    /*
    adds a new layer (parent this)
    Used to: create a child CM that has more state
    */
    public ConditionManagerImpl addState(Expression state, Set<Variable> stateVariables) {
        return new ConditionManagerImpl(condition, conditionVariables, state, stateVariables, precondition, NO_VARS,
                this);
    }

    /*
    stays at the same level (parent=parent)
    Used in: ConditionManagerHelper.makeLocalConditionManager, used in StatementAnalyserImpl.analyseSingleStatement
    This is the feedback loop from MethodLevelData.combinedPrecondition back into the condition manager
     */
    public ConditionManagerImpl withPrecondition(Precondition combinedPrecondition) {
        return new ConditionManagerImpl(condition, conditionVariables, state, stateVariables, combinedPrecondition, ignore,
                parent);
    }

    /*
    stays at the same level
    Used in EvaluationContext.nneForValue
     */
    public ConditionManagerImpl withoutState(Primitives primitives) {
        return new ConditionManagerImpl(condition, conditionVariables,
                new BooleanConstant(primitives, true), NO_VARS, precondition, ignore, parent);
    }

    /*
    stays at the same level (parent=parent)
    Used in SASubBlocks
     */
    public ConditionManagerImpl newForNextStatementDoNotChangePrecondition(EvaluationResult evaluationContext,
                                                                           Expression addToState,
                                                                           Set<Variable> addToStateVariables) {
        Objects.requireNonNull(addToState);
        if (addToState.isBoolValueTrue()) return this;
        Expression newState = combine(evaluationContext, state, addToState);
        return new ConditionManagerImpl(condition, conditionVariables, newState,
                combine(stateVariables, addToStateVariables), precondition, ignore, parent);
    }

    /*
    Re-assignments of variables... affects absoluteState computations!
     */
    public ConditionManagerImpl removeVariables(Set<Variable> variablesAssigned) {
        return new ConditionManagerImpl(condition, conditionVariables, state, stateVariables, precondition,
                variablesAssigned, parent);
    }

    public Expression absoluteState(EvaluationResult evaluationContext) {
        return absoluteState(evaluationContext, null, false, Set.of());
    }

    @Override
    public Expression absoluteStateUpTo(ConditionManager base, EvaluationResult context) {
        return absoluteState(context, base, false, Set.of());
    }

    private Expression absoluteState(EvaluationResult context,
                                     ConditionManager base,
                                     boolean doingNullCheck,
                                     Set<Variable> ignoreFromChildren) {
        Set<Variable> cumulativeIgnore = SetUtil.immutableUnion(ignoreFromChildren, ignore);
        Expression[] expressions;
        int complexity;
        if (parent == null) {
            return state;
        }
        if (base != null && base.equals(parent)) {
            return And.and(Identifier.CONSTANT, context, condition, state);
        }
        Expression parentAbsolute = ((ConditionManagerImpl) parent)
                .absoluteState(context, base, doingNullCheck, cumulativeIgnore);
        Expression cleanCondition = expressionWithoutVariables(context, condition, cumulativeIgnore);
        expressions = new Expression[]{cleanCondition, state, parentAbsolute};
        complexity = cleanCondition.getComplexity() + state.getComplexity() + parentAbsolute.getComplexity();

        if (complexity > Expression.SOFT_LIMIT_ON_COMPLEXITY) {
            Expression[] values = {cleanCondition, state, parentAbsolute};
            return ExpressionCanBeTooComplex.reducedComplexity(Identifier.CONSTANT, context, List.of(), values);
        }
        return And.and(Identifier.CONSTANT, context, doingNullCheck, expressions);
    }

    public Expression expressionWithoutVariables(EvaluationResult context,
                                                 Expression expression,
                                                 Set<Variable> cumulativeIgnore) {
        if (cumulativeIgnore.isEmpty()) return expression;
        if (expression instanceof ConstantExpression<?>) return expression;
        IsVariableExpression ive = expression.asInstanceOf(IsVariableExpression.class);

        if (ive != null) {
            if (expression.returnType().isBooleanOrBoxedBoolean() && cumulativeIgnore.contains(ive.variable())) {
                return new BooleanConstant(context.getPrimitives(), true); // REMOVE!
            }
            return expression; // we'll catch this one later
        }
        if (expression instanceof Negation negation) {
            Expression e = expressionWithoutVariables(context, negation.expression, cumulativeIgnore);
            if (e.isBoolValueTrue()) return e; // REMOVE!
            return expression;
        }
        if (expression instanceof And and) {
            Expression[] filtered = and.getExpressions().stream()
                    .map(e -> expressionWithoutVariables(context, e, cumulativeIgnore))
                    .filter(e -> !e.isBoolValueTrue())
                    .toArray(Expression[]::new);
            return And.and(and.identifier, context, filtered);
        }
        if (expression instanceof BinaryOperator operator) {
            Expression lhs = expressionWithoutVariables(context, operator.lhs, cumulativeIgnore);
            Expression rhs = expressionWithoutVariables(context, operator.rhs, cumulativeIgnore);
            IsVariableExpression lhsVar = lhs.asInstanceOf(IsVariableExpression.class);
            IsVariableExpression rhsVar = rhs.asInstanceOf(IsVariableExpression.class);
            if (lhsVar != null && cumulativeIgnore.contains(lhsVar.variable()) ||
                    rhsVar != null && cumulativeIgnore.contains(rhsVar.variable())) {
                return new BooleanConstant(context.getPrimitives(), true); // REMOVE!
            }
        }
        if (expression instanceof DelayedExpression de) {
            Expression e = expressionWithoutVariables(context, de.getOriginal(), cumulativeIgnore);
            if (e.isBoolValueTrue()) return e; // REMOVE!
            return expression;
        }
        // TODO ... simplified for now
        return expression;
    }

    public Identifier getIdentifier() {
        List<Identifier> list;
        if (parent == null) {
            list = List.of(condition.getIdentifier(), state.getIdentifier(), precondition.expression().getIdentifier());
        } else {
            list = List.of(condition.getIdentifier(), state.getIdentifier(), precondition.expression().getIdentifier(), parent.getIdentifier());
        }
        return Identifier.joined("cm", list);
    }

    public Expression stateUpTo(EvaluationResult context, int recursions) {
        Expression[] expressions;
        if (parent == null) {
            expressions = new Expression[]{state};
        } else if (recursions == 0) {
            expressions = new Expression[]{condition};
        } else {
            expressions = new Expression[]{condition, state, parent.stateUpTo(context, recursions - 1)};
        }
        return And.and(context, expressions);
    }

    //i>3?i:3, for example. Result is non-boolean. CM may have a state saying that i<0, which solves this one
    // this method is called for scopes and indices of array access, and for scopes of field references
    public Expression evaluateNonBoolean(EvaluationResult context, Expression value) {
        assert !value.returnType().isBooleanOrBoxedBoolean() : "Got " + value.getClass() + ", type " + value.returnType();
        Expression conditionalPart = value.extractConditions(context.getPrimitives());
        if (conditionalPart.isBoolValueTrue()) return value;
        Expression absoluteState = absoluteState(context, null, false, Set.of());
        if (absoluteState.isEmpty() || absoluteState.isBoolValueTrue()) return value;
        Expression newState = And.and(context, absoluteState, conditionalPart);
        if (newState.equals(conditionalPart) || newState.equals(absoluteState)) {
            return value.applyCondition(new BooleanConstant(context.getPrimitives(), true));
        }
        if (newState instanceof And and && and.getExpressions().stream().anyMatch(conditionalPart::equals)) {
            return value; // no improvement can be made
        }
        return value.applyCondition(newState);
    }

    /**
     * computes a value in the context of the current condition manager.
     *
     * @param doingNullCheck a boolean to prevent a stack overflow, repeatedly trying to detect not-null situations
     *                       (see e.g. Store_0)
     * @return a value without the precondition attached
     */
    public Expression evaluate(EvaluationResult context, Expression value, boolean doingNullCheck) {
        assert value.returnType().isBooleanOrBoxedBoolean() : "Got " + value.getClass() + ", type " + value.returnType();
        if (value.isBoolValueFalse()) return value; // no matter what the conditions and state is

        Expression absoluteState = absoluteState(context, null, doingNullCheck, Set.of());
        if (absoluteState.isEmpty() || value.isEmpty()) throw new UnsupportedOperationException();
        /*
        check on true: no state, so don't do anything
         */
        Expression combinedWithPrecondition;
        if (precondition.isEmpty()) {
            combinedWithPrecondition = absoluteState;
        } else {
            combinedWithPrecondition = And.and(Identifier.CONSTANT, context, doingNullCheck, absoluteState,
                    precondition.expression());
        }
        // this one solves boolean problems; in a boolean context, there is no difference
        // between the value and the condition
        Expression resultWithPrecondition = And.and(Identifier.CONSTANT, context, doingNullCheck, combinedWithPrecondition, value);
        if (resultWithPrecondition.equals(combinedWithPrecondition)) {
            // constant true: adding the value has no effect at all
            return new BooleanConstant(context.getPrimitives(), true);
        }
        // return the result without precondition
        Expression result = And.and(Identifier.CONSTANT, context, doingNullCheck, absoluteState, value);
        if (result instanceof And and && and.getExpressions().stream().anyMatch(value::equals)) {
            return value;
        }
        return result;
    }

    private Set<Variable> combine(Set<Variable> cv1, Set<Variable> cv2) {
        return SetUtil.immutableUnion(cv1, cv2);
    }

    private Expression combine(EvaluationResult context, Expression e1, Expression e2) {
        Objects.requireNonNull(e2);
        if (e1.isEmpty() || e2.isEmpty()) throw new UnsupportedOperationException();
        int complexity = e1.getComplexity() + e2.getComplexity();
        if (complexity > Expression.SOFT_LIMIT_ON_COMPLEXITY) {
            Identifier identifier = Identifier.joined("combine", List.of(e1.getIdentifier(), e2.getIdentifier()));
            return ExpressionCanBeTooComplex.reducedComplexity(identifier, context, List.of(), new Expression[]{e1, e2});
        }
        return And.and(context, e1, e2);
    }

    /**
     * Extract NOT_NULL properties from the current condition in ACCEPT mode.
     * See enum ACCEPT for more explanation of the difference between ACCEPT and REJECT.
     *
     * @return individual variables that appear in a top-level conjunction as variable == null
     */
    public Set<Variable> findIndividualNullInCondition(EvaluationResult evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(condition, evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current absolute state, seen as a conjunction (filter mode ACCEPT)
     *
     * @return individual variables that appear in the conjunction as variable == null
     */
    public Set<Variable> findIndividualNullInState(EvaluationResult context, boolean requireEqualsNull) {
        Expression state;
        if (context.evaluationContext().preventAbsoluteStateComputation()) {
            state = this.state;
        } else {
            state = absoluteState(context, null, false, Set.of());
        }
        return findIndividualNull(state, context, Filter.FilterMode.ACCEPT, requireEqualsNull);

    }

    public Set<Variable> findIndividualNullInPrecondition(EvaluationResult evaluationContext, boolean requireEqualsNull) {
        return findIndividualNull(precondition.expression(), evaluationContext, Filter.FilterMode.ACCEPT, requireEqualsNull);
    }

    /**
     * Extract NOT_NULL properties from the current condition
     *
     * @return individual variables that appear in a top-level conjunction or disjunction as variable == null
     */
    public static Set<Variable> findIndividualNull(Expression value,
                                                   EvaluationResult evaluationContext,
                                                   Filter.FilterMode filterMode,
                                                   boolean requireEqualsNull) {
        if (value.isEmpty()) {
            return Set.of();
        }
        Filter filter = new Filter(evaluationContext, filterMode);
        Map<Variable, Expression> individualNullClauses = filter.filter(value, filter.individualNullOrNotNullClause()).accepted();
        return individualNullClauses.entrySet()
                .stream()
                .filter(e -> requireEqualsNull == (e.getValue().equalsNull()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /*
     return that part of the absolute conditional that is NOT covered by @NotNull (individual not null clauses), as
     an AND of negations of the remainder after getting rid of != null, == null clauses.
     */
    public Expression precondition(EvaluationResult evaluationContext) {
        Expression absoluteState = absoluteState(evaluationContext, null, false, Set.of());
        if (absoluteState.isEmpty()) throw new UnsupportedOperationException();
        Expression negated = Negation.negate(evaluationContext, absoluteState);

        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<ParameterInfo> filterResult = filter.filter(negated, filter.individualNullOrNotNullClauseOnParameter());
        // those parts that have nothing to do with individual clauses
        return filterResult.rest();
    }

    private static Filter.FilterResult<Variable> obtainVariableFilter(Expression defaultRest, Variable variable, Expression value) {
        List<Variable> variables = value.variables();
        if (variables.size() == 1 && variable.equals(variables.stream().findAny().orElseThrow())) {
            return new Filter.FilterResult<>(Map.of(variable, value), defaultRest);
        }
        return null;
    }

    /*
    any info there is about this variable
     */
    public Expression individualStateInfo(EvaluationResult evaluationContext, Variable variable) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Expression absoluteState = absoluteState(evaluationContext, null, false, Set.of());
        Expression combinedWithPrecondition;
        if (precondition.isEmpty()) {
            combinedWithPrecondition = absoluteState;
        } else {
            combinedWithPrecondition = And.and(evaluationContext, absoluteState, precondition.expression());
        }

        Filter.FilterResult<Variable> filterResult = filter.filter(combinedWithPrecondition,
                value -> obtainVariableFilter(filter.getDefaultRest(), variable, value));
        return filterResult.accepted().getOrDefault(variable, filter.getDefaultRest());
    }

    /*
    why a separate version? because preconditions do not work 'cumulatively', preconditionIsDelayed
    has no info about delays in the parent. This is not compatible with writing an eventually final version.
    See Project_0 ...
     */
    public boolean isSafeDelayed() {
        return isDelayed() || parent != null && parent.isDelayed() || parent == SPECIAL;
    }

    public CausesOfDelay stateDelayedOrPreconditionDelayed() {
        return state.causesOfDelay().merge(precondition.expression().causesOfDelay());
    }

    public CausesOfDelay causesOfDelay() {
        CausesOfDelay mine = condition.causesOfDelay().merge(state.causesOfDelay()).merge(precondition.expression().causesOfDelay());
        return parent == null ? mine : mine.merge(parent.causesOfDelay());
    }

    private static String nice(Set<Variable> set) {
        return set.stream().map(Variable::simpleName).sorted().collect(Collectors.joining(","));
    }

    @Override
    public String toString() {
        return "CM{" +
                (condition.isBoolValueTrue() ? "" : "condition=" + condition + ";") +
                (state.isBoolValueTrue() ? "" : "state=" + state + ";") +
                (precondition.isEmpty() ? "" : "pc=" + precondition + ";") +
                (ignore.isEmpty() ? "" : "ignore=" + nice(ignore) + ";") +
                (parent == null ? "" : parent == SPECIAL ? "**" : "parent=" + parent) + '}';
    }

    public List<Variable> variables() {
        return Stream.concat(parent == null ? Stream.of() : parent.variables().stream(),
                Stream.concat(conditionVariables.stream(),
                        Stream.concat(precondition.expression().variableStream(),
                                stateVariables.stream()))).toList();
    }

    public Expression multiExpression() {
        return MultiExpressions.from(getIdentifier(), variables());
    }

    public static class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        private final AnalyserContext analyserContext;

        public EvaluationContextImpl(AnalyserContext analyserContext) {
            this.analyserContext = analyserContext;
        }

        @Override
        public int getDepth() {
            return 1;
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Primitives getPrimitives() {
            return analyserContext.getPrimitives();
        }

        @Override
        public DV getProperty(Expression value, Property property, boolean duringEvaluation, boolean ignoreStateInConditionManager) {
            return commonGetProperty(this, value, property, duringEvaluation, ignoreStateInConditionManager);
        }

        @Override
        public Expression currentValue(Variable variable,
                                       Expression scopeValue,
                                       Expression indexValue,
                                       Identifier identifier,
                                       ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(identifier, variable, VariableExpression.NO_SUFFIX, scopeValue, indexValue);
        }
    }

    public static DV commonGetProperty(EvaluationContext evaluationContext,
                                       Expression value,
                                       Property property,
                                       boolean duringEvaluation,
                                       boolean ignoreStateInConditionManager) {

        if (value instanceof VariableExpression variableValue) {
            Variable variable = variableValue.variable();
            if (variable instanceof ParameterInfo parameterInfo) {
                Property vp = property == NOT_NULL_EXPRESSION ? NOT_NULL_PARAMETER : property;
                return evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            if (variable instanceof FieldReference fieldReference) {
                Property vp = property == NOT_NULL_EXPRESSION ? EXTERNAL_NOT_NULL : property;
                return evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo()).getProperty(vp);
            }
            if (variable instanceof This thisVariable) {
                return evaluationContext.getAnalyserContext().getTypeAnalysis(thisVariable.typeInfo).getProperty(property);
            }
            if (variable instanceof PreAspectVariable pre) {
                /*
                pre-aspect variables must be nullable, because there can be no information, in which case "null" is injected.
                the companion methods must take the null-value into account, see e.g. that of List.addAll, size aspect,
                Modification_26. See also CompanionAnalyser.EvaluationContextImpl.getProperty().
                 */
                if (property == NOT_NULL_EXPRESSION) return MultiLevel.NULLABLE_DV;
                return pre.valueForProperties().getProperty(EvaluationResultImpl.from(evaluationContext), property, true);
            }
            throw new UnsupportedOperationException("Variable value of type " + variable.getClass());
        }
        return value.getProperty(EvaluationResultImpl.from(evaluationContext), property, true); // will work in many cases
    }
}
