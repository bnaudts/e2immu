package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.delay.DelayFactory;
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
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMethodCallLinkedVariablesFromObjectToValue extends CommonTest {

    private final HiddenContentSelector SELECT_0 = HiddenContentSelector.CsSet.selectTypeParameter(null,0);

    private MethodInfo methodWithTwoArgs(DV identity,
                                         DV fluent,
                                         DV independent,
                                         HiddenContentSelector hiddenContentSelector,
                                         ParameterizedType methodReturnType) {
        return methodWithTwoArgs(identity, fluent, independent, hiddenContentSelector, methodReturnType, primitives.stringTypeInfo());
    }

    private MethodInfo methodWithTwoArgs(DV identity,
                                         DV fluent,
                                         DV independent,
                                         HiddenContentSelector hiddenContentSelector,
                                         ParameterizedType methodReturnType,
                                         TypeInfo methodOwner) {
        ParameterizedType parameterType = hiddenContentSelector == null ? mutablePt : mutablePtWithOneTypeParameter;
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                parameterType, "p0", 0);
        ParameterInspectionImpl.Builder param1Inspection = new ParameterInspectionImpl.Builder(newId(),
                parameterType, "p1", 1);
        MethodInfo methodInfo = new MethodInspectionImpl.Builder(newId(), methodOwner, "method",
                MethodInfo.MethodType.METHOD)
                .addParameter(param0Inspection)
                .addParameter(param1Inspection)
                .setReturnType(methodReturnType)
                .build(inspectionProvider).getMethodInfo();
        HiddenContentTypes hctType = methodOwner.typeResolution.get().hiddenContentTypes();
        assertNotNull(hctType, "Need hct for " + methodOwner);
        HiddenContentTypes hctMethod = HiddenContentTypes.compute(hctType, methodInfo.methodInspection.get());
        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false, hctMethod);
        methodInfo.methodResolution.set(methodResolution);

        ParameterInfo param0 = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterInfo param1 = methodInfo.methodInspection.get().getParameters().get(1);
        ParameterAnalysis p0Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0)
                .setHiddenContentSelector(HiddenContentSelector.None.INSTANCE)
                .build();
        ParameterAnalysis p1Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param1)
                .setHiddenContentSelector(HiddenContentSelector.None.INSTANCE)
                .build();

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, methodInfo, primitives.stringTypeInfo().typeAnalysis.get(),
                List.of(p0Analysis, p1Analysis));
        builder.setProperty(Property.IDENTITY, identity);
        builder.setProperty(Property.FLUENT, fluent);
        builder.setProperty(Property.INDEPENDENT, independent);
        builder.setHiddenContentSelector(Objects.requireNonNullElse(hiddenContentSelector, HiddenContentSelector.None.INSTANCE));
        methodInfo.setAnalysis(builder.build());
        return methodInfo;
    }

    private LinkedVariables callMethodWithTwoArgs(MethodInfo methodInfo,
                                                  ParameterizedType objectType, LV lvo, LV lva, LV lvb) {
        Expression zero = IntConstant.zero(primitives);
        List<ParameterInfo> params = methodInfo.methodInspection.get().getParameters();
        VariableExpression va = makeLVAsExpression("a", zero, mutablePt);
        VariableExpression vb = makeLVAsExpression("b", zero, mutablePt);
        VariableExpression vo = makeLVAsExpression("o", zero, objectType);

        ParameterizedType pt0 = params.get(0).parameterizedType;
        ParameterizedType pt1 = params.get(1).parameterizedType;

        Expression p0 = simpleMock(pt0, LinkedVariables.EMPTY);
        Expression p1 = simpleMock(pt1, LinkedVariables.EMPTY);

        VariableExpression thisVE = new VariableExpression(newId(), new This(analyserContext,
                primitives.stringTypeInfo()));

        LinkedVariables lv = LinkedVariables.of(Map.of(vo.variable(), lvo, va.variable(), lva, vb.variable(), lvb,
                thisVE.variable(), LINK_DEPENDENT));
        Expression object = simpleMock(objectType, lv);

        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        return methodCall.evaluate(context, ForwardEvaluationInfo.DEFAULT).linkedVariablesOfExpression();
    }


    @Test
    @DisplayName("base, identity delayed, object has no linked variables")
    public void test1() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.DEPENDENT_DV,
                null, mutablePt);
        Expression p0 = simpleMock(mutablePt, LinkedVariables.EMPTY);
        Expression p1 = simpleMock(mutablePt, LinkedVariables.EMPTY);
        Expression object = simpleMock(mutablePt, LinkedVariables.EMPTY);
        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        LinkedVariables lv = methodCall.evaluate(context, ForwardEvaluationInfo.DEFAULT)
                .linkedVariablesOfExpression();
        assertTrue(lv.isEmpty());
    }

    @Test
    @DisplayName("two parameters, identity delayed, object has no linked variables")
    public void test2() {
        DV delay = DelayFactory.createDelay(Location.NOT_YET_SET, CauseOfDelay.Cause.IDENTITY);
        MethodInfo methodInfo = methodWithTwoArgs(delay, DV.FALSE_DV, MultiLevel.DEPENDENT_DV,
                null, mutablePt);

        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, primitives.intParameterizedType());
        VariableExpression vb = makeLVAsExpression("b", zero, primitives.intParameterizedType());

        Expression p0 = simpleMock(mutablePt, LinkedVariables.of(va.variable(), LINK_DEPENDENT));
        Expression p1 = simpleMock(mutablePt, LinkedVariables.of(vb.variable(), LINK_DEPENDENT));
        Expression object = simpleMock(mutablePt, LinkedVariables.EMPTY);
        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        LinkedVariables lv = methodCall.evaluate(context, ForwardEvaluationInfo.DEFAULT)
                .linkedVariablesOfExpression();

        assertEquals("identity@NOT_YET_SET", lv.causesOfDelay().toString());
        assertEquals("a:-1,b:-1", lv.toString());
    }

    @Test
    @DisplayName("two parameters, identity")
    public void test3() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.TRUE_DV, DV.FALSE_DV, MultiLevel.DEPENDENT_DV,
                null, mutablePt);

        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, primitives.intParameterizedType());
        VariableExpression vb = makeLVAsExpression("b", zero, primitives.intParameterizedType());

        for (LV dv : List.of(LINK_STATICALLY_ASSIGNED, LINK_ASSIGNED, LINK_DEPENDENT, LINK_DEPENDENT)) {
            Expression p0 = simpleMock(mutablePt, LinkedVariables.of(va.variable(), dv));
            Expression p1 = simpleMock(mutablePt, LinkedVariables.of(vb.variable(), LINK_DEPENDENT));
            Expression object = simpleMock(mutablePt, LinkedVariables.EMPTY);
            MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
            Expression abc = new StringConstant(primitives, "abc");
            EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
            LinkedVariables lv = methodCall.evaluate(context, ForwardEvaluationInfo.DEFAULT)
                    .linkedVariablesOfExpression();
            assertEquals(1L, lv.stream().count());
            LV result = dv.max(LINK_ASSIGNED);

            // value of 1st parameter, statically_assigned -> assigned
            assertSame(result, lv.value(va.variable()));
        }
    }

    @Test
    @DisplayName("two parameters, void method")
    public void test3B() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.TRUE_DV, MultiLevel.DEPENDENT_DV,
                null, primitives.voidParameterizedType());
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutablePt, LINK_ASSIGNED, LINK_DEPENDENT,
                LINK_STATICALLY_ASSIGNED);
        // the resulting object is the same as the object, so it keeps its linked variables
        assertTrue(lv.isEmpty());
    }

    @Test
    @DisplayName("two parameters, void method, HC for b")
    public void test3Bb() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.TRUE_DV, MultiLevel.DEPENDENT_DV,
                SELECT_0, primitives.voidParameterizedType());
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutablePt, LINK_ASSIGNED, LINK_DEPENDENT, lv0hc0);
        // the resulting object is the same as the object, so it keeps its linked variables
        assertTrue(lv.isEmpty());
    }

    @Test
    @DisplayName("two parameters, fluent")
    public void test3C() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.TRUE_DV, MultiLevel.DEPENDENT_DV,
                null, mutablePt);
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutablePt, LINK_ASSIGNED, LINK_DEPENDENT,
                LINK_STATICALLY_ASSIGNED);
        // the resulting object is the same as the object, so it keeps its linked variables
        assertEquals("a:2,b:1,o:1,this:2", lv.toString());
    }

    @Test
    @DisplayName("two parameters, fluent, HC for b")
    public void test3Cb() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.TRUE_DV, MultiLevel.DEPENDENT_DV,
                SELECT_0, mutablePt);
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutablePt, LINK_ASSIGNED, LINK_DEPENDENT, lv0hc0);
        // the resulting object is the same as the object, so it keeps its linked variables
        assertEquals("a:2,b:4,o:1,this:2", lv.toString());
    }

    @Test
    @DisplayName("mutable object, independence delayed")
    public void test4() {
        DV delay = DelayFactory.createDelay(primitives.stringTypeInfo().newLocation(), CauseOfDelay.Cause.VALUE_INDEPENDENT);
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, delay, null,
                mutablePt);
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutablePt, LINK_ASSIGNED, LINK_DEPENDENT,
                LINK_STATICALLY_ASSIGNED);

        assertEquals("a:-1,b:-1,o:-1,this:-1", lv.toString());
        assertEquals("independent@Class_String", lv.causesOfDelay().toString());
    }

    @Test
    @DisplayName("mutable object, independent method")
    public void test5() {
        /* example: List<T>.size() method independent because result recursively immutable
           example:
              class I {
                 public int i;
                 I(i){ this.i=i; }
                 I copy() { return new I(i); }
              }
              I is mutable, copy() is independent
           a change in the result of copy() will not be a change in the object
         */
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.INDEPENDENT_DV,
                null, mutablePt);
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutablePt, LINK_ASSIGNED, LINK_DEPENDENT,
                LINK_STATICALLY_ASSIGNED);

        assertEquals("", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }

    @Test
    @DisplayName("mutable object, dependent method, mutable(immutable)")
    public void test6() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.DEPENDENT_DV, SELECT_0,
                mutablePtWithOneTypeParameter, mutableWithOneTypeParameter);
        assertEquals("0-4-1", lv0hc1.toString());
        ParameterizedType mutableImmutable = new ParameterizedType(mutableWithOneTypeParameter, List.of(tp0Pt));
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutableImmutable, LINK_STATICALLY_ASSIGNED, LINK_DEPENDENT,
                lv0hc1);

        /*
        object is linked o:0, a:2, b:4 <0>-<0>, this:2
        for example
            o = new ArrayList<T>(ts); // ts is some collection of T objects
            a = o.sublist(1, 3);
            b = new HashSet<>(o);
            List<T> object = o;
        now 'method' is dependent, changes in result may cause changes in object, and/or vice versa
            List<T> result = object.method(p0, p1);
        we must have
            o:2 (dependent)
            a:2 (still dependent)
            b:4 (with mine computed as (*), and theirs the mine of object->4->b)
            this:2 (still dependent)

        NOTE: linked variables of parameters are not part of the computation
        (*): the computation is dependent on the method's formal return type, and is stored in
             MethodAnalysis.getHiddenContentSelector(), then corrected for the concrete return type
             see "correctedTransferSelector"
         */

        assertEquals("a:2,b:4,o:2,this:2", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
        Variable b = lv.variables().keySet().stream().filter(v -> "b".equals(v.simpleName())).findFirst().orElseThrow();
        LV lvb = lv.value(b);
        assertEquals("0-4-0", lvb.toString());
    }

    @Test
    @DisplayName("recursively immutable object, dependent method")
    public void test7() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.DEPENDENT_DV,
                null, mutablePt);
        // values LINK_ASSIGNED should not be present; but they won't be used
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, recursivelyImmutablePt, LINK_ASSIGNED, LINK_ASSIGNED,
                LINK_ASSIGNED);

        assertTrue(lv.isEmpty());
    }

    @Test
    @DisplayName("mutable object, method independent HC, mutable(immutable)")
    public void test9() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.INDEPENDENT_HC_DV, SELECT_0,
                mutablePtWithOneTypeParameter, mutableWithOneTypeParameter);
        ParameterizedType mutableImmutable = new ParameterizedType(mutableWithOneTypeParameter, List.of(tp0Pt));
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, mutableImmutable, LINK_STATICALLY_ASSIGNED, LINK_DEPENDENT,
                lv0hc0);

        /*
         o = new ArrayList<>(ts), with ts some collection of Ts
         object = o;
         a = object.sublist(...)
         b = new HashSet(object)

         method is independent HC, e.g., object.get(index)
         */
        assertEquals("a:4,b:4,o:4,this:4", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }

    @Test
    @DisplayName("mutable object, method independent HC, immutable(mutable)")
    public void test10() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.INDEPENDENT_HC_DV,
                new HiddenContentSelector.All(null,0), tpHc0Pt, immutableHcWithOneTypeParameter);
        ParameterizedType immutableMutable = new ParameterizedType(immutableHcWithOneTypeParameter, List.of(mutablePt));
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, immutableMutable, LINK_STATICALLY_ASSIGNED, LINK_DEPENDENT,
                lv0hc0);

        /*
         o = List.copyOf(is), with I the mutable object shown higher up; concrete type is mutable
         object = o;
         a = object.sublist(); because 'o' is mutable, 'a' is mutable as well, so 'a' can be dependent on object
         b = new HashSet(is)

         again, take object.get(index). The result is an 'I' object. Changes in this 'I' object change the
         object graph of o, a, b

         how to distinguish? using the correction algorithm 'correctIndependent'

         20240330 downgrade removed!
         */
        assertEquals("a:4,b:4,o:4,this:4", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }


    @Test
    @DisplayName("immutable delayed, dependent method")
    public void test11() {
        MethodInfo methodInfo = methodWithTwoArgs(DV.FALSE_DV, DV.FALSE_DV, MultiLevel.DEPENDENT_DV,
                null, mutablePt);
        // values LINK_ASSIGNED should not be present; but they won't be used
        LinkedVariables lv = callMethodWithTwoArgs(methodInfo, immutableDelayedPt, LINK_ASSIGNED, LINK_ASSIGNED,
                LINK_ASSIGNED);

        // FIXME: is this:-1 the correct decision?? we used to have special code for This to avoid delays
        //   in ComputeIndependent, because of Test_Util_07_Trie causing CONTEXT_IMMUTABLE delays
        //   however, this code seemed to be in ineffective
        assertEquals("a:-1,b:-1,o:-1,this:-1", lv.toString());
    }

}
