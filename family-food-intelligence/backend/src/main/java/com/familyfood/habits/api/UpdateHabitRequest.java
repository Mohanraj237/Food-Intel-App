package com.familyfood.habits.api;

import com.familyfood.habits.domain.HabitCadence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Both editable fields are required, because the route replaces them. A partial update that
 * silently kept the old cadence would make "what does this habit expect" depend on the order of
 * past requests.
 */
public record UpdateHabitRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull HabitCadence cadence) {
}
