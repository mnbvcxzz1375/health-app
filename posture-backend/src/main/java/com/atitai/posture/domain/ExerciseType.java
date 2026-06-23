package com.atitai.posture.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum ExerciseType {
    SQUAT,
    PUSH_UP,
    PLANK,
    LUNGE;

    @JsonCreator
    public static ExerciseType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("exerciseType is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ExerciseType.valueOf(normalized);
    }
}

