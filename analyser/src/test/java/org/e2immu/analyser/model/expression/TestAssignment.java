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
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.inspector.expr.ParseArrayCreationExpr;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestAssignment extends CommonTest {
    @Test
    @DisplayName("int i=0; i+=1;")
    public void test1() {
        LocalVariable lvi = makeLocalVariable(primitives.intParameterizedType(), "i");
        IntConstant zero = IntConstant.zero(primitives);
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, zero));
        assertEquals("int i=0", i.minimalOutput());
        MethodInfo plusEquals = primitives.assignPlusOperatorInt();
        assertNotNull(plusEquals);
        assertEquals("int.+=(int)", plusEquals.fullyQualifiedName());
        Assignment iPlusEquals1 = new Assignment(newId(), primitives,
                new VariableExpression(newId(), i.localVariableReference), IntConstant.one(primitives),
                plusEquals, null, false,
                false, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", zero)));
        EvaluationResult result = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1", result.value().toString());
    }

    @Test
    @DisplayName("i+=1, ++i, i++")
    public void test2() {
        IntConstant zero = IntConstant.zero(primitives);
        VariableExpression ve = makeLVAsExpression("i", zero, primitives.intParameterizedType());
        IntConstant one = IntConstant.one(primitives);
        Expression iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", zero)));
        EvaluationResult result = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1", result.value().toString());

        Expression plusPlusI = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), true,
                true, true, null);
        assertEquals("++i", plusPlusI.minimalOutput());
        EvaluationResult result2 = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1", result2.value().toString());

        Expression iPlusPlus = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), false,
                true, true, null);
        assertEquals("i++", iPlusPlus.minimalOutput());
        EvaluationResult result3 = iPlusPlus.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("0", result3.value().toString());
    }

    @Test
    @DisplayName("i++ ++i")
    public void test3() {
        VariableExpression ve = makeLVAsExpression("i", IntConstant.zero(primitives),
                primitives.intParameterizedType());

        Expression iPlusPlus = new UnaryOperator(newId(), primitives.postfixIncrementOperatorInt(),
                ve, Precedence.PLUSPLUS);
        assertEquals("i++", iPlusPlus.minimalOutput());

        Expression plusPlusI = new UnaryOperator(newId(), primitives.prefixIncrementOperatorInt(),
                ve, Precedence.UNARY);
        assertEquals("++i", plusPlusI.minimalOutput());
    }

    @Test
    @DisplayName("direct assignment i=j")
    public void test4() {
        IntConstant zero = IntConstant.zero(primitives);
        VariableExpression vi = makeLVAsExpression("i", zero, primitives.intParameterizedType());
        Instance instance = Instance.forTesting(primitives.intParameterizedType());
        VariableExpression vj = makeLVAsExpression("j", instance, primitives.intParameterizedType());
        Assignment assignment = new Assignment(primitives, vi, vj);
        assertEquals("i=j", assignment.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", zero, "j", instance)));
        EvaluationResult result = assignment.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("j:0", result.linkedVariables(vi.variable()).toString());
    }

    @Test
    @DisplayName("only sort")
    public void test5() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three, primitives.intParameterizedType());
        IntConstant one = IntConstant.one(primitives);
        Expression iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, null);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult onlySortResult = iPlusEquals1.evaluate(context, onlySort);
        assertEquals("i+=1", onlySortResult.value().toString());
        EvaluationResult eval = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("4", eval.value().toString());
    }

    @Test
    @DisplayName("assignment to self")
    public void test6() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three, primitives.intParameterizedType());
        Assignment toSelf = new Assignment(newId(), primitives, ve, ve);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult er = toSelf.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals(1, er.messages().size());
    }

    @Test
    @DisplayName("evaluationOfValue, += ")
    public void test7() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three, primitives.intParameterizedType());
        IntConstant five = new IntConstant(primitives, 5);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult evalFour = five.evaluate(context, ForwardEvaluationInfo.DEFAULT);

        IntConstant one = IntConstant.one(primitives);
        Assignment iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, evalFour);
        EvaluationResult onlySortResult = iPlusEquals1.evaluate(context, onlySort);
        assertEquals("i+=1", onlySortResult.value().toString());
        EvaluationResult eval = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        // start off with 3, add 5 instead of 1
        assertEquals("8", eval.value().toString());
    }

    @Test
    @DisplayName("assign to array: int[] a = new int[10]; int i=03; a[i]=j")
    public void test8() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression vi = makeLVAsExpression("i", three, primitives.intParameterizedType());
        Instance instance = Instance.forTesting(primitives.intParameterizedType());
        VariableExpression vj = makeLVAsExpression("j", instance, primitives.intParameterizedType());
        ParameterizedType intArray = new ParameterizedType(primitives.intTypeInfo(), 1);
        MethodInfo constructor = ParseArrayCreationExpr.createArrayCreationConstructor(typeContext, intArray);
        Expression newIntArray = new ConstructorCall(newId(), null, constructor, intArray,
                Diamond.NO, List.of(new IntConstant(primitives, 10)), null, null);
        LocalVariable aLv = new LocalVariable.Builder()
                .setName("a")
                .setParameterizedType(intArray)
                .setOwningType(primitives.stringTypeInfo())
                .build();
        LocalVariableCreation a = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(aLv, newIntArray));
        assertEquals("int[] a=new int[10]", a.minimalOutput());
        VariableExpression va = new VariableExpression(newId(), a.localVariableReference);
        DependentVariable aiDv = new DependentVariable(newId(), va, va.variable(), vi, vi.variable(),
                primitives.intParameterizedType(), "0");
        VariableExpression ai = new VariableExpression(newId(), aiDv);
        assertEquals("a[i]", ai.minimalOutput());

        DependentVariable a3Dv = new DependentVariable(newId(), va, va.variable(), three, null,
                primitives.intParameterizedType(), "0");
        VariableExpression a3 = new VariableExpression(newId(), a3Dv);

        EvaluationResult context = context(evaluationContext(Map.of("i", three,
                "j", instance, "a", newIntArray, "a[i]", a3)));
        EvaluationResult aiResult = ai.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("a[3]", aiResult.value().toString());
        // any change to a[i] (in particular here, a re-assignment) will change the object graph of a
        assertEquals("a[i]:0", aiResult.linkedVariablesOfExpression().toString());

        Assignment assignment = new Assignment(primitives, ai, vj);
        assertEquals("a[i]=j", assignment.minimalOutput());
        EvaluationResult eval = assignment.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("instance 0 type int", eval.value().minimalOutput());
        assertTrue(eval.messages().isEmpty());
        // ?? potentially also a[3]:0
        assertEquals("a[i]:0,j:0", eval.linkedVariablesOfExpression().toString());
        assertEquals("", eval.linkedVariables(a.localVariableReference).toString());

        assertEquals(2, eval.changeData().size());
        ChangeData cdAi = eval.changeData().get(aiDv);
        assertNull(cdAi);

        ChangeData cdA3 = eval.changeData().get(a3Dv);
        assertNotNull(cdA3);
        assertSame(eval.value(), cdA3.value());
    }


    @Test
    @DisplayName("assignment to same value")
    public void test9() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three, primitives.intParameterizedType());
        Assignment toSameValue = new Assignment(newId(), primitives, ve, three);
        assertEquals("i=3", toSameValue.minimalOutput());
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult er = toSameValue.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals(1, er.messages().size());
    }

    @Test
    @DisplayName("hack for loop")
    public void test10() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression vi = makeLVAsExpression("i", three, primitives.intParameterizedType());
        MethodInfo plusEquals = primitives.assignPlusOperatorInt();
        Assignment iPlusEquals1 = new Assignment(newId(), primitives, vi, IntConstant.one(primitives),
                plusEquals, null, false,
                false, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());
        Assignment hack = (Assignment) iPlusEquals1.cloneWithHackForLoop();
        assertEquals("i+=1", hack.minimalOutput());
        assertTrue(hack.hackForUpdatersInForLoop);

        EvaluationResult context = context(evaluationContext(Map.of("i", three)));

        EvaluationResult er = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("4", er.value().toString());
        ChangeData cd = er.changeData().get(vi.variable());
        assertEquals("4", cd.value().toString());

        EvaluationResult er2 = hack.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("4", er2.value().toString());
        ChangeData cd2 = er2.changeData().get(vi.variable());
        assertEquals("instance type int", cd2.value().toString());
    }


    @Test
    @DisplayName("hack for loop delayed")
    public void test11() {
        IntConstant three = new IntConstant(primitives, 3);
        CausesOfDelay causes = DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET,
                CauseOfDelay.Cause.CONSTANT));
        Expression delayed = DelayedExpression.forTest(newId(), three, causes);
        VariableExpression vi = makeLVAsExpression("i", delayed, primitives.intParameterizedType());
        MethodInfo plusEquals = primitives.assignPlusOperatorInt();
        Assignment iPlusEquals1 = new Assignment(newId(), primitives, vi, IntConstant.one(primitives),
                plusEquals, null, false,
                false, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());
        Assignment hack = (Assignment) iPlusEquals1.cloneWithHackForLoop();
        assertEquals("i+=1", hack.minimalOutput());
        assertTrue(hack.hackForUpdatersInForLoop);

        EvaluationResult context = context(evaluationContext(Map.of("i", delayed)));

        EvaluationResult er = iPlusEquals1.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1+<test>", er.value().toString());
        ChangeData cd = er.changeData().get(vi.variable());
        assertEquals("1+<test>", cd.value().toString());

        assertEquals("constant@NOT_YET_SET", cd.value().causesOfDelay().toString());
        assertEquals("", er.linkedVariables(vi.variable()).toString());

        EvaluationResult er2 = hack.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("1+<test>", er2.value().toString());
        ChangeData cd2 = er2.changeData().get(vi.variable());
        assertEquals("<v:i>", cd2.value().toString());
    }


    @Test
    @DisplayName("evaluationOfValue, =")
    public void test12() {
        IntConstant three = new IntConstant(primitives, 3);
        VariableExpression ve = makeLVAsExpression("i", three, primitives.intParameterizedType());
        IntConstant five = new IntConstant(primitives, 5);
        EvaluationResult context = context(evaluationContext(Map.of("i", three)));
        EvaluationResult evalFour = five.evaluate(context, ForwardEvaluationInfo.DEFAULT);

        IntConstant one = IntConstant.one(primitives);
        Assignment assignment = new Assignment(primitives, ve, one, evalFour);
        EvaluationResult onlySortResult = assignment.evaluate(context, onlySort);
        assertEquals("i=1", onlySortResult.value().toString());
        EvaluationResult eval = assignment.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("5", eval.value().toString());
    }

    //  v = mockFormal linked to a (evaluated as mockEval linked to b)
    @Test
    @DisplayName("linked variables")
    public void test13() {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, primitives.intParameterizedType());
        VariableExpression vb = makeLVAsExpression("b", zero, primitives.intParameterizedType());
        VariableExpression vc = makeLVAsExpression("c", zero, primitives.intParameterizedType());
        VariableExpression vd = makeLVAsExpression("d", zero, primitives.intParameterizedType());
        VariableExpression ve = makeLVAsExpression("e", zero, primitives.intParameterizedType());
        VariableExpression vv = makeLVAsExpression("v", zero, primitives.intParameterizedType());

        FieldInfo fieldInfo = new FieldInfo(newId(), primitives.intParameterizedType(), "f",
                primitives.stringTypeInfo());
        fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder(fieldInfo).build(inspectionProvider));
        FieldReference f = new FieldReferenceImpl(inspectionProvider, fieldInfo);
        VariableExpression vf = new VariableExpression(newId(), f);

        ExpressionMock mock1 = new ExpressionMock() {
            @Override
            public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
                LinkedVariables lv = LinkedVariables.of(Map.of(
                        va.variable(), LINK_DEPENDENT,
                        vc.variable(), LINK_ASSIGNED,
                        vf.variable(), LINK_STATICALLY_ASSIGNED,
                        vd.variable(), lv0hc0));
                return new EvaluationResultImpl.Builder(context)
                        .setExpression(this)
                        .setLinkedVariablesOfExpression(lv)
                        .build();
            }

            @Override
            public Set<Variable> directAssignmentVariables() {
                return Set.of(ve.variable());
            }
        };

        Instance instance = Instance.forTesting(primitives.intParameterizedType());
        EvaluationContext ec = evaluationContext(Map.of("v", instance));
        EvaluationResult context = new EvaluationResultImpl.Builder(ec).build();

        Assignment assignment = new Assignment(primitives, vv, mock1);
        EvaluationResult result = assignment.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        LinkedVariables linkedVariables = result.linkedVariablesOfExpression();
        assertEquals("a:2,c:1,d:4,this.f:0,v:0", linkedVariables.toString());
        LinkedVariables lv = result.linkedVariables(vv.variable());

        // we take values from both, minimize; 0->1; static assignment variables
        assertEquals("a:2,c:1,d:4,this.f:0", lv.toString());
    }

    @Test
    @DisplayName("mark modified")
    public void test14() {
        LocalVariable lvs = makeLocalVariable(primitives.stringParameterizedType(), "s");
        StringConstant abc = new StringConstant(primitives, "abc");
        LocalVariableCreation s = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvs, abc));
        VariableExpression vs = new VariableExpression(newId(), s.localVariableReference);

        FieldInfo fieldInfo = new FieldInfo(newId(), primitives.intParameterizedType(), "f",
                primitives.stringTypeInfo());
        fieldInfo.fieldInspection.set(new FieldInspectionImpl.Builder(fieldInfo).build(inspectionProvider));
        FieldReference f = new FieldReferenceImpl(inspectionProvider, fieldInfo, vs, null,
                primitives.stringTypeInfo());
        VariableExpression vf = new VariableExpression(newId(), f);
        assertEquals("s.f", vf.toString());

        StringConstant x = new StringConstant(primitives, "x");
        Assignment a = new Assignment(primitives, vf, x);
        assertEquals("s.f=\"x\"", a.minimalOutput());

        EvaluationResult context = context(evaluationContext(Map.of("s", abc, "f", NullConstant.NULL_CONSTANT)));
        EvaluationResult result = a.evaluate(context, ForwardEvaluationInfo.DEFAULT);

        assertEquals(1, result.messages().size());
    }
}
