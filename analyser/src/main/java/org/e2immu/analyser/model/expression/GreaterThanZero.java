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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.value.Instance;
import org.e2immu.analyser.model.value.UnknownPrimitiveValue;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public record GreaterThanZero(ParameterizedType booleanParameterizedType,
                              Expression expression,
                              boolean allowEquals,
                              ObjectFlow objectFlow) implements Expression {


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GreaterThanZero that = (GreaterThanZero) o;
        if (allowEquals != that.allowEquals) return false;
        return expression.equals(that.expression);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reValue = expression.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setExpression(GreaterThanZero.greater(evaluationContext,
                reValue.getExpression(),
                new IntConstant(evaluationContext.getPrimitives(), 0, ObjectFlow.NO_FLOW),
                allowEquals, getObjectFlow())).build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, allowEquals);
    }

    // NOT (x >= 0) == x < 0  == (not x) > 0
    // NOT (x > 0)  == x <= 0 == (not x) >= 0
    // note that this one does not solve the int-problem where we always want to maintain allowEquals == True
    public Expression negate(EvaluationContext evaluationContext) {
        if (expression instanceof Sum sum) {
            if (sum.lhs instanceof Numeric ln && sum.lhs.isDiscreteType()) {
                // NOT (-3 + x >= 0) == NOT (x >= 3) == x < 3 == x <= 2 == 2 + -x >= 0
                // NOT (3 + x >= 0) == NOT (x >= -3) == x < -3 == x <= -4 == -4 + -x >= 0
                Expression minusSumPlusOne = IntConstant.intOrDouble(evaluationContext.getPrimitives(),
                        -(ln.doubleValue() + 1.0), sum.lhs.getObjectFlow());
                return new GreaterThanZero(booleanParameterizedType,
                        Sum.sum(evaluationContext, minusSumPlusOne,
                                NegatedExpression.negate(evaluationContext, sum.rhs),
                                expression.getObjectFlow()), true, getObjectFlow());
            }
        }
        return new GreaterThanZero(booleanParameterizedType,
                NegatedExpression.negate(evaluationContext, expression),
                !allowEquals, getObjectFlow());
    }

    /**
     * if xNegated is false: -b + x >= 0 or x >= b
     * if xNegated is true: b - x >= 0 or x <= b
     */
    public record XB(Expression x, double b, boolean lessThan) {
    }

    public XB extract(EvaluationContext evaluationContext) {
        if (expression instanceof Sum sumValue) {
            if (sumValue.lhs instanceof Numeric ln) {
                Expression v = sumValue.rhs;
                Expression x;
                boolean lessThan;
                double b;
                if (v instanceof NegatedExpression ne) {
                    x = ne.expression;
                    lessThan = true;
                    b = ln.doubleValue();
                } else {
                    x = v;
                    lessThan = false;
                    b = ((Numeric) NegatedExpression.negate(evaluationContext, sumValue.lhs)).doubleValue();
                }
                return new XB(x, b, lessThan);
            }
        }
        Expression x;
        boolean lessThan;
        if (expression instanceof NegatedExpression ne) {
            x = ne.expression;
            lessThan = true;
        } else {
            x = expression;
            lessThan = false;
        }
        return new XB(x, 0.0d, lessThan);
    }

    // testing only
    public static Expression greater(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals) {
        return greater(evaluationContext, l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Expression greater(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return new BooleanConstant(primitives, false);
        if (l.isUnknown() || r.isUnknown()) return EmptyExpression.UNKNOWN_PRIMITIVE;


        if (l instanceof Numeric ln && r instanceof Numeric rn) {
            if (allowEquals)
                return new BooleanConstant(primitives, ln.doubleValue() >= rn.doubleValue(), objectFlow);
            return new BooleanConstant(primitives, ln.doubleValue() > rn.doubleValue(), objectFlow);
        }

        ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
        ParameterizedType booleanParameterizedType = evaluationContext.getPrimitives().booleanParameterizedType;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof Numeric ln && !allowEquals && l.isDiscreteType()) {
            // 3 > x == 3 + (-x) > 0 transform to 2 >= x
            Expression lMinusOne = IntConstant.intOrDouble(primitives, ln.doubleValue() - 1.0, l.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, lMinusOne,
                            NegatedExpression.negate(evaluationContext, r),
                            objectFlowSum), true, objectFlow);
        }
        if (r instanceof Numeric rn && !allowEquals && r.isDiscreteType()) {
            // x > 3 == -3 + x > 0 transform to x >= 4
            Expression minusRPlusOne = IntConstant.intOrDouble(primitives, -(rn.doubleValue() + 1.0), r.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, l, minusRPlusOne, objectFlowSum), true, objectFlow);
        }

        return new GreaterThanZero(booleanParameterizedType,
                Sum.sum(evaluationContext, l, NegatedExpression.negate(evaluationContext, r), objectFlowSum),
                allowEquals, objectFlow);
    }

    // testing only
    public static Expression less(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals) {
        return less(evaluationContext, l, r, allowEquals, ObjectFlow.NO_FLOW);
    }

    public static Expression less(EvaluationContext evaluationContext, Expression l, Expression r, boolean allowEquals, ObjectFlow objectFlow) {
        Primitives primitives = evaluationContext.getPrimitives();
        if (l.equals(r) && !allowEquals) return new BooleanConstant(primitives, false);
        if (l.isUnknown() || r.isUnknown()) return EmptyExpression.UNKNOWN_PRIMITIVE;


        if (l instanceof Numeric ln && r instanceof Numeric rn) {
            if (allowEquals)
                return new BooleanConstant(primitives, ln.doubleValue() <= rn.doubleValue(), objectFlow);
            return new BooleanConstant(primitives, ln.doubleValue() < rn.doubleValue(), objectFlow);
        }

        ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
        ParameterizedType booleanParameterizedType = evaluationContext.getPrimitives().booleanParameterizedType;

        ObjectFlow objectFlowSum = objectFlow == null ? null : new ObjectFlow(objectFlow.location, intParameterizedType, Origin.RESULT_OF_OPERATOR);

        if (l instanceof Numeric ln && !allowEquals && l.isDiscreteType()) {
            // 3 < x == x > 3 == -3 + x > 0 transform to x >= 4
            Expression minusLPlusOne = IntConstant.intOrDouble(primitives, -(ln.doubleValue() + 1.0), l.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, minusLPlusOne, r, objectFlowSum), true, objectFlow);
        }
        if (r instanceof Numeric rn && !allowEquals && r.isDiscreteType()) {
            // x < 3 == 3 + -x > 0 transform to x <= 2 == 2 + -x >= 0
            Expression rMinusOne = IntConstant.intOrDouble(primitives, rn.doubleValue() - 1.0, r.getObjectFlow());
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, NegatedExpression.negate(evaluationContext, l), rMinusOne, objectFlowSum), true, objectFlow);
        }
        // l < r <=> l-r < 0 <=> -l+r > 0
        if (l instanceof Numeric ln) {
            return new GreaterThanZero(booleanParameterizedType,
                    Sum.sum(evaluationContext, ln.negate(), r, objectFlowSum), allowEquals, objectFlow);
        }

        // TODO add tautology call

        return new GreaterThanZero(primitives.booleanParameterizedType, Sum.sum(evaluationContext,
                NegatedExpression.negate(evaluationContext, l), r, objectFlowSum), allowEquals, objectFlow);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        if (printMode.forDebug()) {
            String op = allowEquals ? ">=" : ">";
            return expression + " " + op + " 0";
        }
        // transparent
        return expression.print(printMode);
    }

    @Override
    public ParameterizedType returnType() {
        return null;
    }

    @Override
    public String expressionString(int indent) {
        return null;
    }

    @Override
    public int precedence() {
        return 0;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_GEQ0;
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return expression.compareTo(((GreaterThanZero) v).expression);
    }

    @Override
    public ParameterizedType type() {
        return booleanParameterizedType;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return null;
    }

    @Override
    public List<Variable> variables() {
        return expression.variables();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }
}
