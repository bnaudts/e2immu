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

    final TypeInfo collection = new TypeInfo("java.util", "Collection");
    final TypeInfo list = new TypeInfo("java.util", "List");
    ParameterizedType collectionEPt;
    ParameterizedType listEPt;

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
}
