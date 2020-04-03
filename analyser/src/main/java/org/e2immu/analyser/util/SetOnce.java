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

@E2Final(after = "set", type = AnnotationType.CONTRACT)
public class SetOnce<T> {
    // volatile guarantees that once the value is set, other threads see the effect immediately
    private volatile T t;

    @Mark("set")
    @Only(before = "set")
    public void set(@NullNotAllowed T t) { // @NotModified implied
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t != null) {
                throw new UnsupportedOperationException("Already set");
            }
            this.t = t;
        }
    }

    @Mark("set")
    public void setIfNotYetSet(@NullNotAllowed T t) {
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t == null) {
                this.t = t;
            }
        }
    }

    @Only(after = "set")
    public T get() {
        if (t == null) {
            throw new UnsupportedOperationException("Not yet set");
        }
        return t;
    }

    // TODO need special semantics for this one...
    public void overwrite(@NullNotAllowed T t) {
        if (t == null) throw new NullPointerException("Null not allowed");
        synchronized (this) {
            if (this.t == null) {
                throw new UnsupportedOperationException("Not yet set; do not use overwrite lightly");
            }
            this.t = t;
        }
    }

    @NotModified
    public boolean isSet() {
        return t != null;
    }
}
