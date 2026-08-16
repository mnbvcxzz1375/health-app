# 药品识别测试图片

本目录包含 5 张用于测试"扫描识别"功能的药盒/药品图片（SVG 格式）。

## 图片清单

| 文件 | 类型 | 药品名 | 国药准字 |
|------|------|--------|----------|
| `ibuprofen_box.svg` | 西药 | 布洛芬缓释胶囊 | H10900089 |
| `metformin_box.svg` | 西药 | 盐酸二甲双胍片 | H11020375 |
| `liuheining_box.svg` | 中成药 | 六味地黄丸 | Z11020146 |
| `sangju_box.svg` | 中成药 | 桑菊感冒片 | Z11020392 |
| `gancao_herb.svg` | 中药材 | 甘草饮片 | 一级品 |

## 用途

在 `MedicationPage.vue` 的"扫描识别"Tab 中，点击"使用示例图片"区域任一缩略图，会自动触发 OCR 识别流程，模拟用户上传药盒照片。

## 来源

这些 SVG 图片是为测试目的生成，包含药品名、规格、用法用量、适应症等结构化文字信息，便于验证 OCR 文字提取和 LLM 药品解释的完整流程。

## 引用方式

```ts
import ibuprofenImg from '@/pictures/medication/ibuprofen_box.svg'
```

Vite 默认支持 SVG 作为 URL 字符串导入（返回文件路径），可用于 `<img :src="ibuprofenImg">`。
