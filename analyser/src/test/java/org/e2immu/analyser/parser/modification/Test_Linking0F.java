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
import org.e2immu.analyser.analyser.HiddenContentTypes;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking0F extends CommonTestRunner {

    public Test_Linking0F() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {


        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "getI" -> {
                        assertCurrentValue(d, 1, "i$0");
                        assertLinked(d, it(0, "this.i:0"));
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            HiddenContentSelector hcs = d.methodAnalysis().getHiddenContentSelector();
            String expectHcs = switch (d.methodInfo().name) {
                case "getT", "getM" -> "*";
                case "getI", "getFm", "getListFM", "getFarFM", "getListI", "getFarI" -> "X";
                case "getFar" -> "X"; // IMPROVE Far is non-extensible, but has (unspecified) type parameters
                case "getListT", "getFarT" -> "0"; // 0=0, 0=type param T, 0=index in List
                case "getListM", "getFarM" -> "1=0"; // 1=extensible field type M, 0=index in List
                default -> null;
            };
            if (expectHcs != null) {
                assertEquals(expectHcs, hcs.toString(), "For method " + d.methodInfo());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            HiddenContentTypes hiddenContentTypes = d.typeInfo().typeResolution.get().hiddenContentTypes();
            String expectHct = switch (d.typeInfo().simpleName) {
                case "Linking_0F", "FM", "M", "E" -> "";
                case "C1" -> "M, T";
                case "FE", "FME" -> "E";
                case "FAR" -> "L";
                default -> null;
            };
            if (expectHct != null) {
                assertEquals(expectHct, hiddenContentTypes.sortedTypes(), "For type " + d.typeInfo());
            }
            Boolean expectTypeIsExtensible = switch (d.typeInfo().simpleName) {
                case "M", "E", "C1", "Linking_0F" -> true;
                case "FAR", "FE", "FM", "FME" -> false;
                default -> null;
            };
            if (expectTypeIsExtensible != null) {
                assertEquals(expectTypeIsExtensible, hiddenContentTypes.isTypeIsExtensible(),
                        d.typeInfo().fullyQualifiedName);
            }
            Boolean expectHasHiddenContent = switch (d.typeInfo().simpleName) {
                case "M", "E", "C1", "Linking_0F", "FE", "FAR", "FME" -> true;
                case "FM" -> false;
                default -> null;
            };
            if (expectHasHiddenContent != null) {
                assertEquals(expectHasHiddenContent, hiddenContentTypes.hasHiddenContent(),
                        d.typeInfo().fullyQualifiedName);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("Linking_0F", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
