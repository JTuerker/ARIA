package compiler;

@FunctionalInterface
public interface TransitionSink {
    void accept(Object[] target, String rateLabel);
}