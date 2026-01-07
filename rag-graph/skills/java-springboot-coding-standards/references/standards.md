# Spring Boot 编码规范参考

## 目录
- 1. 总体原则
- 2. 工程结构与分层
- 3. 命名与包组织
- 4. 依赖与构建
- 5. 配置与环境管理
- 6. 异常与错误码
- 7. DTO/VO/实体
- 8. 校验与参数处理
- 9. 日志规范
- 10. 事务与一致性
- 11. 持久化与仓储
- 12. API 设计与响应
- 13. 安全与权限
- 14. 测试策略
- 15. 可观测性与运维
- 16. 性能与资源

## 1. 总体原则
- 以可维护、可测试、可演进为第一目标，避免“聪明写法”。
- 控制复杂度：类职责单一，方法短小（建议 < 80 行），嵌套层级 <= 3。
- 依赖倒置：controller 只依赖 service 接口，service 依赖 repository/adapter。
- 统一约定优于配置，规范优先级高于个人偏好。

## 2. 工程结构与分层
- 推荐包结构：
  - `config` 配置与 Bean 装配
  - `controller` Web 层
  - `service` 业务接口，`service.impl` 实现
  - `repository` 持久化访问层
  - `model` 领域模型
    - `entity` 数据实体
    - `dto` 传输对象
    - `vo` 展示对象
  - `mapper` 对象转换
  - `exception` 异常定义与处理
  - `util` 通用工具
  - `common` 通用模型与常量
- 严格分层调用：controller -> service -> repository，禁止反向依赖。
- 业务模块多时按领域拆包：`order`, `user`, `billing`，各模块内再分层。

## 3. 命名与包组织
- 类名使用 UpperCamelCase，方法/字段使用 lowerCamelCase。
- 常量使用 `UPPER_SNAKE_CASE`，布尔字段以 `isXxx` 作为 getter。
- 业务接口名以动词开头，如 `createOrder`，避免 `processData` 等含糊命名。
- 包名全小写，避免缩写与拼音混用；单词尽量完整。

## 4. 依赖与构建
- 依赖最小化，避免引入未使用依赖。
- 使用 Spring Boot BOM 统一版本，避免版本漂移。
- 明确可选依赖，避免传递依赖隐式生效。

## 5. 配置与环境管理
- 使用 `@ConfigurationProperties` 统一配置绑定。
- 配置按环境拆分（dev/test/prod），严禁硬编码密钥。
- 日志、连接池、线程池、超时等必须可配置。

## 6. 异常与错误码
- 定义业务异常基类（如 `BusinessException`），携带错误码与提示。
- 统一异常处理：`@ControllerAdvice` + `@ExceptionHandler`。
- 不在接口暴露堆栈信息，日志记录全量堆栈。
- 错误码结构统一（模块码 + 业务码）。

## 7. DTO/VO/实体
- 严禁在 API 直接暴露实体对象。
- DTO 仅承载传输数据，不包含业务逻辑。
- VO 仅服务展示或响应封装，避免复用 DTO。
- 对象转换使用 Mapper（如 MapStruct），禁止在 controller 堆业务转换逻辑。

## 8. 校验与参数处理
- 入参统一使用 `@Valid` 或 `@Validated` 触发校验。
- 复杂校验使用自定义注解或 service 级校验。
- 校验失败统一返回错误码与字段级错误信息。

## 9. 日志规范
- 使用 SLF4J，禁止 `System.out`。
- 日志分级清晰：`INFO` 业务关键节点，`WARN` 非预期但可恢复，`ERROR` 异常。
- 使用参数化日志：`log.info("id={}, name={}", id, name)`。
- 打印关键链路标识（traceId/requestId）。
- 避免记录敏感信息（密码、证件号）。

## 10. 事务与一致性
- `@Transactional` 放在 service 层，避免 controller 开事务。
- 自调用不生效问题需通过拆分或代理解决。
- 只读场景标记 `readOnly = true`。
- 避免长事务，尽量缩小事务边界。

## 11. 持久化与仓储
- Repository 仅做数据访问，不放业务逻辑。
- 避免 N+1 查询，必要时使用批量或 join。
- SQL/DSL 必须可读，复杂逻辑拆分。
- 统一分页与排序规范，限制最大分页大小。

## 12. API 设计与响应
- 使用清晰的 REST 路径与 HTTP 方法语义。
- 响应结构统一（code/message/data）。
- 支持分页、排序、过滤的标准参数。
- 接口版本化（URL 或 Header）。

## 13. 安全与权限
- 使用统一鉴权机制（JWT、Session、OAuth2）。
- 接口权限统一拦截，避免散落在 controller。
- 开放接口做好限流与防刷。
- 输入做白名单校验，输出做必要脱敏。

## 14. 测试策略
- 单元测试覆盖核心业务逻辑，使用 mock 隔离外部依赖。
- 集成测试覆盖关键链路，使用 `@SpringBootTest`。
- API 合同测试覆盖外部依赖接口。

## 15. 可观测性与运维
- 关键业务指标输出指标或事件日志。
- 对外依赖调用记录耗时与失败率。
- 统一健康检查与探针配置。

## 16. 性能与资源
- 线程池、连接池参数必须可配置。
- 大量数据处理使用分页或批处理。
- 合理使用缓存并设置 TTL。
- 避免在请求线程中执行长耗时任务。
