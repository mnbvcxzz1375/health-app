package com.atitai.posture.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum CameraView {
    SIDE,
    FRONT,
    ANGLED;

    @JsonCreator
    public static CameraView fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("cameraView is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return CameraView.valueOf(normalized);
    }
}

