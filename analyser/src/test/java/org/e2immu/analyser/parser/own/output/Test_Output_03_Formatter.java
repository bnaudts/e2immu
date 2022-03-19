
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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Output_03_Formatter extends CommonTestRunner {

    public Test_Output_03_Formatter() {
        super(true);
    }

    // without the whole formatter package
    @Test
    public void test_0() throws IOException {
        testSupportAndUtilClasses(List.of(ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                6, 15, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    // the real deal
    @Test
    public void test_1() throws IOException {
        int BIG = 20;
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "NOT_END".equals(fr.fieldInfo.name)) {
                    if ("5".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("6".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("6.0.0".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("pop".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("tab".equals(d.variableName())) {
                    if ("0.0.3".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<m:pop>" : "nullable instance type Tab";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    assertNotEquals("0", d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "writer".equals(fr.fieldInfo.name)) {
                    if ("tab".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            String expected = d.iteration() < 2 ? "<f:writer>" : "nullable instance type Writer";
                            assertEquals(expected, d.currentValue().toString());
                        }
                        if ("0.0.3".equals(d.statementId())) {
                            String expected = d.iteration() < 2 ? "<f:writer>" : "nullable instance type Writer";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if ("<m:pop>".equals(fr.scope.toString())) {
                        assertEquals("0", d.statementId());
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.DelayedScope);
                        assertTrue(d.iteration() < 2);
                    } else if ("tabs.peek()".equals(fr.scope.toString())) {
                        if ("0.0.3".equals(d.statementId())) {
                            assertEquals("nullable instance type Writer", d.currentValue().toString());
                        }
                    } else if ("nullable instance type Tab".equals(fr.scope.toString())) {
                        if ("0.0.3".equals(d.statementId())) {
                            assertEquals("nullable instance type Writer", d.currentValue().toString());
                        }
                    } else fail("scope " + fr.scope);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("NOT_END".equals(d.fieldInfo().name)) {
                assertDv(d, 5, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("writer".equals(d.fieldInfo().name) && "Tab".equals(d.fieldInfo().owner.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
                assertEquals("new StringWriter()", ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
                assertTrue(d.fieldAnalysis().valuesDelayed().isDone());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pop".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                String delay = "initial:tab.writer@Method_pop_0.0.3-C";
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("swap".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("writer".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() == 0 ? "<m:writer>" : "tabs.isEmpty()?writer:tabs.peek().writer$0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("NewLineDouble".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testSupportAndUtilClasses(List.of(Formatter.class,
                        Forward.class, Lookahead.class, CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                26, 50, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
