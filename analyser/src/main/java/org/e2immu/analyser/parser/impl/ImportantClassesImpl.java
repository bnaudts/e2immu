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

package org.e2immu.analyser.parser.impl;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.support.SetOnce;

import java.util.List;

public class ImportantClassesImpl implements ImportantClasses {

    private final ParameterizedType iterable;
    private final TypeInfo list;
    private final SetOnce<MethodInfo> arrayFieldAccess = new SetOnce<>();
    private final TypeInfo intTypeInfo;

    public ImportantClassesImpl(TypeContext typeContext) {
        iterable = typeContext.getFullyQualified(Iterable.class).asParameterizedType(typeContext);
        list = typeContext.getFullyQualified(List.class);
        intTypeInfo = typeContext.getPrimitives().intTypeInfo();
    }

    public ParameterizedType iterable() {
        return iterable;
    }

    public MethodInfo arrayFieldAccess() {
        synchronized (arrayFieldAccess) {
            if (!arrayFieldAccess.isSet()) {
                arrayFieldAccess.set(list.findUniqueMethod("get", intTypeInfo));
            }
        }
        return arrayFieldAccess.get();
    }
}
