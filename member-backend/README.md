# member-backend

独立的个人中心后端（与 `java-backend`、`common` 同级），负责：

- 登录认证（策略模式）：`password/sms/email`
- 会员会话管理
- 个人中心资料查询

## 依赖

本模块依赖根目录 `common` 模块：

- Redis 封装
- Sa-Token 注解与拦截
- MyBatis-Plus 通用配置

## 启动

推荐先在仓库根目录聚合构建：

```bash
cd ..
mvn -pl member-backend -am clean package
```

单独启动：

```bash
cd member-backend
mvn spring-boot:run
```

## 主要接口

- `POST /luoluo/member/auth/login`
- `GET /luoluo/member/profile/me`（`Authorization: Bearer <token>`）

公共验证码接口已迁移到 `ll-system`：

- `GET /luoluo/system/public/captcha/image`
- `POST /luoluo/system/public/member/auth/code/send`

说明：当前默认不强制图形验证码，只保留发送频率限制；如需启用图形验证码，可打开 `app.member.auth.image-captcha-enabled`。

## SQL 目录（手动执行）

- `../script/sql/member/baseline`
- `../script/sql/member/rollback`

