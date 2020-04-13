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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariableReference;
import org.e2immu.analyser.model.Statement;

import java.util.List;
import java.util.Objects;
import java.util.Set;

// @ContextClass inherited
// @NullNotAllowed inherited
// @NotNull inherited
public abstract class StatementWithExpression implements Statement {
    public final Expression expression;

    protected StatementWithExpression(Expression expression) {
        this.expression = Objects.requireNonNull(expression);
    }

    @Override
    public List<Expression> expressions() {
        return List.of(expression);
    }

    @Override
    // @Immutable inherited
    public Set<String> imports() {
        return expression.imports();
    }

}
