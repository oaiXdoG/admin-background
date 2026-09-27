# 获取项目列表

`POST /api/project/list`，请求体 `{}`。

处理顺序：校验令牌；超级管理员读取全部项目；其他账号读取 `project_user` 中挂载的项目和角色；按 `account.last_project_id` 选择当前项目，不存在时选择列表第一项，并回写账号。

返回 `currentProjectId` 和 `projects`。超级管理员项目项的 `roleName` 为 `超级管理员`；普通账号返回其项目角色。

所有账号的列表均排除 `closed = true` 的项目，包括前端项目管理列表和顶部切换列表。

每个项目项包含：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| projectId | number | 项目 ID |
| appKey | string | 手动填写、全局唯一的 App Key |
| name | string | 项目名称 |
| createdAt | number | 创建时间，Unix 毫秒时间戳 |
| roleId | number / null | 原有项目角色字段 |
| roleName | string / null | 原有角色名称字段 |

项目管理表格展示项目 ID、App Key、项目名称、创建时间及操作；角色字段不再作为该表格的展示列。创建时间由前端按浏览器本地时区显示为 `YYYY-MM-DD HH:mm:ss`。

数据库结构见 `src/main/resources/db/schema.sql`：`project.app_key` 为 `varchar(64) NOT NULL`，具有唯一约束 `uk_project_app_key`；`project.created_at` 为 `bigint NOT NULL`，保存创建时的毫秒时间戳。

已有数据库须手工同步字段并补录各项目的真实 App Key 和创建时间，再启用非空与唯一约束。完整建表文件用于初始化，不能在已有库中直接重复执行；不要自动生成历史 App Key 或用当前时间冒充历史创建时间。

参考官方文档：[PostgreSQL 唯一约束](https://www.postgresql.org/docs/current/ddl-constraints.html)、[Java 时间戳](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#currentTimeMillis())、[Spring 重复键异常](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/DuplicateKeyException.html)、[Day.js 时间格式](https://day.js.org/docs/en/display/format)。

错误：`100001` 参数不合法，`100003` 未登录。
