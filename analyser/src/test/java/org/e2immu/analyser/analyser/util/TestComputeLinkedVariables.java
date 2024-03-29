package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoContainerImpl;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestComputeLinkedVariables {
    private static Identifier newId() {
        return Identifier.generate("test");
    }

    private final Primitives primitives = new PrimitivesImpl();
    private final ParameterizedType irrelevantPt = primitives.stringParameterizedType();
    private final StringConstant irrelevantValue = new StringConstant(primitives, "x");
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);
    private final TypeInfo currentType = new TypeInfo("com.foo", "Bar");
    private MethodInfo currentMethod;

    private final Map<String, VariableInfoContainer> vicMap = new HashMap<>();
    private final AtomicBoolean brokeDelay = new AtomicBoolean();
    private StatementAnalysis sa;
    private Location location;
    private LocalVariableReference x;
    private This thisVar;
    private FieldReference thisSet;
    private ReturnVariable returnVariable;

    @BeforeEach
    public void beforeEach() {
        currentMethod = new MethodInspectionImpl.Builder(currentType, "method", MethodInfo.MethodType.METHOD)
                .setReturnType(irrelevantPt)
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider).getMethodInfo();
        currentType.typeInspection.set(new TypeInspectionImpl.Builder(currentType, Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setTypeNature(TypeNature.CLASS)
                .addMethod(currentMethod)
                .build(inspectionProvider));
        location = new LocationImpl(currentMethod, "0-E", newId());
        sa = new StatementAnalysis() {
            @Override
            public int compareTo(StatementAnalysis o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String index() {
                return "0";
            }

            @Override
            public Location location(Stage stage) {
                return location;
            }

            @Override
            public VariableInfoContainer getVariable(String fullyQualifiedName) {
                return Objects.requireNonNull(getVariableOrDefaultNull(fullyQualifiedName));
            }

            @Override
            public VariableInfoContainer getVariableOrDefaultNull(String fullyQualifiedName) {
                return vicMap.get(fullyQualifiedName);
            }

            @Override
            public Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(Stage level) {
                return vicMap.entrySet().stream();
            }

            @Override
            public void setBrokeDelay() {
                brokeDelay.set(true);
            }
        };
        x = new LocalVariableReference(new LocalVariable("x", irrelevantPt),
                irrelevantValue);
        VariableInfoContainerImpl vicX = VariableInfoContainerImpl.newVariable(location, x, VariableNature.METHOD_WIDE,
                false);
        vicMap.put(x.fullyQualifiedName(), vicX);

        thisVar = new This(inspectionProvider, currentType);
        VariableInfoContainerImpl vicThis = VariableInfoContainerImpl.newVariable(location, thisVar,
                VariableNature.METHOD_WIDE, false);
        vicMap.put(thisVar.fullyQualifiedName(), vicThis);

        returnVariable = new ReturnVariable(currentMethod);
        assertEquals("com.foo.Bar.method()", returnVariable.fullyQualifiedName());
        VariableInfoContainerImpl vicRv = VariableInfoContainerImpl.newVariable(location, returnVariable,
                VariableNature.METHOD_WIDE, false);
        vicMap.put(returnVariable.fullyQualifiedName(), vicRv);

        FieldInfo set = new FieldInfo(newId(), irrelevantPt, "set", currentType);
        set.fieldInspection.set(new FieldInspectionImpl.Builder(set)
                .setAccess(Inspection.Access.PRIVATE)
                .build(inspectionProvider));
        assertEquals("com.foo.Bar.set", set.fullyQualifiedName);
        thisSet = new FieldReferenceImpl(inspectionProvider, set, new VariableExpression(newId(), thisVar), currentType);
        VariableInfoContainerImpl vicThisSet = VariableInfoContainerImpl.newVariable(location, thisSet,
                VariableNature.METHOD_WIDE, false);
        vicMap.put(thisSet.fullyQualifiedName(), vicThisSet);

    }

    @Test
    public void test() {
        Properties properties = Properties.of(Map.of());
        vicMap.values().forEach(vic -> vic.setValue(irrelevantValue, LinkedVariables.EMPTY, properties, Stage.INITIAL));

        LV hc0ToAll = LV.createHC(HiddenContentSelector.CsSet.selectTypeParameter(0),
                HiddenContentSelector.All.INSTANCE);

        // corresponds to the fictional statement "return this.set.add(x)", where "add"
        // returns a set dependent on this.set, and "x" becomes hidden content of this.set
        Function<Variable, LinkedVariables> lvs = v -> switch (v.fullyQualifiedName()) {
            case "x" -> LinkedVariables.of(thisSet, hc0ToAll);
            case "com.foo.Bar.this" -> LinkedVariables.EMPTY;
            case "com.foo.Bar.set" -> LinkedVariables.of(Map.of(
                    x, hc0ToAll,
                    returnVariable, LV.LINK_DEPENDENT));
            case "com.foo.Bar.method()" -> LinkedVariables.of(thisSet, LV.LINK_DEPENDENT);
            default -> throw new UnsupportedOperationException("Variable " + v.fullyQualifiedName());
        };

        ComputeLinkedVariables clv = ComputeLinkedVariables.create(sa, Stage.EVALUATION,
                false, (vic, v) -> false, Set.of(),
                lvs, new GraphCacheImpl(10), BreakDelayLevel.NONE);

        assertEquals("[set, this, x]", clv.getVariablesInClusters().stream()
                .map(Variable::simpleName).sorted().toList().toString());
        // FIXME?
        assertEquals("[return method]", clv.getReturnValueCluster().toString());

        ProgressAndDelay pad = clv.writeClusteredLinkedVariables();
        assertTrue(pad.progress());
        assertFalse(pad.isDelayed());
        assertFalse(brokeDelay.get());

        VariableInfo viX = vicMap.get(x.fullyQualifiedName()).current();
        assertEquals("", viX.getLinkedVariables().toString());

        VariableInfo viSet = vicMap.get(thisSet.fullyQualifiedName()).current();
        assertEquals("return method:2", viSet.getLinkedVariables().toString());
    }

}
