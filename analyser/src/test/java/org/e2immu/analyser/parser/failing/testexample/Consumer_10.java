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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/*
situation: @Dependent instead of @Dependent2, entries of map
 */
public class Consumer_10 {

    interface MyConsumer<T> {
        @Modified
        void accept(T t);
    }

    @FinalFields
    @Container
    static class Configuration {
        // not transparent; the entries are neither. They are @Container, mutable, dependent.
        private final Map<String, String> map = new HashMap<>();

        @Modified
        public Configuration add(String key, String value) {
            this.map.put(key, value);
            return this;
        }

        @NotModified
        @Container
        public Stream<Map.Entry<String, String>> stream() {
            return map.entrySet().stream();
        }

        @NotModified
        public void forEach(@IgnoreModifications MyConsumer<Map.Entry<String, String>> consumer) {
            map.entrySet().forEach(consumer::accept);
        }
    }

    private static void print(@NotModified Configuration configuration) {
        configuration.forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));
    }

    private static void change(@Modified Configuration configuration) {
        configuration.forEach(e -> e.setValue(e.getValue() + "!"));
    }

    @Test
    public void test1() {
        Configuration c = new Configuration().add("a", "1").add("b", "2");
        print(c);
        change(c);
        print(c);
    }
}
