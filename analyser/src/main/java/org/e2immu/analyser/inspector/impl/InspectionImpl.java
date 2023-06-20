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

package org.e2immu.analyser.inspector.impl;


import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Comment;
import org.e2immu.analyser.model.Inspection;

import java.util.List;
import java.util.Objects;

public abstract class InspectionImpl implements Inspection {


    private final List<AnnotationExpression> annotations;
    private final boolean synthetic;

    private final Access access;
    private final Comment comment;

    protected InspectionImpl(List<AnnotationExpression> annotations, Access access, Comment comment, boolean synthetic) {
        this.annotations = Objects.requireNonNull(annotations);
        this.synthetic = synthetic;
        this.access = Objects.requireNonNull(access);
        this.comment = comment;
    }

    @Override
    public boolean isSynthetic() {
        return synthetic;
    }

    @Override
    public List<AnnotationExpression> getAnnotations() {
        return annotations;
    }

    @Override
    public Access getAccess() {
        return access;
    }

    @Override
    public Comment getComment() {
        return comment;
    }
}
