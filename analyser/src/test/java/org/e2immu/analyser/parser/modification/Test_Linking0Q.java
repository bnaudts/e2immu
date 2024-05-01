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
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.modification.testexample.Linking_0Q;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking0Q extends CommonTestRunner {

    public Test_Linking0Q() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("viaR0".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable && "1".equals(d.statementId())) {
                    // FIXME, ideally we link 0,1-4-0,1 to "in"
                    assertCurrentValue(d, 2, "in");
                    assertLinked(d, it(0, 1, "in:-1,r.s:0,r:-1"), it(2, "in:4,r.s:0,r:4"));
                    assertSingleLv(d, 2, 0, "0-4-*"); // at the level of "in", not f,g
                    assertSingleLv(d, 2, 2, "0,1-4-0,1"); // at which level???
                }
            }
            if ("viaR1".equals(d.methodInfo().name)) {
                if ("p".equals(d.variableName())) {
                    assertCurrentValue(d, 2, "new Pair<>(x,y)");
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "x:-1,y:-1"), it(2, "x:4,y:4"));
                        assertSingleLv(d, 2, 0, "0-4-*");
                        assertSingleLv(d, 2, 1, "1-4-*");
                    }
                    if ("1".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "r:-1,x:-1,y:-1"), it(2, "r:4,x:4,y:4"));
                        assertSingleLv(d, 2, 0, "0,1-4-0.0,0.1");
                        assertSingleLv(d, 2, 1, "0-4-*");
                        assertSingleLv(d, 2, 1, "1-4-*");
                    }
                }
                if ("r".equals(d.variableName()) && "1".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new R<>(new Pair<>(x,y))");
                    assertEquals("Type %.R<%.Pair<X,Y>>", d.variable().parameterizedType().toString()
                            .replace(Linking_0Q.class.getCanonicalName(), "%"));
                    assertLinked(d, it(0, 1, "p:-1,x:-1,y:-1"), it(2, "p:4,x:4,y:4"));
                    assertSingleLv(d, 2, 0, "0.0,0.1-4-0,1");
                    assertSingleLv(d, 2, 1, "0.0-4-*");
                    assertSingleLv(d, 2, 2, "0.1-4-*");
                }
                if (d.variable() instanceof ParameterInfo pi && "y".equals(pi.name)) {
                    assertCurrentValue(d, 2, "nullable instance type Y");
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "p:-1,x:-1"), it(2, "p:4"));
                        assertSingleLv(d, 2, 0, "*-4-1");
                    }
                    if ("1".equals(d.statementId())) {
                        assertLinked(d, it(0, 1, "p:-1,r:-1,x:-1"), it(2, "p:4,r:4"));
                        assertSingleLv(d, 2, 0, "*-4-1");
                        assertSingleLv(d, 2, 0, "*-4-0.1");
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    assertCurrentValue(d, 2, "new Pair<>(x,y)");
                    assertLinked(d, it(0, 1, "p:-1,r.s:0,r:-1,x:-1,y:-1"),
                            it(2, "p:4,r.s:0,r:4,x:4,y:4"));
                    assertSingleLv(d, 2, 0, "0-4-*"); // at the level of "p", not f,g
                    assertSingleLv(d, 2, 2, "0,1-4-0,1"); // at which level???
                    assertSingleLv(d, 2, 3, "0-4-*");
                    assertSingleLv(d, 2, 4, "0-4-*");
                }
            }

        };


        testClass("Linking_0Q", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
