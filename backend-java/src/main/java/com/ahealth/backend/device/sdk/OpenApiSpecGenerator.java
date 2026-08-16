package com.ahealth.backend.device.sdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 动态生成 OpenAPI 3.0 Spec（JSON 兼容 Map）。
 *
 * <p>供 {@code GET /api/sdk/v1/schema} 端点返回，
 * 与静态 docs/device-aggregation-openapi.yaml 互为补充。
 */
@Service
public class OpenApiSpecGenerator {

  /** 生成完整的 OpenAPI 3.0 spec Map。 */
  public Map<String, Object> generateSpec() {
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("openapi", "3.0.3");
    spec.put("info", info());
    spec.put("servers", List.of(
        Map.of("url", "http://localhost:3302/api/devices/sdk", "description", "本地开发"),
        Map.of("url", "/api/devices/sdk", "description", "生产环境（相对路径）")
    ));
    spec.put("paths", paths());
    spec.put("components", components());
    return spec;
  }

  private Map<String, Object> info() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("title", "健康监测与分析平台 — 开放 SDK API");
    info.put("description", "第三方设备/应用通过此 API 推送健康数据到平台。使用 X-SDK-API-Key Header 鉴权。");
    info.put("version", "1.0.0");
    info.put("contact", Map.of("name", "AHealth Team", "email", "dev@ahealth.local"));
    return info;
  }

  private Map<String, Object> paths() {
    Map<String, Object> paths = new LinkedHashMap<>();
    paths.put("/records/push", recordsPushPath());
    paths.put("/devices/register", devicesRegisterPath());
    paths.put("/schema", schemaPath());
    paths.put("/providers", providersPath());
    return paths;
  }

  private Map<String, Object> recordsPushPath() {
    return Map.of("post", Map.of(
        "summary", "推送单条健康数据",
        "operationId", "pushRecord",
        "tags", List.of("Records"),
        "security", List.of(Map.of("ApiKeyAuth", List.of())),
        "requestBody", Map.of(
            "required", true,
            "content", Map.of("application/json", Map.of(
                "schema", Map.of("$ref", "#/components/schemas/UnifiedHealthRecord")
            ))
        ),
        "responses", Map.of(
            "200", Map.of(
                "description", "写入成功",
                "content", Map.of("application/json", Map.of(
                    "schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "success", Map.of("type", "boolean"),
                            "provider", Map.of("type", "string"),
                            "sourceDevice", Map.of("type", "string"),
                            "recordedAt", Map.of("type", "string", "format", "date-time")
                        )
                    )
                ))
            ),
            "401", unauthorizedResponse()
        )
    ));
  }

  private Map<String, Object> devicesRegisterPath() {
    return Map.of("post", Map.of(
        "summary", "注册第三方设备品牌（返回 API Key）",
        "operationId", "registerDevice",
        "tags", List.of("Devices"),
        "requestBody", Map.of(
            "required", true,
            "content", Map.of("application/json", Map.of(
                "schema", Map.of(
                    "type", "object",
                    "required", List.of("partnerName"),
                    "properties", Map.of(
                        "partnerName", Map.of("type", "string", "description", "合作方名称"),
                        "contact", Map.of("type", "string", "description", "联系邮箱"),
                        "scopes", Map.of("type", "string", "description", "权限范围", "default", "push")
                    )
                )
            ))
        ),
        "responses", Map.of(
            "200", Map.of(
                "description", "注册成功，返回 API Key（明文仅此一次）",
                "content", Map.of("application/json", Map.of(
                    "schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "apiKey", Map.of("type", "string"),
                            "partnerName", Map.of("type", "string"),
                            "createdAt", Map.of("type", "string", "format", "date-time")
                        )
                    )
                ))
            )
        )
    ));
  }

  private Map<String, Object> schemaPath() {
    return Map.of("get", Map.of(
        "summary", "获取本 OpenAPI Spec",
        "operationId", "getSchema",
        "tags", List.of("Meta"),
        "responses", Map.of(
            "200", Map.of(
                "description", "OpenAPI 3.0 JSON",
                "content", Map.of("application/json", Map.of(
                    "schema", Map.of("type", "object")
                ))
            )
        )
    ));
  }

  private Map<String, Object> providersPath() {
    return Map.of("get", Map.of(
        "summary", "列出平台支持的数据 Provider",
        "operationId", "listProviders",
        "tags", List.of("Meta"),
        "responses", Map.of(
            "200", Map.of(
                "description", "Provider 列表",
                "content", Map.of("application/json", Map.of(
                    "schema", Map.of(
                        "type", "array",
                        "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "providerName", Map.of("type", "string"),
                                "displayName", Map.of("type", "string"),
                                "deviceType", Map.of("type", "string"),
                                "configured", Map.of("type", "boolean"),
                                "supportedMetrics", Map.of("type", "array", "items", Map.of("type", "string"))
                            )
                        )
                    )
                ))
            )
        )
    ));
  }

  private Map<String, Object> components() {
    Map<String, Object> schemas = new LinkedHashMap<>();
    schemas.put("UnifiedHealthRecord", unifiedHealthRecordSchema());

    Map<String, Object> securitySchemes = Map.of(
        "ApiKeyAuth", Map.of(
            "type", "apiKey",
            "in", "header",
            "name", "X-SDK-API-Key",
            "description", "通过 /devices/register 获取的 API Key"
        )
    );

    Map<String, Object> components = new LinkedHashMap<>();
    components.put("schemas", schemas);
    components.put("securitySchemes", securitySchemes);
    return components;
  }

  private Map<String, Object> unifiedHealthRecordSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("provider", Map.of("type", "string", "description", "数据来源 Provider 标识"));
    properties.put("sourceDevice", Map.of("type", "string", "description", "来源设备名称"));
    properties.put("recordedAt", Map.of("type", "string", "format", "date-time", "description", "记录时间 ISO-8601"));
    properties.put("heartRateAvgBpm", intProp("平均心率 bpm"));
    properties.put("heartRateRestingBpm", intProp("静息心率 bpm"));
    properties.put("hrvMillis", intProp("心率变异性 ms"));
    properties.put("steps", intProp("步数"));
    properties.put("exerciseMinutes", intProp("锻炼分钟"));
    properties.put("standHours", intProp("站立小时"));
    properties.put("activeEnergyKcal", intProp("活动能量 kcal"));
    properties.put("flightsClimbed", intProp("爬楼层数"));
    properties.put("vo2Max", doubleProp("最大摄氧量"));
    properties.put("stressScore", intProp("压力评分 0-100"));
    properties.put("weightKg", doubleProp("体重 kg"));
    properties.put("heightCm", doubleProp("身高 cm"));
    properties.put("bmi", doubleProp("BMI"));
    properties.put("systolicBp", intProp("收缩压 mmHg"));
    properties.put("diastolicBp", intProp("舒张压 mmHg"));
    properties.put("bloodGlucose", doubleProp("血糖 mmol/L"));
    properties.put("bodyTemperature", doubleProp("体温 ℃"));
    properties.put("spo2", intProp("血氧 %"));
    properties.put("respiratoryRate", intProp("呼吸频率 次/分"));
    properties.put("sleepDurationHours", doubleProp("睡眠时长 小时"));
    properties.put("deepSleepHours", doubleProp("深睡时长 小时"));
    properties.put("remSleepHours", doubleProp("REM 时长 小时"));
    properties.put("sleepScore", intProp("睡眠评分 0-100"));
    properties.put("awakeTimes", intProp("夜醒次数"));
    properties.put("mindfulMinutes", intProp("正念分钟"));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("required", List.of("provider", "recordedAt"));
    schema.put("properties", properties);
    return schema;
  }

  private Map<String, Object> unauthorizedResponse() {
    return Map.of(
        "description", "API Key 无效或已过期",
        "content", Map.of("application/json", Map.of(
            "schema", Map.of(
                "type", "object",
                "properties", Map.of("error", Map.of("type", "string"))
            )
        ))
    );
  }

  private Map<String, Object> intProp(String desc) {
    return Map.of("type", "integer", "nullable", true, "description", desc);
  }

  private Map<String, Object> doubleProp(String desc) {
    return Map.of("type", "number", "format", "double", "nullable", true, "description", desc);
  }
}
