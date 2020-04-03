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

package org.e2immu.analyser.parser.expr;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.PackagePrefix;
import org.e2immu.analyser.model.ParameterizedType;

class PackagePrefixExpression implements Expression {
    public final PackagePrefix packagePrefix;

    public PackagePrefixExpression(PackagePrefix packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    @Override
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String expressionString(int indent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int precedence() {
        throw new UnsupportedOperationException();
    }
}
