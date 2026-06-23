package com.atitai.posture.adapter.storage;

import com.atitai.posture.config.PostureProperties;
import com.atitai.posture.domain.StoredVideo;
import com.atitai.posture.port.VideoStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileVideoStorageAdapter implements VideoStoragePort {

    private final Path rootDirectory;

    public LocalFileVideoStorageAdapter(PostureProperties properties) {
        this.rootDirectory = Paths.get(properties.getStorage().getRootDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public StoredVideo store(String jobId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("videoFile is required");
        }

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        Path jobDirectory = rootDirectory.resolve("jobs").resolve(jobId);
        Path videoDirectory = jobDirectory.resolve("video");
        Path evidenceDirectory = jobDirectory.resolve("evidence");

        Files.createDirectories(videoDirectory);
        Files.createDirectories(evidenceDirectory);

        Path target = videoDirectory.resolve("source" + extension);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String relativePath = rootDirectory.relativize(target).toString().replace('\\', '/');
        return new StoredVideo(
            target.toAbsolutePath().normalize().toString(),
            relativePath,
            evidenceDirectory.toAbsolutePath().normalize().toString(),
            originalFilename
        );
    }

    private String normalizeFilename(String filename) {
        String candidate = StringUtils.hasText(filename) ? filename : "upload.mp4";
        return Paths.get(candidate).getFileName().toString();
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return ".mp4";
        }
        String extension = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,5}") ? extension : ".mp4";
    }
}
