package org.e2immu.analyser.analyser;

import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
                .setHiddenContentTypes(HiddenContentTypes.compute(collection.typeInspection.get()))
                .build());

        list.typeInspection.set(new TypeInspectionImpl.Builder(list, Inspector.BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(tpList0)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(collection, List.of(tpList0Pt)))
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        list.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(list.typeInspection.get()))
                .build());

        arrayList.typeInspection.set(new TypeInspectionImpl.Builder(arrayList, Inspector.BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .addTypeParameter(tpAl0)
                .setParentClass(primitives.objectParameterizedType())
                .addInterfaceImplemented(new ParameterizedType(list, List.of(tpAl0Pt)))
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        arrayList.typeResolution.set(new TypeResolution.Builder()
                .setHiddenContentTypes(HiddenContentTypes.compute(arrayList.typeInspection.get()))
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
        ParameterizedType collEA = new ParameterizedType(collection, List.of(tpAl0Pt));
        ParameterizedType listStr = new ParameterizedType(list, List.of(primitives.stringParameterizedType()));
        Map<Integer, Integer> map = hctAl.translateHcs(inspectionProvider, Set.of(0), collEA, listStr, true);
        assertEquals("{0=0}", map.toString());
    }
}
