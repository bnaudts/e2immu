package org.e2immu.analyser.parser.modification.testexample;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Linking_1 {

    // the archetypal modifiable type
    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    static <X> X m0(Supplier<X> supplier) {
        return supplier.get();
    }

    static M m1(Supplier<M> supplier) {
        return supplier.get();
    }

    static Integer m2(Supplier<Integer> supplier) {
        return supplier.get();
    }

    static Stream<M> m3(Stream<M> stream) {
        return stream.filter(m -> m.i == 3);
    }

    static Optional<M> m4(Stream<M> stream) {
        return stream.filter(m -> m.i == 3).findFirst();
    }

    static M m5(Stream<M> stream) {
        return stream.filter(m -> m.i == 3).findFirst().orElseThrow();
    }

    static Stream<Integer> m6(Stream<Integer> stream) {
        return stream.filter(i -> i == 3);
    }

    static Optional<Integer> m7(Stream<Integer> stream) {
        return stream.filter(i -> i == 3).findFirst();
    }

    static Integer m8(Stream<Integer> stream) {
        return stream.filter(i -> i == 3).findFirst().orElseThrow();
    }

    static <X> Stream<X> m9(Stream<X> stream, Predicate<X> predicate) {
        //noinspection ALL
        return stream.filter(i -> predicate.test(i));
    }

    static <X> Optional<X> m10(Stream<X> stream, Predicate<X> predicate) {
        //noinspection ALL
        return stream.filter(i -> predicate.test(i)).findFirst();
    }

    static <X> X m11(Stream<X> stream, Predicate<X> predicate) {
        //noinspection ALL
        return stream.filter(i -> predicate.test(i)).findFirst().orElseThrow();
    }
}
