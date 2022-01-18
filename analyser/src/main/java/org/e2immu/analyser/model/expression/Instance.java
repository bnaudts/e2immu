/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public final class Instance extends BaseExpression implements Expression {
    private final ParameterizedType parameterizedType;
    private final Properties valueProperties;

    public static Expression forUnspecifiedLoopCondition(String index, Primitives primitives) {
        return new Instance(Identifier.loopCondition(index), primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Expression genericFieldAccess(InspectionProvider inspectionProvider, FieldInfo fieldInfo,
                                                Properties valueProperties) {
        return new Instance(Identifier.generate(),
                fieldInfo.owner.asParameterizedType(inspectionProvider),
                valueProperties);
    }

    // IMPROVE should this not be delayed?
    public static Instance forInlinedMethod(Identifier identifier,
                                            ParameterizedType parameterizedType) {
        return new Instance(identifier, parameterizedType,
                Properties.of(Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        Property.IMMUTABLE, MultiLevel.MUTABLE_DV,
                        Property.INDEPENDENT, MultiLevel.DEPENDENT_DV,
                        Property.CONTAINER, DV.FALSE_DV,
                        Property.IDENTITY, DV.FALSE_DV)));
    }

    public static Expression forMethodResult(Identifier identifier,
                                             ParameterizedType parameterizedType,
                                             Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Expression forUnspecifiedCatchCondition(String index, Primitives primitives) {
        return new Instance(Identifier.catchCondition(index),
                primitives.booleanParameterizedType(),
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public static Instance forTesting(ParameterizedType parameterizedType) {
        return new Instance(Identifier.generate(), parameterizedType,
                EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    // never null, never more interesting.
    public static Instance forCatchOrThis(String index, Variable variable, AnalysisProvider analysisProvider) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(VariableIdentifier.variable(variable, index), parameterizedType,
                Properties.of(Map.of(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        Property.IMMUTABLE, defaultImmutable(parameterizedType, analysisProvider),
                        Property.INDEPENDENT, defaultIndependent(parameterizedType, analysisProvider),
                        Property.CONTAINER, defaultContainer(parameterizedType, analysisProvider),
                        Property.IDENTITY, DV.FALSE_DV)));
    }

    private static DV defaultIndependent(ParameterizedType parameterizedType, AnalysisProvider analysisProvider) {
        DV v = analysisProvider.defaultIndependent(parameterizedType);
        return v.replaceDelayBy(MultiLevel.DEPENDENT_DV);
    }

    private static DV defaultImmutable(ParameterizedType parameterizedType, AnalysisProvider analysisProvider) {
        DV v = analysisProvider.defaultImmutable(parameterizedType, false);
        return v.replaceDelayBy(MultiLevel.MUTABLE_DV);
    }

    private static DV defaultContainer(ParameterizedType parameterizedType, AnalysisProvider analysisProvider) {
        DV v = analysisProvider.defaultContainer(parameterizedType);
        return v.replaceDelayBy(DV.FALSE_DV);
    }

    public static Instance forLoopVariable(String index, Variable variable, Properties valueProperties) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new Instance(VariableIdentifier.variable(variable, index),
                parameterizedType, valueProperties);
    }

    /*
    not-null always in properties
     */
    public static Instance initialValueOfParameter(ParameterInfo parameterInfo, Properties valueProperties) {
        return new Instance(VariableIdentifier.variable(parameterInfo), parameterInfo.parameterizedType,
                valueProperties);
    }

    // null-status derived from variable in evaluation context
    public static Instance genericMergeResult(String index, Variable variable, Properties valueProperties) {
        return new Instance(VariableIdentifier.variable(variable, index), variable.parameterizedType(),
                valueProperties);
    }

    public static Expression genericArrayAccess(Identifier identifier,
                                                EvaluationContext evaluationContext,
                                                Expression array,
                                                Variable variable) {
        DV notNull = evaluationContext.getProperty(array, Property.NOT_NULL_EXPRESSION, true, false);
        DV notNullOfElement = MultiLevel.composeOneLevelLessNotNull(notNull);

        // we need to go the base type of the array
        ParameterizedType baseType = array.returnType().copyWithOneFewerArrays();
        Properties properties = evaluationContext.getAnalyserContext().defaultValueProperties(baseType, notNullOfElement);
        CausesOfDelay delays = properties.delays();
        if (delays.isDelayed()) {
            Stream<Variable> variableStream = Stream.concat(Stream.of(variable), array.variables(true).stream());
            return DelayedExpression.forArrayAccessValue(variable.parameterizedType(),
                    LinkedVariables.sameValue(variableStream, delays), delays);
        }
        return new Instance(identifier, variable.parameterizedType(), properties);
    }

    /*
   getInstance is used by MethodCall to enrich an instance with state.

   cannot be null, we're applying a method on it.
    */
    public static Instance forGetInstance(Identifier identifier,
                                          ParameterizedType parameterizedType,
                                          Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Instance forVariableInLoopDefinedOutside(Identifier identifier,
                                                           ParameterizedType parameterizedType,
                                                           Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Instance forTooComplex(Identifier identifier,
                                         ParameterizedType parameterizedType,
                                         Properties valueProperties) {
        return new Instance(identifier, parameterizedType, valueProperties);
    }

    public static Instance forTooComplex(Identifier identifier,
                                         ParameterizedType parameterizedType) {
        return new Instance(identifier, parameterizedType, EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
    }

    public Instance(Identifier identifier,
                    ParameterizedType parameterizedType,
                    Properties valueProperties) {
        super(identifier);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.valueProperties = valueProperties;
        assert internalChecks();
    }

    public static Instance forField(FieldInfo fieldInfo,
                                    ParameterizedType type,
                                    DV notNull, DV immutable, DV container, DV independent) {
        return new Instance(fieldInfo.getIdentifier(), type == null ? fieldInfo.type : type, Properties.of(Map.of(
                Property.NOT_NULL_EXPRESSION, notNull,
                Property.IMMUTABLE, immutable,
                Property.CONTAINER, container,
                Property.INDEPENDENT, independent,
                Property.IDENTITY, DV.FALSE_DV)));
    }

    private boolean internalChecks() {
        assert EvaluationContext.VALUE_PROPERTIES.stream().allMatch(valueProperties::containsKey) :
                "Value properties missing! " + valueProperties;
        assert valueProperties.stream()
                .filter(e -> EvaluationContext.VALUE_PROPERTIES.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .noneMatch(DV::isDelayed) : "Properties: " + valueProperties;
        assert !parameterizedType.isJavaLangString() || valueProperties.get(Property.CONTAINER).valueIsTrue();
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        return identifier.equals(instance.identifier) && parameterizedType.equals(instance.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, parameterizedType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Instance(identifier, translationMap.translateType(parameterizedType), valueProperties);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE;
    }


    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return LinkedVariables.EMPTY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if(!(v instanceof Instance)) {
            return 1; // we're at the back; Instance is used as "too complex" in boolean expressions
        }
        return parameterizedType.detailedString()
                .compareTo(((Instance) v).parameterizedType.detailedString());
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return switch (property) {
            case IDENTITY, IMMUTABLE, NOT_NULL_EXPRESSION, CONTAINER, INDEPENDENT -> valueProperties.get(property);
            case CONTEXT_MODIFIED, IGNORE_MODIFICATIONS -> DV.FALSE_DV;
            default -> throw new UnsupportedOperationException("NewObject has no value for " + property);
        };
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        predicate.test(this);
    }

    @Override
    public boolean isNumeric() {
        return parameterizedType.isType() && parameterizedType.typeInfo.isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();

        Text text = new Text(text() + "instance type " + parameterizedType.printSimple());
        outputBuilder.add(text);

        // TODO not consistent, but hack after changing 10s of tests, don't want to change back again
        if (valueProperties.getOrDefault(Property.IDENTITY, DV.FALSE_DV).valueIsTrue()) {
            outputBuilder.add(new Text("/*@Identity*/"));
        }
        return outputBuilder;
    }

    private String text() {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType != null && bestType.isPrimitiveExcludingVoid()) return "";
        DV minimalNotNull = valueProperties.getOrDefault(Property.NOT_NULL_EXPRESSION, MultiLevel.NULLABLE_DV);
        if (minimalNotNull.lt(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return "nullable ";
        return "";
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(parameterizedType.typesReferenced(true));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of();
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public Identifier identifier() {
        return identifier;
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public Properties valueProperties() {
        return valueProperties;
    }

}
