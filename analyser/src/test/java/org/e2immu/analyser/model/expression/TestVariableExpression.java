package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.inspector.expr.ParseArrayCreationExpr;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestVariableExpression extends CommonTest {
    @Test
    @DisplayName("a.b, a mutable, b mutable")
    public void testF1() {
        testFieldAccess(mutablePtWithOneTypeParameter, mutablePt, "instance 0 type Mutable",
                "a.b:0,a:4");
    }

    @Test
    @DisplayName("a.b, a mutable, b HC")
    public void testF2() {
        testFieldAccess(mutablePtWithOneTypeParameter, tp0Pt, "instance 0 type T", "a.b:0,a:4");
    }

    @Test
    @DisplayName("a.b, a mutable, b int")
    public void testF3() {
        testFieldAccess(mutablePtWithOneTypeParameter, primitives.intParameterizedType(),
                "instance 0 type int", "a.b:0");
    }

    private void testFieldAccess(ParameterizedType typeA, ParameterizedType typeB, String expectedB, String expectedLvs) {
        FieldInfo fieldInfo = new FieldInfo(newId(), typeB, "b", typeA.typeInfo);
        fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder(fieldInfo).setAccess(Inspection.Access.PRIVATE)
                .build(inspectionProvider));
        LocalVariable la = makeLocalVariable(typeA, "a");
        LocalVariableReference a = new LocalVariableReference(la);
        VariableExpression va = new VariableExpression(newId(), a);
        FieldReference ab = new FieldReferenceImpl(inspectionProvider, fieldInfo, va, primitives.stringTypeInfo());
        assertEquals("a.b", ab.minimalOutput());
        Instance instanceA = Instance.forTesting(typeA);
        Instance instanceB = Instance.forTesting(typeB);
        EvaluationResult context = context(evaluationContext(Map.of("a", instanceA, "b", instanceB)));
        VariableExpression vab = new VariableExpression(newId(), ab);
        EvaluationResult abResult = vab.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals(expectedB, abResult.value().toString());
        assertEquals(expectedLvs, abResult.linkedVariablesOfExpression().toString());
    }

    @Test
    @DisplayName("a[i], int array")
    public void testA1() {
        ParameterizedType intArray = new ParameterizedType(primitives.intTypeInfo(), 1);

        DV intImmutable = primitives.intTypeInfo().typeAnalysis.get().getProperty(Property.IMMUTABLE);
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, intImmutable);

        testAi(intArray, "int[] a=new int[10]", "a[i]:0");
    }

    @Test
    @DisplayName("a[i], containing HC")
    public void testA2() {
        ParameterizedType array = new ParameterizedType(tp0, 1, ParameterizedType.WildCard.NONE);
        testAi(array, "T[] a=new T[10]", "a:4,a[i]:0");
    }

    @Test
    @DisplayName("a[i], containing mutable")
    public void testA3() {
        ParameterizedType array = new ParameterizedType(mutable, 1);
        testAi(array, "Mutable[] a=new Mutable[10]", "a:4,a[i]:0");
    }

    private void testAi(ParameterizedType arrayType, String expectedCreation, String expectedLvs) {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression vi = makeLVAsExpression("i", three, primitives.intParameterizedType());
        Instance instance = Instance.forTesting(arrayType);

        MethodInfo constructor = ParseArrayCreationExpr.createArrayCreationConstructor(typeContext, arrayType);
        Expression newIntArray = new ConstructorCall(newId(), null, constructor, arrayType,
                Diamond.NO, List.of(new IntConstant(primitives, 10)), null, null);
        LocalVariable aLv = new LocalVariable.Builder()
                .setName("a")
                .setParameterizedType(arrayType)
                .setOwningType(primitives.stringTypeInfo())
                .build();
        LocalVariableCreation a = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(aLv, newIntArray));
        assertEquals(expectedCreation, a.minimalOutput());
        VariableExpression va = new VariableExpression(newId(), a.localVariableReference);
        DependentVariable aiDv = new DependentVariable(newId(), va, va.variable(), vi, vi.variable(),
                arrayType.copyWithOneFewerArrays(), "0");
        VariableExpression ai = new VariableExpression(newId(), aiDv);
        assertEquals("a[i]", ai.minimalOutput());

        EvaluationResult context = context(evaluationContext(Map.of("i", three,
                "j", instance, "a", newIntArray, "a[i]", ai)));
        EvaluationResult aiResult = ai.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("a[i]", aiResult.value().toString());
        assertEquals(expectedLvs, aiResult.linkedVariablesOfExpression().toString());

    }
}
