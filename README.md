# Personal-Blog-RAG-Backend-Project

当前仓库采用类似 `cde-base` 的 Maven 多模块架构：

- `luoluo-admin`：统一启动模块，作为唯一运行入口
- `luoluo-rag`：RAG 能力模块（博客内容检索与问答编排）
- `luoluo-common`：公共组件模块（Redis、Sa-Token、MyBatis-Plus、邮件等）
- `luoluo-member`：会员能力模块（登录认证与 profile）
- `luoluo-system`：公共系统接口模块（公共验证码、后续公共资源接口）

## 目录结构

```text
Personal-Blog-RAG-Backend-Project-main
├─ luoluo-admin
├─ luoluo-rag
├─ luoluo-common
├─ luoluo-member
├─ luoluo-system
└─ script
```

## 构建

推荐在仓库根目录聚合构建：

```bash
cd Personal-Blog-RAG-Backend-Project-main
mvn -pl luoluo-admin -am clean package
```

## 启动

统一从 `luoluo-admin` 启动，其他模块作为依赖模块被装配进来：

```bash
cd luoluo-admin
mvn spring-boot:run
```

## SQL 目录（手动执行）

- `script/sql/member/baseline`
- `script/sql/member/rollback`

> 说明：当前 SQL 作为备份/迁移脚本，默认不自动初始化数据库。
