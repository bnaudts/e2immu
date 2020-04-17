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

package org.e2immu.analyser.model.expression;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.MethodAnalyser;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.*;
import java.util.stream.Collectors;

@E2Immutable
public class LambdaBlock implements Expression {
    public final Block block;
    public final List<ParameterInfo> parameters;
    public final ParameterizedType returnType;

    public final SetOnce<List<NumberedStatement>> numberedStatements = new SetOnce<>();

    public LambdaBlock(@NullNotAllowed List<ParameterInfo> parameters,
                       @NullNotAllowed Block block,
                       @NullNotAllowed ParameterizedType returnType) {
        this.block = Objects.requireNonNull(block);
        this.parameters = Objects.requireNonNull(parameters);
        this.returnType = Objects.requireNonNull(returnType);
    }

    // this is a functional interface
    @Override
    @NotNull
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        String blockString;
        if (block.statements.isEmpty()) blockString = "{ }";
        else if (block.statements.size() == 1) blockString = block.statements.get(0).statementString(0);
        else blockString = block.statementString(indent);
        if (parameters.size() == 1) {
            return parameters.get(0).stream();
        }
        return "(" + parameters.stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")) + ")"
                + " -> " + blockString;
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    @NotNull
    @Independent
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(block.imports());
        parameters.forEach(pe -> imports.addAll(pe.imports()));
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return block.sideEffect(sideEffectContext);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        if (block == Block.EMPTY_BLOCK) return UnknownValue.UNKNOWN_VALUE;

        EvaluationContext child = evaluationContext.child(null, null);
        parameters.forEach(pi -> child.create(pi, new VariableValue(pi)));

        boolean changes = false;
        if (!numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            MethodAnalyser.recursivelyCreateNumberedStatements(block.statements,
                    indices,
                    numberedStatements,
                    new SideEffectContext(child.getTypeContext(), child.getCurrentMethod()));
            this.numberedStatements.set(numberedStatements);
            changes = true;
        }
        StatementAnalyser statementAnalyser = new StatementAnalyser(child.getTypeContext(), child.getCurrentMethod());
        if (!this.numberedStatements.get().isEmpty() && statementAnalyser.computeVariablePropertiesOfBlock(this.numberedStatements.get().get(0), child)) {
            changes = true;
        }
        visitor.visit(this, child, UnknownValue.UNKNOWN_VALUE, changes);
        return UnknownValue.UNKNOWN_VALUE;
    }
}
