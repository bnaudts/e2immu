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

package org.e2immu.analyser.analyser.impl.util;

public enum BreakDelayLevel {
    NONE('-', 0),
    STATEMENT('S', 1),
    FIELD('F', 2),
    METHOD('M', 3),
    TYPE('T', 4),
    METHOD_OVERRIDE('O', 5);

    private final int level;
    public final char symbol;

    BreakDelayLevel(char symbol, int level) {
        this.symbol = symbol;
        this.level = level;
    }

    public BreakDelayLevel next() {
        return from(Math.min(METHOD_OVERRIDE.level, level + 1));
    }

    public boolean stop() {
        return this == METHOD_OVERRIDE;
    }

    private static BreakDelayLevel from(int level) {
        return switch (level) {
            case 0 -> NONE;
            case 1 -> STATEMENT;
            case 2 -> FIELD;
            case 3 -> METHOD;
            case 4 -> TYPE;
            case 5 -> METHOD_OVERRIDE;
            default -> throw new UnsupportedOperationException();
        };
    }

    public boolean isActive() {
        return this != NONE;
    }

    public BreakDelayLevel max(BreakDelayLevel other) {
        return level < other.level ? other : this;
    }

    public boolean acceptType() {
        return level >= TYPE.level;
    }

    public boolean acceptMethod() {
        return level >= METHOD.level;
    }

    public boolean acceptField() {
        return level >= FIELD.level;
    }

    public boolean acceptStatement() {
        return level >= STATEMENT.level;
    }

    public boolean acceptMethodOverride() {
        return level >= METHOD_OVERRIDE.level;
    }
}
