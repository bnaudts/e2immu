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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Collection;
import java.util.List;

// simplest version of Modification_10 that has an infinite loop
// we explicitly use methods of the List type, so that it does not become transparent.
// then, immutable is between the extremes (MUTABLE, Level 2), this causes the delay

@FinalFields
@Container
public class Modification_22 {

    @NotModified
    final Collection<String> c1;

    public Modification_22(@NotModified @NotNull List<String> list) {
        c1 = list.subList(0, list.size() / 2);
    }
}
