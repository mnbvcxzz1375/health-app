package com.ahealth.backend.device;

import com.ahealth.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RookService {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final JdbcTemplate jdbc;
  private final String clientUuid;
  private final String clientSecret;
  private final String baseUrl;

  public RookService(
      ObjectMapper objectMapper,
      JdbcTemplate jdbc,
      @Value("${ROOK_CLIENT_UUID:}") String clientUuid,
      @Value("${ROOK_CLIENT_SECRET:}") String clientSecret,
      @Value("${ROOK_BASE_URL:https://api.rook-connect.review}") String baseUrl
  ) {
    this.objectMapper = objectMapper;
    this.jdbc = jdbc;
    this.clientUuid = clientUuid == null ? "" : clientUuid.trim();
    this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  public boolean isConfigured() {
    return !clientUuid.isBlank() && !clientSecret.isBlank();
  }

  /** Get authorization URL for a data source (Garmin, Oura, Fitbit, etc.) */
  public RookDtos.DataSourceAuth getDataSourceAuthorizer(String userId, String dataSource) {
    JsonNode result = rookGet("/api/v1/user_id/" + userId + "/data_source/" + dataSource + "/authorizer");
    return new RookDtos.DataSourceAuth(
        result.path("data_source").asText(dataSource),
        result.path("authorized").asBoolean(false),
        result.path("authorization_url").asText("")
    );
  }

  /** Get all authorized data sources for a user */
  public RookDtos.AuthorizedSources getAuthorizedSources(String userId) {
    JsonNode result = rookGet("/api/v1/user_id/" + userId + "/data_sources/authorized");
    Map<String, Boolean> sources = new LinkedHashMap<>();
    JsonNode sourcesNode = result.path("sources");
    if (sourcesNode.isObject()) {
      sourcesNode.fields().forEachRemaining(e -> sources.put(e.getKey(), e.getValue().asBoolean(false)));
    }
    return new RookDtos.AuthorizedSources(result.path("user_id").asText(userId), sources);
  }

  /** Get physical health summary for a date */
  public RookDtos.PhysicalHealthSummary getPhysicalHealthSummary(String userId, String date) {
    JsonNode result = rookGet("/v2/processed_data/physical_health/summary?user_id=" + userId + "&date=" + date);
    return parsePhysicalHealth(result);
  }

  /** Get sleep health summary for a date */
  public RookDtos.SleepHealthSummary getSleepHealthSummary(String userId, String date) {
    JsonNode result = rookGet("/v2/processed_data/sleep_health/summary?user_id=" + userId + "&date=" + date);
    return parseSleepHealth(result);
  }

  /** Get activity events for a date */
  public List<RookDtos.ActivityEvent> getActivityEvents(String userId, String date) {
    JsonNode result = rookGet("/v2/processed_data/physical_health/events/activity?user_id=" + userId + "&date=" + date);
    List<RookDtos.ActivityEvent> events = new ArrayList<>();
    if (result.isArray()) {
      for (JsonNode item : result) {
        events.add(parseActivityEvent(item));
      }
    }
    return events;
  }

  /** Set user timezone */
  public void setUserTimezone(String userId, String timezone, String offset) {
    rookPost("/api/v1/user_id/" + userId + "/time_zone",
        Map.of("time_zone", timezone, "offset", offset));
  }

  // === Private helpers ===

  private JsonNode rookGet(String path) {
    ensureConfigured();
    try {
      String auth = Base64.getEncoder().encodeToString(
          (clientUuid + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(30))
          .header("Authorization", "Basic " + auth)
          .header("Accept", "application/json")
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() == 204) return objectMapper.createObjectNode();
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "ROOK API 调用失败，状态码：" + response.statusCode());
      }
      return objectMapper.readTree(response.body());
    } catch (ApiException e) { throw e; }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.BAD_GATEWAY, "ROOK 调用被中断"); }
      catch (IOException e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "ROOK 调用失败：" + e.getMessage()); }
  }

  private void rookPost(String path, Map<String, String> body) {
    ensureConfigured();
    try {
      String auth = Base64.getEncoder().encodeToString(
          (clientUuid + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(30))
          .header("Authorization", "Basic " + auth)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (Exception e) { /* best-effort */ }
  }

  private void ensureConfigured() {
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "未配置 ROOK_CLIENT_UUID/ROOK_CLIENT_SECRET");
    }
  }

  private RookDtos.PhysicalHealthSummary parsePhysicalHealth(JsonNode node) {
    JsonNode activity = node.path("activity");
    JsonNode calories = node.path("calories");
    JsonNode distance = node.path("distance");
    JsonNode hr = node.path("heart_rate");
    JsonNode oxi = node.path("oxygenation");
    JsonNode stress = node.path("stress");

    return new RookDtos.PhysicalHealthSummary(
        new RookDtos.PhysicalHealthSummary.ActivityData(
            activity.path("active_seconds").asInt(0), activity.path("inactive_seconds").asInt(0),
            activity.path("rest_seconds").asInt(0), activity.path("high_intensity_seconds").asInt(0),
            activity.path("medium_intensity_seconds").asInt(0), activity.path("low_intensity_seconds").asInt(0)),
        new RookDtos.PhysicalHealthSummary.CaloriesData(
            calories.path("bmr_kcal").asDouble(0), calories.path("expenditure_kcal").asDouble(0),
            calories.path("net_active_kcal").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.DistanceData(
            distance.path("steps").asInt(0), distance.path("active_steps").asInt(0),
            distance.path("floors_climbed").asInt(0), distance.path("elevation_meters").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.HeartRateData(
            hr.path("avg_bpm").asDouble(0), hr.path("max_bpm").asDouble(0),
            hr.path("min_bpm").asDouble(0), hr.path("resting_bpm").asDouble(0),
            hr.path("hrv_avg_rmssd").asDouble(0), hr.path("hrv_avg_sdnn").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.OxygenationData(
            oxi.path("avg_spo2").asDouble(0), oxi.path("vo2_max").asDouble(0)),
        new RookDtos.PhysicalHealthSummary.StressData(
            stress.path("avg_level").asDouble(0), stress.path("max_level").asDouble(0),
            stress.path("high_stress_duration_seconds").asInt(0),
            stress.path("medium_stress_duration_seconds").asInt(0),
            stress.path("low_stress_duration_seconds").asInt(0),
            stress.path("rest_duration_seconds").asInt(0))
    );
  }

  private RookDtos.SleepHealthSummary parseSleepHealth(JsonNode node) {
    JsonNode dur = node.path("duration");
    JsonNode scores = node.path("scores");
    JsonNode hr = node.path("heart_rate");
    JsonNode breathing = node.path("breathing");

    return new RookDtos.SleepHealthSummary(
        new RookDtos.SleepHealthSummary.DurationData(
            dur.path("sleep_start_datetime").asText(""), dur.path("sleep_end_datetime").asText(""),
            dur.path("total_sleep_duration_seconds").asInt(0), dur.path("time_in_bed_seconds").asInt(0),
            dur.path("light_sleep_duration_seconds").asInt(0), dur.path("rem_sleep_duration_seconds").asInt(0),
            dur.path("deep_sleep_duration_seconds").asInt(0), dur.path("time_to_fall_asleep_seconds").asInt(0),
            dur.path("time_awake_during_sleep_seconds").asInt(0)),
        new RookDtos.SleepHealthSummary.ScoresData(
            scores.path("quality_rating").asInt(0), scores.path("efficiency").asInt(0),
            scores.path("continuity_score").asInt(0)),
        new RookDtos.PhysicalHealthSummary.HeartRateData(
            hr.path("avg_bpm").asDouble(0), hr.path("max_bpm").asDouble(0),
            hr.path("min_bpm").asDouble(0), hr.path("resting_bpm").asDouble(0),
            hr.path("hrv_avg_rmssd").asDouble(0), hr.path("hrv_avg_sdnn").asDouble(0)),
        new RookDtos.SleepHealthSummary.BreathingData(
            breathing.path("breaths_avg_per_min").asDouble(0),
            breathing.path("snoring_events_count").asInt(0),
            breathing.path("spo2_avg").asDouble(0))
    );
  }

  /**
   * Sync ROOK physical + sleep data to monitor_records table.
   * This feeds the health scoring engine with real device data.
   */
  public Map<String, Object> syncToMonitorRecords(long uid) {
    String today = java.time.LocalDate.now().toString();

    RookDtos.PhysicalHealthSummary physical = getPhysicalHealthSummary(String.valueOf(uid), today);
    RookDtos.SleepHealthSummary sleep = getSleepHealthSummary(String.valueOf(uid), today);

    int hr = (int) physical.heartRate().restingBpm();
    if (hr <= 0) hr = (int) physical.heartRate().avgBpm();
    int sleepScore = sleep.scores().qualityRating() * 20; // 1-5 → 20-100
    double deepHours = sleep.duration().deepSleepSeconds() / 3600.0;
    int awakeTimes = sleep.duration().timeAwakeDuringSleepSeconds() > 0 ? 1 : 0;
    int stressScore = (int) (physical.stress().avgLevel() * 10); // 0-10 → 0-100
    int hrv = (int) physical.heartRate().hrvAvgRmssd();
    int steps = physical.distance().steps();
    double vo2 = physical.oxygenation().vo2Max();

    // Insert or update today's record
    Integer existing = jdbc.queryForObject(
        "SELECT COUNT(*) FROM monitor_records WHERE DATE(recorded_at) = CURDATE() AND HOUR(recorded_at) = HOUR(NOW())",
        Integer.class);

    if (existing != null && existing > 0) {
      jdbc.update(
          "UPDATE monitor_records SET hr=?, sleep_score=?, deep_sleep_hours=?, awake_times=?, "
          + "stress_score=?, hrv_millis=?, steps=?, vo2_max=? "
          + "WHERE DATE(recorded_at)=CURDATE() AND HOUR(recorded_at)=HOUR(NOW())",
          hr, sleepScore, deepHours, awakeTimes, stressScore, hrv, steps, vo2);
    } else {
      jdbc.update(
          "INSERT INTO monitor_records(recorded_at, hr, sleep_score, deep_sleep_hours, awake_times, "
          + "stress_score, hrv_millis, steps, vo2_max) VALUES(NOW(), ?, ?, ?, ?, ?, ?, ?, ?)",
          hr, sleepScore, deepHours, awakeTimes, stressScore, hrv, steps, vo2);
    }

    return Map.of(
        "synced", true,
        "hr", hr, "sleepScore", sleepScore, "stressScore", stressScore,
        "hrv", hrv, "steps", steps, "vo2Max", vo2,
        "deepSleepHours", deepHours
    );
  }

  private RookDtos.ActivityEvent parseActivityEvent(JsonNode node) {
    JsonNode hr = node.path("heart_rate");
    JsonNode mv = node.path("movement");
    return new RookDtos.ActivityEvent(
        node.path("activity_type_name").asText("unknown"),
        node.path("duration_seconds").asInt(0),
        node.path("start_datetime").asText(""), node.path("end_datetime").asText(""),
        node.path("strain_level").asDouble(0),
        new RookDtos.PhysicalHealthSummary.HeartRateData(
            hr.path("avg_bpm").asDouble(0), hr.path("max_bpm").asDouble(0),
            hr.path("min_bpm").asDouble(0), hr.path("resting_bpm").asDouble(0),
            hr.path("hrv_avg_rmssd").asDouble(0), hr.path("hrv_avg_sdnn").asDouble(0)),
        new RookDtos.ActivityEvent.MovementData(
            mv.path("steps").asInt(0), mv.path("avg_pace").asDouble(0),
            mv.path("max_pace").asDouble(0), mv.path("avg_speed").asDouble(0))
    );
  }
}
