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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;

public class SynchronizedStatement extends StatementWithExpression {

    public SynchronizedStatement(Expression expression,
                                 Block block) {
        super(new CodeOrganization.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatementsExecutedAtLeastOnce(v -> true)
                .setNoBlockMayBeExecuted(false)
                .setBlock(block).build());
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SynchronizedStatement(translationMap.translateExpression(codeOrganization.expression),
                translationMap.translateBlock(codeOrganization.block));
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("synchronized (");
        sb.append(codeOrganization.expression.expressionString(indent));
        sb.append(")");
        sb.append(codeOrganization.block.statementString(indent, NumberedStatement.startOfBlock(numberedStatement, 0)));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(codeOrganization.expression, codeOrganization.block);
    }
}
