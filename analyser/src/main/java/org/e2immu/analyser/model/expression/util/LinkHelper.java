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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.LV.*;
import static org.e2immu.analyser.model.MultiLevel.*;

public class LinkHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinkHelper.class);

    private final EvaluationResult context;
    private final MethodAnalysis methodAnalysis;
    private final MethodInfo methodInfo;
    private final HiddenContentTypes hiddenContentTypes;
    private final HiddenContentSelector hcsSource;

    public LinkHelper(EvaluationResult context, MethodInfo methodInfo) {
        this(context, methodInfo, context.getAnalyserContext().getMethodAnalysis(methodInfo));
    }

    /*
    we move from 'methodIn' to the method actually used by the hidden content selector of 'methodIn'.
    This is relevant in case of anonymous implementations of functional interfaces, see e.g. Linking_1A.s1a()
     */
    public LinkHelper(EvaluationResult context, MethodInfo methodInfoIn, MethodAnalysis methodAnalysis) {
        this.context = context;
        HiddenContentSelector methodHcs = Objects.requireNonNullElse(methodAnalysis.getHiddenContentSelector(),
                HiddenContentSelector.None.INSTANCE);
        hiddenContentTypes = Objects.requireNonNullElseGet(methodHcs.hiddenContentTypes(),
                () -> methodInfoIn.methodResolution.get().hiddenContentTypes());
        this.methodInfo = hiddenContentTypes.getMethodInfo();
        this.methodAnalysis = this.methodInfo == methodInfoIn ? methodAnalysis
                : context.getAnalyserContext().getMethodAnalysis(this.methodInfo);
        ParameterizedType formalObject = this.methodInfo.typeInfo.asParameterizedType(context.getAnalyserContext());
        hcsSource = HiddenContentSelector.selectAll(hiddenContentTypes, formalObject);
    }

    /*
    Linked variables of parameter.
    There are 2 types involved:
      1. type in declaration = parameterMethodType
      2. type in method call = parameterExpression.returnType() == parameterType

    This is a prep method, where we re-compute the base of the parameter's link to the hidden content types of the method.
    The minimum link level is LINK_DEPENDENT.
    The direction of the links is from the method to the variables linked to the parameter, correcting for the concrete parameter type.
    All Indices on the 'from'-side are single HCT indices.

    If the parameterMethodType is a type parameter, we'll have an index of hc wrt the method:
    If the argument is a variable, the link is typically v:0; we'll link 'index of hc' -2-> ALL if the concrete type
      allows so.
    If the argument is dependently linked to a variable, e.g. v.subList(..), then we'll still link DEPENDENT, and
      add the 'index of hc' -2-> ALL.
    If the argument is HC linked to a variable, e.g. new ArrayList<>(..), we'll link 'index of hc' -4-> ALL.

    If parameterMethodType is not a type parameter, we'll compute a translation map which expresses
    the hidden content of the method, expressed in parameterMethodType, to indices in the parameter type.

    IMPORTANT: these links are meant to be combined with either links to object, or links to other parameters.
    This code is different from the normal linkedVariables(...) method.

    In the case of links between parameters, the "source" becomes the object.
     */
    private LinkedVariables linkedVariablesOfParameter(ParameterizedType parameterMethodType,
                                                       ParameterizedType parameterType,
                                                       LinkedVariables linkedVariablesOfParameter,
                                                       HiddenContentSelector hcsSource) {
        InspectionProvider inspectionProvider = context.getAnalyserContext();
        AtomicReference<CausesOfDelay> causes = new AtomicReference<>(CausesOfDelay.EMPTY);
        Map<Variable, LV> map = new HashMap<>();

        Integer index = hiddenContentTypes.indexOfOrNull(parameterMethodType);
        if (index != null && parameterMethodType.parameters.isEmpty()) {
            linkedVariablesOfParameter.stream().forEach(e -> {
                Variable variable = e.getKey();
                LV lv = e.getValue();
                if (lv.isDelayed()) {
                    causes.set(causes.get().merge(lv.causesOfDelay()));
                }
                DV mutable = context.evaluationContext().immutable(parameterType);
                if (mutable.isDelayed()) {
                    causes.set(causes.get().merge(mutable.causesOfDelay()));
                    mutable = MUTABLE_DV;
                }
                if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(mutable)) {
                    boolean m = MultiLevel.isMutable(mutable);
                    Indices indices = new Indices(Set.of(new Index(List.of(0))));
                    Links links = new Links(Map.of(indices, new Link(ALL_INDICES, m)));
                    boolean independentHc = lv.isCommonHC();
                    LV newLv = independentHc ? LV.createHC(links) : LV.createDependent(links);
                    map.put(variable, newLv);
                }
            });
        } else {
            Map<LV.Indices, HiddenContentTypes.IndicesAndType> targetData = hiddenContentTypes
                    .translateHcs(inspectionProvider, hcsSource, parameterMethodType, parameterType);
            linkedVariablesOfParameter.stream().forEach(e -> {
                LV newLv;
                LV lv = e.getValue();
                if (lv.isDelayed()) {
                    causes.set(causes.get().merge(lv.causesOfDelay()));
                }
                if (targetData != null && !targetData.isEmpty()) {
                    Map<LV.Indices, Link> linkMap = new HashMap<>();
                    for (Map.Entry<LV.Indices, HiddenContentTypes.IndicesAndType> entry : targetData.entrySet()) {
                        Indices iInHctSource = entry.getKey();
                        Indices iInHctTarget = entry.getValue().indices();
                        ParameterizedType type = entry.getValue().type();
                        assert type != null;
                        DV immutable = context.evaluationContext().immutable(type);
                        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                            continue;
                        }
                        if (immutable.isDelayed()) {
                            causes.set(causes.get().merge(immutable.causesOfDelay()));
                            immutable = MUTABLE_DV;
                        }
                        boolean mutable = MultiLevel.isMutable(immutable);
                        linkMap.put(iInHctSource, new Link(iInHctTarget, mutable));
                    }
                    if (linkMap.isEmpty()) {
                        newLv = LINK_DEPENDENT;
                    } else {
                        Links links = new Links(Map.copyOf(linkMap));
                        boolean independentHc = lv.isCommonHC();
                        newLv = independentHc ? LV.createHC(links) : LV.createDependent(links);
                    }
                } else {
                    newLv = LINK_DEPENDENT;
                }
                Variable variable = e.getKey();
                map.put(variable, newLv);
            });
        }
        LinkedVariables lvs = LinkedVariables.of(map);
        if (causes.get().isDelayed()) {
            lvs.changeToDelay(LV.delay(causes.get()));
        }
        return lvs;
    }

    public LinkedVariables functional(DV independentOfMethod,
                                      HiddenContentSelector hcsMethod,
                                      LinkedVariables linkedVariablesOfObject,
                                      ParameterizedType concreteReturnType,
                                      List<DV> independentOfParameter,
                                      List<HiddenContentSelector> hcsParameters,
                                      List<ParameterizedType> expressionTypes,
                                      ParameterizedType concreteFunctionalType) {
        LinkedVariables lvs = functional(independentOfMethod, hcsMethod, linkedVariablesOfObject, concreteReturnType,
                concreteFunctionalType);
        int i = 0;
        for (ParameterizedType expressionType : expressionTypes) {
            int index = Math.min(hcsParameters.size() - 1, i);
            DV independent = independentOfParameter.get(index);
            HiddenContentSelector hcs = hcsParameters.get(index);
            LinkedVariables lvsParameter = functional(independent, hcs, linkedVariablesOfObject, expressionType,
                    concreteFunctionalType);
            lvs = lvs.merge(lvsParameter);
            i++;
        }
        return lvs;
    }

    private LinkedVariables functional(DV independent,
                                       HiddenContentSelector hcs,
                                       LinkedVariables linkedVariables,
                                       ParameterizedType type,
                                       ParameterizedType concreteFunctionalType) {
        if (INDEPENDENT_DV.equals(independent)) return LinkedVariables.EMPTY;
        boolean independentHC = INDEPENDENT_HC_DV.equals(independent);
        Map<Variable, LV> map = new HashMap<>();
        List<CausesOfDelay> causesOfDelays = new ArrayList<>();
        linkedVariables.forEach(e -> {
            DV mutable = context.evaluationContext().immutable(type);
            if (mutable.isDelayed()) {
                causesOfDelays.add(mutable.causesOfDelay());
                mutable = MUTABLE_DV;
            }
            if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(mutable)) {
                Links links;
                if (hcs instanceof HiddenContentSelector.All all) {
                    Indices indices = new Indices(all.getHiddenContentIndex());
                    // see e.g. Linking_1A,f9m(): we correct 0 to 0;1, and 1 to 0;1
                    Indices corrected = indices.allOccurrencesOf(context.getAnalyserContext(), concreteFunctionalType);
                    Link link = new Link(indices, MultiLevel.isMutable(mutable));
                    links = new Links(Map.of(corrected, link));
                } else {
                    throw new UnsupportedOperationException();
                }
                LV lv = independentHC ? LV.createHC(links) : LV.createDependent(links);
                map.put(e.getKey(), lv);
            }
        });
        if (map.isEmpty()) return LinkedVariables.EMPTY;
        if (!causesOfDelays.isEmpty()) {
            LV delay = LV.delay(causesOfDelays.stream().reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge));
            return LinkedVariables.of(map).changeToDelay(delay);
        }
        return LinkedVariables.of(map);
    }

    public record LambdaResult(List<LinkedVariables> linkedToParameters, LinkedVariables linkedToReturnValue) {
        public LinkedVariables delay(CausesOfDelay causesOfDelay) {
            return linkedToParameters.stream().reduce(LinkedVariables.EMPTY, LinkedVariables::merge)
                    .merge(linkedToReturnValue.changeToDelay(LV.delay(causesOfDelay)));
        }

        public LinkedVariables mergedLinkedToParameters() {
            return linkedToParameters.stream().reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
        }
    }

    /*
    SITUATION 0: both return value and all parameters @Independent
    no linking

    predicate is the typical example

    SITUATION 1: interesting return value, no parameters, or all parameters @Independent

    rv = supplier.get()             rv *-4-0 supplier       non-modifying, object to return value
    s = () -> supplier.get()        s 0-4-0 supplier
    s = supplier::get               s 0-4-0 supplier

    rv = iterator.next()            rv *-4-0 iterator (it is not relevant if the method is modifying, or not)
    s = () -> iterator.next()       s 0-4-0 iterator
    s = iterator::next              s 0-4-0 iterator
    t = s.get()                     t *-4-0 s, *-0-4 iterator

    rv = biSupplier.get()           rv *-4-0.0;0.1
    s = () -> biSupplier.get()      s 0.0;0.1-4-0.0;0.1
    pair = s.get()                  pair 0.0;0.1-4-0.0;0.1 biSupplier,s
    x = pair.x                      x *-4-0.0 pair, *-4-0.0 biSupplier

    -4- links depending on the HCS of the method (@Independent(hc), @Dependent?)
    -4-M links depending on the concrete type of rv when computing
    -2- links are also possible, e.g. subList(0, 2)

    sub = list.subList(0, 3)        sub 0-2-0 list
    s = i -> list.subList(0, i)     s 0-2-0 list

    conclusion:
     - irrespective of method modification.
     - @Independent of method is primary selector (-2-,-4-,no link)
     - * gets upgraded to the value in method HCS

    SITUATION 2: no return value, or an @Independent return value; at least one interesting parameter

    consumer.accept(t)              t *-4-0 consumer        modifying, parameter to object
    s = (t -> consumer.accept(t))   s 0-4-0 consumer
    sx.foreach(consumer)            sx 0-4-0 consumer

    list.add(t)                     t *-4-0 list
    s = t -> list.add(t)            s 0-4-0 list

    conclusion:
    - identical to situation 1, where the parameter(s) takes the role of the return value; each independently of the other

    SITUATION 3: neither return value, nor at least one parameter is @Independent
    (the return value links to the object, and at least one parameter links to the object)

    do both of 1 and 2, and take union. 0.0-4-0.1 and 0.0-4-0.0 may result in 0.0;0.1-4-0.0;0.1
    example??

    FIXME split method so that it can be called from MR as well
    */
    public static LambdaResult lambdaLinking(EvaluationContext evaluationContext, MethodInfo concreteMethod) {

        MethodAnalysis methodAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(concreteMethod);
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        if (lastStatement == null) {
            return new LambdaResult(List.of(), LinkedVariables.EMPTY);
        }
        MethodInspection methodInspection = evaluationContext.getAnalyserContext().getMethodInspection(concreteMethod);
        List<LinkedVariables> result = new ArrayList<>(methodInspection.getParameters().size() + 1);

        for (ParameterInfo pi : methodInspection.getParameters()) {
            VariableInfo vi = lastStatement.getLatestVariableInfo(pi.fullyQualifiedName);
            LinkedVariables lv = vi.getLinkedVariables().remove(v ->
                    !evaluationContext.acceptForVariableAccessReport(v, concreteMethod.typeInfo));
            result.add(lv);
        }
        if (concreteMethod.hasReturnValue()) {
            ReturnVariable returnVariable = new ReturnVariable(concreteMethod);
            VariableInfo vi = lastStatement.getLatestVariableInfo(returnVariable.fqn);
            if (methodInspection.getParameters().isEmpty()) {
                return new LambdaResult(result, vi.getLinkedVariables());
            }
            // link to the input types rather than the output type, see also HCT.mapMethodToTypeIndices
            Map<Indices, Indices> correctionMap = new HashMap<>();
            // must be of formal type
            HiddenContentTypes hct = methodInspection.getMethodInfo().methodResolution.get().hiddenContentTypes();
            correctionMap.put(new Indices(1), new Indices(0));
            LinkedVariables corrected = vi.getLinkedVariables().map(lv -> lv.correctTo(correctionMap));
            return new LambdaResult(result, corrected);
        }
        return new LambdaResult(result, LinkedVariables.EMPTY);
    }

    public record FromParameters(EvaluationResult intoObject, EvaluationResult intoResult,
                                 Map<Integer, Integer> correctionMap) {
    }

    /*
    Add all necessary links from parameters into scope, and in-between parameters
     */
    public FromParameters linksInvolvingParameters(ParameterizedType objectPt,
                                                   ParameterizedType resultPt,
                                                   List<Expression> parameterExpressions,
                                                   List<EvaluationResult> parameterResults) {
        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodInfo);
        EvaluationResultImpl.Builder intoObjectBuilder = new EvaluationResultImpl.Builder(context)
                .setLinkedVariablesOfExpression(LinkedVariables.EMPTY);
        EvaluationResultImpl.Builder intoResultBuilder = resultPt == null || resultPt.isVoid() ? null
                : new EvaluationResultImpl.Builder(context).setLinkedVariablesOfExpression(LinkedVariables.EMPTY);
        Map<Integer, Integer> correctionMap = new HashMap<>();
        if (!methodInspection.getParameters().isEmpty()) {
            // links between object/return value and parameters
            for (ParameterAnalysis parameterAnalysis : methodAnalysis.getParameterAnalyses()) {
                DV formalParameterIndependent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                LinkedVariables lvsToResult = parameterAnalysis.getLinkToReturnValueOfMethod();
                boolean inResult = intoResultBuilder != null && !lvsToResult.isEmpty();
                if (!INDEPENDENT_DV.equals(formalParameterIndependent) || inResult) {
                    ParameterInfo pi = parameterAnalysis.getParameterInfo();
                    ParameterizedType parameterType = parameterExpressions.get(pi.index).returnType();
                    LinkedVariables parameterLvs;
                    if (inResult) {
                        /*
                        change the links of the parameter to the value of the return variable (see also MethodReference,
                        computation of links when modified is true)
                         */
                        LinkedVariables returnValueLvs = linkedVariablesOfParameter(pi.parameterizedType,
                                parameterExpressions.get(pi.index).returnType(),
                                parameterResults.get(pi.index).linkedVariablesOfExpression(), hcsSource);
                        LV valueOfReturnValue = lvsToResult.stream().filter(e -> e.getKey() instanceof ReturnVariable)
                                .map(Map.Entry::getValue).findFirst().orElseThrow();
                        Map<Variable, LV> map = returnValueLvs.stream().collect(Collectors.toMap(Map.Entry::getKey,
                                e -> Objects.requireNonNull(follow(valueOfReturnValue, e.getValue()))));
                        parameterLvs = LinkedVariables.of(map);
                        formalParameterIndependent = valueOfReturnValue.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
                    } else {
                        parameterLvs = linkedVariablesOfParameter(pi.parameterizedType,
                                parameterExpressions.get(pi.index).returnType(),
                                parameterResults.get(pi.index).linkedVariablesOfExpression(), hcsSource);
                    }
                    ParameterizedType pt = inResult ? resultPt : objectPt;
                    ParameterizedType methodPt;
                    if (inResult) {
                        methodPt = methodInspection.getReturnType();
                    } else {
                        methodPt = methodInfo.typeInfo.asParameterizedType(context.getAnalyserContext());
                    }
                    Map<Integer, Integer> mapMethodHCTIndexToTypeHCTIndex = methodInfo.methodResolution.get().hiddenContentTypes()
                            .mapMethodToTypeIndices(inResult ? methodInspection.getReturnType() : pi.parameterizedType);
                    correctionMap.putAll(mapMethodHCTIndexToTypeHCTIndex);
                    HiddenContentSelector hcsTarget = parameterAnalysis.getHiddenContentSelector().correct(mapMethodHCTIndexToTypeHCTIndex);
                    if (pt != null) {
                        LinkedVariables lv;
                        if (inResult) {
                            // parameter -> result

                            HiddenContentSelector hcsSource = methodAnalysis.getHiddenContentSelector().correct(mapMethodHCTIndexToTypeHCTIndex);
                            lv = linkedVariables(this.hcsSource, parameterType, pi.parameterizedType, hcsTarget,
                                    parameterLvs, false, formalParameterIndependent, pt, methodPt,
                                    hcsSource, false);
                        } else {
                            // object -> parameter (rather than the other way around)
                            lv = linkedVariables(this.hcsSource, pt, methodPt, this.hcsSource, parameterLvs, false,
                                    formalParameterIndependent, parameterType, pi.parameterizedType, hcsTarget,
                                    true);
                        }
                        EvaluationResultImpl.Builder builder = inResult ? intoResultBuilder : intoObjectBuilder;
                        builder.mergeLinkedVariablesOfExpression(lv);
                    }
                }
            }

            linksBetweenParameters(intoObjectBuilder, methodInfo, parameterExpressions, parameterResults);
        }
        return new FromParameters(intoObjectBuilder.build(), intoResultBuilder == null ? null :
                intoResultBuilder.build(), Map.copyOf(correctionMap));
    }

    public void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                       MethodInfo methodInfo,
                                       List<Expression> parameterExpressions,
                                       List<EvaluationResult> parameterResults) {
        Map<ParameterInfo, LinkedVariables> crossLinks = methodInfo.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        crossLinks.forEach((pi, lv) -> {
            boolean sourceIsVarArgs = pi.parameterInspection.get().isVarArgs();
            assert !sourceIsVarArgs : "Varargs must always be a target";
            HiddenContentSelector hcsSource = methodAnalysis.getParameterAnalyses().get(pi.index).getHiddenContentSelector();
            ParameterizedType sourceType = parameterExpressions.get(pi.index).returnType();
            LinkedVariables sourceLvs = linkedVariablesOfParameter(pi.parameterizedType,
                    parameterExpressions.get(pi.index).returnType(),
                    parameterResults.get(pi.index).linkedVariablesOfExpression(), hcsSource);

            lv.stream().forEach(e -> {
                ParameterInfo target = (ParameterInfo) e.getKey();

                boolean targetIsVarArgs = target.parameterInspection.get().isVarArgs();
                if (!targetIsVarArgs || parameterResults.size() > target.index) {

                    LV level = e.getValue();

                    for (int i = target.index; i < parameterResults.size(); i++) {
                        ParameterizedType targetType = parameterExpressions.get(target.index).returnType();
                        HiddenContentSelector hcsTarget = methodAnalysis.getParameterAnalyses().get(target.index).getHiddenContentSelector();

                        LinkedVariables targetLinkedVariables = linkedVariablesOfParameter(target.parameterizedType,
                                parameterExpressions.get(i).returnType(),
                                parameterResults.get(i).linkedVariablesOfExpression(), hcsSource);

                        DV independentDv = level.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
                        LinkedVariables mergedLvs = linkedVariables(hcsSource, targetType, target.parameterizedType, hcsSource,
                                targetLinkedVariables, targetIsVarArgs, independentDv, sourceType, pi.parameterizedType,
                                hcsTarget, targetIsVarArgs);
                        crossLink(sourceLvs, mergedLvs, builder);
                    }
                } // else: no value... empty varargs
            });
        });
    }

    /*
   In general, the method result 'a', in 'a = b.method(c, d)', can link to 'b', 'c' and/or 'd'.
   Independence and immutability restrict the ability to link.

   The current implementation is heavily focused on understanding links towards the fields of a type,
   i.e., in sub = list.subList(0, 10), we want to link sub to list.

   Links from the parameters to the result (from 'c' to 'a', from 'd' to 'a') have currently only
   been implemented for @Identity methods (i.e., between 'a' and 'c').

   So we implement
   1/ void methods cannot link
   2/ if the method is @Identity, the result is linked to the 1st parameter 'c'
   3/ if the method is a factory method, the result is linked to the parameter values

   all other rules now determine whether we return an empty set, or the set {'a'}.

   4/ independence is determined by the independence value of the method, and the independence value of the object 'a'
    */

    public LinkedVariables linkedVariablesMethodCallObjectToReturnType(ParameterizedType objectType,
                                                                       EvaluationResult objectResult,
                                                                       List<EvaluationResult> parameterResults,
                                                                       ParameterizedType returnType) {
        return linkedVariablesMethodCallObjectToReturnType(objectType, objectResult, parameterResults, returnType, Map.of());
    }

    public LinkedVariables linkedVariablesMethodCallObjectToReturnType(ParameterizedType objectType,
                                                                       EvaluationResult objectResult,
                                                                       List<EvaluationResult> parameterResults,
                                                                       ParameterizedType returnType,
                                                                       Map<Integer, Integer> mapMethodHCTIndexToTypeHCTIndex) {
        // RULE 1: void method cannot link
        if (methodInfo.noReturnValue()) return LinkedVariables.EMPTY;
        boolean recursiveCall = recursiveCall(methodInfo, context.evaluationContext());
        boolean breakCallCycleDelay = methodInfo.methodResolution.get().ignoreMeBecauseOfPartOfCallCycle();
        if (recursiveCall || breakCallCycleDelay) {
            return LinkedVariables.EMPTY;
        }
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);

        // RULE 2: @Identity links to the 1st parameter
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.valueIsTrue()) {
            return parameterResults.get(0).linkedVariablesOfExpression().maximum(LINK_ASSIGNED);
        }
        LinkedVariables linkedVariablesOfObject = objectResult.linkedVariablesOfExpression()
                .maximum(LINK_ASSIGNED); // should be delay-able!

        if (identity.isDelayed() && !parameterResults.isEmpty()) {
            // temporarily link to both the object and the parameter, in a delayed way
            LinkedVariables allParams = parameterResults.stream()
                    .map(EvaluationResult::linkedVariablesOfExpression)
                    .reduce(LinkedVariables.EMPTY, LinkedVariables::merge);
            return linkedVariablesOfObject
                    .merge(allParams)
                    .changeToDelay(LV.delay(identity.causesOfDelay()));
        }

        // RULE 3: @Fluent simply returns the same object, hence, the same linked variables
        DV fluent = methodAnalysis.getMethodProperty(Property.FLUENT);
        if (fluent.valueIsTrue()) {
            return linkedVariablesOfObject;
        }
        if (fluent.isDelayed()) {
            return linkedVariablesOfObject.changeNonStaticallyAssignedToDelay(fluent.causesOfDelay());
        }
        DV independent = methodAnalysis.getProperty(Property.INDEPENDENT);
        ParameterizedType methodType = methodInfo.typeInfo.asParameterizedType(context.getAnalyserContext());
        ParameterizedType methodReturnType = context.getAnalyserContext().getMethodInspection(methodInfo).getReturnType();

        HiddenContentSelector hcsTarget = Objects.requireNonNullElse(methodAnalysis.getHiddenContentSelector(),
                HiddenContentSelector.None.INSTANCE).correct(mapMethodHCTIndexToTypeHCTIndex);

        return linkedVariables(hcsSource, objectType,
                methodType, hcsSource, linkedVariablesOfObject,
                false,
                independent, returnType, methodReturnType, hcsTarget,
                false);
    }

       /* we have to probe the object first, to see if there is a value
       A. if there is a value, and the value offers a concrete implementation, we replace methodInfo by that
       concrete implementation.
       B. if there is no value, and the delay indicates that a concrete implementation may be forthcoming,
       we delay
       C otherwise (no value, no concrete implementation forthcoming) we continue with the abstract method.
       */

    public static boolean recursiveCall(MethodInfo methodInfo, EvaluationContext evaluationContext) {
        MethodAnalyser currentMethod = evaluationContext.getCurrentMethod();
        if (currentMethod != null && currentMethod.getMethodInfo() == methodInfo) return true;
        if (evaluationContext.getClosure() != null) {
            LOGGER.debug("Going recursive on call to {}, to {} ", methodInfo.fullyQualifiedName,
                    evaluationContext.getClosure().getCurrentType().fullyQualifiedName);
            return recursiveCall(methodInfo, evaluationContext.getClosure());
        }
        return false;
    }

    /**
     * Important: this method does not deal with hidden content specific to the method, because it has been designed
     * to connect the object to the return value, as called from <code>linkedVariablesMethodCallObjectToReturnType</code>.
     * Calls originating from <code>linksInvolvingParameters</code> must take this into account.
     *
     * @param sourceType                    must be type of object or parameterExpression, return type, non-evaluated
     * @param methodSourceType              the method declaration's type of the source
     * @param hiddenContentSelectorOfSource with respect to the method's HCT and methodSourceType
     * @param sourceLvs                     linked variables of the source
     * @param sourceIsVarArgs               allow for a correction of array -> element
     * @param transferIndependent           the transfer mode (dependent, independent HC, independent)
     * @param targetType                    must be type of object or parameterExpression, return type, non-evaluated
     * @param methodTargetType              the method declaration's type of the target
     * @param hiddenContentSelectorOfTarget with respect to the method's HCT and methodTargetType
     * @param reverse                       reverse the link, because we're reversing source and target, because we
     *                                      only deal with *->0 in this method, never 0->*,
     * @return the linked values of the target
     */
    private LinkedVariables linkedVariables(HiddenContentSelector hcsSource,
                                            ParameterizedType sourceType,
                                            ParameterizedType methodSourceType,
                                            HiddenContentSelector hiddenContentSelectorOfSource,
                                            LinkedVariables sourceLvs,
                                            boolean sourceIsVarArgs,
                                            DV transferIndependent,
                                            ParameterizedType targetType,
                                            ParameterizedType methodTargetType,
                                            HiddenContentSelector hiddenContentSelectorOfTarget,
                                            boolean reverse) {
        assert targetType != null;

        // RULE 1: no linking when the source is not linked or there is no transfer
        if (sourceLvs.isEmpty() || MultiLevel.INDEPENDENT_DV.equals(transferIndependent)) {
            return LinkedVariables.EMPTY;
        }
        assert !(hiddenContentSelectorOfTarget.isNone() && transferIndependent.equals(MultiLevel.INDEPENDENT_HC_DV))
                : "Impossible to have no knowledge of hidden content, and INDEPENDENT_HC";

        DV immutableOfSource = context.evaluationContext().immutable(sourceType);

        // RULE 2: delays
        if (immutableOfSource.isDelayed()) {
            return sourceLvs.changeToDelay(LV.delay(immutableOfSource.causesOfDelay()));
        }

        // RULE 3: immutable -> no link
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableOfSource)) {
            /*
             if the result type immutable because of a choice in type parameters, methodIndependent will return
             INDEPENDENT_HC, but the concrete type is deeply immutable
             */
            return LinkedVariables.EMPTY;
        }

        // RULE 4: delays
        if (transferIndependent.isDelayed()) {
            // delay in method independent
            return sourceLvs.changeToDelay(LV.delay(transferIndependent.causesOfDelay()));
        }
        InspectionProvider inspectionProvider = context.getAnalyserContext();

        Integer index = hiddenContentTypes.indexOfOrNull(methodTargetType);
        Map<Indices, HiddenContentTypes.IndicesAndType> hctMethodToHcsTarget;
        if (index != null && methodTargetType.parameters.isEmpty()) {
            // all links will become ALL->source
            hctMethodToHcsTarget = null;
        } else {
            hctMethodToHcsTarget = hiddenContentTypes.translateHcs(inspectionProvider, hiddenContentSelectorOfTarget,
                    methodTargetType, targetType);
        }

        DV correctedIndependent = correctIndependent(context.evaluationContext(), immutableOfSource,
                transferIndependent, targetType, hiddenContentSelectorOfTarget, hctMethodToHcsTarget);

        if (MultiLevel.INDEPENDENT_DV.equals(correctedIndependent)) {
            return LinkedVariables.EMPTY;
        }
        if (correctedIndependent.isDelayed()) {
            // delay in method independent
            return sourceLvs.changeToDelay(delay(correctedIndependent.causesOfDelay()));
        }

        Map<Indices, HiddenContentTypes.IndicesAndType> hctMethodToHctSource = hiddenContentTypes
                .translateHcs(inspectionProvider, hcsSource, methodSourceType, sourceType);

        Map<Variable, LV> newLinked = new HashMap<>();
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;

        for (Map.Entry<Variable, LV> e : sourceLvs) {
            ParameterizedType pt = e.getKey().parameterizedType();
            // for the purpose of this algorithm, unbound type parameters are HC
            DV immutable = context.evaluationContext().immutable(pt);
            LV lv = e.getValue();
            assert lv.lt(LINK_INDEPENDENT);

            if (immutable.isDelayed() || lv.isDelayed()) {
                causesOfDelay = causesOfDelay.merge(immutable.causesOfDelay()).merge(lv.causesOfDelay());
            } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                boolean createDependentLink = MultiLevel.isMutable(immutable) && isDependent(transferIndependent,
                        correctedIndependent, immutableOfSource, lv);

                if (!hiddenContentSelectorOfTarget.isNone()) {
                    // from mine==target to theirs==source
                    Map<Indices, Link> linkMap = new HashMap<>();

                        /*
                        this is the only place during computational analysis where we create common HC links.
                        all other links are created in the ShallowMethodAnalyser.
                         */
                    if (hiddenContentSelectorOfTarget instanceof HiddenContentSelector.All all) {
                        DV typeImmutable = context.evaluationContext().immutable(targetType);
                        if (typeImmutable.isDelayed()) {
                            causesOfDelay = causesOfDelay.merge(typeImmutable.causesOfDelay());
                        }
                        boolean mutable = MultiLevel.isMutable(typeImmutable);
                        int i = all.getHiddenContentIndex();
                        NamedType namedType = methodTargetType.namedType();
                        boolean accept = hiddenContentTypes.isAssignableTo(inspectionProvider, namedType, i);
                        if (accept) {
                            assert hctMethodToHctSource != null;
                            // the indices contain a single number, the index in the hidden content types of the source.
                            Indices iInHctSource = hctMethodToHctSource.get(new Indices(i)).indices();
                            assert iInHctSource != null;
                            linkMap.put(ALL_INDICES, new Link(iInHctSource, mutable));
                        }
                    } else {
                        // both are CsSet, we'll set mutable what is mutable, in a common way
                        if (hiddenContentSelectorOfTarget instanceof HiddenContentSelector.CsSet mineCsSet) {
                            Boolean correctForVarargsMutable = null;

                            assert hctMethodToHctSource != null;
                            assert hctMethodToHcsTarget != null;

                            for (Map.Entry<Integer, Indices> entry : mineCsSet.getMap().entrySet()) {
                                Indices indicesInTargetWrtMethod = entry.getValue();
                                HiddenContentTypes.IndicesAndType targetAndType = hctMethodToHcsTarget.get(indicesInTargetWrtMethod);
                                assert targetAndType != null;
                                ParameterizedType type = targetAndType.type();
                                assert type != null;

                                DV typeImmutable = context.evaluationContext().immutable(type);
                                if (typeImmutable.isDelayed()) {
                                    causesOfDelay = causesOfDelay.merge(typeImmutable.causesOfDelay());
                                    typeImmutable = MUTABLE_DV;
                                }
                                if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(typeImmutable)) {
                                    continue;
                                }
                                boolean mutable = isMutable(typeImmutable);
                                if (sourceIsVarArgs) {
                                    // we're in a varargs situation: the first element is the type itself
                                    correctForVarargsMutable = mutable;
                                }

                                Indices indicesInSourceWrtMethod = ((HiddenContentSelector.CsSet) hiddenContentSelectorOfSource).getMap().get(entry.getKey());
                                assert indicesInSourceWrtMethod != null;
                                HiddenContentTypes.IndicesAndType indicesAndType = hctMethodToHctSource.get(indicesInSourceWrtMethod);
                                assert indicesAndType != null;
                                Indices indicesInSourceWrtType = indicesAndType.indices();
                                assert indicesInSourceWrtType != null;

                                Indices indicesInTargetWrtType = targetAndType.indices();
                                Indices correctedIndicesInTargetWrtType;
                                if (correctForVarargsMutable != null) {
                                    correctedIndicesInTargetWrtType = ALL_INDICES;
                                } else {
                                    correctedIndicesInTargetWrtType = indicesInTargetWrtType;
                                }
                                linkMap.put(correctedIndicesInTargetWrtType, new Link(indicesInSourceWrtType, mutable));
                            }
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }

                    if (createDependentLink) {
                        if (linkMap.isEmpty()) {
                            newLinked.put(e.getKey(), LINK_DEPENDENT);
                        } else {
                            Links links = new Links(Map.copyOf(linkMap));
                            LV dependent = reverse ? LV.createDependent(links.reverse()) : LV.createDependent(links);
                            newLinked.put(e.getKey(), dependent);
                        }
                    } else if (!linkMap.isEmpty()) {
                        Links links = new Links(Map.copyOf(linkMap));
                        LV commonHC = reverse ? LV.createHC(links.reverse()) : LV.createHC(links);
                        newLinked.put(e.getKey(), commonHC);
                    }
                }
            } else {
                throw new UnsupportedOperationException("I believe we should not link");
            }
        }
        if (causesOfDelay.isDelayed()) {
            return sourceLvs.changeToDelay(LV.delay(causesOfDelay));
        }
        return LinkedVariables.of(newLinked);
    }

    private boolean isDependent(DV transferIndependent, DV correctedIndependent,
                                DV immutableOfSource,
                                LV lv) {
        return
                // situation immutable(mutable), we'll have to override
                MultiLevel.INDEPENDENT_HC_DV.equals(transferIndependent)
                && MultiLevel.DEPENDENT_DV.equals(correctedIndependent)
                ||
                // situation mutable(immutable), dependent method,
                MultiLevel.DEPENDENT_DV.equals(transferIndependent)
                && !lv.isCommonHC()
                && !MultiLevel.isAtLeastImmutableHC(immutableOfSource);
    }
    
    /*
     Important: the last three parameters should form a consistent set, all computed with respect to the same
     formal type (targetType.typeInfo).
    
     First translate the HCS from the method target to the target!
     */

    private static DV correctIndependent(EvaluationContext evaluationContext,
                                         DV immutableOfSource,
                                         DV independent,
                                         ParameterizedType targetType,
                                         HiddenContentSelector hiddenContentSelectorOfTarget,
                                         Map<Indices, HiddenContentTypes.IndicesAndType> hctMethodToHcsTarget) {
        // immutableOfSource is not recursively immutable, independent is not fully independent
        // remaining values immutable: mutable, immutable HC
        // remaining values independent: dependent, independent hc
        if (MultiLevel.DEPENDENT_DV.equals(independent)) {
            if (MultiLevel.isAtLeastImmutableHC(immutableOfSource)) {
                return MultiLevel.INDEPENDENT_HC_DV;
            }
            if (hiddenContentSelectorOfTarget.isAll()) {
                // look at the whole object
                DV immutablePt = evaluationContext.immutable(targetType);
                if (immutablePt.isDelayed()) return immutablePt;
                if (MultiLevel.isAtLeastImmutableHC(immutablePt)) {
                    return MultiLevel.INDEPENDENT_HC_DV;
                }
            } else if (hiddenContentSelectorOfTarget instanceof HiddenContentSelector.CsSet csSet) {
                // if all types of the hcs are independent HC, then we can upgrade
                Map<Integer, Indices> selectorSet = csSet.getMap();
                boolean allIndependentHC = true;
                for (Map.Entry<Indices, HiddenContentTypes.IndicesAndType> entry : hctMethodToHcsTarget.entrySet()) {
                    if (selectorSet.containsValue(entry.getKey())) {
                        DV immutablePt = evaluationContext.immutable(entry.getValue().type());
                        if (immutablePt.isDelayed()) return immutablePt;
                        if (!MultiLevel.isAtLeastImmutableHC(immutablePt)) {
                            allIndependentHC = false;
                            break;
                        }
                    }
                }
                if (allIndependentHC) return MultiLevel.INDEPENDENT_HC_DV;
            }
        }
        if (MultiLevel.INDEPENDENT_HC_DV.equals(independent)) {
            if (hiddenContentSelectorOfTarget.isAll()) {
                DV immutablePt = evaluationContext.immutable(targetType);
                if (immutablePt.isDelayed()) return immutablePt;
                if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutablePt)) {
                    return MultiLevel.INDEPENDENT_DV;
                }
            } else {
                assert !hiddenContentSelectorOfTarget.isNone();
            }
        }
        return independent;
    }


    public void crossLink(LinkedVariables linkedVariablesOfObject,
                          LinkedVariables linkedVariablesOfObjectFromParams,
                          EvaluationResultImpl.Link link) {
        linkedVariablesOfObject.stream().forEach(e ->
                linkedVariablesOfObjectFromParams.stream().forEach(e2 -> {
                    Variable from = e.getKey();
                    Variable to = e2.getKey();
                    LV fromLv = e.getValue();
                    LV toLv = e2.getValue();
                    LV lv = follow(fromLv, toLv);
                    if (lv != null) {
                        link.link(from, to, lv);
                    }
                })
        );
    }

    public static LV follow(LV fromLv, LV toLv) {
        if (fromLv.isDelayed() || toLv.isDelayed()) {
            return LV.delay(fromLv.causesOfDelay().merge(toLv.causesOfDelay()));
        }
        boolean fromLvHaveLinks = fromLv.haveLinks();
        boolean toLvHaveLinks = toLv.haveLinks();
        if (!fromLvHaveLinks && !toLvHaveLinks) {
            return fromLv.max(toLv); // -0- and -1-, -1- and -2-
        }
        if (fromLv.isStaticallyAssignedOrAssigned()) {
            return toLv; // -0- 0-4-1
        }
        if (toLv.isStaticallyAssignedOrAssigned()) {
            return fromLv; // 1-2-1 -1-
        }
        if (fromLvHaveLinks && toLvHaveLinks) {
            boolean fromLvMineIsAll = fromLv.mineIsAll();
            boolean toLvMineIsAll = toLv.mineIsAll();
            if (fromLvMineIsAll && !toLvMineIsAll) {
                return fromLv.reverse();
            }
            if (toLvMineIsAll && !fromLvMineIsAll) {
                return toLv;
            } else if (toLvMineIsAll) {
                return LV.createHC(fromLv.links().theirsToTheirs(toLv.links()));
            }
            boolean fromLvTheirsIsAll = fromLv.theirsIsAll();
            boolean toLvTheirsIsAll = toLv.theirsIsAll();
            if (fromLvTheirsIsAll && !toLvTheirsIsAll) {
                return LV.createHC(fromLv.links().theirsToTheirs(toLv.links()));
            }
            if (toLvTheirsIsAll && fromLvTheirsIsAll) {
                return null;
            }
            return LV.createHC(fromLv.links().mineToTheirs(toLv.links()));
        }
        if (fromLv.isDependent()) {
            assert !fromLvHaveLinks && toLv.isCommonHC();
            return null;
        }
        if (toLv.isDependent()) {
            assert !toLvHaveLinks && fromLv.isCommonHC();
            return null;
        }
        throw new UnsupportedOperationException("?");
    }
}
