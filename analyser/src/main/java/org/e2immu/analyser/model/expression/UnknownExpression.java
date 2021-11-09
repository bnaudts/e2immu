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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

/**
 * Contains "some value".
 */
public class UnknownExpression extends ElementImpl implements Expression {

    public static final String RETURN_VALUE = "return value";
    public static final String VARIABLE = "variable value";

    private final ParameterizedType parameterizedType;
    private final String msg;

    public UnknownExpression(ParameterizedType parameterizedType, String msg) {
        super(Identifier.generate());
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.msg = Objects.requireNonNull(msg);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnknownExpression that = (UnknownExpression) o;
        return msg.equals(that.msg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg);
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if (Primitives.isPrimitiveExcludingVoid(parameterizedType)) {
            return primitiveGetProperty(variableProperty);
        }
        TypeAnalysis typeAnalysis = parameterizedType.typeInfo == null ? null
                : evaluationContext.getAnalyserContext().getTypeAnalysis(parameterizedType.typeInfo);
        switch (variableProperty) {
            case IMMUTABLE:
                return typeAnalysis == null ? MultiLevel.NOT_INVOLVED_DV : typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
            case INDEPENDENT:
                return typeAnalysis == null ? MultiLevel.NOT_INVOLVED_DV : typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
            case CONTAINER:
                return typeAnalysis == null ? Level.FALSE_DV : typeAnalysis.getProperty(VariableProperty.CONTAINER);
            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE_DV;
            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case PROPAGATE_MODIFICATION_DELAY:
            case IDENTITY:
                return Level.FALSE_DV;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for primitive");
    }

    public static DV primitiveGetProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
            case CONTAINER:
                return Level.TRUE_DV;
            case NOT_NULL_EXPRESSION:
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case PROPAGATE_MODIFICATION_DELAY:
            case IDENTITY:
                return Level.FALSE_DV;
            case INDEPENDENT:
                return MultiLevel.INDEPENDENT_DV;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for primitive");
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return this;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("<" + msg + ">", "<" + msg + ":" + parameterizedType.output(qualification) + ">"));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder().setExpression(this).build();
    }

    @Override
    public int order() {
        return 0;
    }

    public String msg() {
        return msg;
    }
}
