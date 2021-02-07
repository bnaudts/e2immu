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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.annotation.E2Container;

@E2Container
public interface Statement extends Element {

    Structure getStructure();

    @Override
    default Statement translate(TranslationMap translationMap) {
        return this;
    }

    OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis);

    @Override
    default OutputBuilder output(Qualification qualification) {
        throw new UnsupportedOperationException("Use other output method");
    }

    @Override
    default String minimalOutput() {
        return output(Qualification.EMPTY, null).toString();
    }

    default OutputBuilder messageComment(StatementAnalysis statementAnalysis) {
        if (statementAnalysis != null && !statementAnalysis.messages.isEmpty()) {
            OutputBuilder outputBuilder = new OutputBuilder();
            statementAnalysis.messages.stream().forEach(message -> outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                    .add(new Text(message.message)).add(Symbol.RIGHT_BLOCK_COMMENT));
            return outputBuilder;
        }
        return null;
    }
}
