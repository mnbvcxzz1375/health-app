package com.atitai.posture.domain;

public enum Severity {
    MAJOR(15),
    MEDIUM(8),
    MINOR(4);

    private final int penaltyPoints;

    Severity(int penaltyPoints) {
        this.penaltyPoints = penaltyPoints;
    }

    public int getPenaltyPoints() {
        return penaltyPoints;
    }
}

