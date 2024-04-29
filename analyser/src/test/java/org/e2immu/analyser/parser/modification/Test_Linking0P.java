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
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking0P extends CommonTestRunner {

    public Test_Linking0P() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            LinkedVariables lvsExpression = d.evaluationResult().linkedVariablesOfExpression();
            if ("create".equals(d.methodInfo().name)) {

            }

        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("create0".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "new Pair<>(x,y)");
                    assertLinked(d, it(0, 1, "x:-1,y:-1"), it(0, "x:4,y:4"));
                    assertSingleLv(d, 2, 0, "0-4-*");
                    assertSingleLv(d, 2, 1, "1-4-*");
                }
            }
            // FIXME 2 needs to be 4, rest is fine
            if ("create1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertCurrentValue(d, 2, "new Pair<>(x,m)");
                    assertLinked(d, it(0, 1, "m:-1,x:-1"), it(0, "m:4,x:4"));
                    assertSingleLv(d, 2, 0, "1M-4-*M");
                    assertSingleLv(d, 2, 1, "0-4-*");
                }
            }
            if ("create2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(x,m)");
                    assertLinked(d, it(0, 1, "m:-1,p:0,x:-1"), it(0, "m:2,p:0,x:4"));
                    assertSingleLv(d, 2, 0, "1M-2-*M");
                    assertSingleLv(d, 2, 2, "0-4-*");
                }
            }
            if ("create3".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(n,m)");
                    assertLinked(d, it(0, 1, "m:-1,n:-1,p:0"), it(0, "m:2,n:2,p:0"));
                    assertSingleLv(d, 2, 0, "1M-2-*M");
                    assertSingleLv(d, 2, 1, "0M-2-*M");
                }
            }
            if ("create4".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(i,m)");
                    assertLinked(d, it(0, 1, "m:-1,p:0"), it(0, "m:2,p:0"));
                    assertSingleLv(d, 2, 0, "1M-2-*M");
                }
            }
        };


        testClass("Linking_0P", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
