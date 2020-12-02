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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;


public class Divide extends BinaryOperator {
    private final Primitives primitives;

    private Divide(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(lhs, primitives.divideOperatorInt, rhs, BinaryOperator.MULTIPLICATIVE_PRECEDENCE, objectFlow);
        this.primitives = primitives;
    }

    public static EvaluationResult divide(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        if (l instanceof Numeric ln && ln.doubleValue() == 0) return builder.setExpression(l).build();
        if (r instanceof Numeric rn && rn.doubleValue() == 0) {
            builder.raiseError(Message.DIVISION_BY_ZERO);
            return builder.setExpression(l).build();
        }
        if (r instanceof Numeric rn && rn.doubleValue() == 1) return builder.setExpression(l).build();
        Primitives primitives = evaluationContext.getPrimitives();

        if (l instanceof IntConstant li && r instanceof IntConstant ri)
            return builder.setExpression(new IntConstant(primitives, li.constant() / ri.constant(), objectFlow)).build();

        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) {
            return builder.setExpression(PrimitiveExpression.PRIMITIVE_EXPRESSION).build();
        }

        return builder.setExpression(new Divide(primitives, l, r, objectFlow)).build();
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Divide orValue = (Divide) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return lhs.print(printMode) + " / " + rhs.print(printMode);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_DIVIDE;
    }

    @Override
    public ParameterizedType type() {
        return primitives.widestType(lhs.type(), rhs.type());
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return PrimitiveExpression.primitiveGetProperty(variableProperty);
    }
}
