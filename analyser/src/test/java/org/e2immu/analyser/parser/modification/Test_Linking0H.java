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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Linking0H extends CommonTestRunner {

    public Test_Linking0H() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("create".equals(d.methodInfo().name)) {
                if ("mList".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 2, "List.of(m)");
                        assertLinked(d, it(0, ""));
                    }
                }
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Linking_0H<>(List.of(m))");
                    assertLinked(d, it(0, 1, "m:-1,mList:-1"), it(2, "mList:4"));
                    assertSingleLv(d, 2, 0, "0M-4-0M");
                    // FIXME need a link  0M-4-*M to m
                }
            }
            // create2 does the same as List.of(..), but then in 2 steps
            if ("create2".equals(d.methodInfo().name)) {
                if ("mList".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertCurrentValue(d, 0, "instance 1 type ArrayList<M>/*1==this.size()&&this.contains(m)*/");
                        assertLinked(d, it(0, 1, "m:-1"), it(2, "m:4"));
                        assertSingleLv(d, 2, 0, "0M-4-*M");
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    assertCurrentValue(d, 0, "new Linking_0H<>(mList$1)");
                    assertLinked(d, it(0, 1, "m:-1,mList:-1"), it(2, "m:4,mList:4"));
                    assertSingleLv(d, 2, 0, "0M-4-*M");
                    assertSingleLv(d, 2, 1, "0M-4-0M");
                }
            }
            if ("getList".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 1, "new ArrayList<>(list)");
                    assertLinked(d, it0("this.list:-1,this:-1"), it(1, "this.list:4,this:4"));
                    assertSingleLv(d, 1, 0, "0-4-0");
                    assertSingleLv(d, 1, 1, "0-4-0");
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            LinkedVariables lvs = d.fieldAnalysis().getLinkedVariables();
            if ("list".equals(d.fieldInfo().name)) {
                assertLinked(d, lvs, it(0, "list:4"));
                assertSingleLv(d, lvs, 0, 0, "0-4-0");
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("Linking_0H", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
