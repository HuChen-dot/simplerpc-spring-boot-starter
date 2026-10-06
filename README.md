# simplerpc-spring-boot-starter

基于 Spring Boot 2.x、Netty 和 JSON 的同步 RPC 框架。支持接口代理、静态地址、ZooKeeper 或 Redis 服务发现，以及轮询、随机和响应时间负载均衡。当前构建与测试使用 Java 8。

## 可运行示例

仓库包含[服务端与客户端示例](examples/README.md)：服务端使用 `@RpcService` 发布服务，客户端使用 `@RpcAutowired` 调用服务并提供 `/greet` HTTP 接口。两端均包含核心 `application.yml` 和完整的 `application-local.yml`、`application-redis.yml`、`application-zk.yml`。通过环境变量 `SPRING_PROFILES_ACTIVE` 选择 `local`、`redis` 或 `zk`，默认 `local` 使用静态地址。

```bash
mvn -f examples/pom.xml clean package
java -jar examples/server/target/simplerpc-example-server.jar
# 在另一个终端启动客户端
java -jar examples/client/target/simplerpc-example-client.jar
```

访问 `http://localhost:8080/greet?name=SimpleRPC` 即可看到远程调用结果。聚合构建使用当前工作区的 Starter；两个示例共用 `examples/shared` 中的接口源码。

## 使用

服务提供者和消费者需要共享相同的接口及参数/返回值 DTO 定义。

```java
public interface GreetingService {
    String greet(String name);
}
```

提供者：实现接口并标记 `@RpcService`。该注解包含 Spring `@Component`，实现类需在应用的扫描范围内。一个接口只能对应一个服务 Bean；多个实现会在启动时报告错误。继承接口及实现类继承的方法也支持调用。

```java
import org.hu.rpc.annotation.RpcService;

@RpcService
public class GreetingServiceImpl implements GreetingService {
    public String greet(String name) {
        return "你好，" + name;
    }
}
```

消费者：在 Spring Bean 中使用 `@RpcAutowired` 注入接口。字段可以在父类中声明，代理在 `@PostConstruct` 前就绪；不支持 static/final 字段。

```java
import org.hu.rpc.annotation.RpcAutowired;
import org.springframework.stereotype.Component;

@Component
public class GreetingClient {
    @RpcAutowired
    private GreetingService greetingService;

    public String hello() {
        return greetingService.greet("世界");
    }
}
```

### 静态地址

提供者默认监听 9091。纯消费者需要关闭本地服务端；地址的键使用接口的完整类名，多个提供者使用 `&` 分隔。

```yaml
simplerpc:
  server:
    enabled: false
  consumer:
    connect-timeout: 3000
    request-timeout: 3000
    load-balancing: polling
    address:
      "[com.example.GreetingService]": "127.0.0.1:9091&127.0.0.1:9092"
```

示例中的 `com.example.GreetingService` 必须替换为你的接口全名。IPv6 使用 `[::1]:9091` 格式。

### ZooKeeper

提供者和消费者启用相同的注册中心后，无需静态地址配置。根路径默认为 `/simplerpc`，服务地址以临时节点注册；会话重新建立后自动恢复注册，监听器随 Spring 容器关闭。

```yaml
simplerpc:
  registry:
    type: zk
    address: "127.0.0.1:2181"
  server:
    enabled: true
    port: 9091
```

纯消费者仍应设置 `simplerpc.server.enabled: false`。ZooKeeper 开启后以发现结果为准，不回退到静态地址。初始连接超过 5 秒会使启动失败。

### Redis

提供者和消费者配置同一个 Redis 地址，并指定 `type: redis`。纯消费者将 `server.enabled` 设置为 `false`。

```yaml
simplerpc:
  registry:
    type: redis
    address: "redis://127.0.0.1:6379/0"
    redis-lease-millis: 15000
  server:
    enabled: true
    port: 9091
  consumer:
    load-balancing: polling
```

地址也可写成 `127.0.0.1:6379`。需要认证时使用 `redis://:password@host:6379/0` 或 `redis://username:password@host:6379/0`；用户名和密码中的特殊字符需按 URI 编码。`rediss://` 使用 TLS。Redis 客户端由 Starter 自行创建和关闭，使用 Jedis 4.4.8，无需配置 Spring RedisTemplate。

服务使用带进程标识的 TTL 租约注册，每隔租约时长的三分之一续期。正常关闭时主动注销，异常退出后最长在租约到期时下线；Redis 恢复后存活的提供者会在后续续期中重新注册。地址索引也设置过期时间，并在发现时清除过期成员。默认租约为 15 秒，生产环境应给续期留出足够的网络延迟和暂停余量。

Redis 模式下每次调用查询当前有效地址，不回退到静态配置。支持 `polling`、`random` 和 `response-time` 三种策略；响应耗时记录保留 5 秒。初始化时会验证 Redis 连接，失败则报告启动失败。

此实现连接一个 Redis 服务端入口，适用于单机 Redis；当前未实现 Sentinel 或 Cluster 客户端。Redis 租约表示提供者持续续期，不主动探测业务健康。`ZkLock` 仍是独立的 ZooKeeper 锁工具，Redis 模式不启用它。

注册中心由 `simplerpc.registry.type` 统一选择，取值为 `none / zk / redis`，默认 `none` 使用静态地址。注册中心配置中的未知字段会使启动失败。未启用 Redis 时不创建连接池或续期线程。

## 配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `simplerpc.server.enabled` | `true` | 是否启动本地 RPC 服务端 |
| `simplerpc.server.port` | `9091` | 服务端端口；绑定失败会直接报告启动失败 |
| `simplerpc.server.log-level` | `info` | Netty 连接日志级别 |
| `simplerpc.server.thread-pool.boss-group-size` | `1` | 接收连接的线程数 |
| `simplerpc.server.thread-pool.worker-group-size` | `min(4, CPU 数)` | 网络线程数；`0` 使用 Netty 默认值 |
| `simplerpc.server.thread-pool.backlog` | `1024` | 接收连接的等待队列大小 |
| `simplerpc.consumer.connect-timeout` | `3000` | 连接超时，毫秒，必须大于 0 |
| `simplerpc.consumer.request-timeout` | `3000` | 响应超时，毫秒，必须大于 0 |
| `simplerpc.consumer.load-balancing` | `polling` | `polling` / `random` / `response-time` |
| `simplerpc.consumer.address` | 空 | 接口全名到服务地址的映射 |
| `simplerpc.registry.type` | `none` | `none` / `zk` / `redis`，统一选择注册中心 |
| `simplerpc.registry.address` | ZooKeeper：`127.0.0.1:2181`；Redis：`redis://127.0.0.1:6379/0` | 所选注册中心的连接地址 |
| `simplerpc.registry.redis-lease-millis` | `15000` | Redis 租约时长，毫秒，范围 300–2147483647 |

服务端业务方法在独立线程组执行，不占用网络线程；业务线程数为 `max(2, worker-group-size)`。Spring 代理共享最多 4 个客户端网络线程，每次调用的连接在完成后关闭。

`response-time` 支持 ZooKeeper 和 Redis。收到 RPC 响应后会记录耗时（包括业务错误响应）；优先探测没有有效记录的节点，再在最近 5 秒内响应最快的节点中随机选择。该记录是最近一次调用的样本。

## 调用行为

- JSON 消息采用 UTF-8，通过完整 JSON 分帧处理 TCP 粘包和分包。单条消息上限为 16 MiB。
- 请求按 ID 关联响应；并发调用不共享响应变量。连接断开、写入失败、响应超时和业务错误会向消费者报告 `SimpleRpcException`。
- 参数和结果按接口声明转换，支持 DTO、集合泛型、继承泛型、基本类型和 void；JSON 外形的字符串保持字符串。服务端只暴露接口中的方法。
- `equals`、`hashCode` 和 `toString` 在代理本地执行。
- 响应超时只停止等待，不取消已经在服务端执行的业务操作。框架不会自动重试请求。
- Fastjson 1.2.84 使用独立 SafeMode 配置；请求中的参数类型只与已注册接口匹配，不加载任意网络指定类型。宿主应用的 Fastjson 全局配置不会被修改。
- 每个容器独立持有服务表；代理通过共享的 `RpcClientTransport` 创建连接，调用处理由 `RpcInvocationHandler` 完成。
- `ZkLock.lock()` / `unlock()` 使用 Curator 可重入锁；必须在同一线程获取和释放，每次获取对应一次释放。会话断开期间业务方不能假定仍持有分布式锁。
- Starter 通过 Spring Boot 自动装配加载；应用配置由宿主应用提供。

当前通信未提供认证和 TLS；服务发现注册的是自动获取的本机 IPv4 地址，调用方需能够访问该地址。尚未增加连接池、自动重试或跨公网部署能力。

## 验证

```bash
mvn clean test
mvn package
```

测试包括真实本地网络调用、中文大消息与分帧、乱序/并发响应、响应超时、断开连接、参数类型转换、Spring 注入、负载均衡，以及临时 ZooKeeper 的锁竞争、服务发现和会话恢复。Redis 集成测试覆盖注册/续期/注销、租约和索引过期、所有者隔离、服务端重启恢复及真实 RPC。测试中的网络监听、ZooKeeper 和 Redis 均使用临时端口。

Redis 测试需要本机可执行的 `redis-server`，测试会自行启动带认证的独立临时实例，不连接已有 Redis，也不保存业务数据。若命令不在 PATH，可指定路径；未安装且未指定路径时，Redis 集成测试会标记跳过，其余测试仍执行。

```bash
mvn -Dredis.test.binary=/absolute/path/to/redis-server clean package
```
