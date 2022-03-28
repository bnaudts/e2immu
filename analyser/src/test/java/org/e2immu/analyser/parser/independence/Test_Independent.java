
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

package org.e2immu.analyser.parser.independence;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_Independent extends CommonTestRunner {

    public Test_Independent() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("Independent_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("Independent_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("Independent_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("methodAnalyserStream".equals(d.methodInfo().name)) {
                if ("AnalyserContext".equals(d.methodInfo().typeInfo.simpleName)) {
                    assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                    assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("MethodAnalyser".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.DEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testClass("Independent_4", 0, 4, new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true).build());
    }
}
