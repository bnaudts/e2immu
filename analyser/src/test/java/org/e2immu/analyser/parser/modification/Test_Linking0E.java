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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Linking0E extends CommonTestRunner {

    public Test_Linking0E() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            switch (d.methodInfo().name) {
                case "m1" -> {
                    String expectedLv = d.iteration() < 2 ? "list:-1" : "list:4";
                    assertEquals(expectedLv, d.evaluationResult().linkedVariablesOfExpression().toString());
                    ChangeData cd = d.findValueChangeByToString("m1");
                    assertEquals(expectedLv, cd.linkedVariables().toString());
                    assertSingleLv(d, d.evaluationResult().linkedVariablesOfExpression(), 2, 0,
                            "*M-4-0M");
                }
                case "m7" -> {
                    String expectedLv = d.iteration() < 2 ? "list:-1" : "list:4";
                    assertEquals(expectedLv, d.evaluationResult().linkedVariablesOfExpression().toString());
                    ChangeData cd = d.findValueChangeByToString("m7");
                    assertEquals(expectedLv, cd.linkedVariables().toString());
                    assertSingleLv(d, d.evaluationResult().linkedVariablesOfExpression(), 2, 0,
                            "0M-4-0M");
                }
                case "m10b" -> {
                    String expectedLv = d.iteration() == 0 ? "map:-1" : "map:4";
                    assertEquals(expectedLv, d.evaluationResult().linkedVariablesOfExpression().toString());
                }
                case "m16" -> {
                    String expectedLv = d.iteration() < 2 ? "list:-1" : "list:4";
                    assertEquals(expectedLv, d.evaluationResult().linkedVariablesOfExpression().toString());
                }
                default -> {
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "m0" -> {
                        assertCurrentValue(d, 0, "list.get(0)");
                        assertLinked(d, it(0, ""));
                    }
                    case "m1" -> {
                        assertCurrentValue(d, 2, "list.get(0)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "m1b" -> {
                        assertCurrentValue(d, 2, "(new ArrayList<>(list)).get(0)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                        assertSingleLv(d, 2, 0, "*M-4-0M");
                    }
                    case "m2" -> {
                        assertCurrentValue(d, 1, "list.get(0)");
                        assertLinked(d, it0("list:-1"), it(1, "list:4"));
                        assertSingleLv(d, 2, 0, "*-4-0");
                    }
                    case "m3" -> {
                        assertCurrentValue(d, 0, "list.subList(0,1)");
                        assertLinked(d, it(0, "list:2"));
                    }
                    case "m4", "m5" -> {
                        assertCurrentValue(d, 2, "list.subList(0,1)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:2"));
                    }
                    case "m6" -> {
                        assertCurrentValue(d, 0, "new ArrayList<>(list)");
                        assertLinked(d, it(0, ""));
                    }
                    case "m7", "m8" -> {
                        assertCurrentValue(d, 0, "new ArrayList<>(list)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                    case "m9" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it(0, 1, "map:-1"), it(2, "map:4"));
                        assertSingleLv(d, 2, 0, "1M-4-1M");
                    }
                    case "m9b" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it0("map:-1"), it(1, "map:4"));
                        assertSingleLv(d, 1, 0, "1-4-1");
                    }
                    case "m10" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it(0, 1, "map:-1"), it(2, "map:4"));
                        assertSingleLv(d, 2, 0, "0M-4-0M");
                    }
                    case "m10b" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it0("map:-1"), it(1, "map:4"));
                        assertSingleLv(d, 2, 0, "1-4-1");
                    }
                    case "m11", "m13b" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it(0, 1, "map:-1"), it(2, "map:4"));
                        assertSingleLv(d, 2, 0, "0M,1-4-0M,1");
                    }
                    case "m12" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it(0, ""));
                    }
                    case "m13" -> {
                        assertCurrentValue(d, 0, "new HashMap<>(map)/*this.size()==map.size()*/");
                        assertLinked(d, it(0, 1, "map:-1"), it(2, "map:4"));
                        assertSingleLv(d, 2, 0, "0M,1M-4-0M,1M");
                    }
                    case "m14", "m15" -> {
                        assertCurrentValue(d, 2, "list.subList(0,1).subList(0,1)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:2"));
                        assertSingleLv(d, 2, 0, "0M-2-0M");
                    }
                    case "m16" -> {
                        assertCurrentValue(d, 2, "new ArrayList<>(list.subList(0,1).subList(0,1))");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                    }
                    case "m16b" -> {
                        assertCurrentValue(d, 1, "new ArrayList<>(list.subList(0,1).subList(0,1))");
                        assertLinked(d, it0("list:-1"), it(1, "list:4"));
                        assertSingleLv(d, 1, 0, "0-4-0");
                    }
                    case "m17" -> {
                        assertCurrentValue(d, 2, "(new ArrayList<>(list.subList(0,1))).subList(0,1)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:4"));
                    }
                    case "m18" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "list");
                            assertLinked(d, it(0, 1, "list:0,x:-1"), it(2, "list:0,x:4"));
                        }
                    }
                    case "m18b" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "list");
                            assertLinked(d, it0("list:0,x:-1"), it(1, "list:0,x:4"));
                            assertSingleLv(d, 1, 1, "0-4-*");
                        }
                    }
                    case "m19" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "list");
                            assertLinked(d, it(0, 1, "list:0,x0:-1,x1:-1"),
                                    it(0, "list:0,x0:4,x1:4"));
                        }
                    }
                    case "m20", "m23" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 0, "list");
                            assertLinked(d, it(0, "list:0"));
                        }
                    }
                    case "m21", "m21b" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "list");
                            assertLinked(d, it(0, 1, "list:0,m:-1"),
                                    it(2, "list:0,m:4"));
                        }
                    }
                    case "m21c" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "list");
                            assertLinked(d, it0("list:0,m:-1"), it(1, "list:0,m:4"));
                            assertSingleLv(d, 1, 1, "0-4-*");
                        }
                    }
                    case "m22" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 2, "list");
                            assertLinked(d, it(0, 1, "list:0,x0:-1,x1:-1"),
                                    it(2, "list:0,x0:4,x1:4"));
                        }
                    }
                    case "m24" -> {
                        if ("1".equals(d.statementId())) {
                            assertCurrentValue(d, 1, "collection$0");
                            assertLinked(d, it(0, "collection:0"));
                        }
                    }
                    default -> {
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeInspection().isExtensible());
                assertDv(d, 0, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("M".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("Linking_0E", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


}
