package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestConstructorCallLinkedVariables extends CommonTest {

    @Test
    @DisplayName("empty constructor")
    public void test() {
        MethodInfo constructor = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(),
                MethodInfo.MethodType.CONSTRUCTOR)
                .setReturnType(primitives.stringParameterizedType())
                .build(inspectionProvider).getMethodInfo();

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, constructor,
                primitives.stringTypeInfo().typeAnalysis.get(),
                List.of());
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setHiddenContentSelector(HiddenContentSelector.None.INSTANCE);
        constructor.setAnalysis(builder.build());

        HiddenContentTypes hcsType = constructor.typeInfo.typeResolution.get().hiddenContentTypes();
        HiddenContentTypes hctMethod = HiddenContentTypes.compute(hcsType, constructor.methodInspection.get());
        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false, hctMethod);
        constructor.methodResolution.set(methodResolution);

        ConstructorCall cc = new ConstructorCall(newId(), null, constructor, mutablePt, Diamond.NO,
                List.of(), null, null);
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationResult context = context(evaluationContext(Map.of("this", thisMock)));
        EvaluationResult er = cc.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("", er.linkedVariablesOfExpression().toString());
    }


    @Test
    @DisplayName("direct assignment of mutable type, delayed")
    public void test2() {
        MethodInfo constructor = constructorOneArgument(mutablePt, mutable2Pt, null,
                HiddenContentSelector.None.INSTANCE,
                HiddenContentSelector.None.INSTANCE);

        EvaluationResult er = evaluateConstructorOneArgument(constructor, mutablePt, mutable2Pt);
        assertEquals("a:-1", er.linkedVariablesOfExpression().toString());
        assertEquals("independent@Parameter_p0", er.linkedVariablesOfExpression().causesOfDelay().toString());
    }

    @Test
    @DisplayName("direct assignment of mutable type, dependent")
    public void test2b() {
        MethodInfo constructor = constructorOneArgument(mutablePt, mutable2Pt,
                MultiLevel.DEPENDENT_DV,
                HiddenContentSelector.None.INSTANCE,
                HiddenContentSelector.None.INSTANCE);

        EvaluationResult er = evaluateConstructorOneArgument(constructor, mutablePt, mutable2Pt);
        assertEquals("a:2", er.linkedVariablesOfExpression().toString());
        assertTrue(er.linkedVariablesOfExpression().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("direct assignment of mutable type, independent HC, 1")
    public void test2c() {
        MethodInfo constructor1 = constructorOneArgument(mutablePt, tp0Pt, MultiLevel.INDEPENDENT_HC_DV,
                HiddenContentSelector.None.INSTANCE,
                HiddenContentSelector.None.INSTANCE);
        // the parameter has HiddenContentSelector == HiddenContentSelector.None.INSTANCE
        assertThrows(AssertionError.class, () -> evaluateConstructorOneArgument(constructor1, mutablePt, tp0Pt));
    }

    /*
    new MutableOneTp<T>(T t)
     */
    @Test
    @DisplayName("direct assignment of mutable type, independent HC, 2")
    public void test2cb() {
        HiddenContentSelector select0 = HiddenContentSelector.CsSet.selectTypeParameter(null,0);
        MethodInfo constructor = constructorOneArgument(mutablePtWithOneTypeParameter, tp0Pt,
                MultiLevel.INDEPENDENT_HC_DV, select0, new HiddenContentSelector.All(null, 0));

        ParameterInfo p0 = constructor.methodInspection.get().getParameters().get(0);
        assertEquals("Type param T", p0.parameterizedType.toString());
        assertEquals("*", p0.parameterAnalysis.get().getHiddenContentSelector().toString());

        EvaluationResult er = evaluateConstructorOneArgument(constructor, mutablePtWithOneTypeParameter, tp0Pt);
        assertEquals("a:4", er.linkedVariablesOfExpression().toString());
        assertTrue(er.linkedVariablesOfExpression().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("direct assignment of mutable type, independent")
    public void test2d() {
        MethodInfo constructor = constructorOneArgument(primitives.stringParameterizedType(),
                mutablePt,
                MultiLevel.INDEPENDENT_DV,
                HiddenContentSelector.None.INSTANCE,
                HiddenContentSelector.None.INSTANCE);

        EvaluationResult er = evaluateConstructorOneArgument(constructor, primitives.stringParameterizedType(), mutablePt);
        assertTrue(er.linkedVariablesOfExpression().isEmpty());
        assertTrue(er.linkedVariablesOfExpression().causesOfDelay().isDone());
    }

    private EvaluationResult evaluateConstructorOneArgument(MethodInfo constructor,
                                                            ParameterizedType constructorType,
                                                            ParameterizedType parameterType) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, mutablePt);
        ExpressionMock m = simpleMock(parameterType, LinkedVariables.of(va.variable(), LV.LINK_ASSIGNED));
        ConstructorCall cc = new ConstructorCall(newId(), null, constructor, constructorType,
                Diamond.NO, List.of(m), null, null);
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va));
        return cc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    private MethodInfo constructorOneArgument(ParameterizedType constructorType,
                                              ParameterizedType parameterType,
                                              DV independentP0,
                                              HiddenContentSelector constructorHcs,
                                              HiddenContentSelector p0Hcs) {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                parameterType, "p0", 0);

        MethodInfo constructor = new MethodInspectionImpl.Builder(newId(), constructorType.typeInfo,
                MethodInfo.MethodType.CONSTRUCTOR)
                .setReturnType(constructorType)
                .addParameter(param0Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext)
                .build();
        ParameterInfo param0 = constructor.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder p0Builder = new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0)
                .setHiddenContentSelector(p0Hcs);
        if (independentP0 != null) {
            p0Builder.setProperty(Property.INDEPENDENT, independentP0);
        }
        ParameterAnalysis p0Analysis = (ParameterAnalysis) p0Builder.build();
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, constructor, typeAnalysis,
                List.of(p0Analysis));
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.DEPENDENT_DV);
        builder.setHiddenContentSelector(constructorHcs);
        constructor.setAnalysis(builder.build());
        HiddenContentTypes hcsType = constructor.typeInfo.typeResolution.get().hiddenContentTypes();
        HiddenContentTypes hctMethod = HiddenContentTypes.compute(hcsType, constructor.methodInspection.get());
        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false, hctMethod);
        constructor.methodResolution.set(methodResolution);
        return constructor;
    }
}
