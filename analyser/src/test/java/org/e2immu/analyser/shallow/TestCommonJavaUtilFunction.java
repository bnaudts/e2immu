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

package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.HiddenContentTypes;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonJavaUtilFunction extends CommonAnnotatedAPI {

    @Test
    public void testConsumer() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Consumer.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));

        assertEquals("T", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testConsumerAccept() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Consumer.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("accept", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        // void method -> independent
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(DV.TRUE_DV, p0.getProperty(Property.MODIFIED_VARIABLE), "in " + methodInfo.fullyQualifiedName);
        /*
        from the point of view of the interface, hidden content is passed on; this hidden content can be modified by implementations.
         */
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testFunction() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Function.class);
        assertTrue(typeInfo.isInterface());
        assertTrue(typeInfo.typeInspection.get().isFunctionalInterface());
        assertEquals("R, T", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testFunctionApply() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Function.class);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("apply", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals("*", methodAnalysis.getHiddenContentSelector().toString());
        if (methodAnalysis.getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
            assertEquals(1, all.getHiddenContentIndex());
        } else fail();
        HiddenContentTypes hct = methodInfo.methodResolution.get().hiddenContentTypes();
        assertEquals("R, T - ", hct.sortedTypes());

        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));

        // parameter
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(DV.TRUE_DV, p0.getProperty(Property.MODIFIED_VARIABLE), "in " + methodInfo.fullyQualifiedName);
        assertEquals("*", p0.getHiddenContentSelector().toString());
        if (p0.getHiddenContentSelector() instanceof HiddenContentSelector.All all) {
            assertEquals(0, all.getHiddenContentIndex());
        } else fail();

        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));
    }


    @Test
    public void testFunctionCompose() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Function.class);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("compose", 1);

        assertEquals("R, T", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
        assertEquals("R, T - V", methodInfo.methodResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testSupplier() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Supplier.class);
        assertTrue(typeInfo.isInterface());
        assertTrue(typeInfo.typeInspection.get().isFunctionalInterface());
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, typeAnalysis.getProperty(Property.INDEPENDENT));

        assertEquals("T", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }


    @Test
    public void testPredicateTest() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Predicate.class);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));

        // e
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(DV.TRUE_DV, p0.getProperty(Property.MODIFIED_VARIABLE), "in " + methodInfo.fullyQualifiedName);
        assertEquals(MultiLevel.CONTAINER_DV, p0.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.NOT_CONTAINER_DV, p0.getProperty(Property.CONTAINER_RESTRICTION));
    }
}
