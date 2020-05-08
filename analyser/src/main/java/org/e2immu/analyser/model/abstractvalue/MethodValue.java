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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodValue implements Value {
    public final MethodInfo methodInfo;
    public final List<Value> parameters;
    public final Value object;

    public MethodValue(@NotNull MethodInfo methodInfo, @NotNull Value object, @NotNull List<Value> parameters) {
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.parameters = Objects.requireNonNull(parameters);
        this.object = Objects.requireNonNull(object);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodValue that = (MethodValue) o;
        return methodInfo.equals(that.methodInfo) &&
                parameters.equals(that.parameters) &&
                object.equals(that.object) &&
                methodInfo.sideEffect().atMost(SideEffect.NONE_CONTEXT);
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, methodInfo, parameters);
    }

    @Override
    public String toString() {
        return methodInfo.fullyQualifiedName()
                + parameters.stream().map(Object::toString).collect(Collectors.joining(", ", "(", ")"));
    }

    @Override
    public int compareTo(Value o) {
        if (o == UnknownValue.UNKNOWN_VALUE) return -1;
        if (o instanceof MethodValue) {
            MethodValue mv = (MethodValue) o;
            int c = methodInfo.fullyQualifiedName().compareTo(mv.methodInfo.fullyQualifiedName());
            if (c != 0) return c;
            int i = 0;
            while (i < parameters.size()) {
                if (i >= mv.parameters.size()) return 1;
                c = parameters.get(i).compareTo(mv.parameters.get(i));
                if (c != 0) return c;
                i++;
            }
            return object.compareTo(mv.object);
        }
        if (o instanceof NegatedValue) {
            NegatedValue negatedValue = (NegatedValue) o;
            return compareTo(negatedValue.value);
        }
        return 1;
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return methodInfo.methodAnalysis.getProperty(variableProperty);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        boolean recursiveCall = methodInfo == evaluationContext.getCurrentMethod();
        if (recursiveCall) {
            return variableProperty.best;
        }
        switch (variableProperty) {
            case NOT_NULL:
            case IMMUTABLE:
            case CONTAINER:
                int identity = methodInfo.methodAnalysis.getProperty(VariableProperty.IDENTITY);
                if (identity == Level.DELAY) return Level.DELAY;
                int firstParameter;
                if (identity == Level.TRUE) {
                    Value valueFirst = parameters.get(0);
                    firstParameter = valueFirst.getProperty(evaluationContext, VariableProperty.NOT_NULL);
                } else {
                    firstParameter = Level.FALSE;
                }
                int fluent = methodInfo.methodAnalysis.getProperty(VariableProperty.FLUENT);
                if (fluent == Level.DELAY) return Level.DELAY;
                int valueOfType;
                if (fluent == Level.TRUE) {
                    valueOfType = methodInfo.typeInfo.typeAnalysis.getProperty(variableProperty);
                } else {
                    valueOfType = Level.FALSE;
                }
                int valueOfMethod = methodInfo.methodAnalysis.getProperty(variableProperty);
                if (valueOfMethod == Level.DELAY) return Level.DELAY;
                return Level.best(valueOfType, Level.best(firstParameter, valueOfMethod));

            default:
        }
        return methodInfo.methodAnalysis.getProperty(variableProperty);
    }

    /* We're in the situation of a = b.method(c, d), and we are computing the variables that `a` will be linked
     * to. There is no need to consider linking between `b`, `c` and `d` here because that linking takes place in the method's
     * definition itself. We consider 4 cases:
     *
     * 1. a is primitive or e2immutable: independent
     * 2. method is @Independent: independent (the very definition)
     * 3. b is @E2Immutable: only dependent on c, d
     *
     * Note that a dependence on a parameter is only possible when it is not primitive or @E2Immutable (see VariableValue).
     * On top of that comes the situation where the analyser has more detailed information than is in the annotations.
     * For now, we decide to ignore such information.
     *
     */

    private static final Set<Variable> INDEPENDENT = Set.of();

    @Override
    public Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        TypeContext typeContext = evaluationContext.getTypeContext();

        // RULE 1
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType == Primitives.PRIMITIVES.voidParameterizedType) return INDEPENDENT; // no assignment
        if (returnType.isPrimitiveOrStringNotVoid()) return INDEPENDENT;

        boolean returnTypeDifferent = returnType.typeInfo != evaluationContext.getCurrentType();
        if ((bestCase || returnTypeDifferent) && (returnType.bestTypeInfo() == null ||
                Level.value(returnType.bestTypeInfo().typeAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE) == Level.TRUE)) {
            return INDEPENDENT;
        }

        // RULE 2
        boolean methodInfoDifferentType = methodInfo.typeInfo != evaluationContext.getCurrentType();
        if ((bestCase || methodInfoDifferentType) && methodInfo.methodAnalysis.getProperty(VariableProperty.INDEPENDENT) == Level.TRUE) {
            return INDEPENDENT;
        }

        // some prep.

        Set<Variable> result = new HashSet<>();
        parameters.forEach(p -> result.addAll(p.linkedVariables(bestCase, evaluationContext)));

        // RULE 3
        if ((bestCase || methodInfoDifferentType) &&
                methodInfo.typeInfo.isE2Immutable(typeContext) == Boolean.TRUE) // RULE 3
            return result;

        // default case, add b
        result.addAll(object.linkedVariables(bestCase, evaluationContext));

        return result;
    }

    @Override
    public ParameterizedType type() {
        return methodInfo.returnType();
    }
}
