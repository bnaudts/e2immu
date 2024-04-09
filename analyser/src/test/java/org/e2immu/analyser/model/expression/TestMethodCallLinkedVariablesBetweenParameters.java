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

public class TestMethodCallLinkedVariablesBetweenParameters extends CommonTest {

    @Test
    @DisplayName("param 1 dependent on param 0, o~c.copy(p0~a,p1~b), dependent")
    public void test1() {
        MethodInfo method = methodCallTwoArguments(mutablePt, mutablePt, true, false);

        EvaluationResult er = evaluateMethodCallTwoArguments(method, mutablePt);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(1, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:2", ca.linkedVariables().toString());
    }


    @Test
    @DisplayName("param 1 dependent on param 0, o~c.copy(p0~a,p1~b), independent HC")
    public void test1b() {
        MethodInfo method = methodCallTwoArguments(mutablePtWithOneTypeParameter, mutablePtWithOneTypeParameter,
                false, false);

        EvaluationResult er = evaluateMethodCallTwoArguments(method, mutablePtWithOneTypeParameter);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(1, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:4", ca.linkedVariables().toString());
    }

    @Test
    @DisplayName("param 1... independent HC on param 0, Collections.add(c, a, b)")
    public void test2() {
        ParameterizedType typeParameterArray = new ParameterizedType(tp0, 1, ParameterizedType.WildCard.NONE);
        MethodInfo method = methodCallTwoArguments(mutablePtWithOneTypeParameter, typeParameterArray, false,
                true);

        EvaluationResult er = evaluateMethodCallVarargs(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData cc = er.findChangeData("c");
        assertEquals("a:4,b:4", cc.linkedVariables().toString());
        ChangeData cThis = er.findChangeData("this");
        assertTrue(cThis.linkedVariables().isEmpty());
    }


    private EvaluationResult evaluateMethodCallTwoArguments(MethodInfo method, ParameterizedType typeOfAB) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, typeOfAB);
        VariableExpression vb = makeLVAsExpression("b", zero, typeOfAB);
        VariableExpression vc = makeLVAsExpression("c", zero, mutablePt);

        ExpressionMock argument0 = simpleMock(mutablePt, LinkedVariables.of(va.variable(), LV.LINK_ASSIGNED));
        ExpressionMock argument1 = simpleMock(mutablePt, LinkedVariables.of(vb.variable(), LV.LINK_ASSIGNED));
        ExpressionMock object = simpleMock(mutablePt, LinkedVariables.of(vc.variable(), LV.LINK_DEPENDENT));

        MethodCall mc = new MethodCall(newId(), object, method, List.of(argument0, argument1));
        Expression thisMock = simpleMock(mutablePtWithOneTypeParameter, LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va, "b", vb, "c", vc));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    private EvaluationResult evaluateMethodCallVarargs(MethodInfo method) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, mutablePt);
        VariableExpression vb = makeLVAsExpression("b", zero, mutablePt);
        VariableExpression vc = makeLVAsExpression("c", zero, mutablePt);

        ExpressionMock argument0 = simpleMock(mutablePtWithOneTypeParameter, LinkedVariables.of(vc.variable(), LV.LINK_DEPENDENT));
        ExpressionMock argument1 = simpleMock(tp0Pt, LinkedVariables.of(va.variable(), LV.LINK_ASSIGNED));
        ExpressionMock argument2 = simpleMock(tp0Pt, LinkedVariables.of(vb.variable(), LV.LINK_ASSIGNED));

        MethodCall mc = new MethodCall(newId(), new TypeExpression(newId(), primitives.stringParameterizedType(), Diamond.NO),
                method, List.of(argument0, argument1, argument2));
        Expression thisMock = simpleMock(mutablePtWithOneTypeParameter, LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va, "b", vb, "c", vc));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    // non-static method, using the type parameter of the type
    private MethodInfo methodCallTwoArguments(ParameterizedType p0Type, ParameterizedType p1Type, boolean dependentLink,
                                              boolean p1Varargs) {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                p0Type, "p0", 0);
        HiddenContentSelector p0Hcs = HiddenContentSelector.CsSet.selectTypeParameter(0);
        ParameterInspection.Builder param1Inspection = new ParameterInspectionImpl.Builder(newId(),
                p1Type, "p1", 1).setVarArgs(p1Varargs);

        MethodInfo method = new MethodInspectionImpl.Builder(newId(), mutableWithOneTypeParameter, "method",
                MethodInfo.MethodType.METHOD)
                .setAccess(Inspection.Access.PUBLIC)
                .setReturnType(primitives.voidParameterizedType())
                .addParameter(param0Inspection)
                .addParameter(param1Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.OF_PRIMITIVE)
                .build();

        ParameterInfo param0 = method.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder p0Builder = new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0)
                .setHiddenContentSelector(p0Hcs);
        p0Builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        int[] hcLinkParameters = dependentLink ? null : new int[]{1};
        int[] dependentLinkParameters = dependentLink ? new int[]{1} : null;
        p0Builder.writeHiddenContentLink(hcLinkParameters, dependentLinkParameters);
        ParameterAnalysis p0Analysis = (ParameterAnalysis) p0Builder.build();

        int d = dependentLink ? 2 : 4;
        assertEquals("p1:" + d, p0Analysis.getLinksToOtherParameters().toString());
        if (!dependentLink && p1Varargs) {
            assertEquals("0-4-*", p0Analysis.getLinksToOtherParameters().stream().findFirst()
                    .orElseThrow().getValue().toString());
        }

        ParameterInfo param1 = method.methodInspection.get().getParameters().get(1);
        ParameterAnalysisImpl.Builder p1Builder = new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param1)
                .setHiddenContentSelector(p0Hcs);
        p1Builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        ParameterAnalysis p1Analysis = (ParameterAnalysis) p1Builder.build();

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, method, typeAnalysis,
                List.of(p0Analysis, p1Analysis));

        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setHiddenContentSelector(HiddenContentSelector.None.INSTANCE);
        method.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        method.methodResolution.set(methodResolution);
        return method;
    }
}
