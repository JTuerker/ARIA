package compiler;

public final class CowObjectArray {
    private final Object[] base;   // ursprüngliche Referenz
    private Object[] copy;         // wird erst bei der ersten Änderung angelegt

    CowObjectArray(Object[] base) { this.base = base; }

    Object getAt(int idx) {
        return (copy != null ? copy : base)[idx];
    }

    void setAt(int idx, Object newVal) {
        Object cur = (copy != null ? copy : base)[idx];
        if (cur == newVal) return;             // keine Änderung nötig
        if (copy == null) copy = java.util.Arrays.copyOf(base, base.length); // erste Änderung -> einmalig kopieren
        copy[idx] =  newVal;
    }

    Object[] get() {
        return (copy != null ? copy : base);
    }
}