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

package org.e2immu.analyser.inspector.util;

import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;

import java.util.List;


public class RecordSynthetics {

    /*
    Create one method per field;
    for now, not writing the code for equals, hashCode, toString
     */
    public static void create(ExpressionContext expressionContext,
                              TypeInfo typeInfo,
                              TypeInspectionImpl.Builder builder,
                              List<FieldInfo> recordFields) {

        var primitives = expressionContext.typeContext.getPrimitives();
        var e2 = expressionContext.typeContext.typeMapBuilder.getE2ImmuAnnotationExpressions();
        var notModifiedContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notModified);

        for (var fieldInfo : recordFields) {
            builder.addMethod(createGetter(expressionContext, typeInfo, fieldInfo, notModifiedContract));
        }
    }

    private static MethodInfo createGetter(ExpressionContext expressionContext,
                                           TypeInfo typeInfo,
                                           FieldInfo fieldInfo,
                                           AnnotationExpression notModifiedContract) {
        var getter = new MethodInspectionImpl.Builder(typeInfo, fieldInfo.name)
                .setSynthetic(true)
                .setReturnType(fieldInfo.type)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notModifiedContract);
        getter.readyToComputeFQN(expressionContext.typeContext);
        var codeBlock = getterCodeBlock(expressionContext, fieldInfo);
        getter.setInspectedBlock(codeBlock);
        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(getter);
        return getter.getMethodInfo();
    }

    // return this.field;
    private static Block getterCodeBlock(ExpressionContext expressionContext, FieldInfo fieldInfo) {
        var returnStatement = new ReturnStatement(
                new VariableExpression(new FieldReference(expressionContext.typeContext, fieldInfo)));
        return new Block.BlockBuilder().addStatement(returnStatement).build();
    }
}
