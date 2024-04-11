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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    public LinkHelper(EvaluationResult context, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        this.context = context;
        this.methodInfo = methodInfo;
        this.methodAnalysis = methodAnalysis;
        hiddenContentTypes = methodInfo.methodResolution.get().hiddenContentTypes();
        assert hiddenContentTypes != null : "For method " + methodInfo;
        hcsSource = hiddenContentTypes.selectAll();
    }

    private static LinkedVariables linkedVariablesOfParameter(EvaluationResult parameterResult) {
        return parameterResult.linkedVariablesOfExpression().maximum(LV.LINK_DEPENDENT);
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
            return new LambdaResult(result, vi.getLinkedVariables());
        }
        return new LambdaResult(result, LinkedVariables.EMPTY);
    }

    public record FromParameters(EvaluationResult intoObject, EvaluationResult intoResult) {
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
                        LinkedVariables returnValueLvs = linkedVariablesOfParameter(parameterResults.get(pi.index));
                        LV valueOfReturnValue = lvsToResult.stream().filter(e -> e.getKey() instanceof ReturnVariable)
                                .map(Map.Entry::getValue).findFirst().orElseThrow();
                        Map<Variable, LV> map = returnValueLvs.stream().collect(Collectors.toMap(Map.Entry::getKey,
                                e -> {
                                    if (e.getValue().isCommonHC() && valueOfReturnValue.isCommonHC()) {
                                        return e.getValue();
                                    }
                                    return e.getValue().min(valueOfReturnValue);
                                }));
                        parameterLvs = LinkedVariables.of(map);
                        formalParameterIndependent = valueOfReturnValue.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
                    } else {
                        parameterLvs = linkedVariablesOfParameter(parameterResults.get(pi.index));
                    }
                    ParameterizedType pt = inResult ? resultPt : objectPt;
                    HiddenContentSelector hcsTarget = parameterAnalysis.getHiddenContentSelector();
                    if (pt != null) {
                        LinkedVariables lv;
                        if (inResult) {
                            // parameter -> result
                            HiddenContentSelector hcsSource = methodAnalysis.getHiddenContentSelector();
                            lv = linkedVariables(parameterType, parameterLvs, hcsTarget, false,
                                    formalParameterIndependent, hcsSource, pt, false);
                        } else {
                            // object -> parameter (rather than the other way around)
                            lv = linkedVariables(pt, parameterLvs, hcsSource, false,
                                    formalParameterIndependent, hcsTarget,
                                    parameterType, true);
                        }
                        EvaluationResultImpl.Builder builder = inResult ? intoResultBuilder : intoObjectBuilder;
                        builder.mergeLinkedVariablesOfExpression(lv);
                    }
                }
            }

            linksBetweenParameters(intoObjectBuilder, methodInfo, parameterExpressions, parameterResults);
        }
        return new FromParameters(intoObjectBuilder.build(), intoResultBuilder == null ? null :
                intoResultBuilder.build());
    }

    public void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                       MethodInfo methodInfo,
                                       List<Expression> parameterExpressions,
                                       List<EvaluationResult> parameterResults) {
        Map<ParameterInfo, LinkedVariables> crossLinks = methodInfo.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        crossLinks.forEach((pi, lv) -> lv.stream().forEach(e -> {
            List<LinkedVariables> parameterLvs = parameterResults.stream()
                    .map(LinkHelper::linkedVariablesOfParameter).toList();
            ParameterInfo target = (ParameterInfo) e.getKey();
            boolean sourceIsVarArgs = pi.parameterInspection.get().isVarArgs();
            assert !sourceIsVarArgs : "Varargs must always be a target";
            boolean targetIsVarArgs = target.parameterInspection.get().isVarArgs();
            if (!targetIsVarArgs || parameterResults.size() > target.index) {
                ParameterizedType sourceType = parameterExpressions.get(pi.index).returnType();
                LinkedVariables sourceLvs = parameterLvs.get(pi.index);
                LV level = e.getValue();

                for (int i = target.index; i < parameterResults.size(); i++) {
                    ParameterizedType targetType = parameterExpressions.get(target.index).returnType();
                    tryLinkBetweenParameters(builder, target.index, targetIsVarArgs, targetType, level,
                            sourceType, sourceLvs, parameterLvs);
                }
            } // else: no value... empty varargs
        }));
    }

    /*
         mergedLvs = LinkedVariables.EMPTY;
                for (int i = targetIndex; i < parameterLvs.size(); i++) {
                    LinkedVariables lvs = parameterLvs.get(i);
                    LinkedVariables lv = linkedVariables(targetType, lvs, hcsSource, true, independentDv,
                            hcsTarget, sourceType, false);
                    mergedLvs = mergedLvs.merge(lv);
                }
     */
    //
    //example Independent1_2_1
    //target = ts, index 0, not varargs, linked 4=common_hc to generator, index 1;
    //target ts is modified; values are new String[4] and generator, linked variables are this.ts:2 and generator:2
    //
    private void tryLinkBetweenParameters(EvaluationResultImpl.Builder builder,
                                          int targetIndex,
                                          boolean targetIsVarArgs,
                                          ParameterizedType targetType,
                                          LV level,
                                          ParameterizedType sourceType,
                                          LinkedVariables sourceLinkedVariables,
                                          List<LinkedVariables> parameterLvs) {
        HiddenContentSelector hcsTarget = level.isCommonHC() ? level.mine() : HiddenContentSelector.None.INSTANCE;
        HiddenContentSelector hcsSource = level.isCommonHC() ? level.theirs() : HiddenContentSelector.None.INSTANCE;
        DV independentDv = level.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
        LinkedVariables targetLinkedVariables = parameterLvs.get(targetIndex);
        LinkedVariables mergedLvs = linkedVariables(targetType, targetLinkedVariables, hcsSource, targetIsVarArgs,
                independentDv, hcsTarget, sourceType, targetIsVarArgs);
        sourceLinkedVariables.stream().forEach(e ->
                mergedLvs.stream().forEach(e2 ->
                        builder.link(e.getKey(), e2.getKey(), e.getValue().max(e2.getValue()))
                ));
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
                    .changeNonStaticallyAssignedToDelay(identity.causesOfDelay());
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
        return linkedVariables(objectType,
                linkedVariablesOfObject,
                hcsSource, false,
                independent, methodAnalysis.getHiddenContentSelector(),
                returnType,
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
     * @param sourceType                    must be type of object or parameterExpression, return type, non-evaluated
     * @param sourceLvs                     linked variables of the source
     * @param hiddenContentSelectorOfSource with respect to the method's HCT (taken from method HCS or parameter HCS)
     * @param sourceIsVarArgs               allow for a correction of array -> element
     * @param transferIndependent           the transfer more (dependent, independent HC, independent)
     * @param hiddenContentSelectorOfTarget with respect to the method's HCT (taken from method HCS or parameter HCS)
     * @param targetType                    must be type of object or parameterExpression, return type, non-evaluated
     * @param reverse                       reverse the link, because we're reversing source and target, because we
     *                                      only deal with *->0, not 0->* in this method.
     * @return the linked values of the target
     */
    private LinkedVariables linkedVariables(ParameterizedType sourceType,
                                            LinkedVariables sourceLvs,
                                            HiddenContentSelector hiddenContentSelectorOfSource,
                                            boolean sourceIsVarArgs,
                                            DV transferIndependent,
                                            HiddenContentSelector hiddenContentSelectorOfTarget,
                                            ParameterizedType targetType,
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
        ParameterizedType formal = targetType.typeInfo.asParameterizedType(context.getAnalyserContext());
        Map<Integer, ParameterizedType> typesCorrespondingToHCOfTarget = hiddenContentTypes.mapTypesRecursively(
                context.getAnalyserContext(), targetType, formal);

        DV correctedIndependent = correctIndependent(immutableOfSource, transferIndependent, targetType,
                typesCorrespondingToHCOfTarget, hiddenContentSelectorOfTarget);

        if (MultiLevel.INDEPENDENT_DV.equals(correctedIndependent)) {
            return LinkedVariables.EMPTY;
        }
        if (correctedIndependent.isDelayed()) {
            // delay in method independent
            return sourceLvs.changeToDelay(delay(correctedIndependent.causesOfDelay()));
        }

        Map<Variable, LV> newLinked = new HashMap<>();
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;

        Map<Integer, Integer> hctMethodToHctSource = hiddenContentTypes.translateHcs(context.getAnalyserContext(),
                hiddenContentSelectorOfSource.set(), sourceType);
        HiddenContentTypes typeHct = formal.typeInfo.typeResolution.get().hiddenContentTypes();

        for (Map.Entry<Variable, LV> e : sourceLvs) {
            ParameterizedType pt = e.getKey().parameterizedType();
            // for the purpose of this algorithm, unbound type parameters are HC
            DV immutable = context.evaluationContext().immutable(pt);
            LV lv = e.getValue();
            assert lv.lt(LINK_INDEPENDENT);

            if (immutable.isDelayed() || lv.isDelayed()) {
                causesOfDelay = causesOfDelay.merge(immutable.causesOfDelay()).merge(lv.causesOfDelay());
            } else {
                if (MultiLevel.isMutable(immutable) && isDependent(transferIndependent, correctedIndependent,
                        immutableOfSource, lv)) {
                    newLinked.put(e.getKey(), LINK_DEPENDENT);
                } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                    if (!hiddenContentSelectorOfTarget.isNone()) {
                        HiddenContentSelector mine; // target
                        HiddenContentSelector theirs; // source

                        /*
                        this is the only place during computational analysis where we create common HC links.
                        all other links are created in the ShallowMethodAnalyser.
                         */
                        if (hiddenContentSelectorOfTarget.isAll()) {
                            DV typeImmutable = context.evaluationContext().immutable(targetType);
                            if (typeImmutable.isDelayed()) {
                                causesOfDelay = causesOfDelay.merge(typeImmutable.causesOfDelay());
                            }
                            boolean mutable = MultiLevel.isMutable(typeImmutable);
                            mine = mutable ? HiddenContentSelector.All.MUTABLE_INSTANCE
                                    : HiddenContentSelector.All.INSTANCE;
                            if (hiddenContentSelectorOfSource instanceof HiddenContentSelector.CsSet csSet) {
                                Map<Integer, Boolean> theirsMap = new HashMap<>();
                                for (int i : csSet.set()) {
                                    int iInHctSource = hctMethodToHctSource.get(i);
                                    theirsMap.put(iInHctSource, mutable);
                                }
                                theirs = new HiddenContentSelector.CsSet(theirsMap);
                            } else {
                                throw new UnsupportedOperationException();
                            }
                        } else {
                            // both are CsSet, we'll set mutable what is mutable, in a common way
                            if (hiddenContentSelectorOfTarget instanceof HiddenContentSelector.CsSet mineCsSet) {
                                Boolean correctForVarargsMutable = null;
                                Map<Integer, Boolean> mineMap = new HashMap<>();
                                Map<Integer, Boolean> theirsMap = new HashMap<>();

                                Map<Integer, Integer> hcsMethodToHctTarget = hiddenContentTypes
                                        .translateHcs(typesCorrespondingToHCOfTarget, typeHct, mineCsSet.set(), formal);

                                for (int i : mineCsSet.set()) {
                                    ParameterizedType type = typesCorrespondingToHCOfTarget.get(i);
                                    DV typeImmutable = context.evaluationContext().immutable(type);
                                    if (typeImmutable.isDelayed()) {
                                        causesOfDelay = causesOfDelay.merge(typeImmutable.causesOfDelay());
                                    }
                                    if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(typeImmutable)) {
                                        continue;
                                    }
                                    int iInHctTarget = hcsMethodToHctTarget.get(i);
                                    boolean mutable = isMutable(typeImmutable);
                                    mineMap.put(iInHctTarget, mutable);
                                    if (sourceIsVarArgs) {
                                        // we're in a varargs situation: the first element is the type itself
                                        correctForVarargsMutable = mutable;
                                    }
                                    int iInHctSource = hctMethodToHctSource.get(i);
                                    theirsMap.put(iInHctSource, mutable);
                                }
                                assert !mineMap.isEmpty();
                                assert !theirsMap.isEmpty();
                                if (correctForVarargsMutable != null) {
                                    // the normal link would be 0-4-0, we make it *-4-0
                                    mine = correctForVarargsMutable ? HiddenContentSelector.All.MUTABLE_INSTANCE
                                            : HiddenContentSelector.All.INSTANCE;
                                } else {
                                    mine = new HiddenContentSelector.CsSet(mineMap);
                                }
                                theirs = new HiddenContentSelector.CsSet(theirsMap);
                            } else {
                                throw new UnsupportedOperationException();
                            }
                        }
                        LV commonHC = reverse ? LV.createHC(theirs, mine) : LV.createHC(mine, theirs);
                        newLinked.put(e.getKey(), commonHC);
                    } else {
                        throw new UnsupportedOperationException("I believe we should not link");
                    }
                }
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
    Example: Map<K,V>.entrySet() has HCS <0,1>: we keep both type parameters. But Map<Long,V> must have
    only <1>, because type parameter 0 cannot be hidden content in this particular instantiation.
    Map<StringBuilder, V> is not relevant here, because then the type would be mutable, the corrected independent
    would be "dependent", and we'll not return a commonHC object.
    So we'll only remove those type parameters that have a recursively immutable instantiation in the concrete type.
     */
    private HiddenContentSelector correctSelector(HiddenContentSelector hiddenContentSelectorOfTarget,
                                                  Set<Integer> typesCorrespondingToHCKeySet) {
        if (hiddenContentSelectorOfTarget.isNone() || hiddenContentSelectorOfTarget.isAll()) {
            return hiddenContentSelectorOfTarget;
        }
        // find the types corresponding to the hidden content indices
        Set<Integer> selectorSet = hiddenContentSelectorOfTarget.set();
        Set<Integer> remaining = typesCorrespondingToHCKeySet.stream()
                .filter(selectorSet::contains)
                .collect(Collectors.toUnmodifiableSet());
        if (remaining.isEmpty()) {
            return HiddenContentSelector.None.INSTANCE;
        }
        return new HiddenContentSelector.CsSet(remaining);
    }

    private DV correctIndependent(DV immutableOfSource,
                                  DV independent,
                                  ParameterizedType targetType,
                                  Map<Integer, ParameterizedType> typesCorrespondingToHCInTarget,
                                  HiddenContentSelector hiddenContentSelectorOfTarget) {
        // immutableOfSource is not recursively immutable, independent is not fully independent
        // remaining values immutable: mutable, immutable HC
        // remaining values independent: dependent, independent hc
        if (MultiLevel.DEPENDENT_DV.equals(independent)) {
            if (MultiLevel.isAtLeastImmutableHC(immutableOfSource)) {
                return MultiLevel.INDEPENDENT_HC_DV;
            }
            if (hiddenContentSelectorOfTarget.isAll()) {
                // look at the whole object
                DV immutablePt = context.evaluationContext().immutable(targetType);
                if (immutablePt.isDelayed()) return immutablePt;
                if (MultiLevel.isAtLeastImmutableHC(immutablePt)) {
                    return MultiLevel.INDEPENDENT_HC_DV;
                }
            } else if (!hiddenContentSelectorOfTarget.isNone()) {
                Set<Integer> selectorSet = hiddenContentSelectorOfTarget.set();
                boolean allIndependentHC = true;
                for (Map.Entry<Integer, ParameterizedType> entry : typesCorrespondingToHCInTarget.entrySet()) {
                    if (selectorSet.contains(entry.getKey())) {
                        DV immutablePt = context.evaluationContext().immutable(entry.getValue());
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
                DV immutablePt = context.evaluationContext().immutable(targetType);
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
}
