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

package org.e2immu.analyser.resolver.testexample;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Lambda_12 {

    enum Choice {a, b, c}

    String[] sort(List<Choice> choices) {
        return choices.stream().map(Choice::name).toArray(String[]::new);
    }

    static record Container(String s, int size) {

    }

    List<List<Container>> containers = new ArrayList<>();

    public void method() {
        containers.add(Arrays.stream(sort(List.of(Choice.a, Choice.c, Choice.b)))
                .map(mod -> new Container(mod, mod.length()))
                .toList());
    }
}
