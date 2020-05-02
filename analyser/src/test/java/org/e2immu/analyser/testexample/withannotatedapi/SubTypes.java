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

package org.e2immu.analyser.testexample.withannotatedapi;

import java.util.Iterator;

public class SubTypes {

    private static String staticField;

    String field;

    static Iterator<Integer> newIt() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Integer next() {
                return 3;
            }
        };
    }

    protected static String methodWithSubType() {

        class KV {
            String key;
            String value;

            KV(String key, String value) {
                this.key = key;
                this.value = value;
            }

            @Override
            public String toString() {
                return "KV=(" + key + "," + value + ")";
            }
        }
        KV kv1 = new KV("a", "BC");
        return kv1.toString();
    }

    class NonStaticSubType {
        @Override
        public String toString() {
            return field;
        }
    }

    static class StaticSubType {
        @Override
        public String toString() {
            staticField = "abc"; // ERROR
            return "hello" + staticField;
        }

        public static void add() {
            staticField += "a"; // error
        }

        static class SubTypeOfStaticSubType {

            @Override
            public int hashCode() {
                return 3;
            }
        }
    }


    private static class PrivateSubType {
        private String field;

        PrivateSubType(String field) {
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    static String doSomething() {
        PrivateSubType pst = new PrivateSubType("Hello");
        pst.field = "help";
        return pst.field;
    }
}
