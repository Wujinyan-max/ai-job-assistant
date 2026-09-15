# 用户级 AI 配置设计

## 目标

让登录用户在 AI 助手页自行配置模型服务，支持 OpenAI 与 OpenAI 协议兼容厂商，并可在 Chat Completions 和 Responses 两种 API 模式之间切换。

## 方案

采用服务端用户级配置。新增 `ai_user_config` 表，以 `user_id` 唯一隔离配置；API Key 使用 AES-256-GCM 加密后保存，密钥来自 `AI_CONFIG_ENCRYPTION_KEY` 环境变量。查询配置只返回 `hasApiKey` 和掩码，不返回密钥明文。更新时空白 API Key 表示保留原值。

厂商预设包括 OpenAI、DeepSeek、通义千问、Moonshot、Ollama 和自定义兼容服务。预设只负责填充默认 Base URL，模型名始终允许用户输入。API 模式为 `CHAT_COMPLETIONS` 或 `RESPONSES`：前者请求 `{baseUrl}/chat/completions` 并解析 `choices[0].message.content`；后者请求 `{baseUrl}/responses` 并优先解析 `output_text`，否则遍历 `output[].content[].text`。

未保存用户配置或未配置 API Key 时继续使用本地模拟引擎，保持现有项目开箱可用。服务器级 `ai.*` 配置保留为兼容兜底，但不会通过接口泄露。

## 接口

- `GET /ai/config`：返回当前用户配置、是否有密钥、密钥掩码和当前运行模式。
- `PUT /ai/config`：保存当前用户配置；API Key 为空时保留旧密钥。
- `POST /ai/config/test`：用当前已保存配置发送最小测试请求，返回连接结果和模型名。

## 前端

AI 助手标题区增加“配置模型”按钮。配置抽屉包含厂商、API 模式、Base URL、模型、API Key、启用开关，以及“保存配置”“测试连接”。模式说明明确标注对应端点。保存后标题状态立即刷新。

## 安全与错误

- API Key 不写日志、不写 AI 分析记录、不返回前端。
- AES-GCM 每次加密使用随机 12 字节 IV，密文包含版本前缀。
- Base URL 仅允许 `http` 或 `https`；禁止 URL 用户信息和片段。
- 解密失败、配置缺失、厂商返回空内容都转换为统一业务错误。
- 测试连接与业务调用都复用超时设置。

## 验收

- 加密后数据库内容不包含明文，且能正确解密。
- Chat 和 Responses 请求体、端点及响应解析均有单元测试。
- 不同用户只能读取和修改自己的配置。
- 前端构建成功，后端测试通过，前后端可实际启动。
