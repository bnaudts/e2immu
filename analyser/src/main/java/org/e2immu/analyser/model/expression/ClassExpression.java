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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.ClassValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClassExpression implements Expression, Constant<ParameterizedType> {
    @NotNull
    public final ParameterizedType parameterizedType;
    @NotNull
    public final ParameterizedType parameterizedClassType;

    public ClassExpression(@NotNull ParameterizedType parameterizedType) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterizedClassType = new ParameterizedType(Primitives.PRIMITIVES.classTypeInfo, List.of(parameterizedType));
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        return new ClassValue(parameterizedType);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedClassType;
    }

    @Override
    public String expressionString(int indent) {
        return parameterizedType.stream() + ".class";
    }

    @Override
    public int precedence() {
        return 17;
    }

    @Override
    public Set<String> imports() {
        if (parameterizedType.typeInfo != null) return Set.of(parameterizedType.typeInfo.fullyQualifiedName);
        return Set.of();
    }

    @Override
    public ParameterizedType getValue() {
        return parameterizedClassType;
    }
}
