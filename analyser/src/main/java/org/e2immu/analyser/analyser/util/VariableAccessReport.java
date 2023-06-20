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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public record VariableAccessReport(Map<Variable, Properties> propertiesMap) {

    public static final VariableAccessReport EMPTY = new VariableAccessReport(Map.of());

    /*
    use overwrite true when merging between successive iterations of the same method/subtype.
     */
    public VariableAccessReport combine(VariableAccessReport other, boolean overwrite) {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;
        Map<Variable, Properties> map = new HashMap<>(propertiesMap);
        BinaryOperator<Properties> operator = overwrite ? Properties::combine : Properties::mergeVariableAccessReport;
        for (Map.Entry<Variable, Properties> e : other.propertiesMap.entrySet()) {
            map.merge(e.getKey(), e.getValue(), operator);
        }
        return new VariableAccessReport(map);
    }

    @Override
    public String toString() {
        return propertiesMap.entrySet().stream()
                .map(e -> e.getKey().simpleName() + "={" + e.getValue().sortedToString() + "}")
                .sorted().collect(Collectors.joining(", "));
    }

    private boolean isEmpty() {
        return propertiesMap.isEmpty();
    }

    public static class Builder {
        private final Map<Variable, Properties> propertiesMap = new HashMap<>();

        public void addVariableRead(Variable v) {
            Properties properties = propertiesMap.computeIfAbsent(v, x -> Properties.writable());
            properties.put(Property.READ, DV.TRUE_DV);
        }

        public void addContextProperty(Variable v, Property property, DV value) {
            Properties properties = propertiesMap.computeIfAbsent(v, x -> Properties.writable());
            properties.put(property, value);
        }

        public VariableAccessReport build() {
            return new VariableAccessReport(Map.copyOf(propertiesMap));
        }
    }
}
