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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;

import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonJavaUtilStream extends CommonAnnotatedAPI {

    @Test
    public void testCollector() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collector.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
    }


    @Test
    public void testStream() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, typeAnalysis.immutableDeterminedByTypeParameters());
        assertEquals("T", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testStreamMap() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("map", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("Type java.util.stream.Stream<R>", methodInfo.returnType().toString());
        assertEquals("Stream:T - map:R", methodInfo.methodResolution.get().hiddenContentTypes().toString());
        /*
        1=0 means: the index in the hidden content type = 1 (R), 0 = the indices on how to select it from the type Stream<R>
         */
        assertEquals("1=0", methodAnalysis.getHiddenContentSelector().toString());

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.IGNORE_MODS_DV, p0.getProperty(Property.IGNORE_MODIFICATIONS));
        assertEquals(MultiLevel.MUTABLE_DV, p0.getProperty(Property.IMMUTABLE));
        assertEquals("Type java.util.function.Function<? super T,? extends R>",
                p0.getParameterInfo().parameterizedType.toString());
        assertEquals("0,1", p0.getHiddenContentSelector().toString());

        assertEquals("return map:4", p0.getLinkToReturnValueOfMethod().toString());
        assertEquals("1-4-0", p0.getLinkToReturnValueOfMethod().stream().findFirst()
                .orElseThrow().getValue().toString());
    }


    /*
    static Stream<T> of(T)

    T is minimally @Independent1, as an unbound type parameter.
    Stream<T> is minimally @Independent1, as it is formally @E2Container.
    The parameter should be @Independent if there is no content link between the parameter and the method result.
     */
    @Test
    public void testStreamOf() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("of", 1);
        assertTrue(methodInfo.isStatic());
        assertTrue(methodInfo.methodInspection.get().isFactoryMethod());

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT),
                methodInfo.fullyQualifiedName);

        // T
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));
    }

    @Test
    public void testStreamEmpty() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("empty", 0);
        assertTrue(methodInfo.isStatic());
        assertTrue(methodInfo.methodInspection.get().isFactoryMethod());

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
    }

    @Test
    public void testStreamFilter() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("filter", 1);
        assertFalse(methodInfo.isStatic());

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
    }

    @Test
    public void testStreamFindFirst() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("findFirst", 0);
        assertFalse(methodInfo.isStatic());

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
    }

    @Test
    public void testIntStreamMapToObj() {
        TypeInfo typeInfo = typeContext.getFullyQualified(IntStream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("mapToObj", 1);

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT),
                methodInfo.fullyQualifiedName);

        // IntFunction<? extends U> mapper
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));

        assertEquals("return mapToObj:4", p0.getLinkToReturnValueOfMethod().toString());
        assertEquals("0-4-0", p0.getLinkToReturnValueOfMethod().stream().findFirst()
                .orElseThrow().getValue().toString());
        assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, p0.getProperty(Property.IMMUTABLE));
    }

}
