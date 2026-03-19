# member-backend

独立的个人中心后端（与 `java-backend` 同级），负责：

- 登录认证（策略模式）：`password/sms/email`
- 会员会话管理
- 个人中心资料查询

## 启动

```bash
cd member-backend
mvn spring-boot:run
```

## 主要接口

- `POST /api/v1/member/auth/login`
- `GET /api/v1/member/profile/me`（`Authorization: Bearer <token>`）

## SQL 目录

- `script/sql/member/baseline`
- `script/sql/member/migration`
- `script/sql/member/rollback`
- `script/sql/member/data`
