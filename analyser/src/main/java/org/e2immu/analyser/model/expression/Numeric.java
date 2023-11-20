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

import org.e2immu.analyser.util.IntUtil;
import org.e2immu.annotation.NotNull;

public interface Numeric extends Negatable {
    static String formatNumber(double d, Class<? extends Numeric> clazz) {
        if (IntUtil.isMathematicalInteger(d)) {
            return Long.toString((long) d);
        }
        if (clazz.equals(FloatConstant.class)) {
            return d + "f";
        }
        return Double.toString(d);
    }

    @NotNull
    Number getNumber();

    default double doubleValue() {
        return getNumber().doubleValue();
    }

    @Override
    default Double numericValue() {
        return doubleValue();
    }

    @Override
    default boolean isNegatedOrNumericNegative() {
        return doubleValue() < 0;
    }
}
