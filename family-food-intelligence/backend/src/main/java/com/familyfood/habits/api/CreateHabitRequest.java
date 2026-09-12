package com.familyfood.habits.api;

import com.familyfood.habits.domain.HabitCadence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Bounds match the {@code habit} name CHECK, so an over-long name is a 400 and not a 500. */
public record CreateHabitRequest(
        @NotNull UUID memberId,
        @NotBlank @Size(max = 80) String name,
        @NotNull HabitCadence cadence) {
}
