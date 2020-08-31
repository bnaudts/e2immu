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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.statement.BreakOrContinueStatement;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class NumberedStatement implements Comparable<NumberedStatement> {
    public final Statement statement;
    public final NumberedStatement parent;
    public SetOnce<Optional<NumberedStatement>> next = new SetOnce<>();
    public SetOnce<List<NumberedStatement>> blocks = new SetOnce<>();
    public SetOnce<Boolean> neverContinues = new SetOnce<>(); // returns, or escapes; set at the beginning of a block
    public SetOnce<Boolean> escapes = new SetOnce<>(); // escapes, on the beginning of a block
    public SetOnce<Boolean> errorValue = new SetOnce<>(); // if we detected an error value on this statement
    public SetOnce<Value> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0
    public SetOnce<Value> state = new SetOnce<>(); // the state as it is after evaluating the statement

    // a set of break and continue statements in sub-blocks of this statement
    public SetOnce<List<BreakOrContinueStatement>> breakAndContinueStatements = new SetOnce<>();
    public SetOnce<Set<Variable>> existingVariablesAssignedInLoop = new SetOnce<>();

    // used for patterns
    public SetOnce<Value> valueOfExpression = new SetOnce<>();

    public final List<Integer> indices;
    public final String index;

    // Transformations
    // if the statement is not important anymore, set it to "ExpressionAsStatement" with "EmptyExpression"
    // the replacement should have the same indices
    public final SetOnce<NumberedStatement> replacement = new SetOnce<>();

    public NumberedStatement(@NotNull Statement statement,
                             NumberedStatement parent,
                             @NotNull @NotModified List<Integer> indices) {
        this.indices = ImmutableList.copyOf(indices);
        index = this.indices.stream().map(i -> Integer.toString(i)).collect(Collectors.joining("."));
        this.statement = Objects.requireNonNull(statement);
        this.parent = parent;
    }

    public String toString() {
        return index + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(NumberedStatement o) {
        return index.compareTo(o.index);
    }

    public boolean inErrorState() {
        boolean parentInErrorState = parent != null && parent.inErrorState();
        if (parentInErrorState) return true;
        return errorValue.isSet() && errorValue.get();
    }
}
