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


import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.CharValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

@E2Container
public class CharConstant implements ConstantExpression<Character> {
    @Override
    @NotNull
    public ParameterizedType returnType() {
        return Primitives.PRIMITIVES.charParameterizedType;
    }

    @NotNull
    public final char constant;

    public CharConstant(char constant) {
        this.constant = constant;
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return "'" + constant + "'";
    }

    @Override
    public int precedence() {
        return 17; // highest
    }

    @Override
    public Value newValue() {
        return new CharValue(constant);
    }

    @Override
    public Character getValue() {
        return constant;
    }
}
