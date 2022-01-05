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
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
This is an example of a cyclic dependency between

1. c = new C1(...) in example1, which needs @Container to produce a value (@Container is one of the value properties)
2. the field "set" is modified in example1, but this can only be seen when there are no delays on the evaluation,
yet we have delays caused by "c"
 */
public class Test_16_Modification_19 extends CommonTestRunner {

    public Test_16_Modification_19() {
        super(true);
    }

    @Test
    public void test19() throws IOException {
        final int LIMIT = 3;

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("example1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "s2".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, LIMIT, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = "nullable instance type Set<String>";
                        String expectedDelay = switch (d.iteration()) {
                            case 0 -> "container@Class_C1;immutable@Class_C1;independent@Class_C1";
                            case 1 -> "assign_to_field@Parameter_setC;initial:this.set@Method_size_0;link:this.set@Method_size_0";
                            case 2 -> "immutable@Class_C1;independent@Parameter_setC;initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                            default -> "initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                        };
                        assertCurrentValue(d, LIMIT, expectedDelay, expectValue);

                        String linkDelay = switch (d.iteration()) {
                            case 0 -> "immutable@Class_C1;independent@Parameter_setC";
                            case 1 -> "initial:this.set@Method_size_0;link:this.set@Method_size_0";
                            default -> "initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                        };
                        assertLinked(d, LIMIT, linkDelay, "this.s2");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "c".equals(fr.scope.toString())) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() < LIMIT ? "<f:set>" : "nullable instance type Set<String>";
                        assertEquals(expectValue, d.currentValue().toString());

                        // delays in iteration 1, because no value yet
                        String expectedDelay = d.iteration() == 0
                                ? "immutable@Class_C1;independent@Parameter_setC;link:this.s2@Method_example1_2"
                                : "initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                        assertDv(d, expectedDelay, LIMIT, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("size".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:set>" : "nullable instance type Set<String>";
                    assertEquals(expectValue, d.currentValue().toString());

                    String expectedDelay = "initial:this.set@Method_size_0;link:this.set@Method_size_0";
                    assertDv(d, expectedDelay, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                String expectedDelay = d.iteration() == 0 ? "cm@Parameter_setC;mom@Parameter_setC" : "mom@Parameter_setC";
                assertDv(d.p(0), expectedDelay, LIMIT, DV.TRUE_DV, Property.MODIFIED_VARIABLE);

                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                String expectAssigned = d.iteration() == 0 ? "[]" : "[set]";
                assertEquals(expectAssigned, p0.getAssignedToField().keySet().toString());
            }
            if ("addAll".equals(d.methodInfo().name)) {
                assertEquals(DV.FALSE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("size".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                String expectedDelay = "initial:this.set@Method_size_0;link:this.set@Method_size_0";
                assertDv(d, expectedDelay, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertEquals("setC", d.fieldAnalysis().getValue().toString());
                assertTrue(d.fieldAnalysis().getValue().isDone());

                assertEquals("setC:0", d.fieldAnalysis().getLinkedVariables().toString());
                assertTrue(((FieldAnalysisImpl.Builder) d.fieldAnalysis()).allLinksHaveBeenEstablished().isDone());

                String expectedDelay = d.iteration() == 0
                        ? "immutable@Class_C1;independent@Parameter_setC;initial:this.set@Method_size_0;link:this.s2@Method_example1_2;link:this.set@Method_size_0"
                        : "initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                assertDv(d, expectedDelay, LIMIT, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("C1".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
                String expectedDelay = d.iteration() == 0
                        ? "initial:this.set@Method_size_0;link:this.set@Method_size_0"
                        : "initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                assertDv(d, expectedDelay, LIMIT, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);

                String expectContainerDelay = switch (d.iteration()) {
                    case 0 -> "assign_to_field@Parameter_setC";
                    case 1 -> "immutable@Class_C1;independent@Parameter_setC;initial:this.set@Method_size_0;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                    default -> "initial:this.set@Method_size_0;link:c@Method_example1_2;link:this.s2@Method_example1_2;link:this.set@Method_size_0";
                };
                assertDv(d, expectContainerDelay, LIMIT, DV.FALSE_DV, Property.CONTAINER);
            }
        };

        testClass("Modification_19", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }
}
