/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import com.github.javaparser.ast.expr.BinaryExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * From https://introcs.cs.princeton.edu/java/11precedence/
 * All associativity is from left to right for binary operators: a+b+c = (a+b)+c
 * <p>
 * precedence 12: * / % multiplicative
 * precedence 11: + - additive, + string concat
 * precedence 10: >>>, <<< shift
 * precedence 9: <, <=, >, >= comparison
 * precedence 8: ==, != equality
 * precedence 7: & AND
 * precedence 6: ^ XOR
 * precedence 5: | OR
 * precedence 4: && logical AND
 * precedence 3: || logical OR
 */
@E2Container
public class BinaryOperator implements Expression {
    public final Expression lhs;
    public final Expression rhs;
    public final int precedence;
    public final MethodInfo operator;

    public static final int MULTIPLICATIVE_PRECEDENCE = 12;
    public static final int ADDITIVE_PRECEDENCE = 11;
    public static final int SHIFT_PRECEDENCE = 10;
    public static final int COMPARISON_PRECEDENCE = 9;
    public static final int EQUALITY_PRECEDENCE = 8;
    public static final int AND_PRECEDENCE = 7;
    public static final int XOR_PRECEDENCE = 6;
    public static final int OR_PRECEDENCE = 5;
    public static final int LOGICAL_AND_PRECEDENCE = 4;
    public static final int LOGICAL_OR_PRECEDENCE = 3;

    public BinaryOperator(
            @NotNull Expression lhs,
            @NotNull MethodInfo operator,
            @NotNull Expression rhs,
            int precedence) {
        this.lhs = Objects.requireNonNull(lhs);
        this.rhs = Objects.requireNonNull(rhs);
        this.precedence = precedence;
        this.operator = Objects.requireNonNull(operator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryOperator that = (BinaryOperator) o;
        return lhs.equals(that.lhs) &&
                rhs.equals(that.rhs) &&
                operator.equals(that.operator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs, operator);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new BinaryOperator(translationMap.translateExpression(lhs),
                operator, translationMap.translateExpression(rhs), precedence);
    }

    // NOTE: we're not visiting here!

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        // we need to handle the short-circuit operators differently
        Primitives primitives = evaluationContext.getPrimitives();
        if (operator == primitives.orOperatorBool) {
            return shortCircuit(evaluationContext, false);
        }
        if (operator == primitives.andOperatorBool) {
            return shortCircuit(evaluationContext, true);
        }

        ForwardEvaluationInfo forward = allowsForNullOperands(primitives) ? ForwardEvaluationInfo.DEFAULT : ForwardEvaluationInfo.NOT_NULL;
        EvaluationResult leftResult = lhs.evaluate(evaluationContext, forward);
        EvaluationResult rightResult = rhs.evaluate(evaluationContext, forward);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(leftResult, rightResult);
        builder.setValue(determineValue(primitives, builder, leftResult, rightResult, evaluationContext));
        return builder.build();
    }

    private Value determineValue(Primitives primitives,
                                 EvaluationResult.Builder builder,
                                 EvaluationResult left,
                                 EvaluationResult right,
                                 EvaluationContext evaluationContext) {
        Value l = left.value;
        Value r = right.value;

        if (l == UnknownValue.NO_VALUE || r == UnknownValue.NO_VALUE) return UnknownValue.NO_VALUE;
        if (l.isUnknown() || r.isUnknown()) return UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;

        if (operator == primitives.equalsOperatorObject) {
            if (l.equals(r)) return BoolValue.createTrue(primitives);

            // HERE are the ==null checks
            if (l == NullValue.NULL_VALUE && right.isNotNull0(evaluationContext) ||
                    r == NullValue.NULL_VALUE && left.isNotNull0(evaluationContext)) {
                return BoolValue.createFalse(primitives);
            }
            return EqualsValue.equals(evaluationContext, l, r, booleanObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.equalsOperatorInt) {
            if (l.equals(r)) return BoolValue.createTrue(primitives);
            if (l == NullValue.NULL_VALUE || r == NullValue.NULL_VALUE) {
                // TODO need more resolution here to distinguish int vs Integer comparison throw new UnsupportedOperationException();
            }
            return EqualsValue.equals(evaluationContext, l, r, booleanObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.notEqualsOperatorObject) {
            if (l.equals(r)) BoolValue.createFalse(primitives);

            // HERE are the !=null checks
            if (l == NullValue.NULL_VALUE && right.isNotNull0(evaluationContext) ||
                    r == NullValue.NULL_VALUE && left.isNotNull0(evaluationContext)) {
                return BoolValue.createTrue(primitives);
            }
            return NegatedValue.negate(evaluationContext,
                    EqualsValue.equals(evaluationContext, l, r, booleanObjectFlow(primitives, evaluationContext)));
        }
        if (operator == primitives.notEqualsOperatorInt) {
            if (l.equals(r)) return BoolValue.createFalse(primitives);
            if (l == NullValue.NULL_VALUE || r == NullValue.NULL_VALUE) {
                // TODO need more resolution throw new UnsupportedOperationException();
            }
            return NegatedValue.negate(evaluationContext,
                    EqualsValue.equals(evaluationContext, l, r, booleanObjectFlow(primitives, evaluationContext)));
        }

        // from here on, straightforward operations
        if (operator == primitives.plusOperatorInt) {
            return SumValue.sum(evaluationContext, l, r, intObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.minusOperatorInt) {
            return SumValue.sum(evaluationContext, l, NegatedValue.negate(evaluationContext, r), intObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.multiplyOperatorInt) {
            return ProductValue.product(evaluationContext, l, r, intObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.divideOperatorInt) {
            EvaluationResult er = DivideValue.divide(evaluationContext, l, r, intObjectFlow(primitives, evaluationContext));
            builder.compose(er);
            return er.value;
        }
        if (operator == primitives.remainderOperatorInt) {
            EvaluationResult er = RemainderValue.remainder(evaluationContext, l, r, intObjectFlow(primitives, evaluationContext));
            builder.compose(er);
            return er.value;
        }
        if (operator == primitives.lessEqualsOperatorInt) {
            return GreaterThanZeroValue.less(evaluationContext, l, r, true, booleanObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.lessOperatorInt) {
            return GreaterThanZeroValue.less(evaluationContext, l, r, false, booleanObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.greaterEqualsOperatorInt) {
            return GreaterThanZeroValue.greater(evaluationContext, l, r, true, booleanObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.greaterOperatorInt) {
            return GreaterThanZeroValue.greater(evaluationContext, l, r, false, booleanObjectFlow(primitives, evaluationContext));
        }
        if (operator == primitives.bitwiseAndOperatorInt) {
            return BitwiseAndValue.bitwiseAnd(evaluationContext, l, r, intObjectFlow(primitives, evaluationContext));
        }
        /*
            if (operator == primitives.bitwiseOrOperatorInt) {
                return new IntValue(l.toInt().value | r.toInt().value);
            }

            if (operator == primitives.bitwiseXorOperatorInt) {
                return new IntValue(l.toInt().value ^ r.toInt().value);
            }
        }
         TODO
         */
        if (operator == primitives.plusOperatorString) {
            return StringConcat.stringConcat(evaluationContext, l, r, stringObjectFlow(primitives, evaluationContext));
        }
        throw new UnsupportedOperationException("Operator " + operator.fullyQualifiedName());
    }

    private ObjectFlow stringObjectFlow(Primitives primitives, EvaluationContext evaluationContext) {
        return new ObjectFlow(evaluationContext.getLocation(), primitives.stringParameterizedType, Origin.RESULT_OF_OPERATOR);
    }

    private ObjectFlow booleanObjectFlow(Primitives primitives, EvaluationContext evaluationContext) {
        return new ObjectFlow(evaluationContext.getLocation(), primitives.booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
    }

    private ObjectFlow intObjectFlow(Primitives primitives, EvaluationContext evaluationContext) {
        return new ObjectFlow(evaluationContext.getLocation(), primitives.intParameterizedType, Origin.RESULT_OF_OPERATOR);
    }

    private EvaluationResult shortCircuit(EvaluationContext evaluationContext, boolean and) {
        ForwardEvaluationInfo forward = ForwardEvaluationInfo.NOT_NULL;
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        Primitives primitives = evaluationContext.getPrimitives();

        EvaluationResult l = lhs.evaluate(evaluationContext, forward);
        Value constant = and ? BoolValue.createFalse(primitives) : BoolValue.createTrue(primitives);
        if (l.value == constant) {
            builder.raiseError(Message.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            return builder.compose(l).build();
        }

        Value condition = and ? l.value : NegatedValue.negate(evaluationContext, l.value);
        EvaluationContext child = evaluationContext.child(condition);
        EvaluationResult r = rhs.evaluate(child, forward);
        if (r.value == constant) {
            builder.raiseError(Message.PART_OF_EXPRESSION_EVALUATES_TO_CONSTANT);
            return builder.compose(l, r).build();
        }
        ObjectFlow objectFlow = new ObjectFlow(evaluationContext.getLocation(),
                evaluationContext.getPrimitives().booleanParameterizedType, Origin.RESULT_OF_OPERATOR);
        if (and) {
            builder.setValue(new AndValue(primitives, objectFlow).append(evaluationContext, l.value, r.value));
        } else {
            builder.setValue(new OrValue(primitives, objectFlow).append(evaluationContext, l.value, r.value));
        }
        return builder.build();
    }

    private boolean allowsForNullOperands(Primitives primitives) {
        return operator == primitives.equalsOperatorInt ||
                operator == primitives.equalsOperatorObject ||
                operator == primitives.notEqualsOperatorObject ||
                operator == primitives.notEqualsOperatorInt ||
                operator == primitives.plusOperatorString;
    }

    @NotNull
    public static MethodInfo getOperator(@NotNull Primitives primitives,
                                         @NotNull @NotModified BinaryExpr.Operator operator,
                                         @NotModified TypeInfo widestType) {
        if (widestType == null || !Primitives.isPrimitiveExcludingVoid(widestType) && !Primitives.isBoxedExcludingVoid(widestType)) {
            if (operator == BinaryExpr.Operator.EQUALS) {
                return primitives.equalsOperatorObject;
            }
            if (operator == BinaryExpr.Operator.NOT_EQUALS) {
                return primitives.notEqualsOperatorObject;
            }
            if (widestType == primitives.stringTypeInfo && operator == BinaryExpr.Operator.PLUS) {
                return primitives.plusOperatorString;
            }
            throw new UnsupportedOperationException("? what else can you have on " + widestType + ", operator " + operator);
        }
        if (widestType == primitives.booleanTypeInfo || widestType.fullyQualifiedName.equals("java.lang.Boolean")) {
            switch (operator) {
                case OR:
                    return primitives.orOperatorBool;
                case AND:
                    return primitives.andOperatorBool;
                case EQUALS:
                    return primitives.equalsOperatorInt; // TODO should clean up
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt;
            }
            throw new UnsupportedOperationException("Operator " + operator + " on boolean");
        }
        if (widestType == primitives.charTypeInfo || widestType.fullyQualifiedName.equals("java.lang.Character")) {
            switch (operator) {
                case PLUS:
                    return primitives.plusOperatorInt;
                case MINUS:
                    return primitives.minusOperatorInt;
                case EQUALS:
                    return primitives.equalsOperatorInt; // TODO should clean up
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt;
            }
            throw new UnsupportedOperationException("Operator " + operator + " on char");
        }
        if (Primitives.isNumeric(widestType)) {
            switch (operator) {
                case MULTIPLY:
                    return primitives.multiplyOperatorInt;
                case REMAINDER:
                    return primitives.remainderOperatorInt;
                case DIVIDE:
                    return primitives.divideOperatorInt;
                case PLUS:
                    return primitives.plusOperatorInt;
                case MINUS:
                    return primitives.minusOperatorInt;
                case BINARY_OR:
                    return primitives.bitwiseOrOperatorInt;
                case BINARY_AND:
                    return primitives.bitwiseAndOperatorInt;
                case XOR:
                    return primitives.bitwiseXorOperatorInt;
                case UNSIGNED_RIGHT_SHIFT:
                    return primitives.unsignedRightShiftOperatorInt;
                case SIGNED_RIGHT_SHIFT:
                    return primitives.signedRightShiftOperatorInt;
                case LEFT_SHIFT:
                    return primitives.leftShiftOperatorInt;
                case GREATER:
                    return primitives.greaterOperatorInt;
                case GREATER_EQUALS:
                    return primitives.greaterEqualsOperatorInt;
                case LESS:
                    return primitives.lessOperatorInt;
                case LESS_EQUALS:
                    return primitives.lessEqualsOperatorInt;
                case EQUALS:
                    return primitives.equalsOperatorInt;
                case NOT_EQUALS:
                    return primitives.notEqualsOperatorInt;
            }
        }

        throw new UnsupportedOperationException("Unknown operator " + operator + " on widest type " +
                widestType.fullyQualifiedName);
    }

    public static MethodInfo fromAssignmentOperatorToNormalOperator(Primitives primitives, MethodInfo methodInfo) {
        if (primitives.assignOperatorInt == methodInfo) return null;
        if (primitives.assignPlusOperatorInt == methodInfo) return primitives.plusOperatorInt;
        if (primitives.assignMinusOperatorInt == methodInfo) return primitives.minusOperatorInt;
        if (primitives.assignMultiplyOperatorInt == methodInfo) return primitives.multiplyOperatorInt;
        if (primitives.assignDivideOperatorInt == methodInfo) return primitives.divideOperatorInt;
        if (primitives.assignOrOperatorBoolean == methodInfo) return primitives.orOperatorBool;

        throw new UnsupportedOperationException("TODO! " + methodInfo.distinguishingName());
    }

    public static int precedence(@NotNull Primitives primitives, @NotNull @NotModified MethodInfo methodInfo) {
        if (primitives.divideOperatorInt == methodInfo || primitives.remainderOperatorInt == methodInfo || primitives.multiplyOperatorInt == methodInfo) {
            return MULTIPLICATIVE_PRECEDENCE;
        }
        if (primitives.minusOperatorInt == methodInfo || primitives.plusOperatorInt == methodInfo || primitives.plusOperatorString == methodInfo) {
            return ADDITIVE_PRECEDENCE;
        }
        if (primitives.signedRightShiftOperatorInt == methodInfo || primitives.unsignedRightShiftOperatorInt == methodInfo || primitives.leftShiftOperatorInt == methodInfo) {
            return SHIFT_PRECEDENCE;
        }
        if (primitives.greaterEqualsOperatorInt == methodInfo || primitives.greaterOperatorInt == methodInfo || primitives.lessEqualsOperatorInt == methodInfo || primitives.lessOperatorInt == methodInfo) {
            return COMPARISON_PRECEDENCE;
        }
        if (primitives.equalsOperatorInt == methodInfo || primitives.equalsOperatorObject == methodInfo || primitives.notEqualsOperatorInt == methodInfo || primitives.notEqualsOperatorObject == methodInfo) {
            return EQUALITY_PRECEDENCE;
        }
        if (primitives.bitwiseAndOperatorInt == methodInfo) {
            return AND_PRECEDENCE;
        }
        if (primitives.bitwiseXorOperatorInt == methodInfo) {
            return XOR_PRECEDENCE;
        }
        if (primitives.bitwiseOrOperatorInt == methodInfo) {
            return OR_PRECEDENCE;
        }
        if (primitives.andOperatorBool == methodInfo) {
            return LOGICAL_AND_PRECEDENCE;
        }
        if (primitives.orOperatorBool == methodInfo) {
            return LOGICAL_OR_PRECEDENCE;
        }
        throw new UnsupportedOperationException("? unknown operator " + methodInfo.distinguishingName());
    }

    // TODO needs cleanup
    @Override
    public ParameterizedType returnType() {
        return operator.returnType();
    }

    @Override
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, lhs) + " " + operator.name + " " + bracketedExpressionString(indent, rhs);
    }

    @Override
    public int precedence() {
        return precedence;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(lhs, rhs);
    }
}
