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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.*;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.StringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// @ContextClass
// @NotNull
// @NullNotAllowed
public class Block implements Statement {
    public static final Block EMPTY_BLOCK = new Block(List.of());

    public final List<Statement> statements;

    private Block(List<Statement> statements) {
        this.statements = statements;
    }

    // all statements should have been parsed, but not all methods are known yet... so there's a serious chance
    // we have no clue... TODO
    public ParameterizedType inferReturnType() {
        return Primitives.PRIMITIVES.voidParameterizedType;
    }

    @Container
    public static class BlockBuilder {
        private final List<Statement> statements = new ArrayList<>();

        @Fluent
        public BlockBuilder addStatement(@NullNotAllowed Statement statement) {
            this.statements.add(statement);
            return this;
        }

        @NotModified
        @NotNull
        public Block build() {
            return new Block(new ImmutableList.Builder<Statement>().addAll(statements).build());
        }
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(" {");
        if (statements.isEmpty()) {
            sb.append(" }\n");
        } else {
            sb.append("\n");
            for (Statement statement : statements) {
                sb.append(statement.statementString(indent + 4));
            }
            StringUtil.indent(sb, indent);
            sb.append("}");
        }
        return sb.toString();
    }

    @Override
    // @Immutable
    public Set<String> imports() {
        Set<String> imports = new HashSet<>();
        for (Statement statement : statements) {
            imports.addAll(statement.imports());
        }
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return statements.stream()
                .map(s -> s.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);
    }

    @Override
    // @Immutable
    public List<Block> blocks() {
        return List.of(this);
    }
}
