# 会员登录、邮箱验证码、短信验证码实现审查说明

本文档基于当前仓库代码整理，目标是把会员登录、邮箱验证码、短信验证码的实际实现方式、类调用链、请求参数、响应结构、当前配置状态和需要关注的问题一次性说清楚，便于后续审核和改代码。

## 1. 当前结论速览

### 1.1 当前对外接口

- 登录接口：`POST /luoluo/member/auth/login`
- 会员资料接口：`GET /luoluo/member/profile/me`
- 图形验证码接口：`GET /luoluo/system/public/captcha/image`
- 对外发送验证码接口：`POST /luoluo/system/public/member/auth/code/send`
- 模块内部发送验证码接口：`POST /luoluo/member/auth/send-code`

说明：

- 对外推荐使用的是 `POST /luoluo/system/public/member/auth/code/send`
- `POST /luoluo/member/auth/send-code` 是 `luoluo-member` 模块直接暴露的内部发送入口，它不会做图形验证码校验，也不会做发送频率限制
- `app.api-prefix=/api/v1` 不作用于这组接口，因为这些 Controller 使用的是硬编码路径，不是 `${app.api-prefix}`

### 1.2 当前支持的登录方式

- `grantType=password`
- `grantType=sms`
- `grantType=email`

对应策略类：

- `PasswordLoginStrategy#authenticate`
- `SmsLoginStrategy#authenticate`
- `EmailLoginStrategy#authenticate`

### 1.3 当前配置状态

当前 [`luoluo-admin/src/main/resources/application.yml`](./luoluo-admin/src/main/resources/application.yml) 中，与本功能直接相关的配置是：

- `spring.mail.host=smtp.qq.com`
- `spring.mail.port=465`
- `spring.mail.username=2130265486@qq.com`
- `spring.mail.password=${SPRING_MAIL_PASSWORD:}`
- `spring.mail.protocol=smtp`
- `spring.mail.properties.mail.smtp.ssl.enable=true`
- `app.member.auth.session-ttl-seconds=86400`
- `app.member.auth.verify-code-ttl-seconds=120`
- `app.member.auth.verify-code-send-interval-seconds=60`
- `app.member.auth.image-captcha-enabled=false`
- `app.member.auth.allow-mock-verify-code=false`
- `app.member.auth.mock-verify-code=123456`
- `app.member.auth.allow-plain-password=true`
- `app.member.sms.aliyun.enabled=false`

### 1.4 按当前配置推导出来的实际行为

- 邮箱验证码理论上可用，但前提是 `SPRING_MAIL_PASSWORD` 环境变量已配置为 QQ 邮箱 SMTP 授权码
- 短信验证码当前不可用，因为 `app.member.sms.aliyun.enabled=false`
- 同时又配置了 `app.member.auth.allow-mock-verify-code=false`
- 这意味着当前短信发送链路不会回退成“可直接返回 mock 验证码”的模式，而是会在 `SmsSendCodeStrategy#send` 中抛出 `503 SERVICE_UNAVAILABLE`
- 邮箱验证码在邮件发送器未正确配置时，也会因为 `allow-mock-verify-code=false` 而抛出 `503 SERVICE_UNAVAILABLE`
- 当前密码登录允许明文密码兜底，因为 `allow-plain-password=true`

### 1.5 这次审查里最重要的几个事实

- 验证码不是摘要存储，当前代码把明文验证码直接写进了 Redis，也直接写进了 `sys_verify_code_record.code_value`
- 图形验证码是摘要存储，图形验证码缓存的是 SHA-256 摘要，不是明文
- 登录成功后数据库保存的是 `token_digest`，不是明文 token
- 登录后的鉴权标头名是 `Authorization`
- 登录后的推荐请求头格式是 `Authorization: Bearer <token>`
- `MemberSendVerifyCodeRequest` 里的 `captchaKey` 和 `captchaCode` 只在公开发送验证码接口里有用，在 `luoluo-member` 直接发送接口里不会被消费

## 2. 模块和职责划分

### 2.1 `luoluo-system`

负责公开入口层，主要做两件事：

- 公开暴露图形验证码接口
- 公开暴露验证码发送接口，并补上图形验证码校验和发送频率限制

核心类：

- `PublicCaptchaController`
- `PublicMemberAuthController`
- `PublicMemberAuthApplicationService`

### 2.2 `luoluo-member`

负责会员认证业务本身，主要做三件事：

- 接收登录请求
- 按 `grantType` 路由到对应登录策略
- 按 `grantType` 路由到对应验证码发送策略

核心类：

- `MemberAuthController`
- `MemberAuthApplicationService`
- `MemberAuthService`
- `MemberSendCodeService`
- `PasswordLoginStrategy`
- `SmsLoginStrategy`
- `EmailLoginStrategy`
- `SmsSendCodeStrategy`
- `EmailSendCodeStrategy`
- `MemberVerifyCodeService`
- `MemberSessionService`
- `MemberUserService`

### 2.3 `luoluo-common`

负责通用基础设施，主要做四件事：

- Redis 读写
- 图形验证码生成与校验
- 登录会话创建
- 邮件发送与验证码记录落库

核心类：

- `RedisClient`
- `ImageCaptchaService`
- `CaptchaSendLimitService`
- `VerifyCodeService`
- `AuthSessionService`
- `AuthDigestService`
- `CommonMailSender`

## 3. 关键数据对象

### 3.1 登录请求对象 `MemberLoginRequest`

字段如下：

- `grantType`
- `username`
- `password`
- `phone`
- `smsCode`
- `email`
- `emailCode`

不同登录方式真正会用到的字段：

- 密码登录：`grantType`、`username`、`password`
- 短信登录：`grantType`、`phone`、`smsCode`
- 邮箱登录：`grantType`、`email`、`emailCode`

### 3.2 发送验证码请求对象 `MemberSendVerifyCodeRequest`

字段如下：

- `grantType`
- `captchaKey`
- `captchaCode`
- `phone`
- `email`

不同发送方式真正会用到的字段：

- 发送短信验证码：`grantType=sms`、`phone`
- 发送邮箱验证码：`grantType=email`、`email`
- 如果 `image-captcha-enabled=true`，还必须额外带上 `captchaKey` 和 `captchaCode`

### 3.3 发送验证码响应对象 `MemberSendVerifyCodeResponse`

字段如下：

- `requestId`
- `grantType`
- `target`
- `expiresIn`
- `issuedCode`

说明：

- `target` 是脱敏后的手机号或邮箱
- `expiresIn` 是验证码有效期，当前配置是 `120` 秒
- `issuedCode` 只有在“允许 mock 验证码”并且底层发送器暴露调试码时才会返回
- 当前配置 `allow-mock-verify-code=false`，所以正常情况下 `issuedCode` 不会返回真实验证码

### 3.4 登录响应对象 `MemberLoginResponse`

字段如下：

- `token`
- `tokenType`
- `expiresIn`
- `grantType`
- `user`

其中 `user` 为 `MemberUserSummary`，包含：

- `id`
- `username`
- `displayName`
- `phone`
- `email`
- `userType`

### 3.5 统一响应包装 `R<T>`

所有接口最终都是：

```json
{
  "code": 0,
  "message": "xxx",
  "data": {}
}
```

其中：

- 成功码：`0`
- 失败码：常见有 `400`、`401`、`403`、`404`

## 4. 数据库和缓存落点

### 4.1 用户表

会员登录查的是 `sys_user` 表，对应实体 `MemberUser`。

本功能实际会用到的字段：

- `user_id`
- `username`
- `password_hash`
- `phone`
- `email`
- `display_name`
- `user_type`
- `status`
- `deleted`

查询时统一要求：

- `status=ACTIVE`
- `deleted=0` 由 MyBatis-Plus 逻辑删除控制

### 4.2 登录会话表

登录成功后会往 `sys_auth_session` 表写一条记录，对应实体 `AuthSession`。

核心字段：

- `session_id`
- `subject_id`
- `subject_type`
- `login_type`
- `token_digest`
- `expires_at`
- `revoked`
- `device_type`
- `client_ip`

重要说明：

- 数据库里保存的是 `token_digest`
- 明文 token 是通过 `Sa-Token` 返回给前端的，不直接入库

### 4.3 验证码记录表

验证码签发后会往 `sys_verify_code_record` 表写一条记录，对应实体 `VerifyCodeRecord`。

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
- `code_value`
- `expires_at`
- `used`
- `used_at`
- `remark`

非常重要：

- 当前实现里写入数据库的是 `code_value` 明文验证码
- 不是摘要，不是加密值
- 这一点和根目录之前已有的旧文档描述不一致，旧文档已经过时

### 4.4 Redis 键设计

图形验证码 Redis Key：

- `auth:image_captcha:member_send_code:<captchaKey>`

发送频率限制 Redis Key：

- `auth:send_limit:member_send_code:sms:<phone>`
- `auth:send_limit:member_send_code:email:<lowercase-email>`

业务验证码 Redis Key：

- `auth:verify_code:member_auth:LOGIN:sms:<phone>`
- `auth:verify_code:member_auth:LOGIN:email:<lowercase-email>`

重要说明：

- 图形验证码缓存的是摘要
- 业务短信/邮箱验证码缓存的是明文

## 5. 图形验证码流程

### 5.1 图形验证码接口

接口：

- `GET /luoluo/system/public/captcha/image`

调用链：

1. `PublicCaptchaController#image`
2. `ImageCaptchaService#create`
3. `RedisClient#set`

### 5.2 具体逻辑

`PublicCaptchaController#image` 会先看配置：

- 如果 `app.member.auth.image-captcha-enabled=false`，直接返回：
  - `captchaKey=null`
  - `imageBase64=null`
  - `expiresIn=0`
- 如果开启，则调用 `ImageCaptchaService#create("member_send_code", length, ttlSeconds)`

`ImageCaptchaService#create` 做的事情：

1. 生成一个随机 `captchaKey`
2. 生成一个长度为 `imageCaptchaLength` 的随机验证码字符串
3. 把验证码转大写后做 SHA-256 摘要
4. 把摘要写入 Redis
5. 生成 SVG 图片
6. 返回 `captchaKey + imageBase64 + expiresIn`

### 5.3 校验逻辑

发送验证码前，公开发送接口会调用：

1. `PublicMemberAuthApplicationService#verifyImageCaptcha`
2. `ImageCaptchaService#verifyAndConsume`
3. `RedisClient#get`
4. 比对摘要
5. 校验成功后 `RedisClient#delete`

说明：

- 图形验证码是一次性的
- 校验成功后立即删除
- 图形验证码不参与登录接口本身，只参与“发送短信/邮箱验证码”这一步

## 6. 对外发送验证码总流程

### 6.1 推荐调用入口

推荐走：

- `POST /luoluo/system/public/member/auth/code/send`

因为这条链路包含：

- 图形验证码校验
- 发送频率限制
- 再转发到 `luoluo-member` 的业务发送能力

### 6.2 总调用链

公共发送入口完整调用链如下：

1. `PublicMemberAuthController#sendCode`
2. `PublicMemberAuthApplicationService#sendCode`
3. `PublicMemberAuthApplicationService#verifyImageCaptcha`
4. `CaptchaSendLimitService#tryAcquire`
5. `MemberAuthApplicationService#sendCode`
6. `MemberSendCodeService#send`
7. 根据 `grantType` 路由到：
   - `SmsSendCodeStrategy#send`
   - `EmailSendCodeStrategy#send`
8. 发送成功后调用 `MemberVerifyCodeService#recordAndCache`
9. `VerifyCodeService#issue`
10. `RedisClient#set`
11. `VerifyCodeRecordMapper#insert`

如果业务发送阶段抛异常，`PublicMemberAuthApplicationService#sendCode` 会调用：

- `CaptchaSendLimitService#release`

也就是失败时会释放刚刚占用的发送频控锁。

### 6.3 发送频率限制

发送间隔配置：

- `app.member.auth.verify-code-send-interval-seconds=60`

实现类：

- `CaptchaSendLimitService`

关键方法：

- `tryAcquire(namespace, targetType, targetValue, intervalSeconds)`
- `getRemainingSeconds(namespace, targetType, targetValue)`
- `release(namespace, targetType, targetValue)`

实际行为：

- 同一手机号 60 秒内只能发送一次
- 同一邮箱 60 秒内只能发送一次
- 如果频繁发送，会返回 `400 BAD_REQUEST`
- 错误消息会带上剩余等待秒数

## 7. 邮箱验证码实现细节

### 7.1 邮箱验证码请求参数

对外推荐发送接口：

- `POST /luoluo/system/public/member/auth/code/send`

请求体：

```json
{
  "grantType": "email",
  "email": "demo@example.com"
}
```

如果启用了图形验证码，请求体需要变成：

```json
{
  "grantType": "email",
  "email": "demo@example.com",
  "captchaKey": "图形验证码key",
  "captchaCode": "用户输入的图形验证码"
}
```

### 7.2 邮箱验证码发送调用链

1. `PublicMemberAuthController#sendCode`
2. `PublicMemberAuthApplicationService#sendCode`
3. `MemberAuthApplicationService#sendCode`
4. `MemberSendCodeService#send`
5. `EmailSendCodeStrategy#send`
6. `CommonMailSender#sendText`
7. `MemberVerifyCodeService#recordAndCache`
8. `VerifyCodeService#issue`
9. `RedisClient#set`
10. `VerifyCodeRecordMapper#insert`

### 7.3 `EmailSendCodeStrategy#send` 的实际逻辑

这个方法会做下面这些事：

1. 读取 `request.getEmail()`
2. 判空
3. 把邮箱转成小写：`email.trim().toLowerCase()`
4. 随机生成 6 位验证码
5. 读取验证码 TTL，当前是 120 秒
6. 从 `app.member.email.login-subject` 读取邮件主题
7. 从 `app.member.email.login-content-template` 读取模板，并把验证码和有效分钟数格式化进去
8. 调用 `CommonMailSender#sendText`
9. 如果当前不允许 mock，但底层发送器只返回 mock 回执，则抛出 `503`
10. 发送成功后调用 `MemberVerifyCodeService#recordAndCache`
11. 返回脱敏后的邮箱和过期时间

### 7.4 `CommonMailSender#sendText` 的实际逻辑

这个方法是邮件发送底座。

执行逻辑如下：

1. 校验 `to`、`subject`、`content` 非空
2. 尝试从 Spring 容器获取 `JavaMailSender`
3. 读取环境中的 `spring.mail.host`
4. 如果 `JavaMailSender` 不存在，或者 `spring.mail.host` 为空，则直接返回 mock 回执
5. 如果存在真实邮件配置，则创建 `MimeMessage`
6. 设置收件人
7. 读取 `spring.mail.username` 作为发件人地址
8. 设置主题和正文
9. 调用 `javaMailSender.send(mimeMessage)`
10. 返回 `MailSendReceipt(provider, requestId, debugPayloadVisible)`

### 7.5 当前邮箱验证码是否可用

按当前配置推断：

- `spring.mail.host=smtp.qq.com`
- `spring.mail.username=2130265486@qq.com`
- `spring.mail.password=${SPRING_MAIL_PASSWORD:}`

因此是否真正可用，取决于：

- 你是否在运行环境里设置了 `SPRING_MAIL_PASSWORD`
- 该值是否是 QQ 邮箱的 SMTP 授权码
- 该 QQ 邮箱是否开通 SMTP

如果以上条件都满足，当前邮箱验证码链路可正常发信。

如果没有配置 `SPRING_MAIL_PASSWORD`，通常会出现两种情况：

- `JavaMailSender` 存在但发信鉴权失败，报邮件发送失败
- 或者底层被判定为 mock 发信，再因为 `allow-mock-verify-code=false` 抛出 `503`

### 7.6 邮箱验证码校验登录链路

登录接口：

- `POST /luoluo/member/auth/login`

请求体：

```json
{
  "grantType": "email",
  "email": "demo@example.com",
  "emailCode": "123456"
}
```

调用链：

1. `MemberAuthController#login`
2. `MemberAuthApplicationService#login`
3. `MemberAuthService#login`
4. `EmailLoginStrategy#authenticate`
5. `MemberVerifyCodeService#verifyAndConsume("email", normalizedEmail, emailCode)`
6. `VerifyCodeService#verifyAndConsume`
7. `RedisClient#get`
8. 对比 Redis 中缓存的验证码明文
9. 成功后 `RedisClient#delete`
10. `VerifyCodeRecordMapper#update` 把对应记录标记成 `used=true`
11. `MemberUserService#findActiveByEmail`
12. `MemberSessionService#createSession`
13. `AuthSessionService#createSession`
14. `StpUtil.login`
15. `AuthDigestService#sha256Hex(token)`
16. `AuthSessionMapper#insert`
17. 返回 `MemberLoginResponse`

邮箱登录失败的常见原因：

- `email` 为空
- `emailCode` 为空
- Redis 里没有对应验证码
- 验证码不匹配
- 验证码已过期
- 数据库中不存在 `status=ACTIVE` 的该邮箱用户

## 8. 短信验证码实现细节

### 8.1 短信验证码请求参数

对外推荐发送接口：

- `POST /luoluo/system/public/member/auth/code/send`

请求体：

```json
{
  "grantType": "sms",
  "phone": "13800138000"
}
```

如果启用了图形验证码，请求体需要变成：

```json
{
  "grantType": "sms",
  "phone": "13800138000",
  "captchaKey": "图形验证码key",
  "captchaCode": "用户输入的图形验证码"
}
```

### 8.2 短信验证码发送调用链

1. `PublicMemberAuthController#sendCode`
2. `PublicMemberAuthApplicationService#sendCode`
3. `MemberAuthApplicationService#sendCode`
4. `MemberSendCodeService#send`
5. `SmsSendCodeStrategy#send`
6. `MemberSmsSender#sendLoginCode`
7. `MemberVerifyCodeService#recordAndCache`
8. `VerifyCodeService#issue`
9. `RedisClient#set`
10. `VerifyCodeRecordMapper#insert`

### 8.3 `MemberSmsConfig#memberSmsSender` 的选择逻辑

这里会根据配置决定具体注入哪个发送器：

- 如果 `app.member.sms.aliyun.enabled=true`，使用 `AliyunMemberSmsSender`
- 否则使用 `MockMemberSmsSender`

### 8.4 `SmsSendCodeStrategy#send` 的实际逻辑

这个方法会做下面这些事：

1. 读取 `request.getPhone()`
2. 判空
3. `trim` 标准化手机号
4. 随机生成 6 位验证码
5. 读取验证码 TTL，当前是 120 秒
6. 调用 `memberSmsSender.sendLoginCode(normalizedPhone, verifyCode, ttlSeconds)`
7. 如果当前不允许 mock，但底层发送器暴露了调试验证码，则抛出 `503 SERVICE_UNAVAILABLE`
8. 发送成功后调用 `MemberVerifyCodeService#recordAndCache`
9. 返回脱敏手机号和过期时间

### 8.5 `AliyunMemberSmsSender#sendLoginCode` 的实际逻辑

如果启用了阿里云短信，这个类会：

1. 在构造时校验以下配置是否完整：
   - `app.member.sms.aliyun.access-key-id`
   - `app.member.sms.aliyun.access-key-secret`
   - `app.member.sms.aliyun.region-id`
   - `app.member.sms.aliyun.endpoint`
   - `app.member.sms.aliyun.sign-name`
   - `app.member.sms.aliyun.login-template-code`
   - `app.member.sms.aliyun.code-param-name`
   - `app.member.sms.aliyun.ttl-minutes-param-name`
2. 创建 `IAcsClient`
3. 构造 `SendSmsRequest`
4. 把验证码和有效分钟数序列化成模板参数 JSON
5. 调用阿里云 `acsClient.getAcsResponse(request)`
6. 检查返回码是否为 `OK`
7. 返回 `SmsSendReceipt(provider, templateId, requestId, false)`

### 8.6 `MockMemberSmsSender#sendLoginCode` 的实际逻辑

如果阿里云短信未启用，则会走：

- `MockMemberSmsSender#sendLoginCode`

它不会真的发短信，只会返回：

- `provider=member-mock-sms`
- `requestId=随机值`
- `debugCodeVisible=true`

### 8.7 当前短信验证码是否可用

按当前配置：

- `app.member.sms.aliyun.enabled=false`
- `app.member.auth.allow-mock-verify-code=false`

因此当前真实行为是：

1. `MemberSmsConfig` 注入 `MockMemberSmsSender`
2. `MockMemberSmsSender` 返回 `debugCodeVisible=true`
3. `SmsSendCodeStrategy#send` 发现当前不允许 mock
4. 直接抛出 `503 SERVICE_UNAVAILABLE`
5. 不会继续写入验证码缓存和记录表

也就是说：

- 当前短信验证码功能在现有配置下实际上不可用

### 8.8 短信验证码校验登录链路

登录接口：

- `POST /luoluo/member/auth/login`

请求体：

```json
{
  "grantType": "sms",
  "phone": "13800138000",
  "smsCode": "123456"
}
```

调用链：

1. `MemberAuthController#login`
2. `MemberAuthApplicationService#login`
3. `MemberAuthService#login`
4. `SmsLoginStrategy#authenticate`
5. `MemberVerifyCodeService#verifyAndConsume("sms", phone, smsCode)`
6. `VerifyCodeService#verifyAndConsume`
7. `RedisClient#get`
8. 比对 Redis 中缓存的验证码明文
9. 成功后 `RedisClient#delete`
10. `VerifyCodeRecordMapper#update` 把记录标记为已使用
11. `MemberUserService#findActiveByPhone`
12. `MemberSessionService#createSession`
13. `AuthSessionService#createSession`
14. `StpUtil.login`
15. `AuthSessionMapper#insert`
16. 返回 `MemberLoginResponse`

当前要注意：

- 虽然短信登录逻辑是完整的
- 但由于“短信验证码发送”当前不可用，所以实际前置数据拿不到

## 9. 密码登录实现细节

### 9.1 密码登录请求参数

接口：

- `POST /luoluo/member/auth/login`

请求体：

```json
{
  "grantType": "password",
  "username": "demo_user",
  "password": "123456"
}
```

### 9.2 密码登录调用链

1. `MemberAuthController#login`
2. `MemberAuthApplicationService#login`
3. `MemberAuthService#login`
4. `PasswordLoginStrategy#authenticate`
5. `MemberUserService#findActiveByUsername`
6. `PasswordEncoder#matches`
7. 如命中明文兼容逻辑则走 `raw.equals(storedHash)`
8. `MemberSessionService#createSession`
9. `AuthSessionService#createSession`
10. `StpUtil.login`
11. `AuthDigestService#sha256Hex(token)`
12. `AuthSessionMapper#insert`
13. 返回 `MemberLoginResponse`

### 9.3 `PasswordLoginStrategy#authenticate` 的关键逻辑

它会按以下顺序校验：

1. 校验 `username` 和 `password` 是否为空
2. 查 `sys_user`，要求 `status=ACTIVE`
3. 优先使用 `PasswordEncoder#matches(raw, storedHash)` 校验
4. 如果 `storedHash` 不是合法加密串，或者加密比对没过，则进入兜底
5. 如果 `app.member.auth.allow-plain-password=true`，则允许直接比较 `raw.equals(storedHash)`

这意味着当前配置下：

- 项目为了兼容本地演示数据，允许明文密码登录
- 这是一个明显应该在生产环境审查的点

## 10. 统一登录主链路说明

### 10.1 `MemberAuthService#login` 的职责

`MemberAuthService#login` 是整个登录的总调度器。

它做的事：

1. 读取 `request.getGrantType()`
2. 转成小写
3. 从 `strategyMap` 中找到对应的 `MemberLoginStrategy`
4. 调用该策略的 `authenticate(request)`
5. 拿到 `MemberUser`
6. 调用 `MemberSessionService#createSession(userId, grantType)`
7. 根据会话过期时间算出 `expiresIn`
8. 组装 `MemberLoginResponse`

### 10.2 `strategyMap` 是怎么来的

`MemberAuthService` 构造时会接收 Spring 注入的所有 `MemberLoginStrategy` 实现类：

- `PasswordLoginStrategy`
- `SmsLoginStrategy`
- `EmailLoginStrategy`

然后以 `grantType().toLowerCase()` 作为 key 组装成一个 map。

对应关系：

- `password` -> `PasswordLoginStrategy`
- `sms` -> `SmsLoginStrategy`
- `email` -> `EmailLoginStrategy`

### 10.3 登录成功后 token 是怎么来的

登录成功后，`MemberSessionService#createSession` 会调用：

- `AuthSessionService#createSession`

里面的实际逻辑是：

1. 校验 `subjectId` 和 `ttlSeconds`
2. 计算 `loginType`
3. 计算 `deviceType`
4. 调用 `StpUtil.login(subjectId, SaLoginParameter)`
5. 通过 `StpUtil.getTokenValue()` 取回 token
6. 调用 `AuthDigestService#sha256Hex(token)` 生成 token 摘要
7. 把会话记录写入 `sys_auth_session`
8. 返回 `AuthSessionResult(sessionId, token, expiresAt)`

这里的关键点是：

- 明文 token 给前端
- 数据库只留摘要
- Redis 中的登录态由 `Sa-Token` 负责

## 11. 登录后如何带 token

### 11.1 推荐方式

请求头：

```http
Authorization: Bearer <token>
```

原因：

- `sa-token.token-name=Authorization`
- `sa-token.token-prefix=Bearer`
- `sa-token.is-read-header=true`

### 11.2 额外说明

当前还配置了：

- `sa-token.is-read-body=true`

所以从能力上说，也支持从请求体中读取 token，但实际联调时建议统一走请求头。

### 11.3 获取当前登录用户资料

接口：

- `GET /luoluo/member/profile/me`

调用链：

1. `MemberProfileController#me`
2. `@MemberLoginRequired`
3. `MemberProfileApplicationService#getCurrentProfile`
4. `MemberProfileService#getCurrentProfile`
5. `MemberSessionService#getCurrentLoginUserId`
6. `AuthSessionService` 通过 `StpUtil` 识别当前登录人
7. `MemberUserService#findActiveById`
8. 返回 `MemberProfileResponse`

## 12. 典型请求示例

### 12.1 发送邮箱验证码

```http
POST /luoluo/system/public/member/auth/code/send
Content-Type: application/json

{
  "grantType": "email",
  "email": "demo@example.com"
}
```

### 12.2 邮箱验证码登录

```http
POST /luoluo/member/auth/login
Content-Type: application/json

{
  "grantType": "email",
  "email": "demo@example.com",
  "emailCode": "123456"
}
```

### 12.3 发送短信验证码

```http
POST /luoluo/system/public/member/auth/code/send
Content-Type: application/json

{
  "grantType": "sms",
  "phone": "13800138000"
}
```

### 12.4 短信验证码登录

```http
POST /luoluo/member/auth/login
Content-Type: application/json

{
  "grantType": "sms",
  "phone": "13800138000",
  "smsCode": "123456"
}
```

### 12.5 密码登录

```http
POST /luoluo/member/auth/login
Content-Type: application/json

{
  "grantType": "password",
  "username": "demo_user",
  "password": "123456"
}
```

### 12.6 登录后查询当前会员信息

```http
GET /luoluo/member/profile/me
Authorization: Bearer <token>
```

## 13. 常见异常和触发条件

### 13.1 400 BAD_REQUEST

常见触发点：

- `grantType` 为空
- `phone` 为空
- `email` 为空
- `username` 为空
- `password` 为空
- `captchaKey` 为空
- `captchaCode` 为空
- 图形验证码错误或过期
- 验证码发送过于频繁
- 不支持的 `grantType`

### 13.2 401 UNAUTHORIZED

常见触发点：

- 短信验证码错误
- 邮箱验证码错误
- 用户名密码不正确
- 手机号不存在对应有效用户
- 邮箱不存在对应有效用户
- 当前未登录访问 `/luoluo/member/profile/me`

### 13.3 503 SERVICE_UNAVAILABLE

常见触发点：

- 当前短信发送器是 mock，但系统又不允许 mock 验证码
- 当前邮件发送器是 mock，但系统又不允许 mock 验证码

### 13.4 502 BAD_GATEWAY

常见触发点：

- 阿里云短信请求失败
- 邮件底层发送失败

## 14. 当前最值得审核的实现问题

### 14.1 短信验证码当前不可用

原因：

- `app.member.sms.aliyun.enabled=false`
- `app.member.auth.allow-mock-verify-code=false`

结果：

- 发送短信验证码一定会失败

### 14.2 业务验证码是明文存储

当前实现中：

- Redis 里保存明文验证码
- `sys_verify_code_record.code_value` 里也保存明文验证码

这意味着：

- 运维侧拿到 Redis 或数据库读权限后可以直接看到业务验证码
- 这一点和图形验证码的摘要方案不一致

### 14.3 密码登录允许明文兼容

当前实现中：

- `allow-plain-password=true`

这意味着：

- 如果数据库里的 `password_hash` 实际存的就是明文，也能登录成功
- 适合开发演示，不适合正式环境

### 14.4 内部发送验证码接口绕过公开前置校验

`POST /luoluo/member/auth/send-code` 直接调用：

- `MemberAuthController#sendCode`

它不会做：

- 图形验证码校验
- 发送频率限制

所以如果未来前端、第三方或别的模块误接到了这个接口，会绕过公开安全约束。

### 14.5 `MemberSendVerifyCodeRequest` 中的图形验证码字段并不是所有入口都消费

DTO 里虽然有：

- `captchaKey`
- `captchaCode`

但只有：

- `PublicMemberAuthApplicationService#verifyImageCaptcha`

会实际读取它们。

换句话说：

- 字段是否生效，取决于调用的是哪个入口，不是 DTO 本身决定的

## 15. 建议你审核时重点确认的问题

建议这次审核重点确认下面这些点：

1. 业务验证码是否接受明文存储，还是要改成摘要存储
2. 短信验证码当前是要接入真实阿里云，还是临时允许 mock
3. 密码登录是否还需要保留明文兼容
4. `POST /luoluo/member/auth/send-code` 是否应该下线、加鉴权，或至少标成内部专用
5. 会员相关接口是否要统一接入 `/api/v1`，避免路径风格不一致
6. 邮件验证码模板内容是否要调整成明确、可读、可审计的正式文案

## 16. 代码定位清单

如果后续要改代码，最核心的文件就是这些：

- `luoluo-system/src/main/java/com/personalblog/ragbackend/system/controller/pub/PublicMemberAuthController.java`
- `luoluo-system/src/main/java/com/personalblog/ragbackend/system/application/PublicMemberAuthApplicationService.java`
- `luoluo-system/src/main/java/com/personalblog/ragbackend/system/controller/pub/PublicCaptchaController.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/controller/MemberAuthController.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/application/MemberAuthApplicationService.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/MemberAuthService.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/code/MemberSendCodeService.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/code/strategy/EmailSendCodeStrategy.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/code/strategy/SmsSendCodeStrategy.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/auth/strategy/PasswordLoginStrategy.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/auth/strategy/SmsLoginStrategy.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/auth/strategy/EmailLoginStrategy.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/MemberVerifyCodeService.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/MemberSessionService.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/MemberUserService.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/config/MemberProperties.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/config/MemberSmsConfig.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/code/sms/AliyunMemberSmsSender.java`
- `luoluo-member/src/main/java/com/personalblog/ragbackend/member/service/code/sms/MockMemberSmsSender.java`
- `luoluo-common/src/main/java/com/personalblog/ragbackend/common/mail/service/CommonMailSender.java`
- `luoluo-common/src/main/java/com/personalblog/ragbackend/common/auth/service/VerifyCodeService.java`
- `luoluo-common/src/main/java/com/personalblog/ragbackend/common/auth/service/AuthSessionService.java`
- `luoluo-common/src/main/java/com/personalblog/ragbackend/common/captcha/service/ImageCaptchaService.java`
- `luoluo-common/src/main/java/com/personalblog/ragbackend/common/captcha/service/CaptchaSendLimitService.java`
- `luoluo-common/src/main/java/com/personalblog/ragbackend/common/auth/service/AuthDigestService.java`

## 17. 一句话总结

当前代码里，会员登录三种模式的主干链路已经齐了，邮箱验证码在配置好邮件授权码后大概率能用，短信验证码在当前配置下实际上不可用，验证码存储策略目前是“图形验证码存摘要、短信/邮箱验证码存明文”，这几个点是后续改代码前最值得先确认的核心问题。
