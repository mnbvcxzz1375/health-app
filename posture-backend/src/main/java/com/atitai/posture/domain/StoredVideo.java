package com.atitai.posture.domain;

public class StoredVideo {

    private final String absolutePath;
    private final String relativePath;
    private final String evidenceDirectory;
    private final String originalFilename;

    public StoredVideo(String absolutePath, String relativePath, String evidenceDirectory, String originalFilename) {
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
        this.evidenceDirectory = evidenceDirectory;
        this.originalFilename = originalFilename;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getEvidenceDirectory() {
        return evidenceDirectory;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }
}

