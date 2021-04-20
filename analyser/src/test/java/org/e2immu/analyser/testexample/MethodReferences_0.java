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

import org.e2immu.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@E1Container
public class MethodReferences_0 {

    private List<C> strings;

    @E2Container
    static class C {
        final String s;

        C(String s) {
            this.s = s;
        }

        C(Integer t) {
            s = t.toString();
        }
    }

    public MethodReferences_0(@NotModified List<Integer> input) {
        strings = input.stream().map(C::new).collect(Collectors.toList());
    }

    @Modified
    public String removeFirst() {
        return strings.remove(0).s;
    }

    @Dependent
    public List<C> getStrings() {
        return strings;
    }
}
