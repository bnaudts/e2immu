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
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.model.abstractvalue.UnknownPrimitiveValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ArrayLengthExpression implements Expression {

    public final Expression scope;

    public ArrayLengthExpression(@NotNull Expression scope) {
        this.scope = Objects.requireNonNull(scope);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayLengthExpression(translationMap.translateExpression(scope));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(scope);
    }

    @Override
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.intParameterizedType;
    }

    @Override
    public String expressionString(int indent) {
        return "length";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        Value v = scope.evaluate(evaluationContext, visitor, ForwardEvaluationInfo.NOT_NULL);
        Value result;
        if (v instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) v;
            result = new IntValue(arrayValue.values.size());
        } else {
            result = UnknownPrimitiveValue.UNKNOWN_PRIMITIVE;
        }
        visitor.visit(this, evaluationContext, result);
        return result;
    }
}
