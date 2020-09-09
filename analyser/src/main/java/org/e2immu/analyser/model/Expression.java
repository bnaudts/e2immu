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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

// at the moment we're modifying evaluation context, we want to be @E2Container
public interface Expression extends Element {

    @NotModified
    ParameterizedType returnType();

    @NotModified
    String expressionString(int indent);

    @NotModified
    int precedence();

    @NotModified
    Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo);

    @NotModified
    @E2Container
    default Set<String> imports() {
        return Set.of();
    }

    default Set<TypeInfo> typesReferenced() {
        return Set.of();
    }

    @NotModified
    default String bracketedExpressionString(int indent, Expression expression) {
        if (expression.precedence() < precedence()) {
            return "(" + expression.expressionString(indent) + ")";
        }
        return expression.expressionString(indent);
    }

    /**
     * @return variables in the scope side of THIS expression (not sub-expressions!)
     */
    @NotModified
    default List<Variable> variablesInScopeSide() {
        return List.of();
    }

    @NotModified
    default List<LocalVariableReference> newLocalVariables() {
        return List.of();
    }

    @NotModified
    default Optional<Variable> assignmentTarget() {
        throw new UnsupportedOperationException("Class is " + getClass());
    }

    @Override
    default Expression translate(TranslationMap translationMap) {
        return this;
    }

}
