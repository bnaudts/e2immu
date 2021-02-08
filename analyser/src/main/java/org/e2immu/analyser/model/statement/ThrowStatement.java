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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.*;

public class ThrowStatement extends StatementWithExpression {

    public ThrowStatement(Expression expression) {
        super(new Structure.Builder().setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL).build(), expression);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ThrowStatement(translationMap.translateExpression(expression));
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("throw"))
                .add(Space.ONE).add(expression.output(qualification))
                .add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        // at least static only
        return SideEffect.STATIC_ONLY.combine(expression.sideEffect(evaluationContext));
    }
}
