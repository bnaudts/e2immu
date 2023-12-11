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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.graph.op.DijkstraShortestPath;
import org.e2immu.support.Freezable;
import org.jgrapht.alg.util.UnionFind;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;


public class WeightedGraphImpl extends Freezable implements WeightedGraph {

    @SuppressWarnings("unchecked")
    public WeightedGraphImpl(Supplier<Map<?, ?>> mapSupplier) {
        this.mapSupplier = mapSupplier;
        nodeMap = (Map<Variable, Node>) mapSupplier.get();
    }

    /**
     * In-house implementation of a directed graph that is used to model the links between objects.
     * A distance of 0 (STATICALLY_ASSIGNED) is always kept, even across delays.
     * <p>
     * Hidden content: Variable, DV are interfaces with different implementations.
     */
    private static class Node {
        Map<Variable, DV> dependsOn;
        final Variable variable;

        private Node(Variable v) {
            variable = v;
        }
    }

    private final Supplier<Map<?, ?>> mapSupplier;
    private final Map<Variable, Node> nodeMap;

    @SuppressWarnings("unchecked")
    @Override
    public ClusterResult staticClusters() {
        super.freeze();
        UnionFind<Variable> unionFind = new UnionFind<>(nodeMap.keySet());
        Variable rv = null;
        Set<Variable> dependsOnRv = null;
        for (Map.Entry<Variable, Node> entry : nodeMap.entrySet()) {
            Variable variable = entry.getKey();
            boolean isRv = variable instanceof ReturnVariable;
            if (isRv) {
                rv = variable;
                dependsOnRv = new HashSet<>();
            }
            Map<Variable, DV> dependsOn = entry.getValue().dependsOn;
            if (dependsOn != null) {
                for (Map.Entry<Variable, DV> e2 : dependsOn.entrySet()) {
                    if (LinkedVariables.LINK_STATICALLY_ASSIGNED.equals(e2.getValue())) {
                        if (isRv) {
                            dependsOnRv.add(e2.getKey());
                        } else {
                            unionFind.union(variable, e2.getKey());
                        }
                    }
                }
            }
        }
        Map<Variable, Cluster> representativeToCluster = (Map<Variable, Cluster>) mapSupplier.get();
        for (Variable variable : nodeMap.keySet()) {
            if (!(variable instanceof ReturnVariable)) {
                Variable representative = unionFind.find(variable);
                Cluster cluster = representativeToCluster.computeIfAbsent(representative, v -> new Cluster(new HashSet<>()));
                cluster.variables().add(variable);
            }
        }
        List<Cluster> clusters = representativeToCluster.values().stream().toList();
        Cluster rvCluster;
        if (rv != null) {
            rvCluster = new Cluster(new HashSet<>());
            rvCluster.variables().add(rv);
            for (Variable v : dependsOnRv) {
                Variable r = unionFind.find(v);
                Cluster c = representativeToCluster.get(r);
                rvCluster.variables().addAll(c.variables());
            }
        } else {
            rvCluster = null;
        }
        return new ClusterResult(rvCluster, rv, clusters);
    }

    public int size() {
        return nodeMap.size();
    }

    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }

    public void visit(BiConsumer<Variable, Map<Variable, DV>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.variable, n.dependsOn));
    }

    private Node getOrCreate(Variable v) {
        ensureNotFrozen();
        Objects.requireNonNull(v);
        Node node = nodeMap.get(v);
        if (node == null) {
            node = new Node(v);
            nodeMap.put(v, node);
        }
        return node;
    }


    public void addNode(Variable v, Map<Variable, DV> dependsOn) {
        addNode(v, dependsOn, false, (o, n) -> o);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addNode(Variable v, Object... variableDvPairs) {
        Map<Variable, DV> dependsOn = (Map<Variable, DV>) mapSupplier.get();
        for (int i = 0; i < variableDvPairs.length; i += 2) {
            dependsOn.put((Variable) variableDvPairs[i], (DV) variableDvPairs[i + 1]);
        }
        addNode(v, dependsOn, false, (o, n) -> o);
    }

    @SuppressWarnings("unchecked")
    public void addNode(Variable v,
                        Map<Variable, DV> dependsOn,
                        boolean bidirectional,
                        BinaryOperator<DV> merger) {
        ensureNotFrozen();
        Node node = getOrCreate(v);
        for (Map.Entry<Variable, DV> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) {
                node.dependsOn = (Map<Variable, DV>) mapSupplier.get();
            }
            DV linkLevel = e.getValue();
            assert !LinkedVariables.LINK_INDEPENDENT.equals(linkLevel);

            node.dependsOn.merge(e.getKey(), linkLevel, merger);
            if (bidirectional || LinkedVariables.LINK_STATICALLY_ASSIGNED.equals(linkLevel)
                    && !(e.getKey() instanceof This)
                    && !(v instanceof This)
                    && !(e.getKey() instanceof ReturnVariable)
                    && !(v instanceof ReturnVariable)) {
                Node n = getOrCreate(e.getKey());
                if (n.dependsOn == null) {
                    n.dependsOn = (Map<Variable, DV>) mapSupplier.get();
                }
                n.dependsOn.merge(v, linkLevel, merger);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ShortestPath shortestPath() {
        int n = nodeMap.size();
        Variable[] variables = new Variable[n];
        Map<Variable, Integer> variableIndex = (Map<Variable, Integer>) mapSupplier.get();
        int i = 0;
        for (Variable v : nodeMap.keySet()) {
            variableIndex.put(v, i);
            variables[i] = v;
            ++i;
        }
        Map<Integer, Map<Integer, Long>> edges = new HashMap<>();
        CausesOfDelay delay = null;
        for (Map.Entry<Variable, Node> entry : nodeMap.entrySet()) {
            Map<Variable, DV> dependsOn = entry.getValue().dependsOn;
            if (dependsOn != null) {
                int d1 = variableIndex.get(entry.getKey());
                Map<Integer, Long> edgesOfD1 = new HashMap<>();
                edges.put(d1, edgesOfD1);
                for (Map.Entry<Variable, DV> e2 : dependsOn.entrySet()) {
                    int d2 = variableIndex.get(e2.getKey());
                    DV dv = e2.getValue();
                    if (delay == null && dv.isDelayed()) {
                        delay = dv.causesOfDelay();
                    }
                    long d = ShortestPathImpl.toDistanceComponent(dv);
                    edgesOfD1.put(d2, d);
                }
            }
        }
        return new ShortestPathImpl(variableIndex, variables, edges, delay);
    }

    @Override
    public DV edgeValueOrNull(Variable v1, Variable v2) {
        Node n = nodeMap.get(v1);
        if (n != null && n.dependsOn != null) {
            return n.dependsOn.get(v2);
        }
        return null;
    }
}
