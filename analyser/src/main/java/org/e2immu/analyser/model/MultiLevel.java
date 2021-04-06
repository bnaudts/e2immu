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

import java.util.function.IntFunction;

public class MultiLevel {

    // system of 6 values encoded in 3 bits per level

    public static final int SHIFT = 3;
    public static final int FACTOR = 1 << SHIFT;
    public static final int AND = FACTOR - 1;

    // different values at level

    public static final int DELAY = 0;
    public static final int FALSE = 1;
    public static final int EVENTUAL = 2;
    public static final int EVENTUAL_BEFORE = 3;
    public static final int EVENTUAL_AFTER = 4;
    public static final int EFFECTIVE = 5;

    // different levels

    public static final int E1IMMUTABLE = 0;
    public static final int E2IMMUTABLE = 1;
    public static final int NOT_NULL = 0;
    public static final int NOT_NULL_1 = 1;
    public static final int NOT_NULL_2 = 2;
    public static final int NOT_NULL_3 = 3;

    // DEPENDENT (only at the first level, nothing to do with eventual)

    public static final int DEPENDENT = FALSE; // no need for more
    public static final int DEPENDENT_1 = EVENTUAL_BEFORE;
    public static final int DEPENDENT_2 = EVENTUAL_AFTER;
    public static final int INDEPENDENT = EFFECTIVE;

    // IMMUTABLE

    public static final int EVENTUALLY_E2IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE, EVENTUAL_BEFORE);
    public static final int EVENTUALLY_E1IMMUTABLE_BEFORE_MARK = compose(EVENTUAL_BEFORE);

    public static final int EVENTUALLY_CONTENT2_NOT_NULL = compose(EVENTUAL, EVENTUAL, EVENTUAL);
    public static final int EVENTUALLY_CONTENT_NOT_NULL = compose(EVENTUAL, EVENTUAL);

    public static final int EVENTUALLY_E2IMMUTABLE = compose(EVENTUAL, EVENTUAL);
    public static final int EVENTUALLY_E1IMMUTABLE = compose(EVENTUAL, FALSE);

    public static final int EVENTUALLY_E2IMMUTABLE_AFTER_MARK = compose(EVENTUAL_AFTER, EVENTUAL_AFTER);
    public static final int EVENTUALLY_E1IMMUTABLE_AFTER_MARK = compose(EVENTUAL_AFTER);

    public static final int EFFECTIVELY_CONTENT2_NOT_NULL = compose(EFFECTIVE, EFFECTIVE, EFFECTIVE);
    public static final int EFFECTIVELY_CONTENT_NOT_NULL = compose(EFFECTIVE, EFFECTIVE);
    public static final int EFFECTIVELY_NOT_NULL = compose(EFFECTIVE);

    public static final int EFFECTIVELY_E2IMMUTABLE = compose(EFFECTIVE, EFFECTIVE);
    public static final int EFFECTIVELY_E1IMMUTABLE = compose(EFFECTIVE);
    public static final int EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE = compose(EFFECTIVE, FALSE);

    public static final int EFFECTIVELY_E1_EVENTUALLY_E2IMMUTABLE_BEFORE_MARK = compose(EFFECTIVE, EVENTUAL_BEFORE);

    public static final int MUTABLE = FALSE;
    public static final int NULLABLE = FALSE;
    public static final int NOT_INVOLVED = DELAY;

    /**
     * make a value at a given level
     *
     * @param valuesPerLevel a value for each level
     * @return the composite value
     */
    public static int compose(int... valuesPerLevel) {
        int result = 0;
        int factor = 1;
        for (int value : valuesPerLevel) {
            result += value * factor;
            factor = factor * FACTOR;
        }
        return result;
    }

    /**
     * return the value (DELAY, FALSE, EVENTUAL... EFFECTIVE) at a given level
     *
     * @param i     current value
     * @param level the level
     * @return the value restricted to that level
     */
    public static int value(int i, int level) {
        assert level >= 0;
        // it is possible that Level.DELAY (-1) is sent in here
        if (i <= MultiLevel.DELAY) return MultiLevel.DELAY;

        // IMPORTANT:
        //  if the lower levels are DELAY, return DELAY
        //  if the lower levels are FALSE, return FALSE
        for (int shift = 0; shift < level; shift++) {
            int v = (i >> (SHIFT * shift)) & AND;
            if (v <= MultiLevel.FALSE) return MultiLevel.FALSE;
        }
        return (i >> (SHIFT * level)) & AND;
    }

    public static int level(int i) {
        assert i >= 0;
        int level = 0;
        int reduce = i;
        while (reduce > 0) {
            level++;
            reduce = reduce >> SHIFT;
        }
        return level - 1;
    }


    public static int levelBetterThanFalse(int i) {
        assert i >= 0;
        int level = 0;
        int reduce = i;
        int prevReduce = -1;
        while (reduce > 0) {
            level++;
            prevReduce = reduce;
            reduce = reduce >> SHIFT;
        }
        if (prevReduce == FALSE) return level - 2;
        return level - 1;
    }

    public static boolean isEventuallyE1Immutable(int immutable) {
        return value(immutable, 0) == EVENTUAL;
    }

    public static boolean isEventuallyE2Immutable(int immutable) {
        return value(immutable, 1) == EVENTUAL;
    }

    public static boolean isAtLeastEventuallyE2Immutable(int immutable) {
        return value(immutable, 1) >= EVENTUAL;
    }

    // gets rid of eventual, by adding or subtracting one in each block
    public static int eventual(int i, boolean conditionsMetForEventual) {
        return modifyEachComponent(i, c -> eventualComponent(c, conditionsMetForEventual));
    }

    private static int modifyEachComponent(int i, IntFunction<Integer> intFunction) {
        int newValue = 0;
        int j = i;
        int factor = 1;
        while (j > 0) {
            int component = j & AND;
            int newComponent = intFunction.apply(component);
            j = j >> SHIFT;
            newValue += factor * newComponent;
            factor = factor * FACTOR;
        }
        return newValue;
    }

    private static int delayToFalseKeepRest(int component) {
        return component == DELAY ? FALSE : component;
    }

    private static int eventualComponent(int component, boolean conditionsMetForEventual) {
        if (component == FALSE || component == EFFECTIVE) return component;
        if (conditionsMetForEventual) return EVENTUAL_AFTER;
        if (component == EVENTUAL_AFTER) throw new UnsupportedOperationException();
        return EVENTUAL_BEFORE;
    }

    public static int delayToFalse(int i) {
        if (i < MultiLevel.FALSE) return MultiLevel.FALSE;
        return modifyEachComponent(i, MultiLevel::delayToFalseKeepRest);
    }

    public static int shift(int lowestLevel, int initial) {
        if (initial == -1) return lowestLevel;
        return lowestLevel + (initial << SHIFT);
    }

    public static boolean isEffectivelyNotNull(int notNull) {
        return value(notNull, 0) >= EVENTUAL_AFTER;
    }

    public static int bestNotNull(int nn1, int nn2) {
        return Math.max(nn1, nn2);
    }

    public static int bestImmutable(int imm1, int imm2) {
        return isBetterImmutable(imm1, imm2) ? imm1 : imm2;
    }

    public static boolean isBetterImmutable(int immutableDynamic, int immutableType) {
        return immutableDynamic > immutableType;
    }

    public static int before(int formalLevel) {
        if (formalLevel == E1IMMUTABLE) return EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
        if (formalLevel == E2IMMUTABLE) return EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
        throw new UnsupportedOperationException();
    }

    public static int after(int formalLevel) {
        if (formalLevel == E1IMMUTABLE) return EVENTUALLY_E1IMMUTABLE_AFTER_MARK;
        if (formalLevel == E2IMMUTABLE) return EVENTUALLY_E2IMMUTABLE_AFTER_MARK;
        throw new UnsupportedOperationException();
    }

    public static boolean isAfter(int immutable) {
        if (immutable == Level.DELAY) return false;
        int level = levelBetterThanFalse(immutable);
        if (level < 0) throw new UnsupportedOperationException("Not eventual");
        int value = value(immutable, level);
        return value == EVENTUAL_AFTER || value == EFFECTIVE;
    }

    public static boolean isBefore(int immutable) {
        if (immutable == Level.DELAY) return false;
        int level = levelBetterThanFalse(immutable);
        if (level < 0) {
            throw new UnsupportedOperationException("Not eventual");
        }
        int value = value(immutable, level);
        return value == EVENTUAL_BEFORE || value == EVENTUAL;
    }
}
