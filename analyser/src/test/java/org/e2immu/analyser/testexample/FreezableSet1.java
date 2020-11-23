package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@E2Container(after = "frozen")
public class FreezableSet1 {

    private final Set<String> set = new HashSet<>();
    private boolean frozen;

    // here to see how preconditions work properly with parameters
    private static void check(int n) {
        if (n < 0) throw new UnsupportedOperationException();
    }

    @Only(after = "frozen")
    @NotModified
    @NotNull1
    public Stream<String> stream() {
        if (!frozen) throw new UnsupportedOperationException();
        return set.stream();
    }

    @Only(before = "frozen")
    @NotModified
    @NotNull1
    public Stream<String> streamEarly() {
        if (frozen) throw new UnsupportedOperationException();
        return set.stream();
    }

    @Only(before = "frozen")
    @Modified
    public void add(String s) {
        if (frozen) throw new UnsupportedOperationException();
        set.add(s);
    }

    @Mark("frozen")
    @Modified
    public void freeze() {
        if (frozen) throw new UnsupportedOperationException();
        frozen = true;
    }

    @NotModified
    @Only(absent = true)
    public boolean isFrozen() {
        return frozen;
    }
}
