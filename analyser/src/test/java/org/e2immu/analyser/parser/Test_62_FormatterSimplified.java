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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// sub-records in records; an issue because of JavaParser 3.22.1

public class Test_62_FormatterSimplified extends CommonTestRunner {

    public Test_62_FormatterSimplified() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ForwardInfo".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInspection().isStatic());
            }
        };
        testClass("FormatterSimplified_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("writer.apply(new ForwardInfo(start,9,null,false))",
                            d.evaluationResult().getExpression().toString());
                    assertEquals("Type java.lang.Boolean", d.evaluationResult().getExpression().returnType().toString());
                }
            }
        };
        testClass("FormatterSimplified_1", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("FormatterSimplified_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("FormatterSimplified_3", 4, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("forward".equals(d.methodInfo().name)) {
                if(d.variableName().contains("lastOneWasSpace")) {
                    System.out.println(d.variableName()+": "+d.statementId());
                }
                if ("lastOneWasSpace$4$4.0.0:M".equals(d.variableName())) {
                    if ("4.0.0".equals(d.statementId())) {
                        assertEquals("", d.currentValue().toString());
                    }
                    if ("4.0.1.0.0".equals(d.statementId())) {
                        assertEquals("", d.currentValue().toString());
                    }
                }
            }
        };
        testClass("FormatterSimplified_4", 4, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
