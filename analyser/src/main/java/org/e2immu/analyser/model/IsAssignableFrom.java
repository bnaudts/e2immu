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

package org.e2immu.analyser.model;

import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * The default assignment mode is COVARIANT, in that you can always assign a subtype to a super-type, as in Number <- Integer,
 * or Object <- String.
 *
 * @param inspectionProvider to obtain type inspections
 * @param target             the type as in the assignment "target = from"
 * @param from               the type as in the assignment "target = from"
 */
public record IsAssignableFrom(InspectionProvider inspectionProvider,
                               ParameterizedType target,
                               ParameterizedType from) {

    public IsAssignableFrom {
        Objects.requireNonNull(inspectionProvider);
        Objects.requireNonNull(target);
        Objects.requireNonNull(from);
    }

    public static final int NOT_ASSIGNABLE = -1;

    public boolean execute() {
        return execute(false, Mode.COVARIANT) != NOT_ASSIGNABLE;
    }

    private static final IntBinaryOperator REDUCER = (a, b) -> a == NOT_ASSIGNABLE || b == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : a + b;

    public static final int EQUALS = 0;
    private static final int ASSIGN_TO_NULL = 0;
    public static final int SAME_UNDERLYING_TYPE = 1;
    private static final int BOXING_TO_PRIMITIVE = 1;
    private static final int BOXING_FROM_PRIMITIVE = 1;
    public static final int TYPE_BOUND = 5;
    public static final int IN_HIERARCHY = 10;
    private static final int UNBOUND_WILDCARD = 100;
    private static final int ARRAY_PENALTY = 100;

    public enum Mode {
        INVARIANT, // everything has to be identical, there is no leeway with respect to hierarchy
        COVARIANT, // allow assignment of sub-types: Number <-- Integer; List<Integer> <-- IntegerList
        CONTRAVARIANT, // allow for super-types:  Integer <-- Number; IntegerList <-- List<Integer>
        ANY, // accept everything
        COVARIANT_ERASURE, // covariant, but ignore all type parameters
    }

    /**
     * @param ignoreArrays do the comparison, ignoring array information
     * @param mode         the comparison mode
     * @return a numeric "nearness", the lower, the better and the more specific
     */

    public int execute(boolean ignoreArrays, Mode mode) {
        if (target == from || target.equals(from) || ignoreArrays && target.equalsIgnoreArrays(from)) return EQUALS;

        // NULL
        if (from == ParameterizedType.NULL_CONSTANT) {
            if (target.isPrimitiveExcludingVoid()) return NOT_ASSIGNABLE;
            return ASSIGN_TO_NULL;
        }

        // Assignment to Object: everything can be assigned to object!
        if (ignoreArrays) {
            if (target.typeInfo != null && target.typeInfo.isJavaLangObject()) {
                return IN_HIERARCHY * pathToJLO(from);
            }
        } else if (target.isJavaLangObject()) {
            return IN_HIERARCHY * pathToJLO(from.copyWithoutArrays()) + (from.arrays * ARRAY_PENALTY);
        }


        // TWO TYPES, POTENTIALLY WITH PARAMETERS, but not TYPE PARAMETERS
        // List<T> vs LinkedList; int vs double, but not T vs LinkedList
        if (target.typeInfo != null && from.typeInfo != null) {

            // arrays?
            if (!ignoreArrays) {
                if (target.arrays != from.arrays) {
                    if (target.arrays < from.arrays && target.typeInfo.isJavaLangObject()) {
                        return pathToJLO(from.copyWithoutArrays()) + IN_HIERARCHY * (from.arrays - target.arrays);
                    }
                    // all arrays are serializable if there base object is serializable
                    if (target.arrays == 0 && target.typeInfo.isJavaIoSerializable() && from.arrays > 0) {
                        // See MethodCall_50: always serializable, even if the base type does not extend Serializable
                        return IN_HIERARCHY * (from.arrays - target.arrays);
                    }
                    return NOT_ASSIGNABLE;
                }
                if (target.arrays > 0) {
                    // recurse without the arrays; target and from remain the same
                    return execute(true, Mode.COVARIANT);
                }
            }

            // PRIMITIVES
            if (from.typeInfo.isPrimitiveExcludingVoid()) {
                if (target.typeInfo.isPrimitiveExcludingVoid()) {
                    // use a dedicated method in Primitives
                    return inspectionProvider.getPrimitives().isAssignableFromTo(from, target,
                            mode == Mode.COVARIANT || mode == Mode.COVARIANT_ERASURE);
                }
                return checkBoxing(target, from);
            }
            if (target.typeInfo.isPrimitiveExcludingVoid()) {
                // the other one is not a primitive
                return checkUnboxing(target, from);
            }

            // two different types, so they must be in a hierarchy
            if (target.typeInfo != from.typeInfo) {
                return differentNonNullTypeInfo(mode);
            }
            // identical base type, so look at type parameters
            return sameNoNullTypeInfo(mode);
        }

        if (target.typeInfo != null && from.typeParameter != null) {
            List<ParameterizedType> otherTypeBounds = from.typeParameter.getTypeBounds();
            if (otherTypeBounds.isEmpty()) {
                int pathToJLO = pathToJLO(target);
                if (mode == Mode.COVARIANT_ERASURE) {
                    return UNBOUND_WILDCARD + pathToJLO; // see e.g. Lambda_7, MethodCall_30,_31,_59
                }
                if (!target.typeInfo.isJavaLangObject()) {
                    return NOT_ASSIGNABLE;
                }
                return IN_HIERARCHY + pathToJLO;
            }
            return otherTypeBounds.stream().mapToInt(bound -> new IsAssignableFrom(inspectionProvider, target, bound)
                            .execute(true, mode))
                    .min().orElseThrow();
        }

        // I am a type parameter
        if (target.typeParameter != null) {
            return targetIsATypeParameter(mode);
        }
        // if wildcard is unbound, I am <?>; anything goes
        return target.wildCard == ParameterizedType.WildCard.UNBOUND ? UNBOUND_WILDCARD : NOT_ASSIGNABLE;
    }

    private int targetIsATypeParameter(Mode mode) {
        assert target.typeParameter != null;

        if (target.typeParameter.equals(from.typeParameter) && target.arrays != from.arrays) {
            // T <- T[], T[] <- T, ...
            return NOT_ASSIGNABLE;
        }
        if (target.arrays > 0 && from.arrays < target.arrays) {
            return ARRAY_PENALTY * (target.arrays - from.arrays);
        }

        List<ParameterizedType> targetTypeBounds = target.typeParameter.getTypeBounds();
        if (targetTypeBounds.isEmpty()) {
            int arrayDiff = from.arrays - target.arrays;
            assert arrayDiff >= 0;
            return TYPE_BOUND + IN_HIERARCHY * arrayDiff;
        }
        if (target.arrays > 0 && from.arrays != target.arrays) {
            return NOT_ASSIGNABLE;
        }
        // other is a type
        if (from.typeInfo != null) {
            int best = NOT_ASSIGNABLE;
            for (ParameterizedType typeBound : targetTypeBounds) {
                int score = new IsAssignableFrom(inspectionProvider, typeBound, from).execute(true, mode);
                if (score >= 0 && (best == NOT_ASSIGNABLE || best > score)) {
                    best = score;
                }
            }
            return best == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : best + TYPE_BOUND;
        }
        // other is a type parameter
        if (from.typeParameter != null) {
            List<ParameterizedType> fromTypeBounds = from.typeParameter.getTypeBounds();
            if (fromTypeBounds.isEmpty()) {
                return TYPE_BOUND;
            }
            // we both have type bounds; we go for the best combination
            int best = NOT_ASSIGNABLE;
            for (ParameterizedType myBound : targetTypeBounds) {
                for (ParameterizedType otherBound : fromTypeBounds) {
                    int score = new IsAssignableFrom(inspectionProvider, myBound, otherBound)
                            .execute(true, mode);
                    if (score >= 0 && (best == NOT_ASSIGNABLE || best > score)) {
                        best = score;
                    }
                }
            }
            return best == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : best + TYPE_BOUND;
        }
        return NOT_ASSIGNABLE;
    }

    private int sameNoNullTypeInfo(Mode mode) {
        if (mode == Mode.COVARIANT_ERASURE) return SAME_UNDERLYING_TYPE;

        // List<E> <-- List<String>
        if (target.parameters.isEmpty()) {
            // ? extends Type <-- Type ; Type <- ? super Type; ...
            if (compatibleWildcards(mode, target.wildCard, from.wildCard)) {
                return SAME_UNDERLYING_TYPE;
            }
            return NOT_ASSIGNABLE;
        }
        return ListUtil.joinLists(target.parameters, from.parameters)
                .mapToInt(p -> {
                    Mode newMode = mode == Mode.INVARIANT ? Mode.INVARIANT :
                            switch (p.k.wildCard) {
                                case EXTENDS -> mode == Mode.COVARIANT ? Mode.COVARIANT : Mode.CONTRAVARIANT;
                                case SUPER -> mode == Mode.COVARIANT ? Mode.CONTRAVARIANT : Mode.COVARIANT;
                                case NONE -> Mode.INVARIANT;
                                case UNBOUND -> Mode.ANY;
                            };
                    return new IsAssignableFrom(inspectionProvider, p.k, p.v).execute(true, newMode);
                }).reduce(0, REDUCER);
    }

    private int differentNonNullTypeInfo(Mode mode) {
        if (from.isFunctionalInterface(inspectionProvider) && target.isFunctionalInterface(inspectionProvider)) {
            // two functional interfaces, yet different TypeInfo objects
            return functionalInterface(mode);
        }
        return switch (mode) {
            case COVARIANT, COVARIANT_ERASURE -> hierarchy(target, from, mode);
            case CONTRAVARIANT -> hierarchy(from, target, Mode.COVARIANT);
            case INVARIANT -> NOT_ASSIGNABLE;
            case ANY -> throw new UnsupportedOperationException("?");
        };
    }

    /*
    either COVARIANT_ERASURE, which means we simply have to test the number of parameters and isVoid,
    or INVARIANT... all type parameters identical
     */
    private int functionalInterface(Mode mode) {
        TypeInspection targetInspection = inspectionProvider.getTypeInspection(target.typeInfo);
        MethodInspection mTarget = targetInspection.getSingleAbstractMethod();
        TypeInspection fromInspection = inspectionProvider.getTypeInspection(from.typeInfo);
        MethodInspection mFrom = fromInspection.getSingleAbstractMethod();

        /*
         See call to 'method' in MethodCall_32 for this "if" statement. Both types I and J are functional interfaces,
         with the same return type and parameters. But they're not seen as assignable.
         */
        if (!mTarget.getMethodInfo().name.equals(mFrom.getMethodInfo().name)
                && isNotSyntheticOrFunctionInterface(mTarget.getMethodInfo())
                && isNotSyntheticOrFunctionInterface(mFrom.getMethodInfo())) {
            return NOT_ASSIGNABLE;
        }
        if (mTarget.getParameters().size() != mFrom.getParameters().size()) return NOT_ASSIGNABLE;
        boolean targetIsVoid = mTarget.getReturnType().isVoid();
        boolean fromIsVoid = mFrom.getReturnType().isVoid();
        // target void -> fromIsVoid is unimportant, we can assign a function to a consumer
        if (!targetIsVoid && fromIsVoid) return NOT_ASSIGNABLE;

        if (mode == Mode.COVARIANT_ERASURE) return SAME_UNDERLYING_TYPE;
        // now, ensure that all type parameters have equal values
        int i = 0;
        for (ParameterInfo t : mTarget.getParameters()) {
            ParameterInfo f = mFrom.getParameters().get(i);
            if (!t.parameterizedType.equals(f.parameterizedType)) return NOT_ASSIGNABLE;
            i++;
        }
        if (!mTarget.getReturnType().equals(mFrom.getReturnType())) return NOT_ASSIGNABLE;
        return EQUALS;
    }

    private boolean isNotSyntheticOrFunctionInterface(MethodInfo methodInfo) {
        String packageName = methodInfo.typeInfo.packageName();
        return !"java.util.function".equals(packageName) && !Primitives.INTERNAL.equals(packageName);
    }

    private int hierarchy(ParameterizedType target, ParameterizedType from, Mode mode) {
        TypeInspection otherTypeInspection = inspectionProvider.getTypeInspection(from.typeInfo);
        for (ParameterizedType interfaceImplemented : otherTypeInspection.interfacesImplemented()) {
            ParameterizedType concreteType = from.concreteDirectSuperType(inspectionProvider, interfaceImplemented);
            int scoreInterface = new IsAssignableFrom(inspectionProvider, target, concreteType)
                    .execute(true, mode);
            if (scoreInterface != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreInterface;
        }
        ParameterizedType parentClass = otherTypeInspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            ParameterizedType concreteType = from.concreteDirectSuperType(inspectionProvider, parentClass);
            int scoreParent = new IsAssignableFrom(inspectionProvider, target, concreteType)
                    .execute(true, mode);
            if (scoreParent != NOT_ASSIGNABLE) return IN_HIERARCHY + scoreParent;
        }
        return NOT_ASSIGNABLE;
    }

    private int pathToJLO(ParameterizedType type) {
        if (type.typeInfo == null) {
            if (type.typeParameter != null && !type.typeParameter.getTypeBounds().isEmpty()) {
                return type.typeParameter.getTypeBounds().stream().mapToInt(this::pathToJLO).min()
                        .orElseThrow();
            }
            return 0;
        }
        if (type.isPrimitiveExcludingVoid()) {
            return inspectionProvider.getPrimitives().reversePrimitiveTypeOrder(target);
        }
        int steps;
        if (type.typeInfo.isJavaLangObject()) {
            steps = 0;
        } else {
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(type.typeInfo);
            if (typeInspection.isInterface()) {
                steps = 1 + typeInspection.interfacesImplemented().stream().mapToInt(this::pathToJLO).min().orElse(0);
            } else if (typeInspection.parentClass() != null) {
                steps = 1 + pathToJLO(typeInspection.parentClass());
            } else {
                steps = 1;
            }
        }
        return steps + type.arrays;
    }

    private boolean compatibleWildcards(Mode mode, ParameterizedType.WildCard w1, ParameterizedType.WildCard w2) {
        if (w1 == w2) return true;
        return mode != Mode.INVARIANT || w1 != ParameterizedType.WildCard.NONE;
    }

    // int <- Integer, long <- Integer, double <- Long
    private int checkUnboxing(ParameterizedType primitiveTarget, ParameterizedType from) {
        if (from.isBoxedExcludingVoid()) {
            TypeInfo primitiveFrom = inspectionProvider.getPrimitives().unboxed(from.typeInfo);
            if (primitiveFrom == primitiveTarget.typeInfo) {
                return BOXING_FROM_PRIMITIVE;
            }
            ParameterizedType primitiveFromPt = primitiveFrom.asSimpleParameterizedType();
            int h = inspectionProvider.getPrimitives().isAssignableFromTo(primitiveFromPt, primitiveTarget, true);
            if (h != NOT_ASSIGNABLE) {
                return BOXING_FROM_PRIMITIVE + h;
            }
        }
        return NOT_ASSIGNABLE;
    }

    private int checkBoxing(ParameterizedType target, ParameterizedType primitiveType) {
        TypeInfo boxed = primitiveType.toBoxed(inspectionProvider.getPrimitives());
        if (boxed == target.typeInfo) {
            return BOXING_TO_PRIMITIVE;
        }
        // check the hierarchy of boxed: e.g. Number
        ParameterizedType boxedPt = boxed.asSimpleParameterizedType();
        int h = hierarchy(target, boxedPt, Mode.COVARIANT);
        return h == NOT_ASSIGNABLE ? NOT_ASSIGNABLE : h + BOXING_FROM_PRIMITIVE;
    }
}
