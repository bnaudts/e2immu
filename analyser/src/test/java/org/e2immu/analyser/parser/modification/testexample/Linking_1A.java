package org.e2immu.analyser.parser.modification.testexample;

import java.util.List;
import java.util.Set;
import java.util.function.*;

public class Linking_1A {

    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    static class N {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    /* supplier **/

    static <X> X s0(Supplier<X> supplier) {
        return supplier.get();
    }

    static <X> X s0l(Supplier<X> supplier) {
        //noinspection ALL
        Supplier<X> s = () -> supplier.get();
        return s.get();
    }

    static <X> X s0m(Supplier<X> supplier) {
        //noinspection ALL
        Supplier<X> s = supplier::get;
        return s.get();
    }

    static <X> X s0a(Supplier<X> supplier) {
        //noinspection ALL
        Supplier<X> s = new Supplier<X>() {
            @Override
            public X get() {
                return supplier.get();
            }
        };
        return s.get();
    }

    static M s1(Supplier<M> supplier) {
        return supplier.get();
    }

    static M s1l(Supplier<M> supplier) {
        //noinspection ALL
        Supplier<M> s = () -> supplier.get();
        return s.get();
    }

    static M s1m(Supplier<M> supplier) {
        //noinspection ALL
        Supplier<M> s = supplier::get;
        return s.get();
    }

    static M s1a(Supplier<M> supplier) {
        //noinspection ALL
        Supplier<M> s = new Supplier<>() {
            @Override
            public M get() {
                return supplier.get();
            }
        };
        return s.get();
    }

    static Integer s2(Supplier<Integer> supplier) {
        return supplier.get();
    }

    static Integer s2l(Supplier<Integer> supplier) {
        //noinspection ALL
        Supplier<Integer> s = () -> supplier.get();
        return s.get();
    }

    static Integer s2m(Supplier<Integer> supplier) {
        //noinspection ALL
        Supplier<Integer> s = supplier::get;
        return s.get();
    }

    static Integer s2a(Supplier<Integer> supplier) {
        Supplier<Integer> s = new Supplier<>() {
            @Override
            public Integer get() {
                return supplier.get();
            }
        };
        return s.get();
    }

    /* predicate */

    static <X> boolean p0(X x, Predicate<X> predicate) {
        return predicate.test(x);
    }

    static <X> boolean p0l(X x, Predicate<X> predicate) {
        //noinspection ALL
        Predicate<X> p = t -> predicate.test(t);
        return p.test(x);
    }

    static <X> boolean p0m(X x, Predicate<X> predicate) {
        //noinspection ALL
        Predicate<X> p = predicate::test;
        return p.test(x);
    }

    static <X> boolean p0a(X x, Predicate<X> predicate) {
        Predicate<X> p = new Predicate<>() {
            @Override
            public boolean test(X x) {
                return predicate.test(x);
            }
        };
        return p.test(x);
    }

    static boolean p1(M x, Predicate<M> predicate) {
        return predicate.test(x);
    }

    static boolean p1l(M x, Predicate<M> predicate) {
        //noinspection ALL
        Predicate<M> p = t -> predicate.test(t);
        return p.test(x);
    }

    static boolean p1m(M x, Predicate<M> predicate) {
        //noinspection ALL
        Predicate<M> p = predicate::test;
        return p.test(x);
    }

    static boolean p1a(M x, Predicate<M> predicate) {
        Predicate<M> p = new Predicate<>() {
            @Override
            public boolean test(M x) {
                return predicate.test(x);
            }
        };
        return p.test(x);
    }

    static boolean p2(Integer x, Predicate<Integer> predicate) {
        return predicate.test(x);
    }

    static boolean p2l(Integer x, Predicate<Integer> predicate) {
        //noinspection ALL
        Predicate<Integer> p = t -> predicate.test(t);
        return p.test(x);
    }

    static boolean p2m(Integer x, Predicate<Integer> predicate) {
        //noinspection ALL
        Predicate<Integer> p = predicate::test;
        return p.test(x);
    }

    static boolean p2a(Integer x, Predicate<Integer> predicate) {
        Predicate<Integer> p = new Predicate<>() {
            @Override
            public boolean test(Integer x) {
                return predicate.test(x);
            }
        };
        return p.test(x);
    }

    /* consumer */

    static <X> Consumer<X> c0(X x, Consumer<X> consumer) {
        consumer.accept(x);
        return consumer;
    }

    static Consumer<M> c1(M x, Consumer<M> consumer) {
        consumer.accept(x);
        return consumer;
    }

    static Consumer<Integer> c2(int x, Consumer<Integer> consumer) {
        consumer.accept(x);
        return consumer;
    }

    /* function, X Y do not obviously share hidden content */

    static <X, Y> Y f0(X x, Function<X, Y> function) {
        return function.apply(x);
    }

    static <X, Y> Y f0l(X x, Function<X, Y> function) {
        //noinspection ALL
        Function<X, Y> f = xx -> function.apply(xx);
        return f.apply(x);
    }

    static <X> M f1(X x, Function<X, M> function) {
        return function.apply(x);
    }

    static <X> M f1l(X x, Function<X, M> function) {
        //noinspection ALL
        Function<X, M> f = xx -> function.apply(xx);
        return f.apply(x);
    }

    static <Y> Y f2(M m, Function<M, Y> function) {
        return function.apply(m);
    }

    static <Y> Y f2l(M m, Function<M, Y> function) {
        //noinspection ALL
        Function<M, Y> f = mm -> function.apply(mm);
        return f.apply(m);
    }

    static <X> Integer f3(X x, Function<X, Integer> function) {
        return function.apply(x);
    }

    static <X> Integer f3l(X x, Function<X, Integer> function) {
        //noinspection ALL
        Function<X, Integer> f = xx -> function.apply(xx);
        return f.apply(x);
    }

    static <Y> Y f4(int i, Function<Integer, Y> function) {
        return function.apply(i);
    }

    static <Y> Y f4l(int i, Function<Integer, Y> function) {
        //noinspection ALL
        Function<Integer, Y> f = ii -> function.apply(ii);
        return f.apply(i);
    }

    static M f5(N n, Function<N, M> function) {
        return function.apply(n);
    }

    static M f5l(N n, Function<N, M> function) {
        //noinspection ALL
        Function<N, M> f = nn -> function.apply(nn);
        return function.apply(n);
    }

    static M f6(String s, Function<String, M> function) {
        return function.apply(s);
    }

    static M f6l(String s, Function<String, M> function) {
        //noinspection ALL
        Function<String, M> f = t -> function.apply(t);
        return f.apply(s);
    }

    static String f7(M m, Function<M, String> function) {
        return function.apply(m);
    }

    static String f7s(M m, Function<M, String> function) {
        //noinspection ALL
        Function<M, String> f = mm -> function.apply(mm);
        return f.apply(m);
    }

    static String f8(int i, Function<Integer, String> function) {
        return function.apply(i);
    }

    static String f8l(int i, Function<Integer, String> function) {
        //noinspection ALL
        Function<Integer, String> f = ii -> function.apply(ii);
        return f.apply(i);
    }

    /* function, types that share hidden content */

    static <T> T f9(T t, Function<T, T> function) {
        return function.apply(t);
    }

    static <T> T f10(List<T> ts, Function<List<T>, T> function) {
        return function.apply(ts);
    }

    static <T> List<T> f11(T t, Function<T, List<T>> function) {
        return function.apply(t);
    }

    static <T> Set<T> f12(List<T> ts, Function<List<T>, Set<T>> function) {
        return function.apply(ts);
    }

}
