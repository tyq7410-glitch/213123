package dev.aurora.core;

public abstract class Setting<T> {
    public final String name;
    protected T value;

    protected Setting(String name, T value) { this.name = name; this.value = value; }

    public T get() { return value; }
    public abstract String asString();
    public abstract void fromString(String s);

    public static class Bool extends Setting<Boolean> {
        public Bool(String name, boolean def) { super(name, def); }
        public void toggle() { value = !value; }
        @Override public String asString() { return String.valueOf(value); }
        @Override public void fromString(String s) { value = Boolean.parseBoolean(s); }
    }

    public static class Num extends Setting<Double> {
        public final double min, max, step;
        public Num(String name, double def, double min, double max, double step) {
            super(name, def); this.min = min; this.max = max; this.step = step;
        }
        public int asInt() { return (int) Math.round(value); }
        public float asFloat() { return value.floatValue(); }
        public void setClamped(double v) {
            v = min + Math.round((v - min) / step) * step;
            value = Math.max(min, Math.min(max, v));
        }
        public String display() { return step >= 1 ? String.valueOf(asInt()) : String.format("%.2f", value); }
        @Override public String asString() { return String.valueOf(value); }
        @Override public void fromString(String s) { setClamped(Double.parseDouble(s)); }
    }

    public static class Mode extends Setting<String> {
        public final String[] modes;
        public Mode(String name, String def, String... modes) { super(name, def); this.modes = modes; }
        public boolean is(String m) { return value.equals(m); }
        public void cycle() { cycle(1); }
        public void cycle(int dir) {
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(value)) { value = modes[(i + dir + modes.length) % modes.length]; return; }
            }
            value = modes[0];
        }
        @Override public String asString() { return value; }
        @Override public void fromString(String s) { for (String m : modes) if (m.equals(s)) value = s; }
    }
}
