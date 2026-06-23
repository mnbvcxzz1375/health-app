package com.ahealth.backend.medication;

import com.ahealth.backend.ai.DashScopeService;
import com.ahealth.backend.common.ApiException;
import com.ahealth.backend.common.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MedicationService {
  private static final String MEDICATION_RECOGNITION_SYSTEM_PROMPT = """
      你是药盒文字结构化提取助手。
      你只能依据当前上传图片中肉眼可见的文字填写字段，不允许使用文件名，不允许依赖常识推测，不允许编造内容。
      如果某个字段无法从图片中确认，请返回空字符串或 null。
      请只返回 JSON，不要返回 Markdown，不要解释。
      固定返回结构为：
      {"items":[{"name":"","alias":"","dosageValue":null,"dosageUnit":"","usage":"","notes":"","photoUrl":"","sourceText":""}]}
      其中 dosageUnit 只能是 片、粒、毫升、滴、袋 之一；
      usage 只能是 饭前、饭后、随餐、睡前、按需 之一；
      sourceText 需要填写你确实从图片里读到的关键文字片段。
      """;
  private static final String MEDICATION_RECOGNITION_USER_PROMPT = """
      请对本次上传的全部图片一次性完成识别。
      如果多张图片属于同一种药，请合并为一条 items；
      如果图片中有多种不同药品，请逐条返回。
      需要提取并返回的字段只有：药品名称 name、口语别名 alias、单次剂量 dosageValue、剂量单位 dosageUnit、服用方式 usage、注意事项 notes、图片地址 photoUrl、识别依据 sourceText。
      如果图中同时出现中文和英文药名，name 优先返回中文药名；alias 只在图中明确出现别名、品牌名或口语名称时再填写。
      所有字段都只能来自图片可见文字，不确定就留空，不要用文件名、外部知识或推测补全。
      """;
  private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}$");
  private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

  private final JdbcTemplate jdbcTemplate;
  private final DashScopeService dashScopeService;
  private final String customMedicationRecognizeUrl;
  private final RestTemplate restTemplate;

  public MedicationService(
      JdbcTemplate jdbcTemplate,
      DashScopeService dashScopeService,
      @Value("${custom.medication.recognize-url:}") String customMedicationRecognizeUrl
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.dashScopeService = dashScopeService;
    this.customMedicationRecognizeUrl = customMedicationRecognizeUrl == null ? "" : customMedicationRecognizeUrl.trim();
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(10_000);
    requestFactory.setReadTimeout(120_000);
    this.restTemplate = new RestTemplate(requestFactory);
  }

  public List<MedicationDtos.MedicationItem> listMedications() {
    long userId = CurrentUser.requireUserId();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        SELECT id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url,
               enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled
        FROM medications
        WHERE user_id = ?
        ORDER BY updated_at DESC, id DESC
        """,
        userId
    );

    List<Long> medicationIds = rows.stream()
        .map(row -> longValue(row.get("id")))
        .toList();

    Map<Long, List<MedicationDtos.MedicationReminder>> reminderMap = new HashMap<>();
    if (!medicationIds.isEmpty()) {
      String placeholders = String.join(",", java.util.Collections.nCopies(medicationIds.size(), "?"));
      List<Object> params = new ArrayList<>();
      params.add(userId);
      params.addAll(medicationIds);
      List<Map<String, Object>> reminderRows = jdbcTemplate.queryForList(
          """
          SELECT id, medication_id, reminder_time, enabled
          FROM medication_reminders
          WHERE user_id = ? AND medication_id IN (%s)
          ORDER BY reminder_time ASC, id ASC
          """.formatted(placeholders),
          params.toArray()
      );
      for (Map<String, Object> reminderRow : reminderRows) {
        long medicationId = longValue(reminderRow.get("medication_id"));
        reminderMap.computeIfAbsent(medicationId, ignored -> new ArrayList<>()).add(
            new MedicationDtos.MedicationReminder(
                longValue(reminderRow.get("id")),
                stringValue(reminderRow.get("reminder_time")),
                boolValue(reminderRow.get("enabled"))
            )
        );
      }
    }

    return rows.stream().map(row -> mapMedication(row, reminderMap.getOrDefault(longValue(row.get("id")), List.of()))).toList();
  }

  @Transactional
  public MedicationDtos.MedicationItem createMedication(MedicationDtos.MedicationSaveRequest request) {
    long userId = CurrentUser.requireUserId();
    validateMedicationName(request.name());
    jdbcTemplate.update(
        """
        INSERT INTO medications
          (user_id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url,
           enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        """,
        userId,
        request.name().trim(),
        stringValue(request.alias()),
        dosageValue(request.dosageValue()),
        dosageUnit(request.dosageUnit()),
        usageLabel(request.usage()),
        stringValue(request.notes()),
        stringValue(request.photoUrl()),
        truthy(request.enableOcr()),
        truthy(request.enableYolo()),
        stringValue(request.ocrEndpoint()),
        stringValue(request.yoloEndpoint()),
        truthyDefault(request.enabled(), true)
    );
    Long medicationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    replaceMedicationReminders(userId, medicationId == null ? 0L : medicationId, request.reminders());
    return getMedicationById(userId, medicationId == null ? 0L : medicationId);
  }

  @Transactional
  public MedicationDtos.MedicationItem updateMedication(long id, MedicationDtos.MedicationSaveRequest request) {
    long userId = CurrentUser.requireUserId();
    validateMedicationName(request.name());
    int updated = jdbcTemplate.update(
        """
        UPDATE medications
        SET name = ?, alias = ?, dosage_value = ?, dosage_unit = ?, usage_label = ?, notes = ?, photo_url = ?,
            enable_ocr = ?, enable_yolo = ?, ocr_endpoint = ?, yolo_endpoint = ?, enabled = ?, updated_at = NOW()
        WHERE id = ? AND user_id = ?
        """,
        request.name().trim(),
        stringValue(request.alias()),
        dosageValue(request.dosageValue()),
        dosageUnit(request.dosageUnit()),
        usageLabel(request.usage()),
        stringValue(request.notes()),
        stringValue(request.photoUrl()),
        truthy(request.enableOcr()),
        truthy(request.enableYolo()),
        stringValue(request.ocrEndpoint()),
        stringValue(request.yoloEndpoint()),
        truthyDefault(request.enabled(), true),
        id,
        userId
    );
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "药品不存在");
    }
    replaceMedicationReminders(userId, id, request.reminders());
    return getMedicationById(userId, id);
  }

  @Transactional
  public Map<String, Object> toggleMedication(long id) {
    long userId = CurrentUser.requireUserId();
    Boolean enabled = queryForBoolean(
        "SELECT enabled FROM medications WHERE id = ? AND user_id = ?",
        id,
        userId
    );
    if (enabled == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "药品不存在");
    }
    boolean next = !enabled;
    jdbcTemplate.update(
        "UPDATE medications SET enabled = ?, updated_at = NOW() WHERE id = ? AND user_id = ?",
        next ? 1 : 0,
        id,
        userId
    );
    return Map.of("id", id, "enabled", next);
  }

  @Transactional
  public Map<String, Boolean> deleteMedication(long id) {
    long userId = CurrentUser.requireUserId();
    jdbcTemplate.update("DELETE FROM medication_reminders WHERE medication_id = ? AND user_id = ?", id, userId);
    int updated = jdbcTemplate.update("DELETE FROM medications WHERE id = ? AND user_id = ?", id, userId);
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "药品不存在");
    }
    return Map.of("success", true);
  }

  public MedicationDtos.MedicationRecognitionBatchResult recognize(MultipartFile[] files) {
    if (files == null || files.length == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请先上传药盒图片");
    }
    List<MedicationDtos.MedicationRecognitionResult> items = Arrays.stream(files)
        .filter(Objects::nonNull)
        .map(file -> {
          String source = file.getOriginalFilename() == null ? "未命名图片" : file.getOriginalFilename();
          String name = source.replaceFirst("\\.[^.]+$", "");
          return new MedicationDtos.MedicationRecognitionResult(
              name,
              "",
              null,
              "",
              "",
              "",
              "",
              0.88,
              name
          );
        })
        .toList();
    return new MedicationDtos.MedicationRecognitionBatchResult(items, 0.88);
  }

  public MedicationDtos.MedicationRecognitionBatchResult recognizeByCustomModel(MultipartFile[] files) {
    if (files == null || files.length == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请先上传药盒图片");
    }
    if (customMedicationRecognizeUrl.isBlank()) {
      throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "未配置 CUSTOM_MEDICATION_RECOGNIZE_URL");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("scene", "medication_recognition");

    try {
      for (MultipartFile file : files) {
        if (file == null || file.isEmpty()) {
          continue;
        }
        body.add("files", new MultipartFileResource(file));
      }

      HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
      MedicationDtos.MedicationRecognitionBatchResult response = restTemplate.postForObject(
          customMedicationRecognizeUrl,
          entity,
          MedicationDtos.MedicationRecognitionBatchResult.class
      );
      if (response == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "自定义药品识别未返回结果");
      }
      return response;
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "药品图片读取失败");
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "自定义药品识别接口调用失败");
    }
  }

  public MedicationDtos.MedicationRecognitionBatchResult recognizeByModel(MultipartFile[] files) {
    MultipartFile[] normalizedFiles = Arrays.stream(files == null ? new MultipartFile[0] : files)
        .filter(file -> file != null && !file.isEmpty())
        .toArray(MultipartFile[]::new);
    if (normalizedFiles.length == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请先上传药盒图片");
    }

    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of("type", "text", "text", MEDICATION_RECOGNITION_USER_PROMPT));
    content.addAll(dashScopeService.toImageBlocks(normalizedFiles));

    JsonNode payload = dashScopeService.requestJson(
        MEDICATION_RECOGNITION_SYSTEM_PROMPT,
        content,
        dashScopeService.visionModel(),
        0.1,
        "药品识别"
    );
    return normalizeMedicationRecognitionBatch(payload);
  }

  public List<MedicationDtos.MedicationAlarm> listAlarms() {
    long userId = CurrentUser.requireUserId();
    List<Map<String, Object>> groups = jdbcTemplate.queryForList(
        """
        SELECT id, alarm_time, enabled
        FROM medication_alarm_groups
        WHERE user_id = ?
        ORDER BY alarm_time ASC, id ASC
        """,
        userId
    );
    List<MedicationDtos.MedicationAlarm> alarms = new ArrayList<>();
    for (Map<String, Object> group : groups) {
      long alarmId = longValue(group.get("id"));
      alarms.add(new MedicationDtos.MedicationAlarm(
          alarmId,
          stringValue(group.get("alarm_time")),
          boolValue(group.get("enabled")),
          listAlarmDrugs(alarmId)
      ));
    }
    return alarms;
  }

  @Transactional
  public MedicationDtos.MedicationAlarm createAlarm(MedicationDtos.MedicationAlarmSaveRequest request) {
    long userId = CurrentUser.requireUserId();
    validateAlarmRequest(request);
    Integer existingCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM medication_alarm_groups WHERE user_id = ? AND alarm_time = ?",
        Integer.class,
        userId,
        request.time()
    );
    if (existingCount != null && existingCount > 0) {
      throw new ApiException(HttpStatus.CONFLICT, "该提醒时间已存在");
    }
    jdbcTemplate.update(
        """
        INSERT INTO medication_alarm_groups (user_id, alarm_time, enabled, created_at, updated_at)
        VALUES (?, ?, ?, NOW(), NOW())
        """,
        userId,
        request.time(),
        truthyDefault(request.enabled(), true)
    );
    Long alarmId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    List<Long> medicationIds = persistAlarmDrugs(userId, request.medications());
    replaceAlarmItems(alarmId == null ? 0L : alarmId, medicationIds);
    syncReminderRows(userId, request.time(), medicationIds, truthyDefault(request.enabled(), true) == 1);
    return getAlarmById(userId, alarmId == null ? 0L : alarmId);
  }

  @Transactional
  public MedicationDtos.MedicationAlarm updateAlarm(long id, MedicationDtos.MedicationAlarmSaveRequest request) {
    long userId = CurrentUser.requireUserId();
    validateAlarmRequest(request);
    MedicationDtos.MedicationAlarm existing = getAlarmById(userId, id);
    Integer duplicateCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM medication_alarm_groups WHERE user_id = ? AND alarm_time = ? AND id <> ?",
        Integer.class,
        userId,
        request.time(),
        id
    );
    if (duplicateCount != null && duplicateCount > 0) {
      throw new ApiException(HttpStatus.CONFLICT, "该提醒时间已存在");
    }
    jdbcTemplate.update(
        """
        UPDATE medication_alarm_groups
        SET alarm_time = ?, enabled = ?, updated_at = NOW()
        WHERE id = ? AND user_id = ?
        """,
        request.time(),
        truthyDefault(request.enabled(), true),
        id,
        userId
    );
    List<Long> previousMedicationIds = existing.medications().stream().map(MedicationDtos.MedicationAlarmDrug::id).toList();
    List<Long> medicationIds = persistAlarmDrugs(userId, request.medications());
    jdbcTemplate.update("DELETE FROM medication_alarm_items WHERE alarm_id = ?", id);
    replaceAlarmItems(id, medicationIds);
    if (!previousMedicationIds.isEmpty()) {
      String placeholders = String.join(",", java.util.Collections.nCopies(previousMedicationIds.size(), "?"));
      List<Object> params = new ArrayList<>();
      params.add(userId);
      params.add(existing.time());
      params.addAll(previousMedicationIds);
      jdbcTemplate.update(
          """
          DELETE FROM medication_reminders
          WHERE user_id = ? AND reminder_time = ? AND medication_id IN (%s)
          """.formatted(placeholders),
          params.toArray()
      );
    }
    syncReminderRows(userId, request.time(), medicationIds, truthyDefault(request.enabled(), true) == 1);
    cleanupOrphanMedications(userId, previousMedicationIds);
    return getAlarmById(userId, id);
  }

  @Transactional
  public Map<String, Object> toggleAlarm(long id) {
    long userId = CurrentUser.requireUserId();
    MedicationDtos.MedicationAlarm alarm = getAlarmById(userId, id);
    boolean nextEnabled = !alarm.enabled();
    jdbcTemplate.update(
        "UPDATE medication_alarm_groups SET enabled = ?, updated_at = NOW() WHERE id = ? AND user_id = ?",
        nextEnabled ? 1 : 0,
        id,
        userId
    );
    List<Long> medicationIds = alarm.medications().stream().map(MedicationDtos.MedicationAlarmDrug::id).toList();
    updateReminderEnabled(userId, alarm.time(), medicationIds, nextEnabled);
    return Map.of("id", id, "enabled", nextEnabled);
  }

  @Transactional
  public Map<String, Boolean> deleteAlarm(long id) {
    long userId = CurrentUser.requireUserId();
    MedicationDtos.MedicationAlarm alarm = getAlarmById(userId, id);
    List<Long> medicationIds = alarm.medications().stream().map(MedicationDtos.MedicationAlarmDrug::id).toList();
    if (!medicationIds.isEmpty()) {
      String placeholders = String.join(",", java.util.Collections.nCopies(medicationIds.size(), "?"));
      List<Object> params = new ArrayList<>();
      params.add(userId);
      params.add(alarm.time());
      params.addAll(medicationIds);
      jdbcTemplate.update(
          """
          DELETE FROM medication_reminders
          WHERE user_id = ? AND reminder_time = ? AND medication_id IN (%s)
          """.formatted(placeholders),
          params.toArray()
      );
    }
    jdbcTemplate.update("DELETE FROM medication_alarm_items WHERE alarm_id = ?", id);
    jdbcTemplate.update("DELETE FROM medication_alarm_groups WHERE id = ? AND user_id = ?", id, userId);
    cleanupOrphanMedications(userId, medicationIds);
    return Map.of("success", true);
  }

  private MedicationDtos.MedicationAlarm getAlarmById(long userId, long id) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT id, alarm_time, enabled FROM medication_alarm_groups WHERE id = ? AND user_id = ? LIMIT 1",
        id,
        userId
    );
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "提醒不存在");
    }
    Map<String, Object> row = rows.get(0);
    return new MedicationDtos.MedicationAlarm(
        longValue(row.get("id")),
        stringValue(row.get("alarm_time")),
        boolValue(row.get("enabled")),
        listAlarmDrugs(id)
    );
  }

  private List<MedicationDtos.MedicationAlarmDrug> listAlarmDrugs(long alarmId) {
    return jdbcTemplate.query(
        """
        SELECT m.id, m.name, m.alias, m.dosage_value, m.dosage_unit, m.usage_label, m.notes, m.photo_url,
               m.enable_ocr, m.enable_yolo, m.ocr_endpoint, m.yolo_endpoint, m.enabled
        FROM medication_alarm_items mai
        JOIN medications m ON m.id = mai.medication_id
        WHERE mai.alarm_id = ?
        ORDER BY mai.sort_order ASC, mai.id ASC
        """,
        (rs, rowNum) -> new MedicationDtos.MedicationAlarmDrug(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("alias"),
            rs.getInt("dosage_value"),
            rs.getString("dosage_unit"),
            rs.getString("usage_label"),
            rs.getString("notes"),
            rs.getString("photo_url"),
            rs.getBoolean("enable_ocr"),
            rs.getBoolean("enable_yolo"),
            rs.getString("ocr_endpoint"),
            rs.getString("yolo_endpoint"),
            rs.getBoolean("enabled")
        ),
        alarmId
    );
  }

  private MedicationDtos.MedicationItem getMedicationById(long userId, long id) {
    try {
      Map<String, Object> row = jdbcTemplate.queryForMap(
          """
          SELECT id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url,
                 enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled
          FROM medications
          WHERE id = ? AND user_id = ?
          """,
          id,
          userId
      );
      List<MedicationDtos.MedicationReminder> reminders = jdbcTemplate.query(
          """
          SELECT id, reminder_time, enabled
          FROM medication_reminders
          WHERE medication_id = ? AND user_id = ?
          ORDER BY reminder_time ASC, id ASC
          """,
          (rs, rowNum) -> new MedicationDtos.MedicationReminder(
              rs.getLong("id"),
              rs.getString("reminder_time"),
              rs.getBoolean("enabled")
          ),
          id,
          userId
      );
      return mapMedication(row, reminders);
    } catch (EmptyResultDataAccessException exception) {
      throw new ApiException(HttpStatus.NOT_FOUND, "药品不存在");
    }
  }

  private MedicationDtos.MedicationItem mapMedication(
      Map<String, Object> row,
      List<MedicationDtos.MedicationReminder> reminders
  ) {
    return new MedicationDtos.MedicationItem(
        longValue(row.get("id")),
        stringValue(row.get("name")),
        stringValue(row.get("alias")),
        intValue(row.get("dosage_value"), 1),
        stringValue(row.get("dosage_unit")),
        stringValue(row.get("usage_label")),
        stringValue(row.get("notes")),
        stringValue(row.get("photo_url")),
        boolValue(row.get("enable_ocr")),
        boolValue(row.get("enable_yolo")),
        stringValue(row.get("ocr_endpoint")),
        stringValue(row.get("yolo_endpoint")),
        boolValue(row.get("enabled")),
        reminders
    );
  }

  private void replaceMedicationReminders(long userId, long medicationId, List<MedicationDtos.MedicationReminderInput> reminders) {
    jdbcTemplate.update("DELETE FROM medication_reminders WHERE medication_id = ? AND user_id = ?", medicationId, userId);
    if (reminders == null || reminders.isEmpty()) {
      return;
    }
    for (MedicationDtos.MedicationReminderInput reminder : reminders) {
      if (!isValidTime(reminder.time())) {
        continue;
      }
      jdbcTemplate.update(
          """
          INSERT INTO medication_reminders (medication_id, user_id, reminder_time, enabled, created_at)
          VALUES (?, ?, ?, ?, NOW())
          """,
          medicationId,
          userId,
          reminder.time(),
          truthyDefault(reminder.enabled(), true)
      );
    }
  }

  private List<Long> persistAlarmDrugs(long userId, List<MedicationDtos.MedicationAlarmDrugInput> medications) {
    List<Long> medicationIds = new ArrayList<>();
    for (MedicationDtos.MedicationAlarmDrugInput medication : medications) {
      medicationIds.add(upsertAlarmDrug(userId, medication));
    }
    return medicationIds;
  }

  private long upsertAlarmDrug(long userId, MedicationDtos.MedicationAlarmDrugInput medication) {
    if (medication.id() != null && medication.id() > 0) {
      jdbcTemplate.update(
          """
          UPDATE medications
          SET name = ?, alias = ?, dosage_value = ?, dosage_unit = ?, usage_label = ?, notes = ?, photo_url = ?,
              enable_ocr = ?, enable_yolo = ?, ocr_endpoint = ?, yolo_endpoint = ?, enabled = ?, updated_at = NOW()
          WHERE id = ? AND user_id = ?
          """,
          requiredName(medication.name()),
          stringValue(medication.alias()),
          dosageValue(medication.dosageValue()),
          dosageUnit(medication.dosageUnit()),
          usageLabel(medication.usage()),
          stringValue(medication.notes()),
          stringValue(medication.photoUrl()),
          truthy(medication.enableOcr()),
          truthy(medication.enableYolo()),
          stringValue(medication.ocrEndpoint()),
          stringValue(medication.yoloEndpoint()),
          truthyDefault(medication.enabled(), true),
          medication.id(),
          userId
      );
      return medication.id();
    }
    jdbcTemplate.update(
        """
        INSERT INTO medications
          (user_id, name, alias, dosage_value, dosage_unit, usage_label, notes, photo_url,
           enable_ocr, enable_yolo, ocr_endpoint, yolo_endpoint, enabled, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        """,
        userId,
        requiredName(medication.name()),
        stringValue(medication.alias()),
        dosageValue(medication.dosageValue()),
        dosageUnit(medication.dosageUnit()),
        usageLabel(medication.usage()),
        stringValue(medication.notes()),
        stringValue(medication.photoUrl()),
        truthy(medication.enableOcr()),
        truthy(medication.enableYolo()),
        stringValue(medication.ocrEndpoint()),
        stringValue(medication.yoloEndpoint()),
        truthyDefault(medication.enabled(), true)
    );
    Long medicationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return medicationId == null ? 0L : medicationId;
  }

  private void replaceAlarmItems(long alarmId, List<Long> medicationIds) {
    for (int index = 0; index < medicationIds.size(); index += 1) {
      jdbcTemplate.update(
          """
          INSERT INTO medication_alarm_items (alarm_id, medication_id, sort_order, created_at)
          VALUES (?, ?, ?, NOW())
          """,
          alarmId,
          medicationIds.get(index),
          index
      );
    }
  }

  private void syncReminderRows(long userId, String time, List<Long> medicationIds, boolean enabled) {
    for (Long medicationId : medicationIds) {
      jdbcTemplate.update(
          """
          INSERT INTO medication_reminders (medication_id, user_id, reminder_time, enabled, created_at)
          VALUES (?, ?, ?, ?, NOW())
          """,
          medicationId,
          userId,
          time,
          enabled ? 1 : 0
      );
    }
  }

  private void updateReminderEnabled(long userId, String time, List<Long> medicationIds, boolean enabled) {
    if (medicationIds.isEmpty()) {
      return;
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(medicationIds.size(), "?"));
    List<Object> params = new ArrayList<>();
    params.add(enabled ? 1 : 0);
    params.add(userId);
    params.add(time);
    params.addAll(medicationIds);
    jdbcTemplate.update(
        """
        UPDATE medication_reminders
        SET enabled = ?
        WHERE user_id = ? AND reminder_time = ? AND medication_id IN (%s)
        """.formatted(placeholders),
        params.toArray()
    );
  }

  private void cleanupOrphanMedications(long userId, List<Long> medicationIds) {
    for (Long medicationId : medicationIds) {
      Integer alarmCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM medication_alarm_items mai
          JOIN medication_alarm_groups mag ON mag.id = mai.alarm_id
          WHERE mai.medication_id = ? AND mag.user_id = ?
          """,
          Integer.class,
          medicationId,
          userId
      );
      Integer reminderCount = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM medication_reminders WHERE medication_id = ? AND user_id = ?",
          Integer.class,
          medicationId,
          userId
      );
      if ((alarmCount == null || alarmCount == 0) && (reminderCount == null || reminderCount == 0)) {
        jdbcTemplate.update("DELETE FROM medications WHERE id = ? AND user_id = ?", medicationId, userId);
      }
    }
  }

  private void validateMedicationName(String name) {
    if (name == null || name.trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "药品名称不能为空");
    }
  }

  private void validateAlarmRequest(MedicationDtos.MedicationAlarmSaveRequest request) {
    if (!isValidTime(request.time())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "提醒时间格式不正确");
    }
    if (request.medications() == null || request.medications().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请至少配置一个药品");
    }
  }

  private boolean isValidTime(String time) {
    return time != null && TIME_PATTERN.matcher(time).matches();
  }

  private String requiredName(String name) {
    if (name == null || name.trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "药品名称不能为空");
    }
    return name.trim();
  }

  private int dosageValue(Integer value) {
    return value == null || value <= 0 ? 1 : value;
  }

  private String dosageUnit(String value) {
    return value == null || value.isBlank() ? "片" : value.trim();
  }

  private String usageLabel(String value) {
    return value == null || value.isBlank() ? "饭后" : value.trim();
  }

  private int truthy(Boolean value) {
    return Boolean.TRUE.equals(value) ? 1 : 0;
  }

  private int truthyDefault(Boolean value, boolean fallback) {
    if (value == null) {
      return fallback ? 1 : 0;
    }
    return Boolean.TRUE.equals(value) ? 1 : 0;
  }

  private boolean boolValue(Object value) {
    return value instanceof Number number && number.intValue() == 1;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private int intValue(Object value, int fallback) {
    return value instanceof Number number ? number.intValue() : fallback;
  }

  private Boolean queryForBoolean(String sql, Object... args) {
    try {
      Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
      return result != null && result == 1;
    } catch (EmptyResultDataAccessException exception) {
      return null;
    }
  }

  private static final class MultipartFileResource extends ByteArrayResource {
    private final String filename;

    private MultipartFileResource(MultipartFile file) throws IOException {
      super(file.getBytes());
      this.filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }

  private MedicationDtos.MedicationRecognitionBatchResult normalizeMedicationRecognitionBatch(JsonNode payload) {
    JsonNode itemsNode = payload.path("items");
    List<MedicationDtos.MedicationRecognitionResult> items = new ArrayList<>();
    if (itemsNode.isArray()) {
      for (JsonNode itemNode : itemsNode) {
        items.add(normalizeMedicationRecognitionItem(itemNode));
      }
    }
    if (items.isEmpty()) {
      items.add(new MedicationDtos.MedicationRecognitionResult("", "", null, "", "", "", "", null, ""));
    }

    double confidenceSum = 0D;
    int confidenceCount = 0;
    for (MedicationDtos.MedicationRecognitionResult item : items) {
      if (item.confidence() != null) {
        confidenceSum += item.confidence();
        confidenceCount += 1;
      }
    }
    Double confidence = confidenceCount == 0 ? null : Math.round((confidenceSum / confidenceCount) * 100.0) / 100.0;
    return new MedicationDtos.MedicationRecognitionBatchResult(items, confidence);
  }

  private MedicationDtos.MedicationRecognitionResult normalizeMedicationRecognitionItem(JsonNode node) {
    String name = sanitizeText(firstNonBlank(
        node.path("name").asText(""),
        node.path("medicationName").asText("")
    ));
    String alias = sanitizeText(node.path("alias").asText(""));
    Integer dosageValue = parseDosageValue(node.get("dosageValue"));
    String dosageUnit = normalizeDosageUnit(sanitizeText(node.path("dosageUnit").asText("")));
    String usage = normalizeUsage(sanitizeText(node.path("usage").asText("")));
    String notes = sanitizeText(node.path("notes").asText(""));
    String sourceText = sanitizeText(node.path("sourceText").asText(""));
    Double confidence = node.path("confidence").isNumber() ? node.path("confidence").doubleValue() : null;
    return new MedicationDtos.MedicationRecognitionResult(
        name,
        alias,
        dosageValue,
        dosageUnit,
        usage,
        notes,
        "",
        confidence,
        sourceText
    );
  }

  private Integer parseDosageValue(JsonNode valueNode) {
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    if (valueNode.isInt() || valueNode.isLong()) {
      return valueNode.intValue();
    }
    if (valueNode.isNumber()) {
      return (int) Math.round(valueNode.doubleValue());
    }
    String raw = sanitizeText(valueNode.asText(""));
    java.util.regex.Matcher matcher = NUMBER_PATTERN.matcher(raw);
    if (!matcher.find()) {
      return null;
    }
    double value = Double.parseDouble(matcher.group(1));
    if (value <= 0) {
      return null;
    }
    return Math.max(1, Math.min(12, (int) Math.round(value)));
  }

  private String normalizeDosageUnit(String unit) {
    String text = sanitizeText(unit).toLowerCase();
    if (text.isBlank()) {
      return "";
    }
    return switch (text) {
      case "片", "tablet", "tablets", "tab" -> "片";
      case "粒", "capsule", "capsules", "cap" -> "粒";
      case "毫升", "ml" -> "毫升";
      case "滴", "drop", "drops" -> "滴";
      case "袋", "bag", "bags", "sachet", "sachets" -> "袋";
      default -> "";
    };
  }

  private String normalizeUsage(String usage) {
    String text = sanitizeText(usage);
    if (text.isBlank()) {
      return "";
    }
    return switch (text) {
      case "饭前", "before_meal" -> "饭前";
      case "饭后", "after_meal" -> "饭后";
      case "随餐", "with_meal" -> "随餐";
      case "睡前", "bedtime" -> "睡前";
      case "按需", "as_needed" -> "按需";
      default -> "";
    };
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      String text = sanitizeText(value);
      if (!text.isBlank()) {
        return text;
      }
    }
    return "";
  }

  private String sanitizeText(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("\\s+", " ").trim();
  }

  // === Phase 4: 药明白 methods ===

  public MedicationDtos.MedicationExplainResponse explainMedication(MedicationDtos.MedicationExplainRequest request) {
    String name = sanitizeText(request.name());
    if (name.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "请输入药品名称。");
    }

    String systemPrompt = """
        你是药学助手。请根据药品名称，生成结构化药学解释。
        只返回 JSON，不要输出 Markdown。
        固定结构为：
        {"clinicalParse":"","elderFriendlyExplanation":"","warnings":["",""]}
        约束：
        1. clinicalParse：用中文列出药品通用名、适应症、用法用量、常见不良反应、禁忌，格式为结构化文本。
        2. elderFriendlyExplanation：用简单易懂的中文，60字以内，适合老年人阅读，说明这个药治什么、怎么吃、注意什么。
        3. warnings：返回 2-3 条最重要的中文用药提醒。
        4. 如果无法确定具体药品信息，请说明信息来源不足，不要编造。
        """;

    String userContent = "药品名称：" + name;
    String notes = sanitizeText(request.notes());
    if (!notes.isBlank()) {
      userContent += "\n补充说明：" + notes;
    }

    try {
      JsonNode payload = dashScopeService.requestJson(
          systemPrompt, userContent, dashScopeService.chatModel(), 0.3, "药物解释"
      );

      String clinical = payload.path("clinicalParse").asText("暂无详细信息");
      String elder = payload.path("elderFriendlyExplanation").asText("请咨询医生或药师了解此药的详细用法。");

      List<String> warnings = new ArrayList<>();
      JsonNode warningsNode = payload.path("warnings");
      if (warningsNode.isArray()) {
        for (JsonNode w : warningsNode) {
          String text = w.asText("").trim();
          if (!text.isBlank()) warnings.add(text);
        }
      }
      if (warnings.isEmpty()) {
        warnings.add("请遵医嘱用药，如有不适及时就医");
      }

      return new MedicationDtos.MedicationExplainResponse(clinical, elder, warnings);
    } catch (ApiException e) {
      // LLM unavailable — return fallback
      return new MedicationDtos.MedicationExplainResponse(
          "暂无法获取详细药学信息，请查看药品说明书或咨询药师。",
          "这个药的详细信息暂时查不到，建议看药盒上的说明或问医生。",
          List.of("请遵医嘱用药，如有不适及时就医")
      );
    }
  }

  public Map<String, Object> confirmIntake(MedicationDtos.MedicationIntakeConfirmRequest request) {
    long uid = CurrentUser.requireUserId();
    String status = sanitizeText(request.status());
    if (!status.equals("taken") && !status.equals("skipped") && !status.equals("half")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "状态值无效，应为 taken/skipped/half。");
    }

    String today = java.time.LocalDate.now().toString();

    // Upsert intake log
    int updated = jdbcTemplate.update(
        "UPDATE medication_intake_log SET status=?, confirmed_at=NOW() WHERE user_id=? AND alarm_id=? AND intake_date=?",
        status, uid, request.alarmId(), today
    );
    if (updated == 0) {
      jdbcTemplate.update(
          "INSERT INTO medication_intake_log(user_id,alarm_id,intake_date,status,confirmed_at,created_at) VALUES(?,?,?,?,NOW(),NOW())",
          uid, request.alarmId(), today, status
      );
    }

    return Map.of("success", true, "alarmId", request.alarmId(), "status", status);
  }

  public MedicationDtos.TodayScheduleResponse getTodaySchedule() {
    long uid = CurrentUser.requireUserId();
    String today = java.time.LocalDate.now().toString();

    // Get all alarm groups for user
    var alarms = listAlarms();

    // Get today's intake records
    var intakeRows = jdbcTemplate.queryForList(
        "SELECT alarm_id, status FROM medication_intake_log WHERE user_id=? AND intake_date=?",
        uid, today
    );
    Map<Long, String> intakeMap = new HashMap<>();
    for (var row : intakeRows) {
      intakeMap.put(((Number) row.get("alarm_id")).longValue(), (String) row.get("status"));
    }

    List<MedicationDtos.TodayScheduleItem> items = new ArrayList<>();
    for (var alarm : alarms) {
      String intakeStatus = intakeMap.getOrDefault(alarm.id(), "pending");
      items.add(new MedicationDtos.TodayScheduleItem(
          alarm.id(), alarm.time(), alarm.enabled(), alarm.medications(), intakeStatus
      ));
    }

    int completed = (int) items.stream()
        .filter(i -> !"pending".equals(i.intakeStatus()))
        .count();

    return new MedicationDtos.TodayScheduleResponse(today, items, items.size(), completed);
  }
}
