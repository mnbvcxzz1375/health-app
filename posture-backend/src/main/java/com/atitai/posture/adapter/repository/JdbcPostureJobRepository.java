package com.atitai.posture.adapter.repository;

import com.atitai.posture.domain.CameraView;
import com.atitai.posture.domain.ExerciseType;
import com.atitai.posture.domain.JobStatus;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.port.PostureJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPostureJobRepository implements PostureJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPostureJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public PostureJob save(PostureJob job) {
        jdbcTemplate.update(
            "INSERT INTO posture_jobs "
                + "(id, user_id, exercise_type, camera_view, status, progress, fail_reason, video_path, original_filename, analysis_json, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "user_id = VALUES(user_id), "
                + "exercise_type = VALUES(exercise_type), "
                + "camera_view = VALUES(camera_view), "
                + "status = VALUES(status), "
                + "progress = VALUES(progress), "
                + "fail_reason = VALUES(fail_reason), "
                + "video_path = VALUES(video_path), "
                + "original_filename = VALUES(original_filename), "
                + "analysis_json = VALUES(analysis_json), "
                + "updated_at = VALUES(updated_at)",
            job.getId(),
            job.getUserId(),
            job.getExerciseType() == null ? null : job.getExerciseType().name(),
            job.getCameraView() == null ? null : job.getCameraView().name(),
            job.getStatus() == null ? null : job.getStatus().name(),
            job.getProgress(),
            job.getFailReason(),
            job.getVideoPath(),
            job.getOriginalFilename(),
            writeAnalysis(job.getAnalysis()),
            toTimestamp(job.getCreatedAt()),
            toTimestamp(job.getUpdatedAt())
        );
        return job;
    }

    @Override
    public Optional<PostureJob> findById(String jobId) {
        List<PostureJob> jobs = jdbcTemplate.query(
            "SELECT id, user_id, exercise_type, camera_view, status, progress, fail_reason, "
                + "video_path, original_filename, analysis_json, created_at, updated_at "
                + "FROM posture_jobs "
                + "WHERE id = ? "
                + "LIMIT 1",
            (rs, rowNum) -> {
                PostureJob job = new PostureJob();
                job.setId(rs.getString("id"));
                job.setUserId(rs.getString("user_id"));
                job.setExerciseType(ExerciseType.fromValue(rs.getString("exercise_type")));
                job.setCameraView(CameraView.fromValue(rs.getString("camera_view")));
                job.setStatus(JobStatus.valueOf(rs.getString("status")));
                job.setProgress(rs.getInt("progress"));
                job.setFailReason(rs.getString("fail_reason"));
                job.setVideoPath(rs.getString("video_path"));
                job.setOriginalFilename(rs.getString("original_filename"));
                job.setAnalysis(readAnalysis(rs.getString("analysis_json")));
                job.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
                job.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
                return job;
            },
            jobId
        );
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.get(0));
    }

    private String writeAnalysis(PostureAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(analysis);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize posture analysis", exception);
        }
    }

    private PostureAnalysis readAnalysis(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, PostureAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize posture analysis", exception);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? Timestamp.from(Instant.now()) : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
