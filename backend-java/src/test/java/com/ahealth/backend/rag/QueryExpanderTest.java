package com.ahealth.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * QueryExpander 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>静态词典命中（药品商品名 / 缩写 / 口语化归一）</li>
 *   <li>DB 加载成功时合并到运行时词典</li>
 *   <li>DB 加载失败时 fallback 到静态词典</li>
 *   <li>空查询 / null 查询 / 无匹配查询的边界情况</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QueryExpanderTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @InjectMocks
  private QueryExpander queryExpander;

  @BeforeEach
  void setUp() {
    // 默认 mock：DB 查询返回空列表（仅用静态词典）
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
    queryExpander.init();
  }

  @Test
  void expandNullReturnsNull() {
    assertThat(queryExpander.expand(null)).isNull();
  }

  @Test
  void expandBlankReturnsBlank() {
    assertThat(queryExpander.expand("   ")).isEqualTo("   ");
  }

  @Test
  void expandEmptyStringReturnsEmpty() {
    assertThat(queryExpander.expand("")).isEmpty();
  }

  @Test
  void expandNoMatchReturnsOriginal() {
    String query = "今天天气不错";
    assertThat(queryExpander.expand(query)).isEqualTo(query);
  }

  @Test
  void expandDrugSynonymAppended() {
    String result = queryExpander.expand("阿司匹林能长期服用吗");
    assertThat(result).contains("阿司匹林");
    assertThat(result).contains("ASA");
    assertThat(result).contains("aspirin");
    assertThat(result).contains("乙酰水杨酸");
  }

  @Test
  void expandMultipleDrugSynonymsAllAppended() {
    String result = queryExpander.expand("阿司匹林和华法林能一起吃吗");
    assertThat(result).contains("ASA");
    assertThat(result).contains("warfarin");
    assertThat(result).contains("苄丙酮香豆素钠");
  }

  @Test
  void expandAbbreviationAppended() {
    String result = queryExpander.expand("我的 BMI 是多少");
    assertThat(result).contains("BMI");
    assertThat(result).contains("身体质量指数");
  }

  @Test
  void expandColloquialTermNormalized() {
    String result = queryExpander.expand("最近老是睡不着");
    assertThat(result).contains("睡不着");
    assertThat(result).contains("失眠");
    assertThat(result).contains("sleep");
  }

  @Test
  void expandPreservesOriginalQueryFirst() {
    String query = "头疼怎么办";
    String result = queryExpander.expand(query);
    // 原文必须在最前面（保证 LLM 看到的查询语义不被稀释）
    assertThat(result).startsWith(query);
  }

  @Test
  void initLoadsTcmHerbAliasesFromDb() {
    // 重新 mock：DB 返回中药别名
    QueryExpander freshExpander = new QueryExpander(jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
        Map.of("name", "甘草", "alias", "甜根子,蜜草"),
        Map.of("name", "当归", "alias", "秦归；西当归")
    ));
    freshExpander.init();
    String result = freshExpander.expand("甘草的功效");
    assertThat(result).contains("甜根子");
    assertThat(result).contains("蜜草");
  }

  @Test
  void initLoadsDrugIngredientsFromDb() {
    QueryExpander freshExpander = new QueryExpander(jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
        Map.of("drug_name", "阿莫西林胶囊", "ingredients", "阿莫西林三水合物")
    ));
    freshExpander.init();
    String result = freshExpander.expand("阿莫西林胶囊的副作用");
    assertThat(result).contains("阿莫西林三水合物");
  }

  @Test
  void initDbFailureFallsBackToStaticDict() {
    QueryExpander freshExpander = new QueryExpander(jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("DB 不可用"));
    freshExpander.init(); // 应该不抛异常
    // 静态词典仍可用
    String result = freshExpander.expand("阿司匹林");
    assertThat(result).contains("ASA");
  }

  @Test
  void initDbEmptyAliasSkipped() {
    QueryExpander freshExpander = new QueryExpander(jdbcTemplate);
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
        Map.of("name", "空别名", "alias", ""),
        Map.of("name", "", "alias", "某别名")
    ));
    freshExpander.init();
    // 空字段被跳过，不污染词典
    String result = freshExpander.expand("空别名");
    assertThat(result).isEqualTo("空别名"); // 无扩展
  }

  @Test
  void initLongIngredientsTruncated() {
    QueryExpander freshExpander = new QueryExpander(jdbcTemplate);
    String longIngredients = "成分1".repeat(50); // 200 字符
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
        Map.of("drug_name", "测试药", "ingredients", longIngredients)
    ));
    freshExpander.init();
    String result = freshExpander.expand("测试药");
    // 扩展后的内容应该有截断
    assertThat(result.length()).isLessThan(longIngredients.length() + 10);
  }
}
