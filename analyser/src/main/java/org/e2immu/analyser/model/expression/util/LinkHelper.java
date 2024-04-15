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
import org.e2immu.analyser.model.variable.This;
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

    public LinkHelper(EvaluationResult context, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        this.context = context;
        this.methodInfo = methodInfo;
        this.methodAnalysis = methodAnalysis;
        hiddenContentTypes = methodInfo.methodResolution.get().hiddenContentTypes();
        assert hiddenContentTypes != null : "For method " + methodInfo;
        hcsSource = hiddenContentTypes.selectAll();
    }

    private LinkedVariables linkedVariablesOfParameter(ParameterizedType parameterMethodType,
                                                       ParameterizedType parameterType,
                                                       EvaluationResult parameterResult) {
        boolean targetIsTypeParameter = parameterMethodType.isTypeParameter() || parameterType.isTypeParameter();
        Map<Integer, ParameterizedType> typesCorrespondingToHCOfTarget;
        InspectionProvider inspectionProvider = context.getAnalyserContext();
        Map<Integer, ParameterizedType> hcs;
        Map<Integer, Integer> hcsMethodToHctTarget;
        if (targetIsTypeParameter) {
            typesCorrespondingToHCOfTarget = null;
            hcsMethodToHctTarget = null;
            hcs = Map.of();
        } else {
            HiddenContentTypes hctTarget = parameterType.typeInfo.typeResolution.get().hiddenContentTypes();
            typesCorrespondingToHCOfTarget = hctTarget.mapTypesRecursively(inspectionProvider, parameterType,
                    false);
            hcs = hiddenContentTypes.mapTypesRecursively(context.getAnalyserContext(), parameterMethodType,
                    true);

            if (parameterType.arrays > 0) {
                // e.g. Linking_0,m18: sourceType X, methodSourceType T[], sourceIsVarArgs true
                hcsMethodToHctTarget = Map.of(0, 0);// array access
            } else if (hcs.isEmpty()) {
                hcsMethodToHctTarget = null;
            } else {
                ParameterizedType parameterTypeFormal = parameterType.typeInfo.asParameterizedType(inspectionProvider);
                hcsMethodToHctTarget = hiddenContentTypes.translateHcs(inspectionProvider,
                        hcs.keySet(), parameterMethodType, parameterTypeFormal, true);
            }
        }
        Map<Variable, LV> map = new HashMap<>();
        AtomicReference<CausesOfDelay> causes = new AtomicReference<>(CausesOfDelay.EMPTY);
        parameterResult.linkedVariablesOfExpression().stream().forEach(e -> {
            LV newLv;
            LV lv = e.getValue();
            boolean independentHc = lv.isCommonHC();
            Integer index = hiddenContentTypes.indexOfOrNull(parameterMethodType);
            if (index != null) {
                DV mutable = context.evaluationContext().immutable(parameterResult.getExpression().returnType());
                if (mutable.isDelayed()) {
                    causes.set(causes.get().merge(mutable.causesOfDelay()));
                    mutable = MUTABLE_DV;
                }
                if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(mutable)) {
                    newLv = null;
                } else {
                    boolean m = MultiLevel.isMutable(mutable);
                    HiddenContentSelector mine = HiddenContentSelector.All.INSTANCE.ensureMutable(m);
                    HiddenContentSelector theirs = HiddenContentSelector.CsSet.selectTypeParameter(index).ensureMutable(m);
                    newLv = independentHc ? LV.createHC(mine, theirs) : LV.createDependent(mine, theirs);
                }
            } else {
                if (!hcs.isEmpty()) {
                    Map<Integer, Boolean> mineMap = new HashMap<>();
                    Map<Integer, Boolean> theirsMap = new HashMap<>();
                    for (Map.Entry<Integer, ParameterizedType> entry : hcs.entrySet()) {
                        int iInHctTarget = hcsMethodToHctTarget.get(entry.getKey());
                        ParameterizedType type = typesCorrespondingToHCOfTarget.get(iInHctTarget);
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
                        mineMap.put(entry.getKey(), mutable);
                        theirsMap.put(iInHctTarget, mutable);
                    }
                    if (mineMap.isEmpty()) {
                        assert theirsMap.isEmpty();
                        newLv = LINK_DEPENDENT;
                    } else {
                        HiddenContentSelector mine = new HiddenContentSelector.CsSet(mineMap);
                        HiddenContentSelector theirs = new HiddenContentSelector.CsSet(theirsMap);
                        newLv = independentHc ? LV.createHC(mine, theirs) : LV.createDependent(mine, theirs);
                    }
                } else {
                    newLv = LINK_DEPENDENT;
                }
            }
            if (newLv != null) {
                map.put(e.getKey(), newLv);
            }
        });
        LinkedVariables lvs = LinkedVariables.of(map);
        if (causes.get().isDelayed()) {
            lvs.changeToDelay(LV.delay(causes.get()));
        }
        return lvs;
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
                        LinkedVariables returnValueLvs = linkedVariablesOfParameter(pi.parameterizedType,
                                parameterExpressions.get(pi.index).returnType(),
                                parameterResults.get(pi.index));
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
                        parameterLvs = linkedVariablesOfParameter(pi.parameterizedType,
                                parameterExpressions.get(pi.index).returnType(),
                                parameterResults.get(pi.index));
                    }
                    ParameterizedType pt = inResult ? resultPt : objectPt;
                    ParameterizedType methodPt;
                    if (inResult) {
                        methodPt = methodInspection.getReturnType();
                    } else {
                        methodPt = methodInfo.typeInfo.asParameterizedType(context.getAnalyserContext());
                    }
                    HiddenContentSelector hcsTarget = parameterAnalysis.getHiddenContentSelector();
                    if (pt != null) {
                        LinkedVariables lv;
                        if (inResult) {
                            // parameter -> result
                            HiddenContentSelector hcsSource = methodAnalysis.getHiddenContentSelector();
                            lv = linkedVariables(parameterType, pi.parameterizedType, hcsTarget, parameterLvs, false,
                                    formalParameterIndependent, pt, methodPt, hcsSource, false);
                        } else {
                            // object -> parameter (rather than the other way around)
                            lv = linkedVariables(pt, methodPt, hcsSource, parameterLvs, false,
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
                intoResultBuilder.build());
    }

    public void linksBetweenParameters(EvaluationResultImpl.Builder builder,
                                       MethodInfo methodInfo,
                                       List<Expression> parameterExpressions,
                                       List<EvaluationResult> parameterResults) {
        Map<ParameterInfo, LinkedVariables> crossLinks = methodInfo.crossLinks(context.getAnalyserContext());
        if (crossLinks.isEmpty()) return;
        crossLinks.forEach((pi, lv) -> lv.stream().forEach(e -> {
            List<LinkedVariables> parameterLvs = new ArrayList<>(parameterResults.size());
            int parameterIndex = 0;
            for (EvaluationResult parameterResult : parameterResults) {
                int index = Math.min(methodAnalysis.getParameterAnalyses().size() - 1, parameterIndex);
                ParameterInfo p = methodAnalysis.getParameterAnalyses().get(index).getParameterInfo();
                LinkedVariables lvs = linkedVariablesOfParameter(p.parameterizedType,
                        parameterExpressions.get(parameterIndex).returnType(),
                        parameterResult);
                parameterLvs.add(lvs);
                parameterIndex++;
            }
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
                    tryLinkBetweenParameters(builder, i, targetIsVarArgs, targetType,
                            target.parameterizedType, level, sourceType, pi.parameterizedType, sourceLvs, parameterLvs);
                }
            } // else: no value... empty varargs
        }));
    }

    private void tryLinkBetweenParameters(EvaluationResultImpl.Builder builder,
                                          int targetIndex,
                                          boolean targetIsVarArgs,
                                          ParameterizedType targetType,
                                          ParameterizedType methodTargetType,
                                          LV level,
                                          ParameterizedType sourceType,
                                          ParameterizedType methodSourceType,
                                          LinkedVariables sourceLinkedVariables,
                                          List<LinkedVariables> parameterLvs) {
        HiddenContentSelector hcsTarget = level.isCommonHC() ? level.mine() : HiddenContentSelector.None.INSTANCE;
        HiddenContentSelector hcsSource = level.isCommonHC() ? level.theirs() : HiddenContentSelector.None.INSTANCE;
        DV independentDv = level.isCommonHC() ? INDEPENDENT_HC_DV : DEPENDENT_DV;
        LinkedVariables targetLinkedVariables = parameterLvs.get(targetIndex);
        LinkedVariables mergedLvs = linkedVariables(targetType, methodTargetType, hcsSource, targetLinkedVariables,
                targetIsVarArgs, independentDv, sourceType, methodSourceType, hcsTarget, targetIsVarArgs);
        crossLink(sourceLinkedVariables, mergedLvs, builder);
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
        ParameterizedType methodType = methodInfo.typeInfo.asParameterizedType(context.getAnalyserContext());
        ParameterizedType methodReturnType = context.getAnalyserContext().getMethodInspection(methodInfo).getReturnType();

        return linkedVariables(objectType,
                methodType, hcsSource, linkedVariablesOfObject,
                false,
                independent, returnType, methodReturnType, methodAnalysis.getHiddenContentSelector(),
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
     * @param methodSourceType              the formal method's type of the source
     * @param hiddenContentSelectorOfSource with respect to the method's HCT and methodSourceType
     * @param sourceLvs                     linked variables of the source
     * @param sourceIsVarArgs               allow for a correction of array -> element
     * @param transferIndependent           the transfer more (dependent, independent HC, independent)
     * @param targetType                    must be type of object or parameterExpression, return type, non-evaluated
     * @param methodTargetType              the formal method's type of the target
     * @param hiddenContentSelectorOfTarget with respect to the method's HCT and methodTargetType
     * @param reverse                       reverse the link, because we're reversing source and target, because we
     *                                      only deal with *->0, not 0->* in this method.
     * @return the linked values of the target
     */
    private LinkedVariables linkedVariables(ParameterizedType sourceType,
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
        boolean targetIsTypeParameter = methodTargetType.isTypeParameter() || targetType.isTypeParameter();
        HiddenContentSelector hcsTarget;
        Map<Integer, ParameterizedType> typesCorrespondingToHCOfTarget;
        ParameterizedType targetTypeFormal;
        InspectionProvider inspectionProvider = context.getAnalyserContext();
        if (targetIsTypeParameter) {
            hcsTarget = hiddenContentSelectorOfTarget;
            assert hcsTarget.isAll() || hcsTarget.isNone();
            typesCorrespondingToHCOfTarget = null;
            targetTypeFormal = null;
        } else {
            targetTypeFormal = targetType.typeInfo.asParameterizedType(inspectionProvider);
            if (hiddenContentSelectorOfTarget instanceof HiddenContentSelector.CsSet csSet) {
                Map<Integer, Integer> map3 = hiddenContentTypes.translateHcs(inspectionProvider, csSet.set(),
                        methodTargetType, targetTypeFormal, true);
                hcsTarget = new HiddenContentSelector.CsSet(new HashSet<>(map3.values()));
                HiddenContentTypes hctTarget = targetType.typeInfo.typeResolution.get().hiddenContentTypes();
                typesCorrespondingToHCOfTarget = hctTarget.mapTypesRecursively(inspectionProvider, targetType,
                        false);
            } else {
                hcsTarget = hiddenContentSelectorOfTarget;
                typesCorrespondingToHCOfTarget = null;
            }
        }

        DV correctedIndependent = correctIndependent(context.evaluationContext(),
                immutableOfSource, transferIndependent, targetType, typesCorrespondingToHCOfTarget, hcsTarget);

        if (MultiLevel.INDEPENDENT_DV.equals(correctedIndependent)) {
            return LinkedVariables.EMPTY;
        }
        if (correctedIndependent.isDelayed()) {
            // delay in method independent
            return sourceLvs.changeToDelay(delay(correctedIndependent.causesOfDelay()));
        }

        Map<Variable, LV> newLinked = new HashMap<>();
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;

        Map<Integer, Integer> hctMethodToHctSource;
        if (sourceType.arrays > 0 || sourceIsVarArgs) {
            // e.g. Linking_0,m18: sourceType X, methodSourceType T[], sourceIsVarArgs true
            hctMethodToHctSource = Map.of(0, 0);// array access
        } else if (hiddenContentSelectorOfSource instanceof HiddenContentSelector.CsSet set && sourceType.typeInfo != null) {
            hctMethodToHctSource = hiddenContentTypes.translateHcs(inspectionProvider, set.set(),
                    methodSourceType, sourceType, true);
        } else {
            hctMethodToHctSource = null;
        }

        HiddenContentTypes hctSource = sourceType.typeInfo == null ? null
                : sourceType.typeInfo.typeResolution.get().hiddenContentTypes();

        for (Map.Entry<Variable, LV> e : sourceLvs) {
            ParameterizedType pt = e.getKey().parameterizedType();
            // for the purpose of this algorithm, unbound type parameters are HC
            DV immutable = context.evaluationContext().immutable(pt);
            LV lv = e.getValue();
            assert lv.lt(LINK_INDEPENDENT);

            if (immutable.isDelayed() || lv.isDelayed()) {
                causesOfDelay = causesOfDelay.merge(immutable.causesOfDelay()).merge(lv.causesOfDelay());
            } else if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                boolean createDependentLink = MultiLevel.isMutable(immutable) && isDependent(transferIndependent, correctedIndependent,
                        immutableOfSource, lv);

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
                                NamedType namedType = methodTargetType.isTypeParameter()
                                        ? methodTargetType.typeParameter : methodTargetType.typeInfo;
                                boolean accept = hiddenContentTypes.isAssignableTo(inspectionProvider, namedType, i);
                                if (accept) {
                                    assert hctMethodToHctSource != null;
                                    Integer iInHctSource = hctMethodToHctSource.get(i);
                                    assert iInHctSource != null;
                                    theirsMap.put(iInHctSource, mutable);
                                }
                            }
                            assert hctSource != null;
                            theirs = reverse
                                    // correction takes place later
                                    ? new HiddenContentSelector.CsSet(theirsMap)
                                    : correctWithRespectTo(inspectionProvider, e.getKey() instanceof This, pt, hctSource, theirsMap);
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    } else {
                        // both are CsSet, we'll set mutable what is mutable, in a common way
                        if (hiddenContentSelectorOfTarget instanceof HiddenContentSelector.CsSet mineCsSet) {
                            Boolean correctForVarargsMutable = null;
                            Map<Integer, Boolean> mineMap = new HashMap<>();
                            Map<Integer, Boolean> theirsMap = new HashMap<>();

                            assert hctMethodToHctSource != null;
                            assert targetTypeFormal != null;
                            Map<Integer, Integer> hcsMethodToHctTarget = hiddenContentTypes.translateHcs(inspectionProvider,
                                    mineCsSet.set(), methodTargetType, targetTypeFormal, true);

                            for (int i : mineCsSet.set()) {
                                int iInHctTarget = hcsMethodToHctTarget.get(i);
                                ParameterizedType type = typesCorrespondingToHCOfTarget.get(iInHctTarget);
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
                                mineMap.put(iInHctTarget, mutable);
                                if (sourceIsVarArgs) {
                                    // we're in a varargs situation: the first element is the type itself
                                    correctForVarargsMutable = mutable;
                                }
                                int iInHctSource = hctMethodToHctSource.get(i);
                                theirsMap.put(iInHctSource, mutable);
                            }
                            if (correctForVarargsMutable != null) {
                                // the normal link would be 0-4-0, we make it *-4-0
                                mine = correctForVarargsMutable ? HiddenContentSelector.All.MUTABLE_INSTANCE
                                        : HiddenContentSelector.All.INSTANCE;
                            } else {
                                mine = mineMap.isEmpty() ? null : new HiddenContentSelector.CsSet(mineMap);
                            }
                            if (theirsMap.isEmpty()) {
                                theirs = null;
                            } else if (sourceIsVarArgs || reverse) {
                                // no need for a correction, '0' is correct
                                theirs = new HiddenContentSelector.CsSet(theirsMap);
                            } else {
                                assert hctSource != null;
                                theirs = correctWithRespectTo(inspectionProvider, e.getKey() instanceof This,
                                        pt, hctSource, theirsMap);
                            }
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }
                    if (createDependentLink) {
                        LV dependent = reverse ? LV.createDependent(theirs, mine) : LV.createDependent(mine, theirs);
                        newLinked.put(e.getKey(), dependent);
                    } else if (mine != null && theirs != null) {
                        LV commonHC = reverse ? LV.createHC(theirs, mine) : LV.createHC(mine, theirs);
                        newLinked.put(e.getKey(), commonHC);
                    }
                } else {
                    throw new UnsupportedOperationException("I believe we should not link");
                }
            }
        }
        if (causesOfDelay.isDelayed()) {
            return sourceLvs.changeToDelay(LV.delay(causesOfDelay));
        }
        return LinkedVariables.of(newLinked);
    }

    private HiddenContentSelector.CsSet correctWithRespectTo(InspectionProvider inspectionProvider,
                                                             boolean variableIsThis,
                                                             ParameterizedType pt,
                                                             HiddenContentTypes hctSource,
                                                             Map<Integer, Boolean> theirsMap) {
        if (variableIsThis) {
            boolean mutable = theirsMap.values().stream().anyMatch(v -> v);
            return new HiddenContentSelector.CsSet(Map.of(0, mutable));
        }
        Map<Integer, Boolean> correctedMap = new HashMap<>();
        ParameterizedType ptFormal = pt.typeInfo.asParameterizedType(inspectionProvider);
        ParameterizedType sourceType = hctSource.getTypeInfo().asParameterizedType(inspectionProvider);
        Map<Integer, Integer> translate = hctSource.translateHcs(inspectionProvider, theirsMap.keySet(), sourceType,
                ptFormal, false);
        for (Map.Entry<Integer, Boolean> e : theirsMap.entrySet()) {
            Integer translated = translate.get(e.getKey());
            assert translated != null;
            correctedMap.put(translated, e.getValue());
        }
        return new HiddenContentSelector.CsSet(correctedMap);
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
                DV immutablePt = evaluationContext.immutable(targetType);
                if (immutablePt.isDelayed()) return immutablePt;
                if (MultiLevel.isAtLeastImmutableHC(immutablePt)) {
                    return MultiLevel.INDEPENDENT_HC_DV;
                }
            } else if (!hiddenContentSelectorOfTarget.isNone()) {
                Set<Integer> selectorSet = hiddenContentSelectorOfTarget.set();
                boolean allIndependentHC = true;
                for (Map.Entry<Integer, ParameterizedType> entry : typesCorrespondingToHCInTarget.entrySet()) {
                    if (selectorSet.contains(entry.getKey())) {
                        DV immutablePt = evaluationContext.immutable(entry.getValue());
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
                    LV lv = follow(fromLv, toLv, to, from);
                    if (lv != null) {
                        link.link(from, to, lv);
                    }
                })
        );
    }

    public static LV follow(LV fromLv, LV toLv, Variable to, Variable from) {
        if (fromLv.isDelayed() || toLv.isDelayed()) {
            return LV.delay(fromLv.causesOfDelay().merge(toLv.causesOfDelay()));
        }
        if (fromLv.mine() == null && toLv.mine() == null) {
            return fromLv.max(toLv); // -0- and -1-, -1- and -2-
        }
        if (fromLv.isStaticallyAssignedOrAssigned()) {
            return toLv; // -0- 0-4-1
        }
        if (toLv.isStaticallyAssignedOrAssigned()) {
            return fromLv; // 1-2-1 -1-
        }
        if (fromLv.mine() != null && toLv.mine() != null) {
            if (fromLv.mine().isAll() && !toLv.mine().isAll()) {
                return fromLv.reverse();
            }
            if (toLv.mine().isAll() && !fromLv.mine().isAll()) {
                return toLv;
            } else if (toLv.mine().isAll() && fromLv.mine().isAll()) {
                return LV.createHC(fromLv.theirs(), toLv.theirs());
            }
            if (fromLv.theirs().isAll() && !toLv.theirs().isAll()) {
                return LV.createHC(fromLv.theirs(), toLv.theirs());
            }
            if (toLv.theirs().isAll() && fromLv.theirs().isAll()) {
                return null;
            }
            return LV.createHC(fromLv.mine(), toLv.theirs());
        }
        if (fromLv.isDependent()) {
            assert fromLv.mine() == null && toLv.isCommonHC();
            return null;
        }
        if (toLv.isDependent()) {
            assert toLv.mine() == null && fromLv.isCommonHC();
            return null;
        }
        throw new UnsupportedOperationException("?");
    }
}
