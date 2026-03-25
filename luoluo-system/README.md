# luoluo-system

公共系统接口模块，负责承接跨业务复用的公共接口入口。

当前已对外暴露的接口：

- `GET /luoluo/system/public/captcha/image`
- `POST /luoluo/system/public/member/auth/code/send`

## 当前验证码发送流程

默认情况下，发送验证码只做 Redis 发送频率限制，不强制要求图形验证码。

如果打开 `app.member.auth.image-captcha-enabled`，则流程如下：

1. 调用 `GET /luoluo/system/public/captcha/image` 获取图形验证码。
2. 前端将返回的 `imageBase64` 渲染成图片。
3. 用户输入图形验证码后，再调用 `POST /luoluo/system/public/member/auth/code/send`。
4. 请求体携带 `grantType`、`captchaKey`、`captchaCode` 与 `phone` 或 `email`。

说明：

- `imageBase64` 是 SVG 的 Base64 内容，前端可拼接为 `data:image/svg+xml;base64,${imageBase64}`。
- 同一手机号或邮箱默认 `60` 秒内只能发送一次验证码。

## 架构职责

- `luoluo-system`：对外暴露公共接口
- `luoluo-member`：提供会员认证业务编排与规则
- `luoluo-common`：提供 Redis、Sa-Token、MyBatis-Plus 与认证基础设施

## 启动

统一从 `luoluo-admin` 启动：

```bash
cd luoluo-admin
mvn spring-boot:run
```
