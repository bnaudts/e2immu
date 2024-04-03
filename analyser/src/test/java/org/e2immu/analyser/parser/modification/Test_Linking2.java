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

import org.e2immu.analyser.analyser.DV;
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

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Linking2 extends CommonTestRunner {

    public Test_Linking2() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            switch (d.methodInfo().name) {
                case "m1" -> {
                    if ("2".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertLinked(d, it(0, "selection:0"));
                    }
                }
                case "m2" -> {
                    if ("1.0.0".equals(d.statementId()) && "x".equals(d.variableName())) {
                        assertLinked(d, it(0, 1, "selection:-1,selector:-1,xs:-1"),
                                it(2, "selection:4,selector:4,xs:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                        assertSingleLv(d, 2, 1, "*-4-0"); // FIXME ??
                        assertSingleLv(d, 2, 2, "*-4-0");
                    }
                    if ("2".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertLinked(d, it(0, 1, "selection:0,selector:-1,xs:-1"),
                                it(2, "selection:0,selector:4,xs:4"));
                    }
                }
                case "m3" -> {
                    if ("1.0.0".equals(d.statementId()) && "m".equals(d.variableName())) {
                        assertLinked(d, it(0, 1, "ms:-1,selection:-1,selector:-1"),
                                it(2, "ms:4,selection:4,selector:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    if ("2".equals(d.statementId()) && d.variable() instanceof ReturnVariable) {
                        assertCurrentValue(d, 2,
                                "ms.isEmpty()?new ArrayList<>()/*0==this.size()*/:selector.test(nullable instance 1 type M)?instance 1.0.0.0.0 type List<M>:instance 1 type List<M>");
                        assertLinked(d, it(0, 1, "ms:-1,selection:0,selector:-1"),
                                it(2, "ms:4,selection:0,selector:4"));
                    }
                }
            }
        };


        testClass("Linking_2", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
