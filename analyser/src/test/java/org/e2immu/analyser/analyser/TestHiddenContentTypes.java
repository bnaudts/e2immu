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
        HiddenContentTypes stringHcs = HiddenContentTypes.computeShallow(analyserContext,
                primitives.stringTypeInfo().typeInspection.get());
        primitives.stringTypeInfo().typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, primitives.stringTypeInfo(), analyserContext)
                .setHiddenContentTypes(stringHcs)
                .build());

        TypeParameter iteratorE = new TypeParameterImpl(iterator, "E", 0).noTypeBounds();
        iteratorEPt = new ParameterizedType(iteratorE, 0, ParameterizedType.WildCard.NONE);

        iterator.typeInspection.set(new TypeInspectionImpl.Builder(iterator, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(iteratorE)
                .setParentClass(primitives.objectParameterizedType())
                .build(analyserContext));
        iterator.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives, iterator, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext, iterator.typeInspection.get()))
                .build());

        TypeParameter collectionE = new TypeParameterImpl(collection, "E", 0).noTypeBounds();
        collectionEPt = new ParameterizedType(collectionE, 0, ParameterizedType.WildCard.NONE);

        collection.typeInspection.set(new TypeInspectionImpl.Builder(collection, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(collectionE)
                .setParentClass(primitives.objectParameterizedType())
                .build(analyserContext));
        collection.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives, collection, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext, collection.typeInspection.get()))
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
        list.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives, list, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext, list.typeInspection.get()))
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
        arrayList.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                arrayList, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext,
                        arrayList.typeInspection.get()))
                .build());

        stringList.typeInspection.set(new TypeInspectionImpl.Builder(stringList, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.CLASS)
                .addTypeModifier(TypeModifier.FINAL)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(primitives.stringParameterizedType())))
                .build(analyserContext));
        stringList.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                stringList, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext,
                        stringList.typeInspection.get()))
                .build());

        stringListInterface.typeInspection.set(new TypeInspectionImpl.Builder(stringListInterface, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.INTERFACE)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(primitives.stringParameterizedType())))
                .build(analyserContext));
        stringListInterface.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                stringListInterface, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext,
                        stringListInterface.typeInspection.get()))
                .build());

        stringListImpl.typeInspection.set(new TypeInspectionImpl.Builder(stringListImpl, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(stringListInterface, List.of()))
                .build(analyserContext));
        stringListImpl.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                stringListImpl, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext,
                        stringListImpl.typeInspection.get()))
                .build());

        arrayListItr.typeInspection.set(new TypeInspectionImpl.Builder(arrayListItr, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PRIVATE)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(iterator, List.of(arrayListEPt)))
                .build(analyserContext));
        arrayListItr.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                arrayListItr, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext,
                        arrayListItr.typeInspection.get()))
                .build());

        arrayListListItr.typeInspection.set(new TypeInspectionImpl.Builder(arrayListListItr, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PRIVATE)
                .setTypeNature(TypeNature.CLASS)
                .setParentClass(new ParameterizedType(arrayListItr, List.of(arrayListEPt)))
                .build(analyserContext));
        arrayListListItr.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                arrayListListItr, analyserContext)
                .setHiddenContentTypes(HiddenContentTypes.computeShallow(analyserContext,
                        arrayListListItr.typeInspection.get()))
                .build());
    }

    @Test
    @DisplayName("Object")
    public void test0() {
        HiddenContentTypes objectHcs = HiddenContentTypes.computeShallow(analyserContext,
                primitives.objectTypeInfo().typeInspection.get());
        assertSame(HiddenContentTypes.OF_OBJECT, objectHcs);
    }

    @Test
    @DisplayName("String: no type parameters, final")
    public void test1() {
        HiddenContentTypes stringHcs = primitives.stringTypeInfo().typeAnalysis.get().getHiddenContentTypes();
        assertFalse(stringHcs.hasHiddenContent());
    }

    @Test
    @DisplayName("Collection")
    public void test2() {
        HiddenContentTypes hcs = collection.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());
        assertEquals(1, hcs.size());
        assertThrows(NullPointerException.class, () -> hcs.relationToParent(primitives.objectTypeInfo()));
    }

    @Test
    @DisplayName("List")
    public void test3() {
        HiddenContentTypes hcs = list.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());
        assertEquals(1, hcs.size());
        HiddenContentTypes.RelationToParent rtp = hcs.relationToParent(collection);
        assertEquals(1, rtp.parentHcsToMyType().size());
        // collection 0 -> E of List
        ParameterizedType pt = rtp.parentHcsToMyType().get(0);
        assertNotNull(pt.typeParameter);
        assertEquals("E as #0 in java.util.List", pt.typeParameter.toString());
    }


    @Test
    @DisplayName("ArrayList")
    public void test4() {
        HiddenContentTypes hcs = arrayList.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());
        assertEquals(1, hcs.size());
        HiddenContentTypes.RelationToParent rtpC = hcs.relationToParent(collection);
        assertEquals(1, rtpC.parentHcsToMyType().size());
        // collection 0 -> E of ArrayList
        ParameterizedType ptC = rtpC.parentHcsToMyType().get(0);
        assertNotNull(ptC.typeParameter);
        assertSame(arrayListEPt.typeParameter, ptC.typeParameter);
        HiddenContentTypes.RelationToParent rtpL = hcs.relationToParent(list);
        assertEquals(1, rtpL.parentHcsToMyType().size());
        // list 0 -> E of ArrayList
        ParameterizedType ptL = rtpL.parentHcsToMyType().get(0);
        assertSame(arrayListEPt.typeParameter, ptL.typeParameter);

        assertEquals(0, hcs.indexOf(arrayListEPt));
        assertEquals(arrayListEPt, hcs.typeByIndex(0));
    }

    @Test
    @DisplayName("StringList")
    public void test5() {
        HiddenContentTypes hcs = stringList.typeAnalysis.get().getHiddenContentTypes();
        assertFalse(hcs.hasHiddenContent());

        HiddenContentTypes.RelationToParent rtpC = hcs.relationToParent(collection);
        assertEquals(1, rtpC.parentHcsToMyType().size());
        // collection 0 -> String
        ParameterizedType ptC = rtpC.parentHcsToMyType().get(0);
        assertSame(primitives.stringTypeInfo(), ptC.typeInfo);
    }

    @Test
    @DisplayName("StringListInterface")
    public void test6() {
        HiddenContentTypes hcs = stringListInterface.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());

        HiddenContentTypes.RelationToParent rtpC = hcs.relationToParent(collection);
        assertEquals(1, rtpC.parentHcsToMyType().size());
        // collection 0 -> String
        ParameterizedType ptC = rtpC.parentHcsToMyType().get(0);
        assertSame(primitives.stringTypeInfo(), ptC.typeInfo);
    }


    @Test
    @DisplayName("StringListImpl")
    public void test7() {
        HiddenContentTypes hcs = stringListImpl.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());

        HiddenContentTypes.RelationToParent rtpC = hcs.relationToParent(collection);
        assertEquals(1, rtpC.parentHcsToMyType().size());
        // collection 0 -> String
        ParameterizedType ptC = rtpC.parentHcsToMyType().get(0);
        assertSame(primitives.stringTypeInfo(), ptC.typeInfo);
    }

    @Test
    @DisplayName("ArrayList.Itr")
    public void test8() {
        ParameterizedType formalArrayListItr = arrayListItr.asParameterizedType(analyserContext);
        assertEquals("Type java.util.ArrayList<E>.Itr", formalArrayListItr.toString());
        HiddenContentTypes hcs = arrayListItr.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());

        HiddenContentTypes.RelationToParent rtpI = hcs.relationToParent(iterator);
        assertEquals(1, rtpI.parentHcsToMyType().size());
        // collection 0 -> String
        ParameterizedType ptC = rtpI.parentHcsToMyType().get(0);
        assertSame(arrayListEPt.typeParameter, ptC.typeParameter);
    }


    @Test
    @DisplayName("ArrayList.ListItr")
    public void test9() {
        ParameterizedType formalArrayListListItr = arrayListListItr.asParameterizedType(analyserContext);
        assertEquals("Type java.util.ArrayList<E>.ListItr", formalArrayListListItr.toString());
        HiddenContentTypes hcs = arrayListListItr.typeAnalysis.get().getHiddenContentTypes();
        assertTrue(hcs.hasHiddenContent());

        HiddenContentTypes.RelationToParent rtpI = hcs.relationToParent(iterator);
        assertEquals(1, rtpI.parentHcsToMyType().size());
        // collection 0 -> String
        ParameterizedType ptC = rtpI.parentHcsToMyType().get(0);
        assertSame(arrayListEPt.typeParameter, ptC.typeParameter);

        HiddenContentTypes.RelationToParent rtpItr = hcs.relationToParent(arrayListItr);
        assertTrue(rtpItr.parentHcsToMyType().isEmpty());
    }
}
