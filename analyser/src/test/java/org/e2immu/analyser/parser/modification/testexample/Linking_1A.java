package org.e2immu.analyser.parser.modification.testexample;

import java.util.List;
import java.util.Set;
import java.util.function.*;

/*
systematic approach to testing the linking of lambda's method references, and
anonymous implementations of simple functional interfaces:

Supplier
Predicate
Consumer
Function
 */
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
/*
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
*/
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
/*
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
*/
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
/*
    static Integer s2a(Supplier<Integer> supplier) {
        Supplier<Integer> s = new Supplier<>() {
            @Override
            public Integer get() {
                return supplier.get();
            }
        };
        return s.get();
    }
*/
    // predicate

    static <X> boolean p0(X x, Predicate<X> predicate) {
        return predicate.test(x);
    }
/*
    static <X> boolean p0l(X x, Predicate<X> predicate) {
        //noinspection ALL
        Predicate<X> p = t -> predicate.test(t);
        return p.test(x);
    }
*/
    static <X> boolean p0m(X x, Predicate<X> predicate) {
        //noinspection ALL
        Predicate<X> p = predicate::test;
        return p.test(x);
    }
/*
    static <X> boolean p0a(X x, Predicate<X> predicate) {
        Predicate<X> p = new Predicate<>() {
            @Override
            public boolean test(X x) {
                return predicate.test(x);
            }
        };
        return p.test(x);
    }
*/
    static boolean p1(M x, Predicate<M> predicate) {
        return predicate.test(x);
    }
/*
    static boolean p1l(M x, Predicate<M> predicate) {
        //noinspection ALL
        Predicate<M> p = t -> predicate.test(t);
        return p.test(x);
    }
*/
    static boolean p1m(M x, Predicate<M> predicate) {
        //noinspection ALL
        Predicate<M> p = predicate::test;
        return p.test(x);
    }
/*
    static boolean p1a(M x, Predicate<M> predicate) {
        Predicate<M> p = new Predicate<>() {
            @Override
            public boolean test(M x) {
                return predicate.test(x);
            }
        };
        return p.test(x);
    }
*/
    static boolean p2(Integer x, Predicate<Integer> predicate) {
        return predicate.test(x);
    }
/*
    static boolean p2l(Integer x, Predicate<Integer> predicate) {
        //noinspection ALL
        Predicate<Integer> p = t -> predicate.test(t);
        return p.test(x);
    }
*/
    static boolean p2m(Integer x, Predicate<Integer> predicate) {
        //noinspection ALL
        Predicate<Integer> p = predicate::test;
        return p.test(x);
    }
/*
    static boolean p2a(Integer x, Predicate<Integer> predicate) {
        Predicate<Integer> p = new Predicate<>() {
            @Override
            public boolean test(Integer x) {
                return predicate.test(x);
            }
        };
        return p.test(x);
    }

    // consumer

    static <X> Consumer<X> c0(X x, Consumer<X> consumer) {
        consumer.accept(x);
        return consumer;
    }

    static <X> Consumer<X> c0l(X x, Consumer<X> consumer) {
        //noinspection ALL
        Consumer<X> c = xx -> consumer.accept(xx);
        c.accept(x);
        return consumer;
    }

    static <X> Consumer<X> c0m(X x, Consumer<X> consumer) {
        //noinspection ALL
        Consumer<X> c = consumer::accept;
        c.accept(x);
        return consumer;
    }

    static <X> Consumer<X> c0a(X x, Consumer<X> consumer) {
        Consumer<X> c = new Consumer<X>() {
            @Override
            public void accept(X x) {
                consumer.accept(x);
            }
        };
        c.accept(x);
        return consumer;
    }

    static Consumer<M> c1(M m, Consumer<M> consumer) {
        consumer.accept(m);
        return consumer;
    }

    static Consumer<M> c1l(M m, Consumer<M> consumer) {
        //noinspection ALL
        Consumer<M> c = mm -> consumer.accept(mm);
        c.accept(m);
        return consumer;
    }

    static Consumer<M> c1m(M m, Consumer<M> consumer) {
        //noinspection ALL
        Consumer<M> c = consumer::accept;
        c.accept(m);
        return consumer;
    }

    static Consumer<M> c1a(M m, Consumer<M> consumer) {
        Consumer<M> c = new Consumer<>() {
            @Override
            public void accept(M m) {
                consumer.accept(m);
            }
        };
        c.accept(m);
        return consumer;
    }

    static Consumer<Integer> c2(int i, Consumer<Integer> consumer) {
        consumer.accept(i);
        return consumer;
    }

    static Consumer<Integer> c2l(int i, Consumer<Integer> consumer) {
        //noinspection ALL
        Consumer<Integer> c = ii -> consumer.accept(ii);
        c.accept(i);
        return consumer;
    }

    static Consumer<Integer> c2m(int i, Consumer<Integer> consumer) {
        //noinspection ALL
        Consumer<Integer> c = consumer::accept;
        c.accept(i);
        return consumer;
    }

    static Consumer<Integer> c2a(int i, Consumer<Integer> consumer) {
        Consumer<Integer> c = new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) {
                consumer.accept(integer);
            }
        };
        c.accept(i);
        return consumer;
    }

    // function, X Y do not obviously share hidden content

    static <X, Y> Y f0(X x, Function<X, Y> function) {
        return function.apply(x);
    }

    static <X, Y> Y f0l(X x, Function<X, Y> function) {
        //noinspection ALL
        Function<X, Y> f = xx -> function.apply(xx);
        return f.apply(x);
    }

    static <X, Y> Y f0m(X x, Function<X, Y> function) {
        //noinspection ALL
        Function<X, Y> f = function::apply;
        return f.apply(x);
    }

    static <X, Y> Y f0a(X x, Function<X, Y> function) {
        //noinspection ALL
        Function<X, Y> f = new Function<X, Y>() {
            @Override
            public Y apply(X x) {
                return function.apply(x);
            }
        };
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

    static <X> M f1m(X x, Function<X, M> function) {
        //noinspection ALL
        Function<X, M> f = function::apply;
        return f.apply(x);
    }

    static <X> M f1a(X x, Function<X, M> function) {
        Function<X, M> f = new Function<X, M>() {
            @Override
            public M apply(X x) {
                return function.apply(x);
            }
        };
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

    static <Y> Y f2m(M m, Function<M, Y> function) {
        //noinspection ALL
        Function<M, Y> f = function::apply;
        return f.apply(m);
    }

    static <Y> Y f2a(M m, Function<M, Y> function) {
        Function<M, Y> f = new Function<M, Y>() {
            @Override
            public Y apply(M m) {
                return function.apply(m);
            }
        };
        return f.apply(m);
    }

    // equivalent to List.add(), where we'd return the size rather than boolean

    static <X> Integer f3(X x, Function<X, Integer> function) {
        return function.apply(x);
    }

    static <X> Integer f3l(X x, Function<X, Integer> function) {
        //noinspection ALL
        Function<X, Integer> f = xx -> function.apply(xx);
        return f.apply(x);
    }

    static <X> Integer f3m(X x, Function<X, Integer> function) {
        //noinspection ALL
        Function<X, Integer> f = function::apply;
        return f.apply(x);
    }

    static <X> Integer f3a(X x, Function<X, Integer> function) {
        Function<X, Integer> f = new Function<X, Integer>() {
            @Override
            public Integer apply(X x) {
                return function.apply(x);
            }
        };
        return f.apply(x);
    }

    // equivalent to List.get(index)

    static <Y> Y f4(int i, Function<Integer, Y> function) {
        return function.apply(i);
    }

    static <Y> Y f4l(int i, Function<Integer, Y> function) {
        //noinspection ALL
        Function<Integer, Y> f = ii -> function.apply(ii);
        return f.apply(i);
    }

    static <Y> Y f4m(int i, Function<Integer, Y> function) {
        //noinspection ALL
        Function<Integer, Y> f = function::apply;
        return f.apply(i);
    }

    static <Y> Y f4a(int i, Function<Integer, Y> function) {
        Function<Integer, Y> f = new Function<Integer, Y>() {
            @Override
            public Y apply(Integer integer) {
                return function.apply(integer);
            }
        };
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

    static M f5m(N n, Function<N, M> function) {
        //noinspection ALL
        Function<N, M> f = function::apply;
        return function.apply(n);
    }

    static M f5a(N n, Function<N, M> function) {
        Function<N, M> f = new Function<N, M>() {
            @Override
            public M apply(N n) {
                return function.apply(n);
            }
        };
        return function.apply(n);
    }

    // equivalent to Map.get(string), List.get(...)

    static M f6(String s, Function<String, M> function) {
        return function.apply(s);
    }

    static M f6l(String s, Function<String, M> function) {
        //noinspection ALL
        Function<String, M> f = t -> function.apply(t);
        return f.apply(s);
    }

    static M f6m(String s, Function<String, M> function) {
        //noinspection ALL
        Function<String, M> f = function::apply;
        return f.apply(s);
    }

    static M f6a(String s, Function<String, M> function) {
        Function<String, M> f = new Function<String, M>() {
            @Override
            public M apply(String s) {
                return function.apply(s);
            }
        };
        return f.apply(s);
    }

    // equivalent to List.add(), where we'd return some String rather than boolean

    static String f7(M m, Function<M, String> function) {
        return function.apply(m);
    }

    static String f7s(M m, Function<M, String> function) {
        //noinspection ALL
        Function<M, String> f = mm -> function.apply(mm);
        return f.apply(m);
    }

    static String f7m(M m, Function<M, String> function) {
        //noinspection ALL
        Function<M, String> f = function::apply;
        return f.apply(m);
    }

    static String f7a(M m, Function<M, String> function) {
        //noinspection ALL
        Function<M, String> f = new Function<M, String>() {
            @Override
            public String apply(M m) {
                return function.apply(m);
            }
        };
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

    static String f8m(int i, Function<Integer, String> function) {
        //noinspection ALL
        Function<Integer, String> f = function::apply;
        return f.apply(i);
    }

    static String f8a(int i, Function<Integer, String> function) {
        Function<Integer, String> f = new Function<>() {
            @Override
            public String apply(Integer integer) {
                return function.apply(integer);
            }
        };
        return f.apply(i);
    }

    // function, types that share hidden content

    static <T> T f9(T t, Function<T, T> function) {
        return function.apply(t);
    }

    static <T> T f9l(T t, Function<T, T> function) {
        //noinspection ALL
        Function<T, T> f = tt -> function.apply(tt);
        return f.apply(t);
    }

    static <T> T f9m(T t, Function<T, T> function) {
        //noinspection ALL
        Function<T, T> f = function::apply;
        return f.apply(t);
    }

    static <T> T f9a(T t, Function<T, T> function) {
        Function<T, T> f = new Function<>() {
            @Override
            public T apply(T t) {
                return function.apply(t);
            }
        };
        return f.apply(t);
    }

    static <T> T f10(List<T> ts, Function<List<T>, T> function) {
        return function.apply(ts);
    }

    static <T> T f10l(List<T> ts, Function<List<T>, T> function) {
        //noinspection ALL
        Function<List<T>, T> f = t -> function.apply(t);
        return f.apply(ts);
    }

    static <T> T f10m(List<T> ts, Function<List<T>, T> function) {
        //noinspection ALL
        Function<List<T>, T> f = function::apply;
        return f.apply(ts);
    }

    static <T> T f10a(List<T> ts, Function<List<T>, T> function) {
        Function<List<T>, T> f = new Function<>() {
            @Override
            public T apply(List<T> list) {
                return function.apply(list);
            }
        };
        return f.apply(ts);
    }

    static <T> List<T> f11(T t, Function<T, List<T>> function) {
        return function.apply(t);
    }

    static <T> List<T> f11l(T t, Function<T, List<T>> function) {
        //noinspection ALL
        Function<T, List<T>> f = tt -> function.apply(tt);
        return f.apply(t);
    }

    static <T> List<T> f11m(T t, Function<T, List<T>> function) {
        //noinspection ALL
        Function<T, List<T>> f = function::apply;
        return f.apply(t);
    }

    static <T> List<T> f11a(T t, Function<T, List<T>> function) {
        Function<T, List<T>> f = new Function<T, List<T>>() {
            @Override
            public List<T> apply(T tt) {
                return function.apply(tt);
            }
        };
        return f.apply(t);
    }

    static <T> Set<T> f12(List<T> ts, Function<List<T>, Set<T>> function) {
        return function.apply(ts);
    }

    static <T> Set<T> f12l(List<T> ts, Function<List<T>, Set<T>> function) {
        //noinspection ALL
        Function<List<T>, Set<T>> f = t -> function.apply(t);
        return f.apply(ts);
    }

    static <T> Set<T> f12m(List<T> ts, Function<List<T>, Set<T>> function) {
        //noinspection ALL
        Function<List<T>, Set<T>> f = function::apply;
        return f.apply(ts);
    }

    static <T> Set<T> f12a(List<T> ts, Function<List<T>, Set<T>> function) {
        Function<List<T>, Set<T>> f = new Function<List<T>, Set<T>>() {
            @Override
            public Set<T> apply(List<T> list) {
                return function.apply(list);
            }
        };
        return f.apply(ts);
    }
*/
}
