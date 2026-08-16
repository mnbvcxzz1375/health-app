# 垂域健康大模型工程验收记录（2026-08-02）

这份记录只描述可复现的工程证据，不把合成病例契约指标包装为临床准确率。

## 已完成的关键加固

- `OAuthStateService` 对设备 OAuth 回调 state 做服务端 HMAC 签名、provider 绑定、10 分钟过期和单次消费；回调不再从前端可控 state 解析用户 ID。
- 年龄、身高或体重缺失时，康复计划不自动生成默认动作；补齐资料后才进入个性化规划。完整资料下的低负荷回退最多 3 个动作，单个不超过 8 分钟。
- 健康评分仅按实际可用指标归一化，分类返回 `dataAvailable`，整体返回 `dataQuality`/`dataWarnings`。
- RAG 不摄入用户私有问诊历史和私有康复动作；AI/RAG 管理端使用管理员白名单。
- 设备同步不随机修改电池电量；无新遥测时保留上次上报值。
- 饮食日志支持用户级更正、删除和审计查询；删除后保留前快照，避免识别结果被静默覆盖或丢失追溯链。

## 回归证据

```text
backend: .\mvnw.cmd test                         91 passed
frontend: npm run typecheck                       passed
frontend: npm run test -- --run                  15 files / 42 tests passed
frontend: npm run build                           passed
evaluation: python -m unittest ...                9 passed
evaluation: validate_dataset.py                  6 cases passed
evaluation: example report + 3-arm comparison    generated; contract metrics 1.0
```

## 提交前仍需完成

1. 生产环境配置 `DEVICE_OAUTH_STATE_SECRET` 和 OAuth token 加密密钥；有 Redis 时 replay registry 使用共享存储，Redis 不可用时仅保留进程内开发回退；正式多实例部署必须提供共享 Redis/数据库并配置 secret。
2. 冻结不提交的 blind test 集，按 `source_case_family` 做分组拆分。
3. 由临床/药学背景人员盲审安全升级、证据覆盖、动作标签和拒答边界。
4. 仅在完成盲测与专家审核后讨论模型效果，不宣称已完成临床验证。
