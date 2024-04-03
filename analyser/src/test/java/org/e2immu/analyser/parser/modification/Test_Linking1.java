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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.ChangeData;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking1 extends CommonTestRunner {

    public Test_Linking1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "m0" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                    }
                    case "m1" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "m2" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, ""));
                    }
                    case "m3" -> {
                        assertCurrentValue(d, 2, "stream.filter(/*inline test*/3==m.i$0)");
                        assertLinked(d, it(0, 1, "stream:-1"), it(2, "stream:2"));
                    }
                    case "m4" -> {
                        assertCurrentValue(d, 2, "stream.filter(/*inline test*/3==m.i$0).findFirst()");
                        assertLinked(d, it(0, 1, "stream:-1"), it(2, "stream:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                    case "m5" -> {
                        assertCurrentValue(d, 2, "stream.filter(/*inline test*/3==m.i$0).findFirst().orElseThrow()");
                        assertLinked(d, it(0, 1, "stream:-1"), it(2, "stream:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "m6" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/3==i)");
                        assertLinked(d, it(0, "stream:2"));
                    }
                    case "m7" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/3==i).findFirst()");
                        assertLinked(d, it(0, ""));
                    }
                    case "m8" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/3==i).findFirst().orElseThrow()");
                        assertLinked(d, it(0, ""));
                    }
                    case "mPredicate" -> {
                        assertCurrentValue(d, 0, "predicate.test(x)");
                        assertLinked(d, it(0, ""));
                    }
                    case "m9" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/predicate.test(i))");
                        assertLinked(d, it(0, "stream:2"));
                    }
                    case "m10" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/predicate.test(x)).findFirst()");
                        assertLinked(d, it(0, "stream:4"));
                    }
                    case "test" -> {
                        if ("$8".equals(d.methodInfo().typeInfo.simpleName)) {
                            assertEquals("m10", d.enclosingMethod().name);
                            assertCurrentValue(d, 0, "predicate.test(x)");
                            assertLinked(d, it0("NOT_YET_SET"), it(1, "")); // important! should not become predicate:4
                        }
                    }
                    case "m11" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/predicate.test(i)).findFirst().orElseThrow()");
                        assertLinked(d, it(0, "stream:4"));
                    }
                    case "m12" -> {
                        assertCurrentValue(d, 1, "stream.map(/*inline apply*/function.apply(x)/*{L function:4}*/)");
                        assertLinked(d, it0("function:-1,stream:-1"), it(1, "function:4,stream:2"));
                    }
                    case "m12b" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "stream.map(/*inline apply*/function.apply(x)/*{L function:4}*/)");
                            assertLinked(d, it0("f:-1,function:-1,stream:-1"), it(1, "f:4,function:4,stream:2"));
                        }
                    }
                    case "m13" -> {
                        assertCurrentValue(d, 0, "stream.map(function::apply)");
                        assertLinked(d, it(0, "function:4,stream:2"));
                    }
                    case "m14" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "out");
                            assertLinked(d, it(0, "out:0"));
                        }
                    }
                    case "m15" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "out");
                            assertLinked(d, it(0, "in:4,out:0"));
                        }
                    }
                    case "m15b" -> {
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "out");
                            assertLinked(d, it(0, "add:4,in:4,out:0"));
                        }
                    }
                    case "m16" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "out");
                            assertLinked(d, it(0, 1, "in:-1,out:0"), it(0, "in:4,out:0"));
                        }
                    }
                    case "m17" -> {
                        assertCurrentValue(d, 1, "IntStream.of(3).mapToObj(/*inline apply*/supplier.get()/*{L supplier:4}*/)");
                        assertLinked(d, it0("supplier:-1"), it(1, "supplier:4"));
                    }
                    case "m18" -> {
                        assertCurrentValue(d, 2, "IntStream.of(3).mapToObj(/*inline apply*/supplier.get()/*{L supplier:4}*/)");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                    case "m19" -> {
                        assertCurrentValue(d, 1, "IntStream.of(3).mapToObj(/*inline apply*/list.get(i)/*{L list:4}*/)");
                        assertLinked(d, it0("list:-1"), it(1, "list:4"));
                    }
                    case "m20" -> {
                        assertCurrentValue(d, 2, "IntStream.of(3).mapToObj(/*inline apply*/list.get(i)/*{L list:4}*/)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                    case "m21" -> {
                        assertCurrentValue(d, 0, "IntStream.of(3).mapToObj(list::get)");
                        assertLinked(d, it(0, "list:4"));
                    }
                    case "m22" -> {
                        assertCurrentValue(d, 0, "IntStream.of(3).mapToObj(list::get)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                    }
                    case "m22b" -> {
                        if ("1".equals(d.statementId())) {
                            // "get" is expanded to "list::get"
                            assertCurrentValue(d, 0, "IntStream.of(3).mapToObj(list::get)");
                            assertLinked(d, it(0, 1, "get:-1,list:-1"), it(2, "get:4,list:4"));
                        }
                    }
                    case "m23" -> {
                        assertCurrentValue(d, 1, "IntStream.of(3).mapToObj(new IntFunction<X>(){public X apply(int value){return list.get(value);}})");
                        assertLinked(d, it0("list:-1"), it(1, "list:4"));
                    }
                    case "m23b" -> {
                        if ("2".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "IntStream.of(3).mapToObj(new IntFunction<>(){public X apply(int value){return list.get(value);}})");
                            assertLinked(d, it0("f:-1,intStream:-1,list:-1"), it(1, "f:4,intStream:2,list:4"));
                        }
                    }
                    case "m24" -> {
                        assertCurrentValue(d, 2, "IntStream.of(3).mapToObj(new IntFunction<M>(){public M apply(int value){return list.get(value);}})");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                    default -> {
                    }
                }
            }
            switch (d.methodInfo().name) {
                case "mPredicate" -> {
                    // predicate.test(x) is no different from list.add(x), because Predicate.test is modifying!
                    assert "0".equals(d.statementId());
                    if (d.variable() instanceof ParameterInfo pi && "predicate".equals(pi.name)) {
                        assertLinked(d, it(0, "x:4"));
                        assertSingleLv(d, 0, 0, "0-4-*");
                    }
                    if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                        assertLinked(d, it(0, "predicate:4"));
                        assertSingleLv(d, 0, 0, "*-4-0");
                    }
                }
                case "test" -> {
                    if ("m10".equals(d.enclosingMethod().name)) {
                        assertEquals("$8", d.methodInfo().typeInfo.simpleName);
                        if (d.variable() instanceof ParameterInfo pi && "x".equals(pi.name)) {
                            assertLinked(d, it0("NOT_YET_SET"), it(1, "predicate:4"));
                        }
                    }
                }
                case "m12b" -> {
                    if ("0".equals(d.statementId()) && "f".equals(d.variableName())) {
                        assertLinked(d, it0("function:-1"), it(1, "function:4"));
                    }
                }
                case "m15b" -> {
                    if ("0".equals(d.statementId()) && "add".equals(d.variableName())) {
                        assertLinked(d, it(0, "out:4"));
                    }
                }
                case "m16" -> {
                    if ("1".equals(d.statementId())) {
                        if (d.variable() instanceof ParameterInfo pi && "in".equals(pi.name)) {
                            assertLinked(d, it(0, 1, "out:-1"), it(2, "out:4"));
                            assertSingleLv(d, 2, 0, "0M-4-0M");
                        }
                    }
                }
                case "m17b" -> {
                    if ("0".equals(d.statementId()) && "f".equals(d.variableName())) {
                        assertLinked(d, it0("supplier:-1"), it(1, "supplier:4"));
                    }
                }
                case "m22b" -> {
                    if ("0".equals(d.statementId()) && "get".equals(d.variableName())) {
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                }
                case "m23b" -> {
                    if ("0".equals(d.statementId()) && "f".equals(d.variableName())) {
                        assertLinked(d, it0("list:-1"), it(1, "list:4"));
                        assertSingleLv(d, 1, 0, "0-4-0");
                    }
                }
            }
        };

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("mPredicate".equals(d.methodInfo().name)) {
                testPredicateTestCall(d, "mPredicate");
            }
            if ("test".equals(d.methodInfo().name) && "$8".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("m10", d.methodInfo().typeInfo.typeInspection.get().enclosingMethod().name);
                assertEquals("predicate.test(x)", d.evaluationResult().value().toString());
                assertEquals("", d.evaluationResult().linkedVariablesOfExpression().toString());
                MethodAnalysis maOfLambdaMethod = d.evaluationResult().evaluationContext()
                        .getAnalyserContext().getMethodAnalysis(d.methodInfo());
                DV modified = maOfLambdaMethod.getProperty(Property.MODIFIED_METHOD);
                if (d.iteration() > 0) {
                    // in the lambda, no fields are being modified
                    assertEquals(DV.FALSE_DV, modified);
                } else {
                    assertTrue(modified.isDelayed());
                }
                // exactly the same as in 'mPredicate'
                testPredicateTestCall(d, "test");
            }
            if ("m23".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:mapToObj>"
                        : "IntStream.of(3).mapToObj(new IntFunction<X>(){public X apply(int value){return list.get(value);}})";
                assertEquals(expected, d.evaluationResult().value().toString());
                assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it0("list:-1"),
                        it(1, "list:4"));
                assertSingleLv(d, 1, 0, "0-4-*");
            }
            if ("m23b".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("new IntFunction<>(){public X apply(int value){return list.get(value);}}",
                            d.evaluationResult().value().toString());
                    assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it0("f:0,list:-1"),
                            it(1, "f:0,list:4"));
                    assertSingleLv(d, 1, 1, "*-4-0");
                }
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0 ? "<m:mapToObj>"
                            : "IntStream.of(3).mapToObj(new IntFunction<>(){public X apply(int value){return list.get(value);}})";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it(0, "f:4,intStream:2"));
                    assertSingleLv(d, 0, 0, "0-4-*"); // FIXME should be 0-4-0
                }
            }
        };

        // finalizer on a parameter
        testClass("Linking_1", 6, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    private void testPredicateTestCall(EvaluationResultVisitor.Data d, String returnValueName) {
        assertEquals(3, d.evaluationResult().changeData().size());
        ChangeData cdX = d.findValueChangeBySubString("0:x");
        assertNotNull(cdX);
        assertLinked(d, cdX.linkedVariables(), it(0, ""));
        ChangeData cdPredicate = d.findValueChangeBySubString("predicate");
        assertNotNull(cdPredicate);
        assertLinked(d, cdPredicate.linkedVariables(), it(0, "x:4"));
        ChangeData cdTest = d.findValueChangeByToString(returnValueName);
        assertNotNull(cdTest);
        assertLinked(d, cdTest.linkedVariables(), it(0, ""));
    }
}
