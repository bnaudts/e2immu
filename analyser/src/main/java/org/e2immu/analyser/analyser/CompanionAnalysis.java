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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.annotation.NotNull;

import java.util.List;

public interface CompanionAnalysis {

    @NotNull
    MethodInfo getCompanion();

    @NotNull
    AnnotationParameters getAnnotationType();

    /**
     * @return the value that represents the companion.
     */
    Expression getValue();

    /**
     * The variable value referring to the "pre" aspect variable.
     * This value is part of the getValue() value.
     * We provide it to facilitate re-evaluation.
     *
     * @return NO_VALUE when there is none
     */
    Expression getPreAspectVariableValue();

    /**
     * The values of the parameters, part of the getValue() value.
     * We provide them to facilitate re-evaluation.
     *
     * @return a list of parameters, never null.
     */
    List<Expression> getParameterValues();

    /**
     * Delays are centralized
     *
     * @return the reason why the companion analysis has not yet taken place
     */
    CausesOfDelay causesOfDelay();
}
