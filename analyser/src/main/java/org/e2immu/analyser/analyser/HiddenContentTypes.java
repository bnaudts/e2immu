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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record HiddenContentTypes(Set<ParameterizedType> types) {

    public static final HiddenContentTypes EMPTY = new HiddenContentTypes(Set.of());

    public HiddenContentTypes(Set<ParameterizedType> types) {
        this.types = Set.copyOf(types);
    }

    public static HiddenContentTypes of(ParameterizedType pt) {
        return new HiddenContentTypes(Set.of(pt));
    }

    // if T is hidden, then ? extends T is hidden as well
    public boolean contains(ParameterizedType parameterizedType) {
        if (types.contains(parameterizedType)) return true;
        if (parameterizedType.typeParameter != null) {
            if (parameterizedType.wildCard != ParameterizedType.WildCard.NONE) {
                ParameterizedType withoutWildcard = parameterizedType.copyWithoutWildcard();
                return types.contains(withoutWildcard);
            } else {
                // try with wildcard
                ParameterizedType withWildCard = new ParameterizedType(parameterizedType.typeParameter, parameterizedType.arrays,
                        ParameterizedType.WildCard.EXTENDS);
                return types.contains(withWildCard);
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return types.isEmpty();
    }

    @Override
    public String toString() {
        return types.stream().map(ParameterizedType::printSimple).sorted().collect(Collectors.joining(", "));
    }

    public HiddenContentTypes union(HiddenContentTypes other) {
        Set<ParameterizedType> set = new HashSet<>(types);
        set.addAll(other.types);
        return new HiddenContentTypes(set);
    }

    public HiddenContentTypes intersection(HiddenContentTypes other) {
        Set<ParameterizedType> set = new HashSet<>(types);
        set.retainAll(other.types);
        return new HiddenContentTypes(set);
    }

    public int size() {
        return types.size();
    }

    /*
    make a translation map based on pt2, and translate from formal to concrete.

    If types contains E=formal type parameter of List<E>, and pt = List<T>, we want
    to return a HiddenContentTypes containing T instead of E
     */
    public HiddenContentTypes translate(InspectionProvider inspectionProvider, ParameterizedType pt) {
        Map<NamedType, ParameterizedType> map = pt.initialTypeParameterMap(inspectionProvider);
        Set<ParameterizedType> newTypes = types.stream()
                .map(t -> translate(inspectionProvider, pt, map, t))
                .collect(Collectors.toUnmodifiableSet());
        return new HiddenContentTypes(newTypes);
    }

    private ParameterizedType translate(InspectionProvider inspectionProvider,
                                        ParameterizedType pt,
                                        Map<NamedType, ParameterizedType> map,
                                        ParameterizedType t) {
        if (map.isEmpty() && t.isTypeParameter() && t.isAssignableFrom(inspectionProvider, pt)) {
            return pt;
        }
        return t.applyTranslation(inspectionProvider.getPrimitives(), map);
    }

    public HiddenContentTypes dropArrays() {
        if (types.isEmpty()) return this;
        Set<ParameterizedType> newSet = types.stream()
                .map(ParameterizedType::copyWithoutArrays)
                .collect(Collectors.toUnmodifiableSet());
        return new HiddenContentTypes(newSet);
    }
}
