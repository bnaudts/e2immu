package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.Value;

import java.util.Map;

/*
 can only be created as the single result value of a method

 will be substituted at any time in MethodCall
 */
public class InlineValue implements Value {

    public final MethodInfo methodInfo;
    public final Value value;

    public InlineValue(MethodInfo methodInfo, Value value) {
        this.methodInfo = methodInfo;
        this.value = value;
    }

    @Override
    public int order() {
        return ORDER_INLINE_METHOD;
    }

    @Override
    public int internalCompareTo(Value v) {
        InlineValue mv = (InlineValue) v;
        return methodInfo.distinguishingName().compareTo(mv.methodInfo.distinguishingName());
    }

    @Override
    public Value reEvaluate(Map<Value, Value> translation) {
        return value.reEvaluate(translation);
    }

}
