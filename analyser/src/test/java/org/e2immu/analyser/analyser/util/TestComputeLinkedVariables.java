package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoContainerImpl;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestComputeLinkedVariables {

    public static final String SET = "com.foo.Bar.set";
    public static final String X = "x";
    public static final String THIS = "com.foo.Bar.this";
    public static final String METHOD = "com.foo.Bar.method()";

    private static Identifier newId() {
        return Identifier.generate("test");
    }

    private final Primitives primitives = new PrimitivesImpl();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);
    private final AnalyserContext analyserContext = new AnalyserContext() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }
    };

    private final ParameterizedType someTypeWithoutHC = primitives.stringParameterizedType();
    private final StringConstant valueOfSomeTypeWithoutHc = new StringConstant(primitives, X);
    private final TypeInfo currentType = new TypeInfo("com.foo", "Bar");

    private final TypeInfo someTypeWithHC = new TypeInfo("com.foo", "HC");
    private final TypeParameter tp0 = new TypeParameterImpl(someTypeWithHC, "T", 0).noTypeBounds();
    private final ParameterizedType tp0Pt = new ParameterizedType(tp0, 0, ParameterizedType.WildCard.NONE);
    private final ParameterizedType someTypeWithHCPt = new ParameterizedType(someTypeWithHC, List.of(tp0Pt));

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
        TypeInspection objectTi = new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC).build(inspectionProvider);
        primitives.objectParameterizedType().typeInfo.typeInspection.set(objectTi);
        MethodInfo currentMethod = new MethodInspectionImpl.Builder(currentType, "method", MethodInfo.MethodType.METHOD)
                .setReturnType(someTypeWithHCPt)
                .setAccess(Inspection.Access.PUBLIC)
                .build(inspectionProvider).getMethodInfo();
        currentType.typeInspection.set(new TypeInspectionImpl.Builder(currentType, Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setTypeNature(TypeNature.CLASS)
                .addMethod(currentMethod)
                .addTypeParameter(tp0)
                .build(inspectionProvider));

        someTypeWithHC.typeInspection.set(new TypeInspectionImpl.Builder(someTypeWithHC, Inspector.BY_HAND)
                .setParentClass(primitives.objectParameterizedType())
                .setTypeNature(TypeNature.CLASS)
                .addTypeParameter(tp0)
                .build(inspectionProvider));
        someTypeWithHC.typeAnalysis.set(new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                someTypeWithHC, analyserContext).build());
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
        x = new LocalVariableReference(new LocalVariable(X, tp0Pt),
                NullConstant.NULL_CONSTANT);
        VariableInfoContainerImpl vicX = VariableInfoContainerImpl.newVariable(location, x, VariableNature.METHOD_WIDE,
                false);
        vicMap.put(x.fullyQualifiedName(), vicX);

        thisVar = new This(inspectionProvider, currentType);
        VariableInfoContainerImpl vicThis = VariableInfoContainerImpl.newVariable(location, thisVar,
                VariableNature.METHOD_WIDE, false);
        vicMap.put(thisVar.fullyQualifiedName(), vicThis);

        returnVariable = new ReturnVariable(currentMethod);
        assertEquals(METHOD, returnVariable.fullyQualifiedName());
        VariableInfoContainerImpl vicRv = VariableInfoContainerImpl.newVariable(location, returnVariable,
                VariableNature.METHOD_WIDE, false);
        vicMap.put(returnVariable.fullyQualifiedName(), vicRv);

        FieldInfo set = new FieldInfo(newId(), someTypeWithHCPt, "set", currentType);
        set.fieldInspection.set(new FieldInspectionImpl.Builder(set)
                .setAccess(Inspection.Access.PRIVATE)
                .build(inspectionProvider));
        assertEquals(SET, set.fullyQualifiedName);
        thisSet = new FieldReferenceImpl(inspectionProvider, set, new VariableExpression(newId(), thisVar), currentType);
        VariableInfoContainerImpl vicThisSet = VariableInfoContainerImpl.newVariable(location, thisSet,
                VariableNature.METHOD_WIDE, false);
        vicMap.put(thisSet.fullyQualifiedName(), vicThisSet);

    }

    @Test
    public void test() {
        Properties properties = Properties.of(Map.of());
        vicMap.values().forEach(vic -> vic.setValue(valueOfSomeTypeWithoutHc, LinkedVariables.EMPTY, properties, Stage.INITIAL));

        LV hc0ToAll = LV.createHC(HiddenContentSelector.CsSet.selectTypeParameter(0),
                HiddenContentSelector.All.INSTANCE);

        // corresponds to the fictional statement "return this.set.add(x)", where "add"
        // returns a set dependent on this.set, and "x" becomes hidden content of this.set
        // method -2-> this.set <-4-> x; thi link from this.set to this will be added
        Function<Variable, LinkedVariables> lvs = v -> switch (v.fullyQualifiedName()) {
            case X -> LinkedVariables.of(thisSet, hc0ToAll);
            case THIS -> LinkedVariables.EMPTY;
            case SET -> LinkedVariables.of(x, hc0ToAll);
            case METHOD -> LinkedVariables.of(thisSet, LV.LINK_DEPENDENT);
            default -> throw new UnsupportedOperationException("Variable " + v.fullyQualifiedName());
        };

        ComputeLinkedVariables clv = ComputeLinkedVariables.create(sa, Stage.EVALUATION,
                false, (vic, v) -> false, Set.of(),
                lvs, new GraphCacheImpl(10), BreakDelayLevel.NONE);

        // the following are the "static" clusters (linking at level STATICALLY_ASSIGNED)
        assertEquals("[set, this, x]", clv.getVariablesInClusters().stream()
                .map(Variable::simpleName).sorted().toList().toString());
        assertEquals("[return method]", clv.getReturnValueCluster().toString());

        WeightedGraph weightedGraph = clv.getWeightedGraph();
        weightedGraph.visit((v, map) -> {
            String nice = switch (v.fullyQualifiedName()) {
                case SET -> "this=2, x=4";
                case X -> "set=4";
                case THIS, METHOD -> "set=2";
                default -> throw new UnsupportedOperationException();
            };
            assertEquals(nice, niceMap(map), "variable " + v.fullyQualifiedName());
        });
        ProgressAndDelay pad = clv.writeClusteredLinkedVariables(analyserContext);
        assertTrue(pad.progress());
        assertFalse(pad.isDelayed());
        assertFalse(brokeDelay.get());

        VariableInfo viX = vicMap.get(x.fullyQualifiedName()).current();
        assertEquals("this.set:4,this:4", viX.getLinkedVariables().toString());

        VariableInfo viSet = vicMap.get(thisSet.fullyQualifiedName()).current();
        assertEquals("this:2,x:4", viSet.getLinkedVariables().toString());

        VariableInfo viThis = vicMap.get(thisVar.fullyQualifiedName()).current();
        assertEquals("this.set:2,x:4", viThis.getLinkedVariables().toString());

        VariableInfo viMethod = vicMap.get(returnVariable.fullyQualifiedName()).current();
        assertEquals("this.set:2,this:2,x:4", viMethod.getLinkedVariables().toString());
    }

    private static String niceMap(Map<Variable, LV> map) {
        if (map == null) return "null";
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().value())
                .sorted()
                .collect(Collectors.joining(", "));
    }

}
