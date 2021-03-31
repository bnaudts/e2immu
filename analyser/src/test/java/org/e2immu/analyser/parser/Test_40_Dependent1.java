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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_40_Dependent1 extends CommonTestRunner {
    public Test_40_Dependent1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("Dependent1_0", 0, 0,
                new DebugConfiguration.Builder()
                        .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo t && "t".equals(t.name)) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        assertEquals(MultiLevel.DEPENDENT, d.getProperty(VariableProperty.CONTEXT_DEPENDENT));
                    }
                    if ("2".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        int expectDependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                        assertEquals(expectDependent, d.getProperty(VariableProperty.CONTEXT_DEPENDENT));
                    }
                }
            }
        };
        testClass("Dependent1_1", 0, 0,
                new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }
}
