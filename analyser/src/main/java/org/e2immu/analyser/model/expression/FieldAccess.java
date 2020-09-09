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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;

import java.util.*;

@E2Immutable
public class FieldAccess implements Expression {
    public final Expression expression;
    public final Variable variable;

    public FieldAccess(Expression expression, Variable variable) {
        this.variable = Objects.requireNonNull(variable);
        this.expression = Objects.requireNonNull(expression);
    }

    public static Expression orElse(Expression expression, Expression alternative) {
        return expression == null ? alternative : expression;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new FieldAccess(translationMap.translateExpression(expression), translationMap.translateVariable(variable));
    }

    @Override
    public ParameterizedType returnType() {
        return variable.concreteReturnType();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, expression) + "." + variable.name();
    }

    @Override
    public int precedence() {
        return 16;
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return expression.variables();
    }

    @Override
    @NotNull
    public Optional<Variable> assignmentTarget() {
        return Optional.of(variable);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value currentValue = evaluationContext.currentValue(variable);
        VariableExpression.processVariable(variable, currentValue, evaluationContext, forwardEvaluationInfo);

        Value scope = expression.evaluate(evaluationContext, visitor, forwardEvaluationInfo.copyModificationEnsureNotNull());
        if (scope != UnknownValue.NO_VALUE) {
            if (scope instanceof NullValue) {
                evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
            } else {
                int notNull = evaluationContext.getProperty(scope, VariableProperty.NOT_NULL);
                if (notNull == MultiLevel.DELAY) {
                    if (!MultiLevel.isEffectivelyNotNull(notNull)) {
                        evaluationContext.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION, "Scope " + scope);
                    }
                }
            }
        }
        visitor.visit(this, evaluationContext, currentValue);
        return currentValue;
    }
}
