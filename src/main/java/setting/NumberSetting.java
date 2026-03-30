package com.yourname.backtrack.setting;

public class NumberSetting extends Setting {

    private double value;
    private final double min;
    private final double max;
    private final double increment;

    public NumberSetting(String name, double defaultValue, double min, double max, double increment) {
        this(name, defaultValue, min, max, increment, SettingGroup.MAIN);
    }

    public NumberSetting(String name, double defaultValue, double min, double max, double increment, SettingGroup group) {
        super(name, group);
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        if (value < min) {
            value = min;
        }

        if (value > max) {
            value = max;
        }

        this.value = value;
    }

    public void increase() {
        setValue(value + increment);
    }

    public void decrease() {
        setValue(value - increment);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getIncrement() {
        return increment;
    }
}
