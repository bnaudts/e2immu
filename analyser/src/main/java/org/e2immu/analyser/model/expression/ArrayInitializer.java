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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@E2Container
public class ArrayInitializer implements Expression {
    public final List<Expression> expressions;
    public final ParameterizedType commonType;

    public ArrayInitializer(@NotNull @NotModified List<Expression> expressions) {
        this.expressions = Objects.requireNonNull(expressions);
        commonType = commonType(expressions);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new ArrayInitializer(expressions.stream().map(translationMap::translateExpression)
                .collect(Collectors.toList()));
    }

    @Override
    public ParameterizedType returnType() {
        return commonType;
    }

    private static ParameterizedType commonType(List<Expression> expressions) {
        ParameterizedType commonType = null;
        for (Expression expression : expressions) {
            if (expression != NullConstant.NULL_CONSTANT) {
                ParameterizedType parameterizedType = expression.returnType();
                if (commonType == null) commonType = parameterizedType;
                else commonType = commonType.commonType(parameterizedType);
            }
        }
        return commonType == null ? Primitives.PRIMITIVES.objectParameterizedType : commonType;
    }

    @Override
    public String expressionString(int indent) {
        return expressions.stream().map(e -> e.expressionString(indent)).collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public int precedence() {
        return 1;
    }

    @Override
    public List<? extends Element> subElements() {
        return expressions;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        List<EvaluationResult> results = expressions.stream().map(e -> e.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT)).collect(Collectors.toList());
        List<Value> values = results.stream().map(EvaluationResult::getValue).collect(Collectors.toList());

        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(results);
        ObjectFlow objectFlow = builder.createLiteralObjectFlow(commonType);
        builder.setValue(new ArrayValue(objectFlow, values));

        return builder.build();
    }
}
