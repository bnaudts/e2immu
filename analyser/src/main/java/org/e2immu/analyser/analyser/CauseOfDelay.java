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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;

public interface CauseOfDelay {

    enum Cause {
        VALUE("The value has not yet been determined"),
        VALUE_NOT_NULL("The value's NOT_NULL status has not yet been determined"),
        VALUE_IMMUTABLE("The value's IMMUTABLE status has not yet been determined"),
        CONTEXT_MODIFIED("Context modified not yet been determined"),
        LINKING("Delay in linking"),
        REMAP_PARAMETER("Remapping a parameter for the companion analyser is not yet possible"),
        FIELD_FINAL("Effectively final has not yet been determined for this field"),
        ASPECT("The type's aspect has not yet been determined"),
        MODIFIED_METHOD("The method's modification status has not yet been determined"),
        ASSIGNED_TO_FIELD("The component 'analyseFieldAssignments' has not yet finished"),
        IMMUTABLE("Type's IMMUTABLE status has not yet been determined"),
        TYPE_ANALYSIS("Type analysis missing"),
        HIDDEN_CONTENT("Hidden content of type has not yet been determined"),
        INITIAL_VALUE("Not yet initialized"),
        APPROVED_PRECONDITIONS("Approved preconditions for field");

        public final String msg;

        Cause(String msg) {
            this.msg = msg;
        }

        public static Cause from(VariableProperty variableProperty) {
            return switch (variableProperty) {
                case IMMUTABLE -> VALUE_IMMUTABLE;
                default -> throw new UnsupportedOperationException();
            };
        }
    }

    default Variable variable() {
        return null;
    }

    Cause cause();

    WithInspectionAndAnalysis withInspectionAndAnalysis();

    record SimpleCause(WithInspectionAndAnalysis withInspectionAndAnalysis, Cause cause) implements CauseOfDelay {
    }

    record VariableCause(Variable variable, Cause cause) implements CauseOfDelay {
        @Override
        public WithInspectionAndAnalysis withInspectionAndAnalysis() {
            return fromVariable(variable);
        }
    }

    record VariableInStatement(Variable variable, StatementAnalysis statementAnalysis, Cause cause) implements CauseOfDelay {
        @Override
        public WithInspectionAndAnalysis withInspectionAndAnalysis() {
            return fromVariable(variable);
        }
    }

    static private WithInspectionAndAnalysis fromVariable(Variable variable) {
    return    variable instanceof FieldReference fr ? fr.fieldInfo : variable instanceof ParameterInfo pi ? pi : null;
    }
}
