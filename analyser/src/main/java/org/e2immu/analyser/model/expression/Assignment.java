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

import com.github.javaparser.ast.expr.AssignExpr;
import com.google.common.collect.Sets;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@E2Immutable
public class Assignment implements Expression {
    public final Expression target;
    public final Expression value;
    public final MethodInfo primitiveOperator;

    public Assignment(@NullNotAllowed Expression target, @NullNotAllowed Expression value) {
        this(target, value, null);
    }

    public Assignment(@NullNotAllowed Expression target, @NullNotAllowed Expression value,
                      MethodInfo primitiveOperator) {
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
        this.primitiveOperator = primitiveOperator;
    }

    @NotNull
    public static MethodInfo operator(@NullNotAllowed AssignExpr.Operator operator,
                                      @NullNotAllowed TypeInfo widestType) {
        // if (widestType == Primitives.PRIMITIVES.intTypeInfo || widestType == Primitives.PRIMITIVES.longTypeInfo) {
        switch (operator) {
            case PLUS:
                return Primitives.PRIMITIVES.assignPlusOperatorInt;
            case BINARY_OR:
                return Primitives.PRIMITIVES.assignOrOperatorBoolean;
            case ASSIGN:
                return Primitives.PRIMITIVES.assignOperatorInt;
        }
        /// }
        /*if (widestType == Primitives.PRIMITIVES.booleanTypeInfo) {
            switch (operator) {
                case ASSIGN:
                    return Primitives.PRIMITIVES.assignOperatorInt;
                    // TODO
            }
        }*/
        throw new UnsupportedOperationException("Need to add primitive operator " +
                operator + " on type " + widestType.fullyQualifiedName);
    }

    @Override
    public ParameterizedType returnType() {
        return target.returnType();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        String operator = primitiveOperator != null && primitiveOperator != Primitives.PRIMITIVES.assignOperatorInt ? "=" + primitiveOperator.name : "=";
        return target.expressionString(indent) + " " + operator + " " + value.expressionString(indent);
    }

    @Override
    public int precedence() {
        return 1; // lowest precedence
    }

    @Override
    @NotNull
    public Set<String> imports() {
        return Sets.union(target.imports(), value.imports());
    }

    @Override
    @NotNull
    public List<Expression> subExpressions() {
        return List.of(target, value);
    }

    @Override
    public Optional<Variable> assignmentTarget() {
        return target.assignmentTarget();
    }

    @Override
    public Variable variableFromExpression() {
        return target.variableFromExpression();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        if (target instanceof FieldAccess) {
            return SideEffect.SIDE_EFFECT;
        }
        return SideEffect.LOCAL;
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return target.variables();
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        Value v = value.evaluate(evaluationContext, visitor);
        visitor.visit(this, evaluationContext, v);
        return v;
    }
}
