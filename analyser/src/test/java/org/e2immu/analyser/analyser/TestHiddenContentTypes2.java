package org.e2immu.analyser.analyser;

import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestHiddenContentTypes2 {
    private final Primitives primitives = new PrimitivesImpl();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    private final TypeInfo arrayList = new TypeInfo("com.foo", "ArrayList");
    private final TypeParameter tpAl0 = new TypeParameterImpl(arrayList, "EA", 0).noTypeBounds();
    private final ParameterizedType tpAl0Pt = new ParameterizedType(tpAl0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType arrayListPt = new ParameterizedType(arrayList, List.of(tpAl0Pt));

    private final TypeInfo collection = new TypeInfo("com.foo", "Collection");
    private final TypeParameter tpColl0 = new TypeParameterImpl(collection, "EC", 0).noTypeBounds();
    private final ParameterizedType tpColl0Pt = new ParameterizedType(tpColl0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType collectionPt = new ParameterizedType(collection, List.of(tpColl0Pt));

    private final TypeInfo list = new TypeInfo("com.foo", "List");
    private final TypeParameter tpList0 = new TypeParameterImpl(list, "EL", 0).noTypeBounds();
    private final ParameterizedType tpList0Pt = new ParameterizedType(tpList0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType listPt = new ParameterizedType(list, List.of(tpList0Pt));

    private final TypeInfo map = new TypeInfo("com.foo", "Map");
    private final TypeParameter tpMap0 = new TypeParameterImpl(map, "MK", 0).noTypeBounds();
    private final TypeParameter tpMap1 = new TypeParameterImpl(map, "MV", 1).noTypeBounds();
    private final ParameterizedType mapPt = new ParameterizedType(map, List.of(tpMap0.toParameterizedType(), tpMap1.toParameterizedType()));


    @BeforeEach
    public void beforeEach() {
        TypeInspection objectTi = new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC).build(inspectionProvider);
        primitives.objectParameterizedType().typeInfo.typeInspection.set(objectTi);
        for (TypeInfo typeInfo : new TypeInfo[]{primitives.stringTypeInfo(), primitives.integerTypeInfo(),
                primitives.boxedBooleanTypeInfo()}) {
            TypeInspection ti = new TypeInspectionImpl.Builder(typeInfo, Inspector.BY_HAND)
                    .setAccess(Inspection.Access.PUBLIC)
                    .setParentClass(primitives.objectParameterizedType())
                    .build(inspectionProvider);
            typeInfo.typeInspection.set(ti);
        }

        collection.typeInspection.set(new TypeInspectionImpl.Builder(collection, Inspector.BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(tpColl0)
                .setParentClass(primitives.objectParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        collection.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(inspectionProvider, collection.typeInspection.get()))
                .build());

        list.typeInspection.set(new TypeInspectionImpl.Builder(list, Inspector.BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(tpList0)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(collection, List.of(tpList0Pt)))
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        list.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(inspectionProvider, list.typeInspection.get()))
                .build());

        arrayList.typeInspection.set(new TypeInspectionImpl.Builder(arrayList, Inspector.BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .addTypeParameter(tpAl0)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(tpAl0Pt)))
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        arrayList.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(inspectionProvider, arrayList.typeInspection.get()))
                .build());

        map.typeInspection.set(new TypeInspectionImpl.Builder(map, Inspector.BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(tpMap0)
                .addTypeParameter(tpMap1)
                .setParentClass(primitives.objectParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        map.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(inspectionProvider, map.typeInspection.get()))
                .build());
    }

    @Test
    public void test0() {
        assertEquals("Type com.foo.Collection<EC>", collectionPt.toString());
        assertEquals("Type com.foo.List<EL>", listPt.toString());
        assertEquals("Type com.foo.ArrayList<EA>", arrayListPt.toString());

        Map<NamedType, ParameterizedType> map = listPt.translateMap(inspectionProvider, collectionPt,
                false);
        assertEquals("{EL as #0 in com.foo.List=Type param EC}", map.toString());
    }

    @Test
    public void test1() {
        HiddenContentTypes hctAl = arrayList.typeResolution.get().hiddenContentTypes();
        Map<NamedType, Integer> map = hctAl.getTypeToIndex();
        assertEquals(3, map.size());
        assertEquals(0, map.get(tpColl0));
        assertEquals(0, map.get(tpList0));
        assertEquals(0, map.get(tpAl0));
    }

    @Test
    public void test2() {
        HiddenContentSelector hcs = new HiddenContentSelector.CsSet(Map.of(0, new LV.Indices(0)));
        assertEquals("0", hcs.toString());
        ParameterizedType pt = new ParameterizedType(list, List.of(primitives.stringParameterizedType()));
        assertEquals("Type com.foo.List<String>", pt.toString());
        assertEquals("{0=Type param EL}", hcs.extract(inspectionProvider, pt).toString());
    }

    @Test
    public void test2b() {
        HiddenContentSelector hcs = new HiddenContentSelector.CsSet(Map.of(0, new LV.Indices(Set.of(new LV.Index(List.of(0, 0))))));
        assertEquals("0=0.0", hcs.toString());
        ParameterizedType pt = new ParameterizedType(list, List.of(new ParameterizedType(arrayList, List.of(primitives.stringParameterizedType()))));
        assertEquals("Type com.foo.List<com.foo.ArrayList<String>>", pt.toString());
        assertEquals("{0.0=Type param EA}", hcs.extract(inspectionProvider, pt).toString());
    }

    @Test
    @DisplayName("allOccurrencesOf")
    public void test3() {
        ParameterizedType pt = new ParameterizedType(list, List.of(tpAl0Pt, tpList0Pt, tpList0Pt));
        assertEquals("1;2", HiddenContentTypes.allOccurrencesOf(tpList0Pt, pt).toString());

        ParameterizedType pt2 = new ParameterizedType(list, List.of(new ParameterizedType(list, List.of(tpAl0Pt, tpList0Pt, tpList0Pt))));
        assertEquals("0.1;0.2", HiddenContentTypes.allOccurrencesOf(tpList0Pt, pt2).toString());
    }

    @Test
    @DisplayName("translateHcs, simplest situation")
    public void test4a() {
        HiddenContentTypes hctAl = arrayList.typeResolution.get().hiddenContentTypes();
        LV.Indices i0 = new LV.Indices(0);
        HiddenContentSelector hcs = new HiddenContentSelector.CsSet(Map.of(0, i0));
        // from is expressed in terms of the hidden content of hctAl
        ParameterizedType from = new ParameterizedType(collection, List.of(tpAl0Pt));
        assertEquals("Type com.foo.Collection<EA>", from.toString());
        assertEquals(0, hctAl.indexOf(tpAl0));

        ParameterizedType to = new ParameterizedType(collection, List.of(primitives.stringParameterizedType()));
        assertEquals("Type com.foo.Collection<String>", to.toString());

        Map<LV.Indices, HiddenContentTypes.IndicesAndType> map = hctAl.translateHcs(inspectionProvider, hcs, from, to,
                true);
        assertEquals(1, map.size());
        HiddenContentTypes.IndicesAndType iat = map.get(i0);
        assertNotNull(iat);
        assertEquals(i0, iat.indices());
        assertSame(primitives.stringParameterizedType(), iat.type());
    }

    @Test
    @DisplayName("translateHcs, from is supertype of to")
    public void test4b() {
        HiddenContentTypes hctAl = arrayList.typeResolution.get().hiddenContentTypes();
        LV.Indices i0 = new LV.Indices(0);
        HiddenContentSelector hcs = new HiddenContentSelector.CsSet(Map.of(0, i0));
        // from is expressed in terms of the hidden content of hctAl
        ParameterizedType from = new ParameterizedType(collection, List.of(tpAl0Pt));
        assertEquals("Type com.foo.Collection<EA>", from.toString());
        assertEquals(0, hctAl.indexOf(tpAl0));

        ParameterizedType to = new ParameterizedType(list, List.of(primitives.stringParameterizedType()));
        assertEquals("Type com.foo.List<String>", to.toString());

        Map<LV.Indices, HiddenContentTypes.IndicesAndType> map = hctAl.translateHcs(inspectionProvider, hcs, from, to, true);
        assertEquals(1, map.size());
        HiddenContentTypes.IndicesAndType iat = map.get(i0);
        assertNotNull(iat);
        assertEquals(i0, iat.indices());
        assertSame(primitives.stringParameterizedType(), iat.type());
    }

    @Test
    @DisplayName("translateHcs, collection of map, simplest situation")
    public void test4c() {
        HiddenContentTypes hctMap = map.typeResolution.get().hiddenContentTypes();
        LV.Indices i00 = new LV.Indices(Set.of(new LV.Index(List.of(0, 0))));
        LV.Indices i01 = new LV.Indices(Set.of(new LV.Index(List.of(0, 1))));
        HiddenContentSelector hcs = new HiddenContentSelector.CsSet(Map.of(0, i00, 1, i01));
        assertEquals("0=0.0,1=0.1", hcs.toString());

        // from is expressed in terms of the hidden content of hctMap
        ParameterizedType mapKV = new ParameterizedType(map, List.of(tpMap0.toParameterizedType(), tpMap1.toParameterizedType()));
        ParameterizedType from = new ParameterizedType(collection, List.of(mapKV));
        assertEquals("Type com.foo.Collection<com.foo.Map<MK,MV>>", from.toString());
        assertEquals(1, hctMap.indexOf(tpMap1));

        ParameterizedType mapSI = new ParameterizedType(map, List.of(primitives.stringParameterizedType(),
                primitives.integerTypeInfo().asSimpleParameterizedType()));
        ParameterizedType to = new ParameterizedType(collection, List.of(mapSI));
        assertEquals("Type com.foo.Collection<com.foo.Map<String,Integer>>", to.toString());

        Map<LV.Indices, HiddenContentTypes.IndicesAndType> map = hctMap.translateHcs(inspectionProvider, hcs, from, to, true);
        assertEquals(2, map.size());
        HiddenContentTypes.IndicesAndType iat0 = map.get(i00);
        assertNotNull(iat0);
        assertEquals(i00, iat0.indices());
        assertSame(primitives.stringParameterizedType(), iat0.type());
        HiddenContentTypes.IndicesAndType iat1 = map.get(i01);
        assertNotNull(iat1);
        assertEquals(i01, iat1.indices());
        assertEquals(primitives.integerTypeInfo().asSimpleParameterizedType(), iat1.type());
    }


}
