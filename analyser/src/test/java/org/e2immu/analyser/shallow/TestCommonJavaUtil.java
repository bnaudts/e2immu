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
import org.e2immu.analyser.analyser.HiddenContentTypes;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonJavaUtil extends CommonAnnotatedAPI {

    @Test
    public void testCollection() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));

        assertEquals("E", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testCollectionAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertTrue(methodAnalysis.getHiddenContentSelector().isNone());

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.NOT_CONTAINER_DV, p0.getProperty(Property.CONTAINER_RESTRICTION));
        assertEquals(MultiLevel.CONTAINER_DV, p0.getProperty(Property.CONTAINER));
        // as opposed to java.io.PrintStream.print(X x), for example
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
        assertEquals("*", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testCollectionAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals("0", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testCollectionForEach() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("forEach", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);

        // the consumer's accept method will not receive null arguments
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));

        // the hidden content of the type is exposed
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));

        // the consumer's accept method is decreed to be non-modifying (as the modification does not matter to our structure)
        // this is implicit for all functional interface parameter types in non-private methods
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.IGNORE_MODS_DV, p0.getProperty(Property.IGNORE_MODIFICATIONS));

        // however, there is no restriction on the container property: the consumer's accept method is allowed to modify its parameter
        assertEquals(MultiLevel.NOT_CONTAINER_DV, p0.getProperty(Property.CONTAINER));

        assertEquals("Type java.util.function.Consumer<? super E>",
                p0.getParameterInfo().parameterizedType.toString());
        assertEquals("0", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testAbstractCollection() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        // not explicitly marked; no inheritance (we can go down from @Dependent1 to @Dependent)
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));

        assertFalse(errors.stream()
                        .filter(m -> ((LocationImpl) m.location()).info != null)
                        .anyMatch(m -> ((LocationImpl) m.location()).info.getTypeInfo().equals(typeInfo)),
                "Got: " + errors.stream()
                        .filter(m -> ((LocationImpl) m.location()).info != null)
                        .filter(m -> ((LocationImpl) m.location()).info.getTypeInfo().equals(typeInfo)).toList());

        assertEquals("E", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testAbstractCollectionAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals("0", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testAbstractCollectionToArray() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toArray", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT),
                "Method " + methodInfo.fullyQualifiedName);
        // the value should be the one in the map; for speed reasons, we should not be looking at overrides!
        DV inMap = ((MethodAnalysisImpl) methodAnalysis).properties.get(Property.INDEPENDENT);
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, inMap);

        assertEquals("Type Object[]", methodInfo.returnType().toString());
        // TODO 20240327 when we generalize the hidden content system, this should become <n>, as in Array<Object>,
        //   and n some index in AbstractCollection indicating that Object is one of the HC types
        assertEquals("X", methodAnalysis.getHiddenContentSelector().toString());
    }

    @Test
    public void testCollectionToArrayIntFunction() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeInfo intFunction = typeContext.getFullyQualified(IntFunction.class);
        assertNotNull(intFunction);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("toArray", intFunction);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testListAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(List.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals("*", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testListGet() {
        TypeInfo typeInfo = typeContext.getFullyQualified(List.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("get", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("*", methodAnalysis.getHiddenContentSelector().toString());

        // index
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));
        assertTrue(p0.getHiddenContentSelector().isNone());
    }


    @Test
    public void testListOf() {
        TypeInfo typeInfo = typeContext.getFullyQualified(List.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("of", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("1", methodAnalysis.getHiddenContentSelector().toString());

        // index
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals("*", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testListIterator() {
        TypeInfo typeInfo = typeContext.getFullyQualified(List.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("iterator", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertEquals("0", methodAnalysis.getHiddenContentSelector().toString());
    }

    @Test
    public void testListSubList() {
        TypeInfo typeInfo = typeContext.getFullyQualified(List.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("subList", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertEquals("0", methodAnalysis.getHiddenContentSelector().toString());
    }

    @Test
    public void testSet() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Set.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));

        assertEquals("E", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testSetAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Set.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));

        // return is boolean
        assertTrue(methodAnalysis.getHiddenContentSelector().isNone());
    }


    @Test
    public void testSetOf() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Set.class);
        MethodInfo methodInfo = typeInfo.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodInspection.get().getParameters().size() == 1 &&
                             m.name.equals("of") &&
                             m.isStatic() &&
                             m.methodInspection.get().getParameters().get(0).parameterizedType.arrays > 0)
                .findFirst().orElseThrow();
        assertEquals("java.util.Set.of(E...)", methodInfo.fullyQualifiedName);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));
        assertEquals("1", methodAnalysis.getHiddenContentSelector().toString());

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, p0.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals("1", p0.getHiddenContentSelector().toString());
    }


    @Test
    public void testArraysStream() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Arrays.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(DV.TRUE_DV, typeAnalysis.getProperty(Property.UTILITY_CLASS));
        assertEquals("", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());

        MethodInfo methodInfo = typeInfo.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "stream".equals(m.name))
                .filter(m -> m.methodInspection.get().getParameters().get(0).parameterizedType.arrays > 0 &&
                             m.methodInspection.get().getParameters().get(0).parameterizedType.isTypeParameter())
                .findFirst().orElseThrow();
        assertEquals("java.util.Arrays.stream(T[])", methodInfo.fullyQualifiedName);

        ParameterAnalysis p0 = methodInfo.methodAnalysis.get().getParameterAnalyses().get(0);
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        // because an array is always a container...
        assertEquals(MultiLevel.CONTAINER_DV, p0.getProperty(Property.CONTAINER));
        // because T[] ~ Array<T>
        assertEquals("0", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testArraysSetAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Arrays.class);
        MethodInfo methodInfo = typeInfo.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "setAll".equals(m.name))
                .filter(m -> "IntFunction".equals(m.methodInspection.get().getParameters().get(1).parameterizedType.typeInfo.simpleName))
                .findFirst().orElseThrow();
        assertEquals("java.util.Arrays.setAll(T[],java.util.function.IntFunction<? extends T>)", methodInfo.fullyQualifiedName);
        ParameterAnalysis p1 = methodInfo.methodAnalysis.get().getParameterAnalyses().get(1);
        assertEquals("array:4", p1.getLinksToOtherParameters().toString());
        ParameterInfo pi1 = methodInfo.methodInspection.get().getParameters().get(1);
        assertEquals("generator", pi1.name);
        assertEquals(1, pi1.index);
        assertEquals("Type java.util.function.IntFunction<? extends T>", pi1.parameterizedType.toString());
        assertEquals("0", p1.getHiddenContentSelector().toString());
    }

    @Test
    public void testArrayList() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));

        MethodInfo methodInfo = typeInfo.findConstructor(0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));

        assertEquals("E", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testArrayListAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));

        // because of the type parameter, we're assuming it'll go into the hidden content
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));

        assertEquals("*", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testArrayListAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.MUTABLE_DV, p0.getProperty(Property.IMMUTABLE));

        assertEquals("0", p0.getHiddenContentSelector().toString());
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testCollectionStream() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("stream", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));

        assertEquals("0", methodAnalysis.getHiddenContentSelector().toString());
    }

    @Test
    public void testCollections() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collections.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(DV.TRUE_DV, typeAnalysis.getProperty(Property.UTILITY_CLASS));
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER)); // Collections.addAll
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT)); // no data

        assertEquals("", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testCollectionsAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collections.class);
        MethodInfo addAll = typeInfo.findUniqueMethod("addAll", 2);
        HiddenContentTypes hctMethod = addAll.methodResolution.get().hiddenContentTypes();
        assertEquals("Collections: - addAll:T", hctMethod.toString());
        assertTrue(addAll.methodAnalysis.get().getHiddenContentSelector().isNone());
        ParameterAnalysis p0Ana = addAll.methodAnalysis.get().getParameterAnalyses().get(0);
        assertEquals("0", p0Ana.getHiddenContentSelector().toString());
        assertEquals("elements:4", p0Ana.getLinksToOtherParameters().toString());
        assertEquals("0-4-0", p0Ana.getLinksToOtherParameters().stream()
                .map(Map.Entry::getValue).findFirst().orElseThrow().toString());
        assertEquals(MultiLevel.INDEPENDENT_DV, p0Ana.getProperty(Property.INDEPENDENT));

        // this is 0 because it is an array type E[], rather than * for a concrete E instance
        ParameterAnalysis p1Ana = addAll.methodAnalysis.get().getParameterAnalyses().get(1);
        assertEquals("0", p1Ana.getHiddenContentSelector().toString());
        assertEquals("", p1Ana.getLinksToOtherParameters().toString());
    }

    @Test
    public void testMapPut() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("put", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("*", methodAnalysis.getHiddenContentSelector().toString());

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));
        assertEquals("*", p0.getHiddenContentSelector().toString());

        // value
        ParameterAnalysis p1 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p1.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p1.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p1.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p1.getProperty(Property.IMMUTABLE));
        assertEquals("*", p1.getHiddenContentSelector().toString());
    }

    @Test
    public void testMapGetOrDefault() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("getOrDefault", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("*", methodAnalysis.getHiddenContentSelector().toString());

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        // type: object
        assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));
        // TODO 20240327 should this be overridden to '*' ?
        assertEquals("X", p0.getHiddenContentSelector().toString());

        // default value
        ParameterAnalysis p1 = methodInfo.parameterAnalysis(1);
        assertEquals(MultiLevel.NULLABLE_DV, p1.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.FALSE_DV, p1.getProperty(Property.MODIFIED_VARIABLE));
        // type: V
        assertEquals(MultiLevel.INDEPENDENT_DV, p1.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p1.getProperty(Property.IMMUTABLE));
        assertEquals("*", p1.getHiddenContentSelector().toString());
    }

    @Test
    public void testMap() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testMapCopyOf() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("copyOf", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("2,3", methodAnalysis.getHiddenContentSelector().toString());

        ParameterAnalysis p0 = methodAnalysis.getParameterAnalyses().get(0);
        assertEquals(MultiLevel.MUTABLE_DV, p0.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals("2,3", p0.getHiddenContentSelector().toString());
    }

    @Test
    public void testMapOf() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("of", 2);
        assertTrue(methodInfo.isStatic());
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals("2,3", methodAnalysis.getHiddenContentSelector().toString());
    }

    @Test
    public void testMapValues() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("values", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        // important: the 0 is with respect to Collection (V is position 0 in Collection)
        assertEquals("1", methodAnalysis.getHiddenContentSelector().toString());
    }

    @Test
    public void testMapEntrySet() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("entrySet", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));
        assertEquals("0,1", methodAnalysis.getHiddenContentSelector().toString());
    }


    @Test
    public void testMapKeySet() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("keySet", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));
    }

    @Test
    public void testTreeMapFirstEntry() {
        TypeInfo typeInfo = typeContext.getFullyQualified(TreeMap.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("firstEntry", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, methodAnalysis.getProperty(Property.CONTAINER));
    }


    @Test
    public void testMapEntry() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.Entry.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));

        assertEquals("K, V", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testSortedMapValues() {
        TypeInfo typeInfo = typeContext.getFullyQualified(SortedMap.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("values", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testSortedMapHeadMap() {
        TypeInfo typeInfo = typeContext.getFullyQualified(SortedMap.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("headMap", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testHashSetConstructor1() {
        TypeInfo typeInfo = typeContext.getFullyQualified(HashSet.class);
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);

        MethodInfo constructor = typeInfo.findConstructor(collection);
        ParameterAnalysis p0 = constructor.parameterAnalysis(0);
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
    }


    @Test
    public void testHashSetAddAll() {
        TypeInfo hashSet = typeContext.getFullyQualified(AbstractCollection.class);
        MethodInfo addAllHashSet = hashSet.findUniqueMethod("addAll", 1);
        assertEquals(DV.TRUE_DV, addAllHashSet.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
        ParameterAnalysis p0 = addAllHashSet.parameterAnalysis(0);
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
    }

    @Test
    public void testHashMapConstructor1() {
        TypeInfo typeInfo = typeContext.getFullyQualified(HashMap.class);
        TypeInfo map = typeContext.getFullyQualified(Map.class);

        MethodInfo constructor = typeInfo.findConstructor(map);
        ParameterAnalysis p0 = constructor.parameterAnalysis(0);
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
    }


    @Test
    public void testHashMapGet() {
        TypeInfo hashMap = typeContext.getFullyQualified(HashMap.class);
        TypeInfo map = typeContext.getFullyQualified(Map.class);

        MethodInfo mapGet = map.findUniqueMethod("get", 1);
        ParameterAnalysis p0MapGet = mapGet.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0MapGet.getProperty(Property.NOT_NULL_PARAMETER));

        MethodInfo hashMapGet = hashMap.findUniqueMethod("get", 1);
        ParameterAnalysis p0HashMapGet = hashMapGet.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0HashMapGet.getProperty(Property.NOT_NULL_PARAMETER));
    }


    @Test
    public void testObjectsRequireNonNull() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Objects.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("requireNonNull", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.IDENTITY));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        ParameterAnalysis p0 = methodAnalysis.getParameterAnalyses().get(0);
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
    }


    @Test
    public void testObjectsHash() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Objects.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("hash", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.IDENTITY));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, methodAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        ParameterAnalysis p0 = methodAnalysis.getParameterAnalyses().get(0);
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        //! IMPORTANT ! an array is @Container, but a varargs parameter is not seen as an array for the purpose of enforcing @Container
        // see code in ParameterAnalysis.getParameterProperty
        assertEquals(MultiLevel.NOT_CONTAINER_DV, p0.getProperty(Property.CONTAINER_RESTRICTION));
        assertEquals(MultiLevel.CONTAINER_DV, p0.getProperty(Property.CONTAINER));
    }


    @Test
    public void testIterator() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Iterator.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));

        assertEquals("E", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }


    @Test
    public void testIteratorNext() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("next", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testIteratorHasNext() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("hasNext", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testIteratorRemove() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("remove", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testIteratorForEachRemaining() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("forEachRemaining", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(DV.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.IGNORE_MODS_DV, p0.getProperty(Property.IGNORE_MODIFICATIONS));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, p0.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testRandomNextInt() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Random.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("nextInt", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.TRUE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
    }

    @Test
    public void testOptional() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Optional.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals("T", typeInfo.typeResolution.get().hiddenContentTypes().sortedTypes());
    }

    @Test
    public void testOptionalEmpty() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Optional.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("empty", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));

        // not a factory method, static... IMPROVE we had to add this by hand to the method in JavaUtil
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, methodAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testOptionalGet() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Optional.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("get", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));

        // normal instance method returning unbound type parameter
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, methodAnalysis.getProperty(Property.IMMUTABLE),
                methodInfo.fullyQualifiedName);
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }
}
