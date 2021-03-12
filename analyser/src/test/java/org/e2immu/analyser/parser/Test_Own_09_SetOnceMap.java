
/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.ParameterizedType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/*
first issue: import of Collector.Characteristics should be automatic
second issue: UnevaluatedLambdaExpression exception
 */
public class Test_Own_09_SetOnceMap extends CommonTestRunner {

    public Test_Own_09_SetOnceMap() {
        super(true);
    }

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if("SetOnceMap".equals(d.typeInfo().simpleName)) {
            Assert.assertEquals("Type param K, Type param V", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                    .stream().map(ParameterizedType::toString).sorted().collect(Collectors.joining(", ")));
        }
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("SetOnceMap"), 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
