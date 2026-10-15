package com.familyfood.scoring.application;

import java.util.List;

/**
 * Which inputs the score needed, which were present, which were missing — so the UI can say
 * exactly what an {@code INSUFFICIENT_DATA} result is missing rather than "not enough data".
 */
public record Coverage(List<String> required, List<String> present, List<String> missing) {
    public static Coverage empty() {
        return new Coverage(List.of(), List.of(), List.of());
    }
}
