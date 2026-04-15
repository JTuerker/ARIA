package compiler;



import java.util.Arrays;


public final class ObjectArrayStrategy implements it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet.Strategy<Object[]> {
    @Override public int hashCode(Object[] a) {
        return (a == null) ? 0 : Arrays.deepHashCode(a);
    }
    @Override public boolean equals(Object[] a, Object[] b) {
        return a == b || (a != null && b != null && Arrays.deepEquals(a, b));
    }
}
