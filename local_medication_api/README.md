# health-app 本地药品识别副链

这套服务放在 `E:\VScodeProject\health-app\local_medication_api` 下，独立于当前主应用正式药品识别链路。

当前边界：

- 主应用默认药品识别不变
- 这套服务只作为 `CUSTOM_MEDICATION_RECOGNIZE_URL` 对应的本地副链
- 检测真实运行
- OCR 已在副链内接通
- 返回 JSON 形状与主应用现有药品识别结果一致

## 目录

- `app.py`：FastAPI 入口
- `config.py`：配置
- `services/detector.py`：检测推理
- `services/ocr_adapter.py`：OCR 识别与裁剪合并
- `services/formatter.py`：输出格式化与字段抽取
- `vendor/ultralytics/`：从 DINO-SO-YOLO 带过来的自定义 `ultralytics`
- `weights/yolov13n.pt`：默认权重

## 启动

```powershell
cd E:\VScodeProject\health-app
python -m pip install -r .\local_medication_api\requirements.txt
python -m uvicorn local_medication_api.app:app --host 127.0.0.1 --port 8011
```

## 请求协议

路由：

- `GET /health`
- `POST /recognize/medications`

请求固定为 `multipart/form-data`：

- `files[]`
- `scene=medication_recognition`

详细接口说明见：

- `API.md`

## 返回结构

```json
{
  "items": [
    {
      "name": "头孢氨苄片",
      "alias": "",
      "dosageValue": null,
      "dosageUnit": "片",
      "usage": "",
      "notes": "国药准字H37020259；Cefalexin Tablets；0.25g-30片",
      "photoUrl": "",
      "confidence": 0.98,
      "sourceText": "国药准字H37020259 头孢氨苄片 Cefalexin Tablets 0.25g-30片"
    }
  ]
}
```

说明：

- `name / dosageValue / dosageUnit / usage / notes` 由副链内部 OCR 文本做本地规则抽取
- 如果图片里没有足够信息，这些字段会保守留空，不会猜测
- 正式大模型主链不受这套 OCR 副链影响

## 与主应用联调

主应用默认正式链路仍是：

- `POST /api/medications/recognize`

这条正式链路继续走大模型，本地副链不会替换它。

主应用后端当前配置为：

```env
CUSTOM_MEDICATION_RECOGNIZE_URL=http://127.0.0.1:8011/recognize/medications
```

如需联调本地副链，调用主应用已有预留接口：

- `POST /api/medications/recognize/custom-model`

前端默认入口不用切换，只有显式调用 `/custom-model` 才会走这条副链。
