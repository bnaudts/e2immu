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

package org.e2immu.analyser.util;

import org.e2immu.annotation.type.UtilityClass;

@UtilityClass
public class IntUtil {

    private IntUtil() {
    }

    // copied from Guava, DoubleMath class
    public static boolean isMathematicalInteger(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x) && x == Math.rint(x);
    }
}
