package org.e2immu.analyser.analyser;

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestHiddenContentTypes {
    private final Primitives primitives = new PrimitivesImpl();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    private final TypeInfo bar = new TypeInfo("com.foo", "Bar");
    private MethodInfo staticMethod;
    private TypeParameter methodTp0;

    private HiddenContentTypes barHct;

    private final TypeInfo someTypeWithHC = new TypeInfo("com.foo", "HC");
    private final TypeParameter tp0 = new TypeParameterImpl(someTypeWithHC, "T", 0).noTypeBounds();
    private final ParameterizedType tp0Pt = new ParameterizedType(tp0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType someTypeWithHCPt = new ParameterizedType(someTypeWithHC, List.of(tp0Pt));

    private final TypeInfo set = new TypeInfo("com.foo", "Set");
    private final TypeParameter tpSet0 = new TypeParameterImpl(set, "E", 0).noTypeBounds();
    private final ParameterizedType tpSet0Pt = new ParameterizedType(tpSet0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType setPt = new ParameterizedType(set, List.of(tpSet0Pt));

    @BeforeEach
    public void beforeEach() {
        TypeInspection objectTi = new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC).build(inspectionProvider);
        primitives.objectParameterizedType().typeInfo.typeInspection.set(objectTi);
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
        barHct = HiddenContentTypes.compute(bar.typeInspection.get());
        bar.typeResolution.set(new TypeResolution.Builder().setHiddenContentTypes(barHct).build());
        HiddenContentTypes methodHct = HiddenContentTypes.compute(barHct, staticMethod.methodInspection.get());
        staticMethod.methodResolution.set(new MethodResolution.Builder().setHiddenContentTypes(methodHct).build());
    }

    @Test
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
    public void test1() {
        Map<Integer, ParameterizedType> map = barHct.mapTypesRecursively(inspectionProvider, primitives.stringParameterizedType(), tp0Pt);
        assertEquals("{0=Type String}", map.toString());

        ParameterizedType someString = new ParameterizedType(someTypeWithHC, List.of(primitives.stringParameterizedType()));
        assertEquals("Type com.foo.HC<String>", someString.toString());
        Map<Integer, ParameterizedType> map2 = barHct.mapTypesRecursively(inspectionProvider, someString, someTypeWithHCPt);
        assertEquals("{0=Type String}", map2.toString());

        assertEquals("Type com.foo.Set<E>", setPt.toString());
        ParameterizedType setSomeType = new ParameterizedType(set, List.of(someTypeWithHCPt));
        assertEquals("Type com.foo.Set<com.foo.HC<T>>", setSomeType.toString());

        ParameterizedType setSomeTypeString = new ParameterizedType(set, List.of(someString));
        assertEquals("Type com.foo.Set<com.foo.HC<String>>", setSomeTypeString.toString());

        Map<Integer, ParameterizedType> map3 = barHct.mapTypesRecursively(inspectionProvider, setSomeTypeString, setSomeType);
        assertEquals("{0=Type String}", map3.toString());
    }
}
