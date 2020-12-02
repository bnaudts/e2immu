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
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.value.CharValue;
import org.e2immu.analyser.model.value.Instance;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

@E2Container
public record CharConstant(Primitives primitives,
                           char constant,
                           ObjectFlow objectFlow) implements ConstantExpression<Character> {

    public CharConstant(Primitives primitives, char constant) {
        this(primitives, constant, ObjectFlow.NO_FLOW);
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.charParameterizedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CharConstant that = (CharConstant) o;
        return constant == that.constant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    public String toString() {
        return "'" + constant + "'";
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_CHAR;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public Character getValue() {
        return constant;
    }
}
