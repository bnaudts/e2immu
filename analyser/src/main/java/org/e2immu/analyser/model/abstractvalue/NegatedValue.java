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

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.NumericValue;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class NegatedValue extends PrimitiveValue {
    public static NegatedValue NOT_NULL = new NegatedValue(NullValue.NULL_VALUE);

    public final Value value;

    private NegatedValue(@NotNull Value value) {
        this.value = Objects.requireNonNull(value);
    }

    public Value reEvaluate(Map<Value, Value> translation) {
        Value reValue = value.reEvaluate(translation);
        return NegatedValue.negate(reValue);
    }

    public static Value negate(@NotNull Value v) {
        Objects.requireNonNull(v);
        if (v instanceof BoolValue) {
            BoolValue boolValue = (BoolValue) v;
            return boolValue.value ? BoolValue.FALSE : BoolValue.TRUE;
        }
        if (v instanceof NumericValue) {
            return ((NumericValue) v).negate();
        }
        if (v.isUnknown()) return v;

        if (v instanceof NegatedValue) return ((NegatedValue) v).value;
        if (v instanceof OrValue) {
            OrValue or = (OrValue) v;
            Value[] negated = or.values.stream().map(x -> negate(x)).toArray(Value[]::new);
            return new AndValue().append(negated);
        }
        if (v instanceof AndValue) {
            AndValue and = (AndValue) v;
            List<Value> negated = and.values.stream().map(x -> negate(x)).collect(Collectors.toList());
            return new OrValue().append(negated);
        }
        if (v instanceof EqualsValue) {
            EqualsValue equalsValue = (EqualsValue) v;
            if (equalsValue.lhs instanceof NumericValue && equalsValue.rhs instanceof ConstrainedNumericValue) {
                Value improve = ((ConstrainedNumericValue) equalsValue.rhs).notEquals((NumericValue) equalsValue.lhs);
                if (improve != null) return improve;
            }
        }
        if (v instanceof SumValue) {
            return ((SumValue) v).negate();
        }
        if (v instanceof GreaterThanZeroValue) {
            return ((GreaterThanZeroValue) v).negate();
        }
        return new NegatedValue(v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NegatedValue that = (NegatedValue) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "not (" + value + ")";
    }

    @Override
    public int order() {
        return ORDER_NEGATED;
    }

    @Override
    public int internalCompareTo(Value v) {
        return value.compareTo(((NegatedValue) v).value);
    }

    @Override
    public Map<Variable, Boolean> individualNullClauses() {
        Map<Variable, Boolean> individualNullClauses = value.individualNullClauses();
        return individualNullClauses.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, e -> !e.getValue()));
    }

    @Override
    public Map<Variable, Value> individualSizeRestrictions() {
        return value.individualSizeRestrictions().entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, e -> NegatedValue.negate(e.getValue())));
    }

    @Override
    public int encodedSizeRestriction() {
        int sub = value.encodedSizeRestriction();
        if (sub == Analysis.SIZE_EMPTY) return Analysis.SIZE_NOT_EMPTY; // ==0 becomes >= 1
        if (sub == Analysis.SIZE_NOT_EMPTY) return Analysis.SIZE_EMPTY; // >=1 becomes == 0
        return 0; // not much we can do >=0 stays like that , ==5 cannot be replaced by sth else
    }

    @Override
    public Set<Variable> variables() {
        return value.variables();
    }

    @Override
    public ParameterizedType type() {
        return value.type();
    }

    @Override
    public boolean isExpressionOfParameters() {
        return value.isExpressionOfParameters();
    }
}
