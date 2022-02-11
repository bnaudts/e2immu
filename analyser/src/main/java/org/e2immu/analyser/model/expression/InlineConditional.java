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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static org.e2immu.analyser.analyser.Property.*;

/**
 * a ? b : c
 */
public class InlineConditional extends BaseExpression implements Expression {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlineConditional.class);

    public final Expression condition;
    public final Expression ifTrue;
    public final Expression ifFalse;
    public final InspectionProvider inspectionProvider;
    public static final int COMPLEXITY = 10;
    // cached
    private final CausesOfDelay causesOfDelay;

    public InlineConditional(Identifier identifier,
                             InspectionProvider inspectionProvider,
                             Expression condition,
                             Expression ifTrue,
                             Expression ifFalse) {
        super(identifier, COMPLEXITY + condition.getComplexity() + ifFalse.getComplexity() + ifTrue.getComplexity());
        this.condition = Objects.requireNonNull(condition);
        this.ifFalse = Objects.requireNonNull(ifFalse);
        this.ifTrue = Objects.requireNonNull(ifTrue);
        this.inspectionProvider = inspectionProvider;
        this.causesOfDelay = condition.causesOfDelay().merge(ifTrue.causesOfDelay()).merge(ifFalse.causesOfDelay());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlineConditional that = (InlineConditional) o;
        return condition.equals(that.condition) &&
                ifTrue.equals(that.ifTrue) &&
                ifFalse.equals(that.ifFalse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, ifTrue, ifFalse);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression tc = condition.translate(translationMap);
        Expression tt = ifTrue.translate(translationMap);
        Expression tf = ifFalse.translate(translationMap);
        if (tc == condition && tt == ifTrue && tf == ifFalse) return this;
        return new InlineConditional(identifier, inspectionProvider, tc, tt, tf);
    }


    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reCondition = condition.reEvaluate(evaluationContext, translation);
        EvaluationResult reTrue = ifTrue.reEvaluate(evaluationContext, translation);
        EvaluationResult reFalse = ifFalse.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reCondition, reTrue, reFalse);
        EvaluationResult res = EvaluateInlineConditional.conditionalValueConditionResolved(
                evaluationContext, reCondition.value(), reTrue.value(), reFalse.value(), false);
        return builder.setExpression(res.value()).build();
    }


    @Override
    public int internalCompareTo(Expression v) {
        return condition.internalCompareTo(v);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), condition))
                .add(Symbol.QUESTION_MARK)
                .add(outputInParenthesis(qualification, precedence(), ifTrue))
                .add(Symbol.COLON)
                .add(outputInParenthesis(qualification, precedence(), ifFalse));
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        // there is little we can say with certainty until we know that the condition is not trivial, and
        // one of ifTrue, ifFalse is chosen. See Precondition_3
        if (condition.isDelayed()) return condition.causesOfDelay();

        // this code is not in a return switch(property) { ... } expression because JavaParser 3.24.1-SNAPSHOT crashes  while parsing
        if (property == NOT_NULL_EXPRESSION) {
            EvaluationContext child = evaluationContext.child(condition);
            DV nneIfTrue = child.getProperty(ifTrue, NOT_NULL_EXPRESSION, duringEvaluation, false);
            if (nneIfTrue.le(MultiLevel.NULLABLE_DV)) {
                return nneIfTrue;
            }
            Expression notC = Negation.negate(evaluationContext, condition);
            EvaluationContext notChild = evaluationContext.child(notC);
            DV nneIfFalse = notChild.getProperty(ifFalse, NOT_NULL_EXPRESSION, duringEvaluation, false);
            return nneIfFalse.min(nneIfTrue);
        }
        if (property == IDENTITY || property == IGNORE_MODIFICATIONS) {
            return new MultiExpression(ifTrue, ifFalse).getProperty(evaluationContext, property, duringEvaluation);
        }
        if (property == IMMUTABLE || property == INDEPENDENT || property == CONTAINER) {
            if (ifTrue instanceof NullConstant) {
                return evaluationContext.getProperty(ifFalse, property, duringEvaluation, false);
            }
            if (ifFalse instanceof NullConstant) {
                return evaluationContext.getProperty(ifTrue, property, duringEvaluation, false);
            }
            return new MultiExpression(ifTrue, ifFalse).getProperty(evaluationContext, property, duringEvaluation);
        }
        return new MultiExpression(condition, ifTrue, ifFalse).getProperty(evaluationContext, property, duringEvaluation);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        LinkedVariables linkedVariablesTrue = ifTrue.linkedVariables(evaluationContext);
        LinkedVariables linkedVariablesFalse = ifFalse.linkedVariables(evaluationContext);
        return linkedVariablesTrue.merge(linkedVariablesFalse);
    }

    @Override
    public int order() {
        return condition.order();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            condition.visit(predicate);
            ifTrue.visit(predicate);
            ifFalse.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return ListUtil.immutableConcat(condition.variables(descendIntoFieldReferences),
                ifTrue.variables(descendIntoFieldReferences),
                ifFalse.variables(descendIntoFieldReferences));
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult conditionResult = condition.evaluate(evaluationContext, forwardEvaluationInfo.notNullNotAssignment());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(conditionResult);

        boolean resultIsBoolean = returnType().equals(evaluationContext.getPrimitives().booleanParameterizedType());

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        // UNLESS the result is of boolean type. There is sufficient logic in EvaluateInlineConditional to deal
        // with the boolean case.
        Expression condition = conditionResult.value();

        Expression conditionAfterState = evaluationContext.getConditionManager().evaluate(evaluationContext, condition);

        boolean tooComplex = conditionAfterState.getComplexity() *
                Math.max(ifTrue.getComplexity(), ifFalse.getComplexity()) >= 1000;

        EvaluationContext copyForThen = resultIsBoolean || tooComplex ? evaluationContext :
                evaluationContext.child(condition);
        EvaluationResult ifTrueResult = ifTrue.evaluate(copyForThen, forwardEvaluationInfo);
        builder.compose(ifTrueResult);

        EvaluationContext copyForElse = resultIsBoolean ? evaluationContext :
                evaluationContext.child(Negation.negate(evaluationContext, condition));
        EvaluationResult ifFalseResult = ifFalse.evaluate(copyForElse, forwardEvaluationInfo);
        builder.compose(ifFalseResult);

        Expression t = ifTrueResult.value();
        Expression f = ifFalseResult.value();

        if (condition.isEmpty() || t.isEmpty() || f.isEmpty()) {
            throw new UnsupportedOperationException();
        }

        if (tooComplex) {
            LOGGER.debug("Reduced complexity in inline conditional");
            InlineConditional inlineConditional = new InlineConditional(identifier, inspectionProvider, condition, t, f);
            return builder.setExpression(inlineConditional).build();
        }
        EvaluationResult cv = EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext,
                conditionAfterState, t, f, forwardEvaluationInfo.complainInlineConditional());
        return builder.compose(cv).build();
    }

    public Expression optimise(EvaluationContext evaluationContext) {
        return optimise(evaluationContext, false);
    }

    private Expression optimise(EvaluationContext evaluationContext, boolean useState) {
        boolean resultIsBoolean = returnType().equals(evaluationContext.getPrimitives().booleanParameterizedType());

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        // UNLESS the result is of boolean type. There is sufficient logic in EvaluateInlineConditional to deal
        // with the boolean case.
        EvaluationContext copyForThen = resultIsBoolean ? evaluationContext : evaluationContext.child(condition);
        Expression t = ifTrue instanceof InlineConditional inlineTrue ? inlineTrue.optimise(copyForThen, true) : ifTrue;
        EvaluationContext copyForElse = resultIsBoolean ? evaluationContext : evaluationContext.child(Negation.negate(evaluationContext, condition));
        Expression f = ifFalse instanceof InlineConditional inlineFalse ? inlineFalse.optimise(copyForElse, true) : ifFalse;

        if (useState) {
            return EvaluateInlineConditional.conditionalValueCurrentState(evaluationContext, condition, t, f).getExpression();
        }
        return EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, condition, t, f, false).getExpression();

    }

    @Override
    public ParameterizedType returnType() {
        if (ifTrue.isNull() && ifFalse.isNull()) {
            return inspectionProvider.getPrimitives().objectParameterizedType();
        }
        if (ifTrue.isNull()) return ifFalse.returnType().ensureBoxed(inspectionProvider.getPrimitives());
        if (ifFalse.isNull()) return ifTrue.returnType().ensureBoxed(inspectionProvider.getPrimitives());
        return ifTrue.returnType().commonType(inspectionProvider, ifFalse.returnType());
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        if (ifTrue.isNull() && ifFalse.isNull()) {
            return Set.of(inspectionProvider.getPrimitives().objectParameterizedType());
        }
        if (ifTrue.isNull()) {
            return ifFalse.erasureTypes(typeContext);
        }
        if (ifFalse.isNull()) return ifTrue.erasureTypes(typeContext);
        return SetUtil.immutableUnion(ifTrue.erasureTypes(typeContext), ifFalse.erasureTypes(typeContext));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    // IMPORTANT NOTE: this is a pretty expensive operation, even with the causesOfDelay cache!
    // removing the condition is the least to do.
    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        //Expression c = condition.isDelayed() ? condition.mergeDelays(causesOfDelay) : condition;
        Expression t = ifTrue.isDelayed() ? ifTrue.mergeDelays(causesOfDelay) : ifTrue;
        Expression f = ifFalse.isDelayed() ? ifFalse.mergeDelays(causesOfDelay) : ifFalse;
        if (t != ifTrue || f != ifFalse) {
            return new InlineConditional(identifier, inspectionProvider, condition, t, f);
        }
        return this;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(condition, ifTrue, ifFalse);
    }

    @Override
    public Expression removeAllReturnValueParts() {
        boolean removeTrue = ifTrue.isReturnValue();
        boolean removeFalse = ifFalse.isReturnValue();
        if (removeTrue && removeFalse) return ifTrue; // nothing we can do
        if (removeTrue) return ifFalse;
        if (removeFalse) return ifTrue;
        return this;
    }
}
