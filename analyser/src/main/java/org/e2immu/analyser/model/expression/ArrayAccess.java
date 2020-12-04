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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Only not evaluated. Replaced by VariableExpression
 */
@E2Container
public class ArrayAccess implements Expression {

    public final Expression expression;
    public final Expression index;
    public final DependentVariable variableTarget; // can be null
    public final ParameterizedType returnType;

    public ArrayAccess(@NotNull Expression expression, @NotNull Expression index) {
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
        this.returnType = expression.returnType().copyWithOneFewerArrays();
        variableTarget = arrayAccessVariableTarget(expression, index, returnType);
    }

    private static DependentVariable arrayAccessVariableTarget(Expression expression, Expression index, ParameterizedType returnType) {
        Variable arrayVariable = singleVariable(expression);
        Variable indexVariable = singleVariable(index);
        String name = (arrayVariable == null ? expression.minimalOutput() : arrayVariable.fullyQualifiedName())
                + "[" + (indexVariable == null ? index.minimalOutput() : indexVariable.fullyQualifiedName()) + "]";
        return new DependentVariable(name, returnType, indexVariable == null ? List.of() : List.of(indexVariable), arrayVariable);
    }

    private static Variable singleVariable(Expression expression) {
        if (expression instanceof VariableExpression variableExpression) {
            return variableExpression.variable();
        }
        if (expression instanceof FieldAccess fieldAccess) {
            return fieldAccess.variable();
        }
        return null;
    }

    @Override
    public boolean hasBeenEvaluated() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayAccess that = (ArrayAccess) o;
        return expression.equals(that.expression) &&
                index.equals(that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, index);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayAccess(translationMap.translateExpression(expression), translationMap.translateExpression(index));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NYE;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        throw new UnsupportedOperationException("Not yet evaluated");
    }

    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(outputInParenthesis(precedence(), expression))
                .add(Symbol.LEFT_BRACKET).add(index.output()).add(Symbol.RIGHT_BRACKET);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression, index);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult array = expression.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult indexValue = index.evaluate(evaluationContext, ForwardEvaluationInfo.NOT_NULL);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(array, indexValue);

        if (array.value instanceof ArrayInitializer arrayValue && indexValue.value instanceof Numeric in) {
            // known array, known index (a[] = {1,2,3}, a[2] == 3)
            int intIndex = in.getNumber().intValue();
            if (intIndex < 0 || intIndex >= arrayValue.multiExpression.expressions().length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            builder.setExpression(arrayValue.multiExpression.expressions()[intIndex]);
        } else {
            // we have to make an effort to see if we can evaluate the components; maybe there's another variable to be had
            Variable arrayVariable = variableTarget == null ? null : variableTarget.arrayVariable;
            if (array.value instanceof VariableExpression evaluatedArrayValue) {
                arrayVariable = evaluatedArrayValue.variable();
            }
            String index = indexValue.value.toString();
            String name = (arrayVariable != null ? arrayVariable.fullyQualifiedName() : array.value.toString()) + "[" + index + "]";
            DependentVariable dependentVariable = new DependentVariable(name, returnType(),
                    variableTarget != null ? variableTarget.dependencies : List.of(), arrayVariable);

            if (dependentVariable.arrayVariable != null) {
                builder.variableOccursInNotNullContext(dependentVariable.arrayVariable, array.value, MultiLevel.EFFECTIVELY_NOT_NULL);
            }
            if (forwardEvaluationInfo.isNotAssignmentTarget()) {
                builder.markRead(variableTarget, evaluationContext.getIteration());
                Expression variableValue = builder.currentExpression(dependentVariable);
                builder.setExpression(variableValue);
            } else {
                builder.setExpression(new VariableExpression(dependentVariable));
            }
        }

        int notNullRequired = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
        if (notNullRequired > MultiLevel.NULLABLE && builder.getExpression() instanceof VariableExpression e) {
            builder.variableOccursInNotNullContext(e.variable(), builder.getExpression(), notNullRequired);
        }
        return builder.build();
    }
}
