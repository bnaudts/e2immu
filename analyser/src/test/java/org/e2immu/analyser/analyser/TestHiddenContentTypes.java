package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestHiddenContentTypes {

    final Primitives primitives = new PrimitivesImpl();
    final AnalyserContext analyserContext = () -> primitives;

    final TypeInfo iterator = new TypeInfo("java.util", "Iterator");
    final TypeInfo collection = new TypeInfo("java.util", "Collection");
    final TypeInfo list = new TypeInfo("java.util", "List");
    final TypeInfo arrayList = new TypeInfo("java.util", "ArrayList");
    final TypeInfo stringList = new TypeInfo("com.foo", "StringList");
    final TypeInfo stringListInterface = new TypeInfo("com.foo", "StringListInterface");
    final TypeInfo stringListImpl = new TypeInfo("com.foo", "StringListImpl");
    final TypeInfo arrayListItr = new TypeInfo(arrayList, "Itr"); // inner class
    final TypeInfo arrayListListItr = new TypeInfo(arrayList, "ListItr"); // inner class, extends Itr

    ParameterizedType iteratorEPt;
    ParameterizedType collectionEPt;
    ParameterizedType listEPt;
    ParameterizedType arrayListEPt;

    @BeforeEach
    public void beforeEach() {
        primitives.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.CLASS)
                .build(analyserContext));
        primitives.stringTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.stringTypeInfo(), Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setTypeNature(TypeNature.CLASS)
                .setAccess(Inspection.Access.PUBLIC)
                .addTypeModifier(TypeModifier.FINAL)
                .build(analyserContext));
        HiddenContentTypes stringHcs = HiddenContentTypes.compute(primitives.stringTypeInfo().typeInspection.get());
        primitives.stringTypeInfo().typeResolution.set(new TypeResolution.Builder().setHiddenContentTypes(stringHcs).build());

        TypeParameter iteratorE = new TypeParameterImpl(iterator, "E", 0).noTypeBounds();
        iteratorEPt = new ParameterizedType(iteratorE, 0, ParameterizedType.WildCard.NONE);

        iterator.typeInspection.set(new TypeInspectionImpl.Builder(iterator, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(iteratorE)
                .setParentClass(primitives.objectParameterizedType())
                .build(analyserContext));
        iterator.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(iterator.typeInspection.get()))
                .build());

        TypeParameter collectionE = new TypeParameterImpl(collection, "E", 0).noTypeBounds();
        collectionEPt = new ParameterizedType(collectionE, 0, ParameterizedType.WildCard.NONE);

        collection.typeInspection.set(new TypeInspectionImpl.Builder(collection, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(collectionE)
                .setParentClass(primitives.objectParameterizedType())
                .build(analyserContext));
        collection.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(collection.typeInspection.get()))
                .build());

        TypeParameter listE = new TypeParameterImpl(list, "E", 0).noTypeBounds();
        listEPt = new ParameterizedType(listE, 0, ParameterizedType.WildCard.NONE);
        list.typeInspection.set(new TypeInspectionImpl.Builder(list, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(listE)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(collection, List.of(listEPt)))
                .build(analyserContext));
        list.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(list.typeInspection.get()))
                .build());

        TypeParameter arrayListE = new TypeParameterImpl(arrayList, "E", 0).noTypeBounds();
        arrayListEPt = new ParameterizedType(arrayListE, 0, ParameterizedType.WildCard.NONE);
        arrayList.typeInspection.set(new TypeInspectionImpl.Builder(arrayList, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(arrayListE)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(arrayListEPt)))
                .build(analyserContext));
        arrayList.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(arrayList.typeInspection.get()))
                .build());

        stringList.typeInspection.set(new TypeInspectionImpl.Builder(stringList, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.CLASS)
                .addTypeModifier(TypeModifier.FINAL)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(primitives.stringParameterizedType())))
                .build(analyserContext));
        stringList.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(stringList.typeInspection.get()))
                .build());

        stringListInterface.typeInspection.set(new TypeInspectionImpl.Builder(stringListInterface, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(primitives.stringParameterizedType())))
                .build(analyserContext));
        stringListInterface.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(stringListInterface.typeInspection.get()))
                .build());

        stringListImpl.typeInspection.set(new TypeInspectionImpl.Builder(stringListImpl, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(stringListInterface, List.of()))
                .build(analyserContext));
        stringListImpl.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(stringListImpl.typeInspection.get()))
                .build());

        arrayListItr.typeInspection.set(new TypeInspectionImpl.Builder(arrayListItr, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PRIVATE)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(iterator, List.of(arrayListEPt)))
                .build(analyserContext));
        arrayListItr.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(arrayListItr.typeInspection.get()))
                .build());

        arrayListListItr.typeInspection.set(new TypeInspectionImpl.Builder(arrayListListItr, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PRIVATE)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(new ParameterizedType(arrayListItr, List.of(arrayListEPt)))
                .build(analyserContext));
        arrayListListItr.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(arrayListListItr.typeInspection.get()))
                .build());
    }

    @Test
    @DisplayName("Object")
    public void test0() {
        HiddenContentTypes objectHcs = HiddenContentTypes.compute(primitives.objectTypeInfo().typeInspection.get());
        assertSame(HiddenContentTypes.OF_OBJECT, objectHcs);
    }

    @Test
    @DisplayName("String: no type parameters, final")
    public void test1() {
        HiddenContentTypes stringHcs = primitives.stringTypeInfo().typeResolution.get().hiddenContentTypes();
        assertFalse(stringHcs.hasHiddenContent());
    }

    @Test
    @DisplayName("Collection")
    public void test2() {
        HiddenContentTypes hcs = collection.typeResolution.get().hiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());
        assertEquals(1, hcs.size());
    }

    @Test
    @DisplayName("List")
    public void test3() {
        HiddenContentTypes hcs = list.typeResolution.get().hiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());
        assertEquals(1, hcs.size());
    }
}
