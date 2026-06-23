package com.atitai.posture.domain;

public class Landmark {

    private double x;
    private double y;
    private double z;
    private double visibility;
    private double presence;

    public Landmark() {
    }

    public Landmark(double x, double y, double z, double visibility, double presence) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.visibility = visibility;
        this.presence = presence;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public double getVisibility() {
        return visibility;
    }

    public void setVisibility(double visibility) {
        this.visibility = visibility;
    }

    public double getPresence() {
        return presence;
    }

    public void setPresence(double presence) {
        this.presence = presence;
    }
}

