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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Final;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

// simplest example of initialisation

public class StaticBlock_1 {

    @Final // implicitly, one assignment
    @NotNull
    private static Map<String, String> map;

    static {
        map = new HashMap<>();
        map.put("1", "2"); // should not raise a warning
        System.out.println("enclosing type");
    }

    @Nullable
    @NotModified
    public static String get(String s) {
        return map.get(s); // should not raise a warning!
    }

}
