
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.*;
import org.junit.Assert;
import org.junit.Test;
import java.io.IOException;

public class TestDynamicTypeAnnotation extends CommonTestRunner {

    public TestDynamicTypeAnnotation() {
        super(true);
    }

    private final StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if("modifySetCreated".equals(d.methodInfo.name)) {
            Assert.assertTrue(d.numberedStatement.errorValue.isSet());
        }
        if("modifySet1".equals(d.methodInfo.name) && d.iteration > 0) {
            Assert.assertTrue(d.numberedStatement.errorValue.isSet());
        }
    };

    @Test
    public void test() throws IOException {
        testClass("DynamicTypeAnnotation", 2, 0, new DebugConfiguration.Builder()
        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                       .build());
    }

}
