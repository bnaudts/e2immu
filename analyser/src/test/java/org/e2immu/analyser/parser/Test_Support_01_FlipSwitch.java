
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Support_01_FlipSwitch extends CommonTestRunner {

    public Test_Support_01_FlipSwitch() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                String expectValue = "<variable value>";
                assertEquals(expectValue, d.fieldAnalysis().getEffectivelyFinalValue().toString());
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof LocalVariableReference lvr &&
                        lvr.variable.isLocalCopyOf() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertEquals("t$0", d.variableInfo().variable().simpleName());
                    String expectAssigned = d.statementId().startsWith("0.0.0") ? "this.t" : "";
                    //assertEquals(expectAssigned, d.variableInfo().getStaticallyAssignedVariables().toString(),
                    //        "Statement " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                        assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    }
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FlipSwitch".equals(d.typeInfo().simpleName)) {
                String expectE2 = d.iteration() <= 1 ? "{}" : "{t=!t}";
                assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        testSupportClass(List.of("FlipSwitch"), 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

}
