# 会员登录注册实现说明

本文档基于当前仓库代码整理，分成两部分来说明：

- 当前已经落地的会员登录、验证码发送、会话签发实现
- 后续“会员注册”如果继续接着做，推荐沿用的分层和扩展方案

这样可以避免“现状实现”和“设计目标”混在一起，方便后面继续迭代。

## 1. 当前模块职责

当前项目采用多模块结构，但运行时只有一个统一启动入口：

- `luoluo-admin`：启动模块，负责统一装配配置、数据源、Redis、邮件等基础能力
- `luoluo-common`：通用认证与基础设施模块，负责验证码缓存、图形验证码、会话签发、摘要计算、邮件发送等
- `luoluo-member`：会员认证与会员资料模块，负责登录策略、会员信息查询、会话上下文封装
- `luoluo-system`：对外公共接口层，负责暴露无需登录即可访问的公共认证入口
- `luoluo-rag`：RAG 能力模块，与会员登录注册无直接耦合

当前登录注册相关能力的职责边界如下：

- `luoluo-system`：补图形验证码校验、发送频率限制，对外提供公共发送验证码入口
- `luoluo-member`：承载会员认证业务规则与登录用例编排
- `luoluo-common`：承载 Redis、Sa-Token、验证码存取、图形验证码、邮件/SMS 发送适配等通用能力

## 2. 当前已实现的接口

### 2.1 对外接口

当前已经实现并可直接对接前端的接口：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/luoluo/member/auth/login` | `POST` | 会员登录 |
| `/luoluo/member/profile/me` | `GET` | 获取当前登录会员资料 |
| `/luoluo/system/public/captcha/image` | `GET` | 获取图形验证码 |
| `/luoluo/system/public/member/auth/code/send` | `POST` | 对外发送短信/邮箱验证码 |

### 2.2 模块内部接口

仓库里还保留了一个 `luoluo-member` 直接暴露的内部发送入口：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/luoluo/member/auth/send-code` | `POST` | 直接调用会员模块发送验证码，不做图形验证码校验，也不做公开入口限流 |

说明：

- 对外推荐使用 `POST /luoluo/system/public/member/auth/code/send`
- `app.api-prefix=/api/v1` 当前不会作用于这组接口，因为这些 Controller 使用的是硬编码路径 `/luoluo/...`

## 3. 当前支持的登录方式

当前 `grantType` 支持三种模式：

- `password`
- `sms`
- `email`

对应策略类如下：

- `PasswordLoginStrategy`
- `SmsLoginStrategy`
- `EmailLoginStrategy`

整体登录分发链路如下：

1. `MemberAuthController` 接收 `MemberLoginRequest`
2. `MemberAuthApplicationService` 编排登录用例
3. `MemberAuthService` 根据 `grantType` 路由到具体 `MemberLoginStrategy`
4. 登录策略完成身份校验
5. 校验通过后由 `MemberSessionService` 创建会话
6. `MemberSessionService` 委托 `luoluo-common` 的 `AuthSessionService`
7. `AuthSessionService` 调用 `Sa-Token` 生成 token
8. token 明文返回给客户端，数据库只保存 `token_digest`

## 4. 关键请求与响应对象

### 4.1 登录请求 `MemberLoginRequest`

字段如下：

- `grantType`
- `username`
- `password`
- `phone`
- `smsCode`
- `email`
- `emailCode`

不同模式实际使用字段：

- 密码登录：`grantType`、`username`、`password`
- 短信登录：`grantType`、`phone`、`smsCode`
- 邮箱登录：`grantType`、`email`、`emailCode`

### 4.2 发送验证码请求 `MemberSendVerifyCodeRequest`

字段如下：

- `grantType`
- `captchaKey`
- `captchaCode`
- `phone`
- `email`

不同模式实际使用字段：

- 短信验证码：`grantType=sms`、`phone`
- 邮箱验证码：`grantType=email`、`email`
- 当 `imageCaptchaEnabled=true` 时，还必须额外传 `captchaKey` 和 `captchaCode`

### 4.3 登录响应 `MemberLoginResponse`

登录成功后会返回：

- `token`
- `tokenType`
- `expiresIn`
- `grantType`
- `user`

其中 `tokenType` 当前固定为 `Bearer`。

### 4.4 发送验证码响应 `MemberSendVerifyCodeResponse`

字段如下：

- `requestId`
- `grantType`
- `target`
- `expiresIn`
- `issuedCode`

说明：

- `target` 是脱敏后的手机号或邮箱
- `expiresIn` 取自配置 `app.member.auth.verify-code-ttl-seconds`
- `issuedCode` 只有在允许 mock 验证码并且底层发送器暴露调试码时才会返回

### 4.5 统一响应结构

所有接口统一使用 `R<T>`：

```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "token": "xxxxx",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "grantType": "password",
    "user": {
      "id": 1,
      "username": "demo_user"
    }
  }
}
```

## 5. 当前登录实现细节

### 5.1 密码登录

`PasswordLoginStrategy` 的实际逻辑是：

1. 校验 `username`、`password` 非空
2. 通过 `MemberUserService.findActiveByUsername` 查询启用状态的会员
3. 优先走 `PasswordEncoder.matches`
4. 如果配置 `allow-plain-password=true`，则额外兼容“明文密码等于库中值”的本地开发模式
5. 校验成功后进入会话签发

当前配置里：

- `app.member.auth.allow-plain-password=true`

这意味着当前环境允许明文密码兜底，仅建议用于本地开发或兼容历史测试数据。

### 5.2 短信登录

`SmsLoginStrategy` 的实际逻辑是：

1. 校验 `phone`、`smsCode` 非空
2. 调用 `MemberVerifyCodeService.verifyAndConsume("sms", phone, smsCode)`
3. 委托 `luoluo-common.VerifyCodeService` 完成验证码校验和核销
4. 根据手机号查询 `sys_user`
5. 找到有效用户后签发登录会话

### 5.3 邮箱登录

`EmailLoginStrategy` 的实际逻辑是：

1. 校验 `email`、`emailCode` 非空
2. 先把邮箱转成小写标准化
3. 调用 `MemberVerifyCodeService.verifyAndConsume("email", normalizedEmail, emailCode)`
4. 委托 `luoluo-common.VerifyCodeService` 完成验证码校验和核销
5. 根据邮箱查询 `sys_user`
6. 找到有效用户后签发登录会话

### 5.4 会话与 token 签发

`MemberSessionService` 会补齐会员上下文后，调用 `AuthSessionService.createSession`：

- `subjectType=SYS_USER`
- `loginType=grantType`
- `deviceType=grantType`
- `ttlSeconds=app.member.auth.session-ttl-seconds`

`AuthSessionService` 的行为是：

1. 调用 `StpUtil.login(...)` 签发 token
2. token 明文保留在 Sa-Token + Redis 登录态体系中
3. 将 token 的 SHA-256 摘要写入 `sys_auth_session.token_digest`
4. 数据库保留会话审计信息，如 `subject_id`、`login_type`、`device_type`、`expires_at`

## 6. 当前验证码发送实现细节

### 6.1 对外发送链路

推荐使用的公开发送链路如下：

1. `PublicMemberAuthController` 接收 `MemberSendVerifyCodeRequest`
2. `PublicMemberAuthApplicationService` 判断是否启用图形验证码
3. 如果启用，则调用 `ImageCaptchaService.verifyAndConsume`
4. 公开入口再通过 `CaptchaSendLimitService` 做同目标频率限制
5. 通过后委托 `MemberAuthApplicationService.sendCode`
6. `MemberSendCodeService` 根据 `grantType` 路由到对应发送策略

### 6.2 短信验证码发送

`SmsSendCodeStrategy` 的实际逻辑是：

1. 校验手机号非空
2. 随机生成 6 位验证码
3. 调用 `MemberSmsSender.sendLoginCode`
4. 如果短信发送器只是 mock 回显，且当前又不允许 mock 验证码，则直接抛 `503 SERVICE_UNAVAILABLE`
5. 发送成功后通过 `MemberVerifyCodeService.recordAndCache` 落 Redis 和数据库记录
6. 返回脱敏手机号、有效期和请求流水号

### 6.3 邮箱验证码发送

`EmailSendCodeStrategy` 的实际逻辑是：

1. 校验邮箱非空
2. 标准化成小写邮箱
3. 随机生成 6 位验证码
4. 调用 `CommonMailSender.sendText`
5. 如果底层只是 debug/mock 回显，且当前不允许 mock 验证码，则抛 `503 SERVICE_UNAVAILABLE`
6. 发送成功后通过 `MemberVerifyCodeService.recordAndCache` 落 Redis 和数据库记录
7. 返回脱敏邮箱、有效期和请求流水号

### 6.4 图形验证码与发送限流

图形验证码和发送频率限制都沉淀在 `luoluo-common`：

- `ImageCaptchaService`：负责生成 SVG 图形验证码，并把验证码摘要写入 Redis
- `CaptchaSendLimitService`：负责同目标发送间隔控制

当前公开发送接口的限流维度是：

- namespace：`member_send_code`
- targetType：`sms` 或 `email`
- targetValue：手机号或标准化邮箱

## 7. 数据落点与安全现状

### 7.1 token 存储

当前 token 的存储方式是：

- Redis / Sa-Token：保存真实登录态
- 数据库 `sys_auth_session`：保存 `token_digest`，不保存明文 token

这一点和原始设计目标是一致的，优点是：

- 高频鉴权走 Redis
- 数据库只承担留痕和审计
- 后续做会话管理、踢下线、查询在线设备更方便

### 7.2 验证码存储

这里需要特别说明当前代码现状：

- 图形验证码：Redis 中保存的是摘要值
- 短信/邮箱验证码：当前 `VerifyCodeService` 保存的是明文验证码

也就是说，当前会员验证码并不是“摘要存储”：

- Redis 中存的是验证码明文
- 数据库 `sys_verify_code_record.code_value` 中存的也是验证码明文

`verifyAndConsume` 校验时，当前走的是“明文比对 + 成功后删除 Redis + 更新数据库为已使用”。

如果后续要继续提升安全性，建议把会员验证码链路也演进为：

1. 只在发送链路短暂持有明文
2. Redis 中缓存摘要
3. 数据库中保存 `code_digest`
4. 输入验证码时先做摘要再比对

这会和 token、图形验证码的安全策略更加一致。

## 8. 当前配置与实际行为

根据当前 [`luoluo-admin/src/main/resources/application.yml`](d:\develop\Personal-Blog-RAG-Backend-Project\luoluo-admin\src\main\resources\application.yml) 配置，和会员认证直接相关的关键项如下：

- `app.member.auth.session-ttl-seconds=86400`
- `app.member.auth.verify-code-ttl-seconds=120`
- `app.member.auth.verify-code-send-interval-seconds=60`
- `app.member.auth.image-captcha-enabled=false`
- `app.member.auth.allow-mock-verify-code=false`
- `app.member.auth.mock-verify-code=123456`
- `app.member.auth.allow-plain-password=true`
- `app.member.sms.aliyun.enabled=false`
- `spring.mail.host=smtp.qq.com`
- `spring.mail.username=2130265486@qq.com`
- `spring.mail.password=${SPRING_MAIL_PASSWORD:}`

基于当前配置，可以推导出以下实际行为：

- 密码登录可用，并且允许明文密码兜底
- 图形验证码当前默认关闭，因此公开发送验证码接口不要求 `captchaKey` 和 `captchaCode`
- 短信验证码当前不可用，因为阿里云短信发送器没有启用
- 邮箱验证码理论上可用，但前提是 `SPRING_MAIL_PASSWORD` 已正确配置为 SMTP 授权码
- 因为 `allow-mock-verify-code=false`，如果邮件或短信底层发送器只是 mock/debug 实现，接口会直接报 `503`

## 9. 当前推荐的前端对接方式

### 9.1 密码登录

请求：

```json
{
  "grantType": "password",
  "username": "demo_user",
  "password": "123456"
}
```

### 9.2 发送短信验证码

请求：

```json
{
  "grantType": "sms",
  "phone": "13800138000"
}
```

### 9.3 发送邮箱验证码

请求：

```json
{
  "grantType": "email",
  "email": "demo@example.com"
}
```

### 9.4 使用 token 获取当前用户

请求头格式：

```http
Authorization: Bearer <token>
```

## 10. 注册功能推荐实现方案

当前仓库还没有完整的“会员注册”接口，但从已有登录/验证码架构来看，推荐沿用同样的分层方式。

### 10.1 推荐接口

建议新增公开注册入口：

- `POST /luoluo/system/public/member/auth/register`

如果需要保留模块内部直连入口，可再提供：

- `POST /luoluo/member/auth/register`

其中公开入口应继续承担：

- 图形验证码校验
- 注册频率控制
- 对外统一返回格式

### 10.2 推荐请求对象

建议注册请求对象至少包含：

- `registerType`
- `username`
- `password`
- `confirmPassword`
- `displayName`
- `phone`
- `smsCode`
- `email`
- `emailCode`

如果暂时只做一种注册模式，也可以先简化：

- 手机号注册：`phone + smsCode + password`
- 邮箱注册：`email + emailCode + password`

### 10.3 推荐分层

建议沿用当前结构：

1. `controller`：只负责接参与响应
2. `application`：负责编排注册用例
3. `service`：负责会员注册业务规则
4. `luoluo-common`：复用验证码、摘要、发送器、限流等基础能力

一个比较自然的类拆分方式如下：

- `MemberRegisterController`
- `PublicMemberRegisterController`
- `MemberRegisterApplicationService`
- `MemberRegisterService`
- `MemberRegisterStrategy`（按 `phone/email` 扩展）

### 10.4 推荐注册流程

以“邮箱注册”为例，建议流程如下：

1. 公开入口接收注册请求
2. 如果启用了图形验证码，先校验图形验证码
3. 对邮箱注册做发送/提交频率控制
4. `application` 层校验入参完整性
5. `service` 层校验用户名、邮箱是否已存在
6. 通过 `MemberVerifyCodeService.verifyAndConsume("email", email, emailCode)` 校验并核销验证码
7. 对密码做编码后写入 `sys_user`
8. 根据需要补齐默认昵称、用户类型、状态字段
9. 注册成功后可选择：
   - 只返回注册成功
   - 或直接自动登录并签发 token

### 10.5 注册实现时建议遵守的原则

- 控制器不直接写注册规则
- 验证码能力继续复用 `MemberVerifyCodeService`
- 用户唯一性校验放在会员业务层
- 密码统一走 `PasswordEncoder`
- 除非有明确需求，不建议注册时继续保留明文密码兼容逻辑
- 如果注册成功后自动登录，继续复用 `MemberSessionService`

## 11. 后续建议

如果这份登录注册链路准备继续往生产化方向推进，建议优先做下面几项：

1. 把短信/邮箱验证码从“明文存储”改成“摘要存储”
2. 关闭 `allow-plain-password=true`，避免生产环境明文密码兜底
3. 明确区分“公开接口”和“内部接口”，避免前端误接 `/luoluo/member/auth/send-code`
4. 注册功能落地时补充唯一索引、幂等控制和重复提交防护
5. 为登录、发码、注册补完整集成测试，覆盖正常、过期、重复使用、限流等场景

## 12. 总结

当前仓库已经具备比较完整的会员登录基础能力：

- 密码登录、短信登录、邮箱登录都已经分策略实现
- 公共发送验证码入口已经具备图形验证码校验和发送频率限制能力
- 会话签发统一收口到 `luoluo-common.AuthSessionService`
- token 数据库存摘要、Redis 存真实登录态的设计已经落地

真正还没有补齐的是“注册”这一条业务用例本身，以及验证码链路进一步安全化这部分。如果后续按本文建议继续扩展，整体结构是可以平滑长出来的。
