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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class CommonTest {
    protected final TypeMapImpl.Builder typeMapBuilder = new TypeMapImpl.Builder(new Resources(), false);

    protected final Primitives primitives = typeMapBuilder.getPrimitives();
    protected final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);
    protected final AnalysisProvider analysisProvider = AnalysisProvider.DEFAULT_PROVIDER;
    protected final ForwardEvaluationInfo onlySort = new ForwardEvaluationInfo.Builder().setOnlySort(true).build();

    protected final TypeInfo recursivelyImmutable = new TypeInfo("com.foo", "RecursivelyImmutable");
    protected final ParameterizedType recursivelyImmutablePt = new ParameterizedType(recursivelyImmutable, List.of());

    protected final TypeInfo immutableHC = new TypeInfo("com.foo", "ImmutableHC");
    protected final ParameterizedType immutableHCPt = new ParameterizedType(immutableHC, List.of());

    protected final TypeInfo finalFields = new TypeInfo("com.foo", "FinalFields");
    protected final ParameterizedType finalFieldsPt = new ParameterizedType(finalFields, List.of());

    protected final TypeInfo mutable = new TypeInfo("com.foo", "Mutable");
    protected final ParameterizedType mutablePt = new ParameterizedType(mutable, List.of());

    protected final TypeInfo mutable2 = new TypeInfo("com.foo", "Mutable2");
    protected final ParameterizedType mutable2Pt = new ParameterizedType(mutable2, List.of());

    protected final TypeInfo immutableDelayed = new TypeInfo("com.foo", "ImmutableDelayed");
    protected final ParameterizedType immutableDelayedPt = new ParameterizedType(immutableDelayed, List.of());
    protected final CausesOfDelay immutableDelay = DelayFactory.createDelay(Location.NOT_YET_SET,
            CauseOfDelay.Cause.IMMUTABLE);

    protected final TypeInfo mutableWithOneTypeParameter = new TypeInfo("com.foo", "MutableTP");
    protected final TypeParameter tp0 = new TypeParameterImpl(mutableWithOneTypeParameter, "T", 0)
            .noTypeBounds();
    protected final ParameterizedType tp0Pt = new ParameterizedType(tp0, 0, ParameterizedType.WildCard.NONE);
    protected final ParameterizedType mutablePtWithOneTypeParameter
            = new ParameterizedType(mutableWithOneTypeParameter, List.of(tp0Pt));

    protected final TypeInfo immutableHcWithOneTypeParameter = new TypeInfo("com.foo", "ImmutableHcTP");
    protected final TypeParameter tpHc0 = new TypeParameterImpl(immutableHcWithOneTypeParameter, "T", 0)
            .noTypeBounds();
    protected final ParameterizedType tpHc0Pt = new ParameterizedType(tpHc0, 0, ParameterizedType.WildCard.NONE);
    protected final ParameterizedType immutableHcPtWithOneTypeParameter
            = new ParameterizedType(immutableHcWithOneTypeParameter, List.of(tpHc0Pt));

    protected final ImportantClasses importantClasses = new ImportantClasses() {
        @Override
        public ParameterizedType iterable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MethodInfo arrayFieldAccess() {
            // T get()
            MethodInfo mi = new MethodInspectionImpl.Builder(mutableWithOneTypeParameter, "get",
                    MethodInfo.MethodType.METHOD)
                    .setAccess(Inspection.Access.PUBLIC)
                    .setReturnType(tp0Pt)
                    .build(inspectionProvider).getMethodInfo();

            HiddenContentTypes hcsType = mutableWithOneTypeParameter.typeResolution.get().hiddenContentTypes();
            HiddenContentTypes hcs = HiddenContentTypes.compute(hcsType, mi.methodInspection.get());

            MethodAnalysisImpl.Builder mab = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                    analysisProvider, inspectionProvider, mi, mutableWithOneTypeParameter.typeAnalysis.get(), List.of());
            mab.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_HC_DV);
            mab.setProperty(Property.IDENTITY, DV.FALSE_DV);
            mab.setProperty(Property.FLUENT, DV.FALSE_DV);
            mab.setHiddenContentSelector(new HiddenContentSelector.All(0));
            mi.methodAnalysis.set(mab.build());

            mi.methodResolution.set(new MethodResolution(Set.of(), Set.of(), MethodResolution.CallStatus.NON_PRIVATE,
                    false, Set.of(), false, hcs));
            return mi;
        }
    };

    protected final AnalyserContext analyserContext = new AnalyserContext() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public DV typeImmutable(ParameterizedType parameterizedType) {
            if (parameterizedType.isTypeParameter()) return MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
            if (recursivelyImmutablePt == parameterizedType) return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
            if (immutableHCPt == parameterizedType) return MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
            if (finalFieldsPt == parameterizedType) return MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV;
            if (immutableDelayedPt == parameterizedType) return immutableDelay;
            if (parameterizedType.isPrimitiveStringClass()) return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
            return MultiLevel.MUTABLE_DV;
        }

        @Override
        public ImportantClasses importantClasses() {
            return importantClasses;
        }
    };

    protected final TypeMap typeMap = new TypeMap() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return inspectionProvider.getFieldInspection(fieldInfo);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            return inspectionProvider.getMethodInspection(methodInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            return inspectionProvider.getTypeInspection(typeInfo);
        }
    };

    protected final TypeContext typeContext = new TypeContext() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public TypeMap typeMap() {
            return typeMap;
        }

        @Override
        public TypeMap.Builder typeMapBuilder() {
            return typeMapBuilder;
        }
    };

    protected EvaluationContext evaluationContext(Map<String, Expression> variableValues) {
        return new AbstractEvaluationContextImpl() {
            @Override
            public DV getProperty(Expression value, Property property,
                                  boolean duringEvaluation, boolean ignoreStateInConditionManager) {
                if (property == Property.IGNORE_MODIFICATIONS) return MultiLevel.IGNORE_MODS_DV;
                if (property == Property.CONTAINER) return MultiLevel.CONTAINER_DV;
                if (value instanceof ExpressionMock em) {
                    return em.getProperty(null, property, duringEvaluation);
                }
                throw new UnsupportedOperationException("Not implemented: " + property);
            }

            @Override
            public Expression currentValue(Variable variable) {
                return Objects.requireNonNull(variableValues.get(variable.simpleName()),
                        "Have no value for " + variable.simpleName());
            }

            @Override
            public Expression currentValue(Variable variable,
                                           Expression scopeValue,
                                           Expression indexValue,
                                           Identifier identifier, ForwardEvaluationInfo forwardEvaluationInfo) {
                return currentValue(variable);
            }

            @Override
            public AnalyserContext getAnalyserContext() {
                return analyserContext;
            }

            @Override
            public ConditionManager getConditionManager() {
                return ConditionManagerImpl.initialConditionManager(primitives);
            }

            @Override
            public TypeInfo getCurrentType() {
                return primitives.stringTypeInfo();
            }

            @Override
            public Location getLocation(Stage level) {
                return Location.NOT_YET_SET;
            }

            @Override
            public Properties defaultValueProperties(ParameterizedType parameterizedType, DV valueForNotNullExpression) {
                if (parameterizedType == primitives.stringParameterizedType()) {
                    return PRIMITIVE_VALUE_PROPERTIES;
                }
                return super.defaultValueProperties(parameterizedType, valueForNotNullExpression);
            }
        };
    }


    @BeforeEach
    public void beforeEach() {
        primitives.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo(),
                Inspector.BY_HAND).build(analyserContext));

        TypeInfo string = primitives.stringTypeInfo();
        string.typeInspection.set(new TypeInspectionImpl.Builder(string, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC));
        TypeAnalysisImpl.Builder builder = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                string, analyserContext);
        builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV);
        string.typeAnalysis.set(builder.build());
        HiddenContentTypes hctString = HiddenContentTypes.compute(inspectionProvider, string.typeInspection.get());
        string.typeResolution.set(new TypeResolution.Builder().setHiddenContentTypes(hctString).build());
        fill(mutable, MultiLevel.MUTABLE_DV, null);
        fill(mutable2, MultiLevel.MUTABLE_DV, null);
        fill(mutableWithOneTypeParameter, MultiLevel.MUTABLE_DV, tp0);
        fill(immutableHcWithOneTypeParameter, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, tpHc0);
        fill(immutableDelayed, immutableDelay, null);
        fill(recursivelyImmutable, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, null);
        fill(immutableHC, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, null);
    }

    private void fill(TypeInfo typeInfo, DV immutable, TypeParameter typeParameter) {
        TypeInspection.Builder tiBuilder = new TypeInspectionImpl.Builder(typeInfo, Inspector.BY_HAND)
                .setFunctionalInterface(null)
                .setParentClass(primitives.objectParameterizedType());
        if (typeParameter != null) {
            tiBuilder.addTypeParameter(typeParameter);
        }
        typeInfo.typeInspection.set(tiBuilder.build(analyserContext));
        TypeAnalysisImpl.Builder taBuilder = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                typeInfo, analyserContext);
        taBuilder.setProperty(Property.CONTAINER, DV.TRUE_DV);
        taBuilder.setProperty(Property.IMMUTABLE, immutable);
        typeInfo.typeAnalysis.set(taBuilder.build());
        TypeResolution.Builder trBuilder = new TypeResolution.Builder();
        trBuilder.setHiddenContentTypes(HiddenContentTypes.compute(inspectionProvider, typeInfo.typeInspection.get()));
        typeInfo.typeResolution.set(trBuilder.build());
    }

    protected EvaluationResult context(EvaluationContext evaluationContext) {
        return new EvaluationResultImpl.Builder(evaluationContext).build();
    }

    protected LocalVariable makeLocalVariable(ParameterizedType pt, String name) {
        return new LocalVariable.Builder()
                .setName(name)
                .setParameterizedType(pt)
                .setOwningType(primitives.stringTypeInfo())
                .build();
    }

    protected VariableExpression makeLVAsExpression(String name, Expression initializer, ParameterizedType pt) {
        LocalVariable lvi = makeLocalVariable(pt, name);
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, initializer));
        return new VariableExpression(newId(), i.localVariableReference);
    }

    protected static Identifier newId() {
        return Identifier.generate("test");
    }

    protected static ExpressionMock simpleMock(ParameterizedType parameterizedType, LinkedVariables linkedVariables) {
        return new ExpressionMock() {
            @Override
            public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
                return new EvaluationResultImpl.Builder(context)
                        .setLinkedVariablesOfExpression(linkedVariables)
                        .setExpression(this).build();
            }

            @Override
            public ParameterizedType returnType() {
                return parameterizedType;
            }

            @Override
            public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
                if (Property.IGNORE_MODIFICATIONS == property) return DV.TRUE_DV;
                if (Property.NOT_NULL_EXPRESSION == property) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                assert parameterizedType.typeInfo != null;
                return parameterizedType.typeInfo.typeAnalysis.get(parameterizedType.toString()).getProperty(property);
            }

            @Override
            public Precedence precedence() {
                return Precedence.BOTTOM;
            }
        };
    }

    protected static final LV lv0hc0 = LV.createHC(new LV.Links(Map.of(LV.INDICES_0, new LV.Link(LV.INDICES_0, false))));
    protected static final LV lv0hc1 = LV.createHC(new LV.Links(Map.of(LV.INDICES_0, new LV.Link(LV.INDICES_1, false))));

}
