/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

import org.e2immu.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@E2Immutable(after = "freeze")
public class SetOnceMap<K, V> extends Freezable {

    private final Map<K, V> map = new HashMap<>();

    @Only(before = "freeze")
    public void put(@NullNotAllowed K k, @NullNotAllowed V v) {
        Objects.requireNonNull(k);
        Objects.requireNonNull(v);
        ensureNotFrozen();
        if (isSet(k)) throw new UnsupportedOperationException("Already decided on " + k);
        map.put(k, v);
    }

    @NotNull
    public V get(K k) {
        if (!isSet(k)) throw new UnsupportedOperationException("Not yet decided on " + k);
        return Objects.requireNonNull(map.get(k));
    }

    public V getOtherwiseNull(K k) {
        return map.get(k);
    }

    public boolean isSet(K k) {
        return map.containsKey(k);
    }
    
    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void visit(BiConsumer<K, V> consumer) {
        map.forEach(consumer);
    }

    public Stream<Map.Entry<K, V>> stream() {
        return map.entrySet().stream();
    }

    // this function assumes V==Boolean
    public boolean isTrue(K k) {
        return (Boolean) get(k);
    }
}
