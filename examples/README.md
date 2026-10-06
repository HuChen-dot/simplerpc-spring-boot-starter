# 服务端和客户端示例

两个 Spring Boot 示例默认启用 `local` 环境，使用静态地址，在本机无需注册中心即可运行：

| 目录 | 用途 | 默认端口 |
| --- | --- | --- |
| `server` | 使用 `@RpcService` 发布 `GreetingService` | RPC 9091 |
| `client` | 使用 `@RpcAutowired` 调用远程服务，并通过 HTTP 返回结果 | HTTP 8080 |
| `shared` | 两个模块共用的接口源码，不是额外的 Maven 模块 | — |

接口放在 `org.hu.simplerpc.example.api`，两个模块通过构建插件编译同一份源码。在业务项目中，也可以把接口与 DTO 放入独立的 API 依赖。应用类位于各自的包中，只扫描自己的业务组件。

## 配置文件与环境选择

两个模块的 `src/main/resources` 都包含以下四份配置：

| 配置文件 | 内容 |
| --- | --- |
| `application.yml` | 应用名称、公共启动设置及 `spring.profiles.active` 环境选择，默认 `local` |
| `application-local.yml` | 本地完整配置：RPC/HTTP 端口、服务端开关、静态地址及客户端超时和负载均衡 |
| `application-redis.yml` | Redis 完整配置：RPC/HTTP 端口、服务端开关、注册中心 URI/租约及客户端超时和负载均衡 |
| `application-zk.yml` | ZooKeeper 完整配置：RPC/HTTP 端口、服务端开关、注册中心地址及客户端超时和负载均衡 |

服务端各环境还包含连接日志级别与网络线程配置。环境配置只需和公共 `application.yml` 一起加载，不依赖其他环境文件。

公共配置使用：

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
```

在 IDE 中设置环境变量 `SPRING_PROFILES_ACTIVE=local`、`redis` 或 `zk`，即可选择相应配置；不设置时使用 `local`。也可以在启动命令前设置该环境变量，或用 `--spring.profiles.active=redis` 等启动参数覆盖。启动参数优先于环境变量。两个进程应选择相同环境，每次选择一个环境。

## 构建与启动

在仓库根目录执行，使用 Java 8 和 Maven：

```bash
mvn -f examples/pom.xml clean package
```

聚合构建会先构建当前工作区的 Starter，再构建两个示例，无需先安装 Starter。原有根目录 `mvn package` 仍只构建 Starter。

先在一个终端启动服务端：

```bash
java -jar examples/server/target/simplerpc-example-server.jar
```

再在另一个终端启动客户端：

```bash
java -jar examples/client/target/simplerpc-example-client.jar
```

测试调用（或在浏览器访问 `http://localhost:8080/greet`）：

```bash
curl --get --data-urlencode 'name=小明' http://localhost:8080/greet
```

预期结果：

```json
{"message":"你好，小明！来自 SimpleRPC 服务端。"}
```

省略 `name` 时使用“世界”。客户端必须等服务端启动后再调用；启动客户端本身不要求提前连接 RPC 服务端。使用 Ctrl+C 关闭各进程。

端口和静态服务地址可通过环境变量覆盖，两个终端分别执行：

```bash
RPC_PORT=9092 java -jar examples/server/target/simplerpc-example-server.jar
RPC_ADDRESS=127.0.0.1:9092 HTTP_PORT=8081 java -jar examples/client/target/simplerpc-example-client.jar
```

`RPC_ADDRESS` 可以用 `&` 分隔多个服务地址；负载均衡默认轮询。

## Redis 注册中心

准备 Redis，并让两个进程连接同一实例。分别在两个终端运行：

```bash
SPRING_PROFILES_ACTIVE=redis REDIS_ADDRESS=redis://127.0.0.1:6379/0 java -jar examples/server/target/simplerpc-example-server.jar
SPRING_PROFILES_ACTIVE=redis REDIS_ADDRESS=redis://127.0.0.1:6379/0 java -jar examples/client/target/simplerpc-example-client.jar
```

然后调用同一个 `/greet` 接口。Redis 配置位于各模块的 `application-redis.yml`；支持带认证信息的 URI，例如 `redis://:password@127.0.0.1:6379/0`，特殊字符需 URI 编码。租约默认 15 秒，正常停止服务端会主动注销。

## ZooKeeper 注册中心

准备 ZooKeeper，分别在两个终端运行：

```bash
SPRING_PROFILES_ACTIVE=zk ZK_ADDRESS=127.0.0.1:2181 java -jar examples/server/target/simplerpc-example-server.jar
SPRING_PROFILES_ACTIVE=zk ZK_ADDRESS=127.0.0.1:2181 java -jar examples/client/target/simplerpc-example-client.jar
```

然后调用同一个 `/greet` 接口。ZooKeeper 配置位于各模块的 `application-zk.yml`。

Redis 和 ZooKeeper 环境不加载 `application-local.yml`，只使用服务发现结果。消费者需要能够访问注册的服务端本机 IPv4 地址。

示例只演示 RPC 发布与调用，未加入认证或业务数据存储。框架配置与 Redis 集成测试运行方式见[主 README](../README.md)。
