# 中药材识别测试图片

本目录包含用于测试"多药材识别"功能的中药材图片（SVG 格式）。

## 图片清单

| 文件 | 药材名 | 拉丁名 | 性味归经 | 功效 |
|------|--------|--------|----------|------|
| `gouqi_herb.svg` | 枸杞子 | Lycii Fructus | 甘，平。归肝、肾经 | 滋补肝肾，益精明目 |
| `huangqi_herb.svg` | 黄芪 | Astragali Radix | 甘，温。归肺、脾经 | 补气升阳，固表止汗 |
| `danggui_herb.svg` | 当归 | Angelicae Sinensis Radix | 甘、辛，温。归肝、心、脾经 | 补血活血，调经止痛 |
| `jinyinhua_herb.svg` | 金银花 | Lonicerae Japonicae Flos | 甘，寒。归肺、心、胃经 | 清热解毒，疏散风热 |

另外，`../medication/gancao_herb.svg`（甘草饮片）也可用于中药材识别测试。

## 用途

在 `HerbRecognitionPage.vue` 的"多药材识别"页面中，点击"使用示例图片"区域任一缩略图，会自动触发识别流程，模拟用户上传中药材照片。

## 来源

这些 SVG 图片是为测试目的生成，包含药材名、拉丁名、来源、性味归经、功能主治、用法用量、禁忌等结构化文字信息，便于验证 OCR 文字提取和 LLM 药性解析的完整流程。

## 引用方式

```ts
const sampleImages = [
  { name: '枸杞子', url: new URL('../../../../pictures/herbs/gouqi_herb.svg', import.meta.url).href, category: '中药材' },
]
```

Vite 默认支持 SVG 作为 URL 字符串导入（返回文件路径），可用于 `<img :src="url">`。
