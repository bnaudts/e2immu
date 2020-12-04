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

package org.e2immu.analyser.model;

import com.github.javaparser.ast.Modifier;

import java.util.Arrays;
import java.util.Set;

public enum FieldModifier {

    PRIVATE(0), PUBLIC(0), PROTECTED(0),

    // this one obviously does not exist as a field modifier in Java code, but is useful so we can use this enum as an 'access' type
    PACKAGE(0),

    STATIC(1),

    FINAL(2),
    VOLATILE(2),

    TRANSIENT(3),
    ;

    private final int group;

    FieldModifier(int group) {
        this.group = group;
    }

    private static final int GROUPS = 4;

    public static FieldModifier from(Modifier m) {
        return FieldModifier.valueOf(m.getKeyword().toString().toUpperCase());
    }

    public String toJava() {
        return name().toLowerCase();
    }


    public static String[] toJava(Set<FieldModifier> modifiers) {
        FieldModifier[] array = new FieldModifier[GROUPS];
        for (FieldModifier methodModifier : modifiers) {
            if (array[methodModifier.group] != null)
                throw new UnsupportedOperationException("? already have " + array[methodModifier.group]);
            array[methodModifier.group] = methodModifier;
        }
        return Arrays.stream(array).filter(m -> m != null && m != PACKAGE).map(FieldModifier::toJava).toArray(String[]::new);
    }
}
