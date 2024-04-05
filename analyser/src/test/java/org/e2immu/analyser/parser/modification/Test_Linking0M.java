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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Linking0M extends CommonTestRunner {

    public Test_Linking0M() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("m1".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    String expected = d.iteration() < 2 ? "<m:get>" : "listM.get(index)";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertLinked(d, d.evaluationResult().linkedVariablesOfExpression(),
                            it(0, 1, "m:0,this.listM:-1,this:-1"),
                            it(2, "m:0,this.listM:4,this:4"));
                    assertSingleLv(d, 2, 1, "*-4-*");
                    assertSingleLv(d, 2, 2, "*-4-*");
                    if (d.iteration() >= 2) {
                        assertTrue(d.evaluationResult().changeData().values().stream()
                                .noneMatch(cd -> cd.linkedVariables().isDelayed()));
                    }
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("m1".equals(d.methodInfo().name)) {
                if ("m".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "this.listM:-1,this:-1"),
                                it(2, "this.listM:4,this:4"));
                        assertSingleLv(d, 2, 0, "*-4-*"); // FIXME this is not a good notation
                        assertSingleLv(d, 2, 1, "*M-4-0M");
                    }
                }
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("Linking_0M", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
