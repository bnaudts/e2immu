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

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.*;

import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;


public interface WeightedGraph {

    @NotNull
    ClusterResult staticClusters();

    record Cluster(Set<Variable> variables) {
        @Override
        public String toString() {
            return "[" + variables.stream().map(Variable::simpleName).sorted()
                    .collect(Collectors.joining(", ")) + ']';
        }
    }

    record ClusterResult(Cluster returnValueCluster, Variable rv, List<Cluster> clusters) {
        public Set<Variable> variablesInClusters() {
            return clusters.stream().flatMap(c -> c.variables.stream())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @NotModified
    int size();

    @NotModified
    boolean isEmpty();

    @Independent(hc = true)
    @NotModified
    ShortestPath shortestPath();

    @NotModified
    void visit(@NotNull @Independent(hc = true) BiConsumer<Variable, Map<Variable, LV>> consumer);

    @Modified
    void addNode(@NotNull @Independent(hc = true) Variable v,
                 @NotNull @Independent(hc = true) Map<Variable, LV> dependsOn);

}
