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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking1A extends CommonTestRunner {

    public Test_Linking1A() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            switch (d.methodInfo().name) {
                case "s0m" -> {
                    if ("0".equals(d.statementId())) {
                        assertEquals("supplier::get", d.evaluationResult().value().toString());
                        assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(), it(0, "s:0,supplier:4"));
                    }
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "s0" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                    }
                    case "s0l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, "s:4,supplier:4"));
                            assertSingleLv(d, 2, 0, "*-4-0");
                            assertSingleLv(d, 2, 1, "*-4-0");
                        }
                    }
                    case "s0m" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "nullable instance 1 type X");
                            assertLinked(d, it(0, "s:4,supplier:4"));
                        }
                    }
                    case "s0a" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, "s:4,supplier:4")); //FIXME no link at all, even though s-4-supplier
                        }
                    }
                    case "s1" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "s1l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "supplier.get()");
                            assertLinked(d, it(0, 1, "s:-1,supplier:-1"), it(2, "s:4,supplier:4"));
                            assertSingleLv(d, 2, 0, "*M-4-0M");
                            assertSingleLv(d, 2, 1, "*M-4-0M");
                        }
                    }
                    case "s2" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, ""));
                    }
                    case "s2l" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "supplier.get()");
                            assertLinked(d, it(0, ""));
                        }
                    }
                }
            }
            switch (d.methodInfo().name) {
                case "s0l" -> {
                    if ("0".equals(d.statementId()) && "s".equals(d.variableName())) {
                        assertCurrentValue(d, 0, "/*inline get*/supplier.get()/*{L supplier:4}*/");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 0, 0, "*-4-0");
                    }
                }
                case "s0m" -> {
                    if ("0".equals(d.statementId()) && "s".equals(d.variableName())) {
                        assertCurrentValue(d, 0, "supplier::get");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 0, 0, "0-4-0");
                    }
                }
                case "s0a" -> {
                    if ("0".equals(d.statementId()) && "s".equals(d.variableName())) {
                        assertCurrentValue(d, 0, "new $2(){public X get(){return supplier.get();}}");
                        assertLinked(d, it(0, "supplier:4"));
                        assertSingleLv(d, 0, 0, "*-4-0");
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name) && "s0a".equals(d.enclosingMethod().name)) {
                LinkedVariables lvs = d.methodAnalysis().getLinkedVariables();
                assertLinked(d, lvs, it(0, "supplier:4"));
                assertSingleLv(d, lvs, 0, 0, "*-4-0");
                assertDv(d, 0, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                assertEquals("$2:X - get:", d.methodInfo().methodResolution.get().hiddenContentTypes().toString());
                if( d.methodAnalysis().getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
                    assertEquals(0, all.getHiddenContentIndex());
                } else fail();
            }
        };

        // finalizer on a parameter
        testClass("Linking_1A", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
