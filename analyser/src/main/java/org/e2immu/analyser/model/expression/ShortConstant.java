/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;


import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

@E2Container
public record ShortConstant(Primitives primitives,
                            short constant) implements ConstantExpression<Short>, Numeric {

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.shortParameterizedType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortConstant that = (ShortConstant) o;
        return constant == that.constant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constant);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_CONSTANT_SHORT;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return constant - ((ShortConstant) v).constant;
    }

    @Override
    public Short getValue() {
        return constant;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Expression negate() {
        return new ShortConstant(primitives, (short) (-constant));
    }

    @Override
    public Number getNumber() {
        return constant;
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(Short.toString(constant)));
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.CONSTANT;
    }
}
