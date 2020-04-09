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

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@E2Immutable(after = "freeze")
@NullNotAllowed
@Container
public class Trie<T> extends Freezable {

    @NotModified(type = AnnotationType.VERIFY_ABSENT)
    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        List<T> data;
        Map<String, TrieNode<T>> map;
    }

    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    private TrieNode<T> goTo(@NotModified String[] strings) {
        return goTo(strings, strings.length);
    }

    @NotNull(type = AnnotationType.VERIFY_ABSENT)
    private TrieNode<T> goTo(@NotModified String[] strings, int upToPosition) {
        TrieNode<T> node = root;
        for (int i = 0; i < upToPosition; i++) {
            if (node.map == null) return null;
            node = node.map.get(strings[i]);
            if (node == null) return null;
        }
        return node;
    }

    @NotModified
    public boolean isStrictPrefix(String[] prefix) {
        TrieNode<T> node = goTo(prefix);
        return node != null && node.data == null;
    }

    @NotModified
    public List<T> get(String[] strings) {
        TrieNode<T> node = goTo(strings);
        return node == null ? null : node.data;
    }

    @NotModified
    public List<T> get(String[] strings, int upToPosition) {
        TrieNode<T> node = goTo(strings, upToPosition);
        return node == null ? null : node.data == null ? List.of() : node.data;
    }

    @Only(before = "freeze")
    public List<T> getOrCompute(String[] strings, Function<String[], T> action) {
        TrieNode<T> node = goTo(strings);
        if (node == null) {
            return add(strings, action.apply(strings)).data;
        }
        if (node.data == null) {
            node.data = new LinkedList<>();
            node.data.add(action.apply(strings));
        }
        return node.data;
    }

    @NotModified
    public void visitLeaves(String[] strings, BiConsumer<String[], List<T>> visitor) {
        TrieNode<T> node = goTo(strings);
        if (node == null) return;
        if (node.map != null) {
            node.map.forEach((s, n) -> {
                if (n.map == null) { // leaf
                    visitor.accept(new String[]{s}, n.data);
                }
            });
        }
    }

    // @NotModified not possible on visitor (is interface)
    // by visiting.
    @NotModified
    public void visit(String[] strings,
                      BiConsumer<String[], List<T>> visitor) {
        TrieNode<T> node = goTo(strings);
        if (node == null) return;
        recursivelyVisit(node, new Stack<>(), visitor);
    }

    private void recursivelyVisit(TrieNode<T> node,
                                  Stack<String> strings,
                                  BiConsumer<String[], List<T>> visitor) {
        if (node.data != null) {
            visitor.accept(strings.toArray(String[]::new), node.data);
        }
        if (node.map != null) {
            node.map.forEach((s, n) -> {
                strings.push(s);
                recursivelyVisit(n, strings, visitor);
                strings.pop();
            });
        }
    }

    @NotNull
    @Only(before = "freeze")
    public TrieNode<T> add(String[] strings,
                           T data) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) {
                node.map = new HashMap<>();
                newTrieNode = new TrieNode<>();
                node.map.put(s, newTrieNode);
            } else {
                newTrieNode = node.map.get(s);
                if (newTrieNode == null) {
                    newTrieNode = new TrieNode<>();
                    node.map.put(s, newTrieNode);
                }
            }
            node = newTrieNode;
        }
        if (node.data == null) node.data = new LinkedList<>();
        node.data.add(Objects.requireNonNull(data));
        return node;
    }
}
