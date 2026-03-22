# 会员登录注册实现逻辑说明

## 1. 当前架构结论

当前项目已经将认证基础设施拆成两层：

1. `common` 提供平台级认证能力
- `Sa-Token + Redis`：作为登录态真相源
- `sys_auth_session`：作为认证会话记录表，只存令牌摘要，不存明文令牌
- `sys_verify_code_record`：作为验证码记录表，只存验证码摘要，不存明文验证码
- 通用服务：
  - `AuthSessionService`
  - `VerifyCodeService`
  - `AuthDigestService`

2. `member-backend` 提供会员业务编排
- `controller`
- `application`
- `service`
- `mapper`
- `domain`
- 只负责 `sys_user` 用户体系和会员登录场景，不再直接承担底层验证码/会话基础设施实现

---

## 2. 登录实现链路

### 2.1 接口入口

- 登录接口：`POST /api/v1/member/auth/login`
- 控制器：`MemberAuthController`
- 应用服务：`MemberAuthApplicationService`
- 领域服务：`MemberAuthService`

### 2.2 登录流程

1. 控制器接收 `MemberLoginRequest`
2. `MemberAuthApplicationService` 编排登录用例
3. `MemberAuthService` 根据 `grantType` 选择具体登录策略
4. 登录策略校验用户身份
5. 校验通过后调用 `MemberSessionService`
6. `MemberSessionService` 将会员侧上下文补齐后委托 `common` 的 `AuthSessionService`
7. `AuthSessionService` 调用 `Sa-Token` 生成 token
8. token 明文只返回给客户端并保留在 Redis 登录态体系中
9. 数据库 `sys_auth_session` 只保存 `token_digest`
10. 最终统一返回 `R<MemberLoginResponse>`

### 2.3 支持的登录方式

当前支持三种 `grantType`：

- `password`
- `sms`
- `email`

对应策略类：

- `PasswordLoginStrategy`
- `SmsLoginStrategy`
- `EmailLoginStrategy`

---

## 3. Token 架构说明

### 3.1 为什么 token 保留 Redis

Redis 仍然是登录态的唯一真相源，原因是：

1. 登录态校验是高频读取，Redis 更适合
2. `Sa-Token` 天然适合这条链路
3. 后续做踢下线、续期、在线会话管理都更方便

### 3.2 为什么数据库不再存明文 token

数据库中的 `sys_auth_session` 只做：

- 审计记录
- 会话留痕
- 后台会话管理
- 后续扩展设备/IP/活跃时间管理

因此只存：

- `token_digest`

不存：

- 明文 `token`

### 3.3 `sys_auth_session` 表职责

该表不是登录态真相源，而是：

- 认证会话记录表
- 审计表
- 后续会话管理基础表

核心字段：

- `session_id`
- `subject_id`
- `subject_type`
- `login_type`
- `token_digest`
- `expires_at`
- `revoked`
- `deleted`
- `created_at`
- `last_active_at`
- `device_type`
- `client_ip`

---

## 4. 验证码架构说明

### 4.1 当前设计

验证码基础能力已经从 `member-backend` 抽到 `common`：

- `VerifyCodeService`：通用验证码服务
- `AuthDigestService`：统一摘要能力
- `sys_verify_code_record`：验证码记录表

### 4.2 为什么验证码也不存明文

当前设计中：

1. 生成验证码时，系统只在发送链路中短暂拿到明文
2. Redis 中缓存的是验证码摘要
3. 数据库中记录的是 `code_digest`
4. 输入验证码时，先对输入值做摘要，再与 Redis 摘要比较
5. 校验成功后删除 Redis，并把数据库记录标记为已使用

这样做的好处是：

- 降低敏感验证码泄露风险
- 统一“缓存层 + 持久层”安全策略
- 更适合做平台级复用

### 4.3 `sys_verify_code_record` 表职责

该表用于：

- 发送留痕
- 核销留痕
- 审计追踪
- 业务场景复用

核心字段：

- `record_id`
- `biz_type`
- `biz_id`
- `subject_type`
- `subject_id`
- `target_type`
- `target_value`
- `channel`
- `template_id`
- `provider`
- `request_id`
- `code_digest`
- `expires_at`
- `used`
- `used_at`
- `deleted`
- `created_at`
- `remark`

### 4.4 会员模块如何复用验证码能力

`member-backend` 中的 `MemberVerifyCodeService` 现在只是会员侧薄封装，负责补齐：

- `namespace=member_auth`
- `bizType=LOGIN`
- `subjectType=SYS_USER`
- 会员模块的 mock 配置
- 会员模块的验证码 TTL 配置

真正的缓存、摘要、落库、核销逻辑都在 `common`。

---

## 5. 当前登录校验逻辑

### 5.1 密码登录

流程：

1. 根据用户名查 `sys_user`
2. 校验用户状态是否可用
3. 优先走 `PasswordEncoder`
4. 若配置允许开发兼容，则允许明文密码对比
5. 登录成功后创建认证会话

### 5.2 短信登录

流程：

1. 校验手机号和短信验证码参数
2. 调用 `MemberVerifyCodeService#verifyAndConsume`
3. 委托 `common.VerifyCodeService` 完成摘要校验与核销
4. 根据手机号查询 `sys_user`
5. 成功后创建认证会话

### 5.3 邮箱登录

流程：

1. 校验邮箱和邮箱验证码参数
2. 对邮箱做小写标准化
3. 调用 `MemberVerifyCodeService#verifyAndConsume`
4. 委托 `common.VerifyCodeService` 完成摘要校验与核销
5. 根据邮箱查询 `sys_user`
6. 成功后创建认证会话

---

## 6. 返回结构

所有接口统一使用 `R<T>` 返回：

```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "token": "<jwt-or-token>",
    "expiresIn": 7200
  }
}
```

当前登录成功时实际还会返回：

- `token`
- `tokenType`
- `expiresIn`
- `grantType`
- `user`

---

## 7. 后续注册实现建议

如果接下来要继续做注册，建议沿用当前分层：

1. `controller`
- 在 `MemberAuthController` 增加 `register`

2. `application`
- 在应用层编排注册用例

3. `service`
- 会员注册服务只负责会员业务规则
- 如需验证码，仍通过 `MemberVerifyCodeService` 调 `common.VerifyCodeService`

4. `common`
- 不直接写会员注册规则
- 只沉淀可复用的认证基础能力

---

## 8. 现在这套架构的优点

1. 认证基础设施可复用
- 后续后台用户、管理端、绑定邮箱、找回密码都能复用

2. 安全边界更清晰
- Redis 管短期真相源
- 数据库存摘要和审计记录

3. 会员模块更聚焦
- 只关心会员用户和登录业务，不再直接实现底层认证设施

4. 后续扩展更自然
- 可扩展登录设备
- 可扩展客户端 IP
- 可扩展验证码限流
- 可扩展更多业务场景 `bizType`
