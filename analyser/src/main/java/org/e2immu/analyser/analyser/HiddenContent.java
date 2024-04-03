package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface HiddenContent {


    default HiddenContentSelector selectAll() {
        return selectAll(false);
    }

    HiddenContentSelector selectAll(boolean mutable);

    boolean isNone();

    Map<Integer, ParameterizedType> hiddenContentTypes(ParameterizedType concreteType);

    default String niceHiddenContentTypes() {
        return niceHiddenContentTypes(null);
    }

    String niceHiddenContentTypes(ParameterizedType concreteType);

    static HiddenContentSelector selectAllCorrectForMutable(EvaluationContext evaluationContext,
                                                            ParameterizedType parameterizedType) {
        if (parameterizedType.isTypeParameter()) return HiddenContentSelector.All.INSTANCE;
        ParameterizedType formal = parameterizedType.typeInfo.asParameterizedType(evaluationContext.getAnalyserContext());
        HiddenContent hcFormal = from(formal);
        if (hcFormal.isNone()) {
            // the formal type has no type parameters. Given that we're in the context of a ->4-> link,
            // we must be dealing with the "All" side, and a type without type parameters
            DV immutableOfParameterizedType = evaluationContext.immutable(parameterizedType);
            if (immutableOfParameterizedType.isDelayed()) {
                return new HiddenContentSelector.Delayed(immutableOfParameterizedType.causesOfDelay());
            }
            boolean immutable = MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableOfParameterizedType);
            if (immutable) return HiddenContentSelector.None.INSTANCE;
            boolean mutable = MultiLevel.isMutable(immutableOfParameterizedType);
            return mutable ? HiddenContentSelector.All.MUTABLE_INSTANCE : HiddenContentSelector.All.INSTANCE;
        }
        HiddenContentImpl hci = (HiddenContentImpl) hcFormal;
        assert hci.sequence != null;
        Set<Integer> done = new HashSet<>();
        Map<Integer, Boolean> res = new HashMap<>();
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (IndexedType indexedType : hci.sequence) {
            Integer tpi = indexedType.isTypeParameter();
            if (tpi != null && done.add(tpi)) {
                ParameterizedType inConcrete = indexedType.find(parameterizedType);
                DV immutableDv = evaluationContext.immutable(inConcrete);
                if (immutableDv.isDelayed()) {
                    causesOfDelay = causesOfDelay.merge(immutableDv.causesOfDelay());
                } else {
                    boolean immutable = MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutableDv);
                    if (!immutable) {
                        boolean mutable = MultiLevel.isMutable(immutableDv);
                        res.put(tpi, mutable);
                    }
                }
            }
        }
        if (causesOfDelay.isDelayed()) return new HiddenContentSelector.Delayed(causesOfDelay);
        if (res.isEmpty()) return HiddenContentSelector.None.INSTANCE;
        return new HiddenContentSelector.CsSet(res);
    }

    static HiddenContent from(ParameterizedType pt) {
        AtomicInteger counter = new AtomicInteger();
        Map<ParameterizedType, Integer> typeParameterIndex = new HashMap<>();
        HiddenContentImpl withoutArrays = from(pt, typeParameterIndex, counter);
        if (pt.arrays > 0) {
            List<Integer> prefix = new ArrayList<>();
            for (int i = 0; i < pt.arrays; i++) prefix.add(-1);
            if (withoutArrays.wholeTypeIndex != null) {
                // arrays == 1 ~ Array<T> -> <0>
                // arrays == 2 ~ Array<Array<T>> -> <*0-0>
                prefix.set(prefix.size() - 1, 0);
                return new HiddenContentImpl(List.of(new IndexedType(pt.copyWithoutArrays(), prefix)));
            }
            if (withoutArrays.sequence != null) {
                // add prefix to each sequence, set array type
                return new HiddenContentImpl(withoutArrays.sequence.stream().map(it -> it.addPrefix(prefix)).toList());
            }
            return new HiddenContentImpl(List.of(new IndexedType(null, prefix)));
        }
        return withoutArrays;
    }

    private static HiddenContentImpl from(ParameterizedType pt,
                                          Map<ParameterizedType, Integer> typeParameterIndex,
                                          AtomicInteger counter) {
        if (pt.isTypeParameter()) {
            return new HiddenContentImpl(pt, pt.typeParameter.getIndex());
        }
        List<IndexedType> sequence = new ArrayList<>(pt.parameters.size());
        int countParameter = 1;
        for (ParameterizedType tp : pt.parameters) {
            if (tp.isTypeParameter()) {
                Integer index = typeParameterIndex.get(tp);
                if (index == null) {
                    int count = counter.getAndIncrement();
                    typeParameterIndex.put(tp, count);
                    index = count;
                }
                sequence.add(new IndexedType(tp, List.of(index)));
            } else if (!tp.parameters.isEmpty()) {
                HiddenContentImpl recursively = from(tp, typeParameterIndex, counter);
                if (recursively.sequence != null) {
                    for (IndexedType it : recursively.sequence) {
                        List<Integer> indices = Stream.concat(Stream.of(-countParameter), it.index.stream()).toList();
                        sequence.add(new IndexedType(it.parameterizedType, indices));
                    }
                } else if (recursively.wholeTypeIndex != null) {
                    throw new UnsupportedOperationException("this should not happen, we checked separately");
                }
            } else {
                sequence.add(new IndexedType(tp, List.of(-countParameter)));
            }
            countParameter++;
        }
        if (sequence.isEmpty()) {
            return (HiddenContentImpl) NONE;
        }
        return new HiddenContentImpl(List.copyOf(sequence));
    }

    record IndexedType(ParameterizedType parameterizedType, List<Integer> index) {
        private static int realIndex(int i) {
            return i >= 0 ? i : -i - 1;
        }

        @Override
        public String toString() {
            return index.stream().map(i -> i < 0 ? "*" + realIndex(i) : "" + i)
                    .collect(Collectors.joining("-"));
        }

        public Integer isTypeParameter() {
            if (index.isEmpty()) return null;
            int i = index.get(index.size() - 1);
            return i >= 0 ? i : null;
        }

        public Stream<Integer> typeParameterIndexStream() {
            if (index.isEmpty()) return Stream.of();
            int i = index.get(index.size() - 1);
            return i >= 0 ? Stream.of(i) : Stream.of();
        }

        public IndexedType addPrefix(List<Integer> prefix) {
            return new IndexedType(parameterizedType.copyWithArrays(prefix.size()),
                    Stream.concat(prefix.stream(), index.stream()).toList());
        }

        public ParameterizedType find(ParameterizedType concreteType) {
            ParameterizedType pt = concreteType;
            for (int i : index) {
                if (pt.arrays > 0) {
                    pt = pt.copyWithOneFewerArrays();
                } else if (pt.isTypeParameter()) {
                    break;
                } else {
                    pt = pt.parameters.get(realIndex(i));
                }
            }
            return pt.copyWithoutArrays();
        }
    }

    HiddenContent NONE = new HiddenContentImpl();

    class HiddenContentImpl implements HiddenContent {
        private final List<IndexedType> sequence;
        private final ParameterizedType wholeType;
        private final Integer wholeTypeIndex;

        // none
        private HiddenContentImpl() {
            sequence = null;
            wholeType = null;
            wholeTypeIndex = null;
        }

        @Override
        public boolean isNone() {
            return sequence == null && wholeType == null;
        }

        // type parameters of a real type
        private HiddenContentImpl(List<IndexedType> sequence) {
            assert !sequence.isEmpty();
            this.sequence = sequence;
            wholeType = null;
            wholeTypeIndex = null;
        }

        // a single type parameter
        private HiddenContentImpl(ParameterizedType wholeType, Integer wholeTypeIndex) {
            this.wholeTypeIndex = wholeTypeIndex;
            this.wholeType = wholeType;
            this.sequence = null;
        }

        @Override
        public HiddenContentSelector selectAll(boolean mutable) {
            if (sequence != null) {
                Map<Integer, Boolean> map = sequence.stream().flatMap(IndexedType::typeParameterIndexStream)
                        .distinct()
                        .collect(Collectors.toUnmodifiableMap(i -> i, i -> mutable));
                if (map.isEmpty()) {
                    return HiddenContentSelector.None.INSTANCE;
                }
                return new HiddenContentSelector.CsSet(map);
            }
            if (wholeTypeIndex != null) {
                return mutable ? HiddenContentSelector.All.MUTABLE_INSTANCE : HiddenContentSelector.All.INSTANCE;
            }
            return HiddenContentSelector.None.INSTANCE;
        }

        @Override
        public String toString() {
            if (wholeTypeIndex != null) {
                return "=" + wholeTypeIndex;
            }
            if (sequence != null) {
                return sequence.stream().map(IndexedType::toString).collect(Collectors.joining(",",
                        "<", ">"));
            }
            return "X";
        }

        @Override
        public Map<Integer, ParameterizedType> hiddenContentTypes(ParameterizedType concreteType) {
            if (wholeTypeIndex != null) {
                return Map.of(wholeTypeIndex, concreteType == null ? wholeType : concreteType);
            }
            if (sequence != null) {
                // the selector tells us where to find types
                Map<Integer, ParameterizedType> map = new HashMap<>();
                for (IndexedType it : sequence) {
                    int index = it.index.get(it.index.size() - 1);
                    if (index >= 0 && !map.containsKey(index)) {
                        ParameterizedType partOfConcreteType = concreteType == null ? it.parameterizedType
                                : it.find(concreteType);
                        map.put(index, partOfConcreteType);
                    }
                }
                return Map.copyOf(map);
            }
            return Map.of();
        }

        // for testing
        @Override
        public String niceHiddenContentTypes(ParameterizedType concreteType) {
            return hiddenContentTypes(concreteType).entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue()).sorted()
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }
}
