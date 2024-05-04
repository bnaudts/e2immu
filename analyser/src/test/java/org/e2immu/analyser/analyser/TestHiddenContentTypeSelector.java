package org.e2immu.analyser.analyser;

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
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

import static org.junit.jupiter.api.Assertions.*;

public class TestHiddenContentTypeSelector {
    private final Primitives primitives = new PrimitivesImpl();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    private final TypeInfo bar = new TypeInfo("com.foo", "Bar");
    private MethodInfo staticMethod;
    private TypeParameter methodTp0;

    private HiddenContentTypes barHct;
    private final TypeInfo extensible = new TypeInfo("com.foo", "Extensible");
    private final TypeInfo typeWithExtensibleField = new TypeInfo("com.foo", "TypeWithExtensibleField");
    private FieldInfo extensibleField;
    private HiddenContentTypes typeWithExtensibleHct;

    private final TypeInfo someTypeWithHC = new TypeInfo("com.foo", "HC");
    private final TypeParameter tp0 = new TypeParameterImpl(someTypeWithHC, "T", 0).noTypeBounds();
    private final ParameterizedType tp0Pt = new ParameterizedType(tp0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType someTypeWithHCPt = new ParameterizedType(someTypeWithHC, List.of(tp0Pt));

    private final TypeInfo set = new TypeInfo("com.foo", "Set");
    private final TypeParameter tpSet0 = new TypeParameterImpl(set, "E", 0).noTypeBounds();
    private final ParameterizedType tpSet0Pt = new ParameterizedType(tpSet0, 0, ParameterizedType.WildCard.NONE);

    private final TypeInfo someTypeOfString = new TypeInfo("com.foo", "SomeTypeOfString");

    private final TypeInfo mapMap = new TypeInfo("com.foo", "MapMap");
    private final TypeParameter mapMap0 = new TypeParameterImpl(mapMap, "T0", 0).noTypeBounds();
    private final TypeParameter mapMap1 = new TypeParameterImpl(mapMap, "T1", 1).noTypeBounds();
    private final TypeParameter mapMap2 = new TypeParameterImpl(mapMap, "T2", 2).noTypeBounds();
    private HiddenContentTypes mapMapHct;

    @BeforeEach
    public void beforeEach() {
        extensible.typeInspection.set(new TypeInspectionImpl.Builder(extensible, Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        extensibleField = new FieldInfo(Identifier.CONSTANT, extensible.asParameterizedType(inspectionProvider),
                "extensible", typeWithExtensibleField);
        extensibleField.fieldInspection.set(new FieldInspectionImpl.Builder(extensibleField)
                .setAccess(Inspection.Access.PUBLIC).build(inspectionProvider));
        typeWithExtensibleField.typeInspection.set(new TypeInspectionImpl.Builder(typeWithExtensibleField, Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .addField(extensibleField)
                .build(inspectionProvider));
        typeWithExtensibleHct = HiddenContentTypes.compute(inspectionProvider,
                typeWithExtensibleField.typeInspection.get(), false);

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

        TypeParameterImpl m0 = new TypeParameterImpl("M", 0).noTypeBounds();
        staticMethod = new MethodInspectionImpl.Builder(bar, "staticMethod", MethodInfo.MethodType.METHOD)
                .setReturnType(someTypeWithHCPt)
                .setAccess(Inspection.Access.PUBLIC)
                .addModifier(MethodModifier.STATIC)
                .addTypeParameter(m0)
                .build(inspectionProvider).getMethodInfo();
        methodTp0 = m0;
        bar.typeInspection.set(new TypeInspectionImpl.Builder(bar, Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setTypeNature(TypeNature.CLASS)
                .addMethod(staticMethod)
                .addTypeParameter(tp0)
                .build(inspectionProvider));
        barHct = HiddenContentTypes.compute(inspectionProvider, bar.typeInspection.get());
        bar.typeResolution.set(new TypeResolution.Builder().setHiddenContentTypes(barHct).build());
        HiddenContentTypes methodHct = HiddenContentTypes.compute(barHct, staticMethod.methodInspection.get());
        staticMethod.methodResolution.set(new MethodResolution.Builder().setHiddenContentTypes(methodHct).build());

        someTypeWithHC.typeInspection.set(new TypeInspectionImpl.Builder(someTypeWithHC, Inspector.BY_HAND)
                .addTypeParameter(tp0)
                .setParentClass(primitives.objectParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        someTypeOfString.typeInspection.set(new TypeInspectionImpl.Builder(someTypeOfString, Inspector.BY_HAND)
                .setParentClass(new ParameterizedType(someTypeWithHC, List.of(primitives.stringParameterizedType())))
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));

        mapMap.typeInspection.set(new TypeInspectionImpl.Builder(mapMap, Inspector.BY_HAND)
                .setTypeNature(TypeNature.INTERFACE)
                .addTypeParameter(mapMap0)
                .addTypeParameter(mapMap1)
                .addTypeParameter(mapMap2)
                .setParentClass(primitives.objectParameterizedType())
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider));
        mapMapHct = HiddenContentTypes.compute(inspectionProvider, mapMap.typeInspection.get());
        mapMap.typeResolution.set(new TypeResolution.Builder().setHiddenContentTypes(mapMapHct).build());
    }

    @Test
    @DisplayName("HCT indices Bar")
    public void test() {
        assertEquals("Bar:T", barHct.toString());
        assertEquals("{0=T as #0 in com.foo.HC}", barHct.getIndexToType().toString());
        HiddenContentTypes hctMethod = staticMethod.methodResolution.get().hiddenContentTypes();
        assertEquals("Bar:T - staticMethod:M", hctMethod.toString());
        assertEquals("{1=M as #0 in com.foo.Bar.staticMethod()}", hctMethod.getIndexToType().toString());
        assertSame(barHct, hctMethod.getHcsTypeInfo());
        assertEquals(0, hctMethod.indexOf(tp0));
        assertEquals(0, hctMethod.indexOf(tp0Pt));
        assertEquals(1, hctMethod.indexOf(methodTp0));
        assertEquals(1, hctMethod.indexOf(methodTp0.toParameterizedType()));
    }

    @Test
    @DisplayName("HCT indices MapMap")
    public void test2() {
        assertEquals("MapMap:T0, T1, T2", mapMapHct.toString());
        assertEquals(2, mapMapHct.indexOf(mapMap2));
    }

    @Test
    @DisplayName("Extensible")
    public void test3() {
        assertEquals(1, typeWithExtensibleHct.size());
        assertEquals(0, typeWithExtensibleHct.indexOf(extensibleField.type));
    }

    @Test
    @DisplayName("HCS.selectAll")
    public void test4() {
        ParameterizedType formalTWE = typeWithExtensibleField.asParameterizedType(inspectionProvider);
        HiddenContentSelector hcs = HiddenContentSelector.selectAll(typeWithExtensibleHct, formalTWE);
        assertEquals("0", hcs.toString());
    }
}
