# 会员登录注册实现说明

## 1. 当前架构结论

当前项目已经采用类似 `cde-base` 的多模块结构，但运行时只有一个统一启动入口：

- `luoluo-admin`：唯一启动模块，负责统一装配
- `luoluo-common`：平台级公共认证与基础设施能力
- `luoluo-member`：会员认证、会话、资料等业务能力
- `luoluo-system`：公共接口暴露层
- `luoluo-rag`：RAG 能力模块

其中认证基础设施已经沉淀到 `luoluo-common`：

- `Sa-Token + Redis`：登录态真实来源
- `sys_auth_session`：认证会话记录表，只存 `token_digest`
- `sys_verify_code_record`：验证码记录表，只存 `code_digest`
- 通用服务：`AuthSessionService`、`VerifyCodeService`、`AuthDigestService`

`luoluo-member` 负责会员业务编排：

- `controller`
- `application`
- `service`
- `mapper`
- `domain`

它只关心 `sys_user` 用户体系和会员登录场景，不再直接承载底层验证码与会话基础设施实现。

## 2. 接口入口与登录链路

### 2.1 固定前缀

- 当前项目统一使用 `/luoluo` 作为业务接口前缀。

### 2.2 当前接口入口

- 登录接口：`POST /luoluo/member/auth/login`
- 图形验证码接口：`GET /luoluo/system/public/captcha/image`
- 发送验证码接口：`POST /luoluo/system/public/member/auth/code/send`
- 当前登录用户信息接口：`GET /luoluo/member/profile/me`

### 2.3 登录流程

1. 控制器接收 `MemberLoginRequest`
2. `MemberAuthApplicationService` 编排登录用例
3. `MemberAuthService` 根据 `grantType` 选择具体登录策略
4. 登录策略校验用户身份
5. 校验通过后调用 `MemberSessionService`
6. `MemberSessionService` 补齐会员上下文后委托 `luoluo-common` 的 `AuthSessionService`
7. `AuthSessionService` 调用 `Sa-Token` 生成 token
8. token 明文只返回给客户端，并保存在 Redis 登录态体系中
9. 数据库 `sys_auth_session` 仅保存 `token_digest`
10. 最终统一返回 `R<MemberLoginResponse>`

### 2.4 支持的登录方式

当前支持三种 `grantType`：

- `password`
- `sms`
- `email`

对应策略类：

- `PasswordLoginStrategy`
- `SmsLoginStrategy`
- `EmailLoginStrategy`

### 2.5 验证码发送流程

验证码发送采用统一入口 + 策略分发的方式，思路与 `cde-base` 保持一致：

1. `luoluo-system` 的公共控制器接收 `MemberSendVerifyCodeRequest`
2. 若开启图形验证码，则公共接口层先校验图形验证码
3. 公共接口层处理同目标发送频率限制
4. `MemberAuthApplicationService` 编排发送验证码用例
5. `MemberSendCodeService` 根据 `grantType` 路由到具体发送策略
6. `SmsSendCodeStrategy` 或 `EmailSendCodeStrategy` 校验发送目标参数
7. 生成验证码后调用 `MemberVerifyCodeService`
8. `MemberVerifyCodeService` 委托 `luoluo-common.VerifyCodeService`
9. `luoluo-common` 负责 Redis 缓存、摘要落库、发送流水记录

职责边界如下：

- `luoluo-system`：对外暴露公共接口
- `luoluo-member`：承载会员认证业务编排与规则
- `luoluo-common`：承载通用认证基础设施

## 3. Token 设计说明

### 3.1 为什么 token 保留在 Redis

Redis 仍然是登录态的唯一真实来源，原因是：

1. 登录态校验属于高频读取，Redis 更适合
2. `Sa-Token` 原生适配这条链路
3. 后续做踢下线、续期、在线会话管理更方便

### 3.2 为什么数据库不保存明文 token

`sys_auth_session` 主要承担：

- 审计记录
- 会话留痕
- 后台会话管理基础

因此只存：

- `token_digest`

不存：

- 明文 `token`

## 4. 验证码架构说明

### 4.1 当前设计

验证码基础能力已经从会员模块沉淀到 `luoluo-common`：

- `VerifyCodeService`：通用验证码服务
- `AuthDigestService`：统一摘要能力
- `sys_verify_code_record`：验证码记录表

### 4.2 为什么验证码也不存明文

当前设计中：

1. 生成验证码时，系统只在发送链路短暂持有明文
2. Redis 中缓存的是验证码摘要
3. 数据库中记录的是 `code_digest`
4. 用户输入验证码时，先对输入值做摘要，再与缓存中的摘要比对
5. 校验成功后删除 Redis，并把数据库记录标记为已使用

这样做可以降低敏感信息泄露风险，并保持缓存层和持久层的统一安全策略。

### 4.3 会员模块如何复用验证码能力

`luoluo-member` 中的 `MemberVerifyCodeService` 现在只是会员侧薄封装，负责补齐：

- `namespace=member_auth`
- `bizType=LOGIN`
- `subjectType=SYS_USER`
- mock 配置
- 验证码 TTL 配置

真正的缓存、摘要、落库、核销逻辑都在 `luoluo-common`。

## 5. 当前登录校验逻辑

### 5.1 密码登录

1. 根据用户名查询 `sys_user`
2. 校验用户状态是否合法
3. 优先走 `PasswordEncoder`
4. 若配置允许开发兼容，则允许明文密码对比
5. 登录成功后创建认证会话

### 5.2 短信登录

1. 校验手机号和短信验证码参数
2. 调用 `MemberVerifyCodeService#verifyAndConsume`
3. 委托 `luoluo-common.VerifyCodeService` 完成摘要校验与核销
4. 根据手机号查询 `sys_user`
5. 成功后创建认证会话

### 5.3 邮箱登录

1. 校验邮箱和邮箱验证码参数
2. 对邮箱做标准化处理
3. 调用 `MemberVerifyCodeService#verifyAndConsume`
4. 委托 `luoluo-common.VerifyCodeService` 完成摘要校验与核销
5. 根据邮箱查询 `sys_user`
6. 成功后创建认证会话

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

## 7. 后续注册实现建议

如果后续继续做注册，建议沿用当前分层：

1. `controller`
2. `application`
3. `service`
4. `luoluo-common`

建议原则：

- 控制器只负责接参与返回
- 应用层负责用例编排
- 会员服务只承载会员业务规则
- 验证码校验仍通过 `MemberVerifyCodeService` 调 `luoluo-common.VerifyCodeService`
- `luoluo-common` 不直接写会员注册规则，只沉淀可复用的认证基础能力

## 8. 这套架构的优点

1. 认证基础设施可复用，后续后台用户、管理端、绑定邮箱、找回密码都能复用
2. 安全边界更清晰，Redis 保存真实短期状态，数据库保存摘要与审计信息
3. 会员模块更聚焦，只关心会员用户与登录业务
4. 扩展更自然，后续可以继续扩展设备、IP、限流、更多业务场景与模板
