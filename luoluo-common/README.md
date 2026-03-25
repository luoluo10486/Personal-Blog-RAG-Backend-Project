# luoluo-common

公共组件模块，提供：

- Redis 封装：`RedisClient`
- Sa-Token：`@MemberLoginRequired`、注解拦截配置、异常处理
- MyBatis-Plus：分页拦截器与逻辑删除支持配置
- 邮件发送基础能力

## 引用方式

在业务模块 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.personalblog</groupId>
    <artifactId>luoluo-common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 构建

```bash
mvn -pl luoluo-admin -am clean package
```
