package io.github.some_example_name.model;

@FunctionalInterface
public interface MatchModifier {
    void apply(MatchConfig config, ArenaSide side);
}
