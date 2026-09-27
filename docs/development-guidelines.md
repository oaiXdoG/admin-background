# 项目开发规范

本文档记录 adminBackground 项目已经确认的开发约束，抽取自 loginServer 的同一套规范。后续开发默认遵守；与当次明确需求冲突时，以当次需求为准。

## 1. 开发原则

1. 严格按照需求范围开发，只实现用户明确要求的功能。
2. 不擅自增加接口、字段、配置、工具、脚本、测试或额外流程。
3. 需求或接口格式不明确时先询问，不自行猜测。
4. 禁止无用探查，只读取完成当前功能必需的代码和配置。
5. 禁止过早优化，不为尚未出现的场景提前增加抽象、缓存、并发方案或扩展设计。
6. 优先复用现有函数和项目结构。已有能力可以直接组合时，不重写原有逻辑。
7. 采用能够完成当前需求的简单实现，避免 `while (true)`、复杂重试和无必要的状态控制。

## 2. 开发前期限制

开发前期除非用户明确要求，否则禁止执行以下操作：

- 编写或运行测试。
- 阶段性测试。
- 编译项目。
- 语法校验、lint、格式检查或静态分析。
- 启动项目或依赖服务。
- 调用接口、连接数据库或 Redis 验证结果。
- 成果校验以及其他与当前实现无关的检查。

以上限制针对开发过程中的工具校验，不代表删除必要的业务参数校验和异常处理。

## 3. Java 代码风格

1. 使用紧凑型代码风格，不在参数、表达式和简单调用之间到处换行。
2. 一行能够清晰容纳的函数声明、构造调用和方法调用保持在一行。
3. 只有一行过长或结构确实复杂时才换行，换行后保持统一缩进。
4. 局部变量必须声明具体类型，禁止使用 `var`。
5. Spring Bean 使用构造器注入；纯 `final` 依赖统一使用 Lombok `@RequiredArgsConstructor`。
6. 业务服务使用 `@Service`，控制器使用项目现有的 `@Component` 和 `@PacketReceiver` 形式。
7. 类名、包名和职责保持一致。服务类放在 `service` 包，数据库实体放在 `entity` 包，Mapper 放在 `mapper` 包。
8. 不增加无意义注释。代码不能直接表达意图时才添加简短说明。
9. 项目配置项在 20 个以内时统一放入 `ApplicationProperties`，不按领域过早拆分多个配置属性类。
10. `RedisConfig`、`DatabaseConfig` 等配置类只负责创建 Bean，配置字段统一从 `ApplicationProperties` 获取。
11. 字符串为空或空白的判断统一使用 `StringUtils.isBlank`，反向判断使用 `StringUtils.isNotBlank`。
12. Service 返回业务结果，不直接返回 HTTP 协议对象；Controller 负责将业务结果转换为响应对象。
13. 三元表达式作为方法参数时，先提取为有明确名称的局部变量，再使用紧凑的方法调用。

紧凑型示例：

```java
String nickname = StringUtils.isBlank(rawNickname) ? "" : rawNickname;
ApiResponse response = ApiResponse.success(data);
```

## 4. HTTP 接口规范

1. 项目 HTTP 接口统一使用 `POST`。
2. 请求参数统一通过 JSON 请求体传递，不使用 URL 查询参数承载业务请求。
3. 请求头使用 `Content-Type: application/json`。
4. 新增接口时，如果路径、请求方式、请求体或响应格式不明确，必须先询问用户。
5. 修改接口时同步更新对应 Markdown 文档。
6. 所有接口响应统一使用 `code`、`message`、`data` 外层结构。业务成功和业务失败均返回 HTTP `200`。业务码只表示业务判断，不把服务器内部故障包装成错误码返回。

示例：

```http
POST /api/xxx
Content-Type: application/json
```

## 5. PostgreSQL 持久化规范

1. PostgreSQL 使用 MyBatis-Plus，不使用 zfoo 的 MongoDB ORM。
2. 常规 CRUD 使用 MyBatis-Plus `BaseMapper`。
3. PostgreSQL 专用 SQL 写在 Mapper XML 中，禁止把 SQL 字符串写进 Java 业务代码。
4. 数据库目录只维护 `src/main/resources/db/schema.sql` 一份最新完整表结构，不保留版本迁移记录。
5. 表结构变化时直接覆盖 `schema.sql`，该文件由开发者手工执行，项目启动时不自动执行。
6. Service 负责业务流程，Mapper 负责数据访问，不在 Controller 中直接操作数据库。

## 6. Redis 规范

1. Redis 常用操作统一通过 `RedisService` 封装，不在业务类中直接散落 `StringRedisTemplate` 调用。
2. 有有效期的数据必须明确设置 TTL。
3. 缓存重建需要避免重复请求时，优先采用简单明确的锁方案，不引入无必要的循环、轮询或复杂状态机。

## 7. 文档与提交规范

1. 官方接口能力先记录到 `docs`，再按明确需求实现。
2. 流程、请求字段、响应字段或配置发生变化时同步更新相关文档。
3. 只有用户明确要求提交时才执行 Git 提交。
4. 提交只包含当前功能相关内容，不混入用户已有或其他无关改动。
5. 不回退、不覆盖用户已有改动；发现冲突且无法安全继续时先询问。
