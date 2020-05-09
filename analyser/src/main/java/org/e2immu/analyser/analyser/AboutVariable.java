package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.Map;

class AboutVariable {
    private final Map<VariableProperty, Integer> properties = new HashMap<>();

    @NotNull
    private Value currentValue;


    // accessible to the outside world, but not modified
    final Value initialValue;
    final Value resetValue;
    final AboutVariable localCopyOf;
    final Variable variable;
    final String name;
    final boolean singleCopy;

    AboutVariable(Variable variable, String name, AboutVariable localCopyOf, Value initialValue, Value resetValue, boolean singleCopy) {
        this.localCopyOf = localCopyOf;
        this.initialValue = initialValue;
        this.currentValue = resetValue;
        this.resetValue = resetValue;
        this.variable = variable;
        this.name = name; // the value used to put it in the map
        this.singleCopy = singleCopy;
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

    Value getResetValue() {
        return resetValue;
    }

    AboutVariable localCopy() {
        AboutVariable av = new AboutVariable(variable, name, this, initialValue, currentValue, singleCopy);
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

    void removeProperty(VariableProperty variableProperty) {
        properties.remove(variableProperty);
    }

     void clearProperties() {
        properties.clear();
    }
}
