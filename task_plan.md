# 项目优化计划

目标：修复可复现的功能、并发和生命周期问题，简化相关逻辑。当前按用户最新要求作为新项目开发，不保留旧配置或 API 兼容层。

### Phase 1: 检查与基线 [complete]
检查启动、注册发现、网络通信、代理和路由；建立构建基线。

### Phase 2: 修复与回归 [complete]
修复确认的问题，添加针对实际失败场景的回归测试。

### Phase 3: 构建与交付 [complete]
运行完整测试与构建，补齐使用说明，复核改动。

验证：编译成功；回归测试覆盖超时、并发、序列化和路由等已确认问题；git diff --check 通过。

基线：新增 9 项回归测试在原代码上全部失败（6 failures / 3 errors），确认轮询、入参转换、继承方法、异常传播和暴露方法范围问题。

最终：8 组 36 项测试全部通过；mvn clean package 和最终 mvn package 均成功；工作区及暂存区 git diff --check 通过。Java 生产源码从 2187 行降至 1898 行。

### Phase 4: Redis 注册中心 [complete]
保留 openzk 配置兼容性，增加 type=redis 的服务注册、TTL 租约续期、发现、注销及耗时路由；验证真实 Redis 集成与原有测试。

### Phase 5: Redis 交付 [complete]
补充配置说明，执行完整测试及打包，复核生命周期和并发行为。

Redis 最终验证：真实 Redis 7.4.2 测试进程下 clean package 成功；10 组 48 项测试通过，0 失败、0 错误、0 跳过；差异格式及 JAR 内容检查通过。

### Phase 6: 服务端与客户端示例 [complete]
增加独立示例聚合构建、共用接口、可运行服务端与 HTTP 客户端；提供静态、ZooKeeper、Redis 配置及运行说明。

### Phase 7: 示例验证 [complete]
完整构建并验证两个可执行 JAR 的实际调用，检查三种发现配置和进程退出。

示例最终验证：examples 聚合 clean package 成功；原有 48 项测试通过且无跳过；直接运行两个 Spring Boot JAR，在静态、带密码 Redis、临时 ZooKeeper 三种模式分别验证默认参数与中文/特殊字符的 HTTP→RPC 调用，全部成功；测试进程全部关闭，差异格式检查通过。

### Phase 8: 示例环境配置 [complete]
将两端 YAML 分成核心 application.yml 与完整 local/redis/zk 环境配置；更新环境选择说明，验证默认环境及环境变量切换的真实调用。

配置验证：聚合 package 成功；两端 JAR 均包含四份 YAML。默认 local、显式 local、环境变量 redis、环境变量 zk、启动参数覆盖环境变量五种场景的默认参数和中文/特殊字符调用全部成功；临时进程全部关闭，差异格式检查通过。本次只改示例配置及说明，使用实际启动验证，未重复运行 Starter 测试。

### Phase 9: YAML 参数注释 [complete]
核对配置实现，为两端八份 YAML 的每个参数补充中文说明、单位、可选值及环境变量含义；验证配置值未改变。

注释验证：八份 YAML 的 86 个配置键（含分组）全部有中文注释；YAML 解析成功，修改前后解析结果及全部非注释内容一致；差异格式检查通过。仅补注释，未重复运行网络调用或 Starter 测试。

### Phase 10: 删除兼容层 [complete]
统一注册中心 type，删除旧开关及旧入口和闲置工具，规范配置与方法命名，更新测试、示例和文档。

### Phase 11: 新配置验证 [complete]
执行全部回归测试与聚合构建，验证 local/redis/zk 示例实际调用及配置绑定，检查旧入口残留。

最终验证：clean package 与最终聚合 package 成功；10 组 49 项测试通过，0 failures/errors/skipped。两端 JAR 在默认/显式 local、环境变量 Redis/ZooKeeper、启动参数覆盖环境五种场景的中文 RPC 调用通过，Redis/ZooKeeper 额外使用 response-time 策略。实际启动确认已删除的注册开关被拒绝；八份 YAML 的 86 个配置键仍有中文注释；源码、示例和 README 无旧开关与兼容入口残留，JAR 仅含新调用处理器，全部临时进程关闭，差异格式检查通过。
