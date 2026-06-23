# 本地药品识别副链接口文档

## 1. 作用范围

这份文档只描述本地药品识别副链接口。

副链位置：

- `E:\VScodeProject\health-app\local_medication_api`

当前边界固定如下：

- 主应用正式药品识别链路不变，仍由大模型接口负责。
- 本地副链只服务于主应用后端预留的 `CUSTOM_MEDICATION_RECOGNIZE_URL`。
- 前端默认入口不会自动切换到这条副链。
- 这条副链当前负责“检测 + OCR + 本地规则抽取”，不接管正式主业务。

正式主链：

- `POST /api/medications/recognize`

本地副链在主应用中的预留转发入口：

- `POST /api/medications/recognize/custom-model`

## 2. 基础信息

默认本地运行地址：

- `http://127.0.0.1:8011`

服务健康检查：

- `GET /health`

识别接口：

- `POST /recognize/medications`

## 3. 接口总览

### 3.1 GET /health

用途：

- 检查本地副链服务是否启动成功。
- 检查模型权重是否加载完成。
- 检查 OCR 适配层是否可用。

响应示例：

```json
{
  "status": "ok",
  "service": "local_medication_api",
  "modelReady": true,
  "ocrReady": true
}
```

### 3.2 POST /recognize/medications

用途：

- 接收一张或多张药品图片。
- 先做药盒/药品区域检测。
- 再做 OCR。
- 最后输出主应用当前可消费的药品结构化 JSON。

请求类型：

- `multipart/form-data`

请求字段：

- `files[]`
  - 必填
  - 支持多张图片
  - 每个文件应为常见图片格式，例如 `jpg`、`jpeg`、`png`、`webp`
- `scene`
  - 必填
  - 当前固定值：`medication_recognition`

请求示例：

```bash
curl -X POST "http://127.0.0.1:8011/recognize/medications" \
  -F "scene=medication_recognition" \
  -F "files[]=@E:/VScodeProject/health-app/健康监测与分析平台/src/pictures/3-1.jpg"
```

PowerShell 示例：

```powershell
$form = @{
  scene = "medication_recognition"
  "files[]" = Get-Item "E:\VScodeProject\health-app\健康监测与分析平台\src\pictures\3-1.jpg"
}
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8011/recognize/medications" -Form $form
```

## 4. 响应结构

成功响应固定为：

```json
{
  "items": [
    {
      "name": "",
      "alias": "",
      "dosageValue": null,
      "dosageUnit": "",
      "usage": "",
      "notes": "",
      "photoUrl": "",
      "confidence": 0.91,
      "sourceText": ""
    }
  ]
}
```

字段说明：

- `items`
  - 识别结果数组
  - 一次请求可返回多个药品候选
- `name`
  - 药品名称
  - 来自 OCR 文本和本地规则抽取
- `alias`
  - 药品别名
  - 当前允许为空
- `dosageValue`
  - 剂量值
  - 当前无法确定时返回 `null`
- `dosageUnit`
  - 剂量单位，例如 `片`、`粒`、`袋`
- `usage`
  - 服用方式，例如 `饭后`
  - 当前无法稳定识别时允许为空
- `notes`
  - OCR 中提取到的可用包装信息摘要
- `photoUrl`
  - 当前固定返回空字符串
- `confidence`
  - 本地副链的检测/抽取置信参考值
- `sourceText`
  - OCR 原始文本与检测摘要整理后的文本

成功响应示例：

```json
{
  "items": [
    {
      "name": "头孢氨苄片",
      "alias": "",
      "dosageValue": null,
      "dosageUnit": "片",
      "usage": "",
      "notes": "国药准字H37020259，Cefalexin Tablets，0.25g-30片",
      "photoUrl": "",
      "confidence": 0.98,
      "sourceText": "国药准字H37020259 头孢氨苄片 Cefalexin Tablets 0.25g-30片"
    }
  ]
}
```

## 5. 当前识别流程

当前副链流程固定为：

1. 接收原图。
2. 调用本地 DINO-SO-YOLO 权重做检测。
3. 对整图和检测框区域执行 OCR。
4. 合并 OCR 文本。
5. 用本地规则抽取药品名称、单位、备注等字段。
6. 按主应用药品识别结果结构输出。

这条链路当前已经接入：

- 检测层
- OCR 层
- 结果格式化层

这条链路当前不做：

- 不替换主应用正式大模型识别
- 不通过文件名猜药品字段
- 不伪造 OCR 结果

## 6. 错误响应

### 6.1 请求参数错误

响应码：

- `400 Bad Request`

示例：

```json
{
  "message": "缺少 scene 或 scene 非 medication_recognition"
}
```

### 6.2 文件为空或格式不支持

响应码：

- `400 Bad Request`

示例：

```json
{
  "message": "未上传有效图片文件"
}
```

### 6.3 模型或 OCR 执行失败

响应码：

- `500 Internal Server Error`

示例：

```json
{
  "message": "本地药品识别执行失败"
}
```

## 7. 与主应用联调方式

主应用后端通过环境变量指向这条副链：

```env
CUSTOM_MEDICATION_RECOGNIZE_URL=http://127.0.0.1:8011/recognize/medications
```

联调时的调用关系固定为：

1. 前端如果显式使用自定义模型入口，则调用主应用后端：
   - `POST /api/medications/recognize/custom-model`
2. 主应用后端再转发到本地副链：
   - `POST /recognize/medications`

主应用正式药品识别主链保持不变：

- `POST /api/medications/recognize`

注意：

- 这意味着把本地副链挂到 `CUSTOM_MEDICATION_RECOGNIZE_URL`，不会替换正式主链。
- 只有显式命中 `/custom-model` 时，才会进入这条副链。

## 8. 代码位置

关键文件：

- `local_medication_api/app.py`
- `local_medication_api/config.py`
- `local_medication_api/models.py`
- `local_medication_api/services/detector.py`
- `local_medication_api/services/ocr_adapter.py`
- `local_medication_api/services/formatter.py`

## 9. 启动方式

```powershell
cd E:\VScodeProject\health-app
python -m pip install -r .\local_medication_api\requirements.txt
python -m uvicorn local_medication_api.app:app --host 127.0.0.1 --port 8011
```

启动后建议先验证：

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8011/health"
```

## 10. 后续扩展点

后续如果继续完善，只需要沿这条副链继续推进：

- 提升 OCR 文本清洗精度
- 提升剂量和服用方式抽取规则
- 接入更稳定的局部文本区域排序
- 增强多药盒同图场景下的字段归属逻辑

当前不需要改动主应用正式药品识别链路。
