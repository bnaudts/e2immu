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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.ValueComparator;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared properties: @NotNull(n), dynamic type properties (@Immutable(n), @Container)
 * Properties of variables are ALWAYS computed inside an evaluation context; properties of methods come from outside the scope only.
 */
public interface Value extends Comparable<Value> {
    int ORDER_CONSTANT_NULL = 30;
    int ORDER_CONSTANT_BOOLEAN = 31;
    int ORDER_CONSTANT_BYTE = 32;
    int ORDER_CONSTANT_CHAR = 33;
    int ORDER_CONSTANT_SHORT = 34;
    int ORDER_CONSTANT_INT = 35;
    int ORDER_CONSTANT_FLOAT = 36;
    int ORDER_CONSTANT_LONG = 37;
    int ORDER_CONSTANT_DOUBLE = 38;
    int ORDER_CONSTANT_CLASS = 39;
    int ORDER_CONSTANT_STRING = 40;
    int ORDER_PRODUCT = 41;
    int ORDER_DIVIDE = 42;
    int ORDER_REMAINDER = 43;
    int ORDER_SUM = 44;
    int ORDER_BITWISE_AND = 45;

    // variables, types
    int ORDER_PRIMITIVE = 60;
    int ORDER_ARRAY = 61;
    int ORDER_CONSTRAINED_NUMERIC_VALUE = 62;
    int ORDER_INSTANCE = 63;
    int ORDER_INLINE_METHOD = 64;
    int ORDER_METHOD = 65;
    int ORDER_VARIABLE_VALUE = 66;
    int ORDER_COMBINED = 67;
    int ORDER_TYPE = 68;
    int ORDER_NO_VALUE = 69;
    int ORDER_CONDITIONAL = 70;
    int ORDER_ALT_ASSIGNMENT = 71;

    // boolean operations
    int ORDER_INSTANCE_OF = 81;
    int ORDER_EQUALS = 82;
    int ORDER_GEQ0 = 83;
    int ORDER_OR = 85;
    int ORDER_AND = 86;

    int order();

    default boolean isNumeric() {
        return false;
    }

    @Override
    default int compareTo(Value v) {
        return ValueComparator.SINGLETON.compare(this, v);
    }

    default int internalCompareTo(Value v) {
        return 0;
    }

    default boolean isConstant() {
        return false;
    }

    default boolean isUnknown() {
        return false;
    }

    default boolean hasConstantProperties() {
        return true;
    }

    default boolean isDiscreteType() {
        ParameterizedType type = type();
        return type != null && type.isDiscrete();
    }

    // executed without context, default for all constant types
    default int getPropertyOutsideContext(VariableProperty variableProperty) {
        if (VariableProperty.DYNAMIC_TYPE_PROPERTY.contains(variableProperty)) return variableProperty.best;
        if (VariableProperty.NOT_NULL == variableProperty)
            return MultiLevel.EFFECTIVELY_NOT_NULL; // constants are not null
        if (VariableProperty.FIELD_AND_METHOD_PROPERTIES.contains(variableProperty)) return Level.DELAY;

        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    // only called from EvaluationContext.getProperty().
    // Use that method as the general way of obtaining a value for a property from a Value object
    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return getPropertyOutsideContext(variableProperty);
    }

    default IntValue toInt() {
        throw new UnsupportedOperationException(this.getClass().toString());
    }

    @NotModified
    default Set<Variable> linkedVariables(boolean bestCase, EvaluationContext evaluationContext) {
        return Set.of();
    }

    default FilterResult isIndividualNotNullClauseOnParameter() {
        return new FilterResult(Map.of(), this);
    }

    // NOTE: contrary to the SizeRestriction and the FieldCondition, this one stores not the whole equality, but
    // only the NullValue in the Map.
    default FilterResult isIndividualNotNullClause() {
        return new FilterResult(Map.of(), this);
    }

    default FilterResult isIndividualFieldCondition() {
        Set<Variable> variables = variables();
        if (variables.size() == 1 && variables.stream().allMatch(v -> v instanceof FieldReference)) {
            return new FilterResult(Map.of(variables.stream().findAny().orElseThrow(), this), UnknownValue.NO_VALUE);
        }
        return new FilterResult(Map.of(), this);
    }

    default FilterResult isIndividualSizeRestrictionOnParameter() {
        return new FilterResult(Map.of(), this);
    }

    default FilterResult isIndividualSizeRestriction() {
        return new FilterResult(Map.of(), this);
    }

    class FilterResult {
        public final Map<Variable, Value> accepted;
        public final Value rest;

        public FilterResult(Map<Variable, Value> accepted, Value rest) {
            this.accepted = accepted;
            this.rest = rest;
        }
    }

    @FunctionalInterface
    interface FilterMethod {
        FilterResult apply(Value value);
    }

    enum FilterMode {
        ALL,
        ACCEPT, // normal state of the variable AFTER the escape; independent = AND; does not recurse into OrValues
        REJECT, // condition for escaping; independent = OR; does not recurse into AndValues
    }

    /**
     * @param filterMode    mode for filtering
     * @param filterMethods if multiple accepted, the map contains the first result. (It should contain an AND, but see null clause)
     * @return a FilterResult object, always, if only NO_RESULT
     */
    default FilterResult filter(FilterMode filterMode, FilterMethod... filterMethods) {
        return filterMethods[0].apply(this);
    }

    /**
     * @return the type, if we are certain; used in WidestType for operators
     */
    default ParameterizedType type() {
        return null;
    }

    // HELPERS, NO NEED TO IMPLEMENT

    default Set<Variable> variables() {
        return Set.of();
    }

    default int encodedSizeRestriction() {
        return Level.FALSE;
    }

    default Value reEvaluate(EvaluationContext evaluationContext, Map<Value, Value> translation) {
        Value inMap = translation.get(this);
        return inMap == null ? this : inMap;
    }

    default boolean isExpressionOfParameters() {
        return false;
    }

    ObjectFlow getObjectFlow();

    default void visit(Consumer<Value> consumer) {
        consumer.accept(this);
    }
}
