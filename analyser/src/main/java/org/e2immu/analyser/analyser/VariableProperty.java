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

package org.e2immu.analyser.analyser;

public enum VariableProperty {

    // we know this variable can never be null
    PERMANENTLY_NOT_NULL("permanently not null"),

    CHECK_NOT_NULL("check not null"),
    READ("read"),
    READ_MULTIPLE_TIMES("read+"),

    ASSIGNED("assigned"),
    ASSIGNED_MULTIPLE_TIMES("assigned+"),
    // in a block, are we guaranteed to reach the last assignment?
    // we focus on last assignment because that is what the 'currentValue' holds
    LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED("reached"),

    CREATED("created"),
    CONTENT_MODIFIED("content modified"),
    // we don't know yet
    CONTENT_MODIFIED_DELAYED("content modified delayed");

    public final String name;

    VariableProperty(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
