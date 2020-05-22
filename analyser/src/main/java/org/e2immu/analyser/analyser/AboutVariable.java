package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.Map;

class AboutVariable {

    enum FieldReferenceState {
        EFFECTIVELY_FINAL_DELAYED,
        SINGLE_COPY,
        MULTI_COPY,
    }

    private final Map<VariableProperty, Integer> properties = new HashMap<>();

    @NotNull
    private Value currentValue;


    // accessible to the outside world, but not modified
    final Value initialValue;
    final Value resetValue;
    final AboutVariable localCopyOf;
    final Variable variable;
    final String name;

    final FieldReferenceState fieldReferenceState;

    AboutVariable(Variable variable, String name, AboutVariable localCopyOf, Value initialValue, Value resetValue,
                  FieldReferenceState fieldReferenceState) {
        this.localCopyOf = localCopyOf;
        this.initialValue = initialValue;
        this.currentValue = resetValue;
        this.resetValue = resetValue;
        this.variable = variable;
        this.name = name; // the value used to put it in the map
        this.fieldReferenceState = fieldReferenceState;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("props=").append(properties);
        if (currentValue != null) {
            sb.append(", currentValue=").append(currentValue);
        }
        return sb.toString();
    }

    Value getCurrentValue() {
        return currentValue;
    }

    AboutVariable localCopy() {
        AboutVariable av = new AboutVariable(variable, name, this, initialValue, currentValue, fieldReferenceState);
        av.properties.putAll(properties);
        return av;
    }

    boolean isLocalCopy() {
        return localCopyOf != null;
    }

    boolean isNotLocalCopy() {
        return localCopyOf == null;
    }

    int getProperty(VariableProperty variableProperty) {
        return properties.getOrDefault(variableProperty, Level.DELAY);
    }

    void setCurrentValue(Value value) {
        this.currentValue = value;
    }

    void setProperty(VariableProperty variableProperty, int value) {
        properties.put(variableProperty, value);
    }

    public Map<VariableProperty, Integer> properties() {
        return ImmutableMap.copyOf(properties);
    }

    public void markRead() {
        properties.remove(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT);
        int read = getProperty(VariableProperty.READ);
        setProperty(VariableProperty.READ, Level.nextLevelTrue(read, 1));
    }

    boolean haveProperty(VariableProperty variableProperty) {
        Integer i = properties.get(variableProperty);
        return i != null && i != Level.DELAY;
    }

    boolean isLocalVariable() {
        return variable instanceof LocalVariableReference;
    }
}
