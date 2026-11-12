# memshell-party-dubbo

把 Dubbo 内存马从上游 `MemShellParty` 拆出的独立插件。宿主只保留通用的 server / shellTool SPI，本仓库提供：

- `Dubbo` server provider（Apache Dubbo 2.7+/3.x 与 Alibaba Dubbo 2.6.x 两个注入器）
- Command、Godzilla、Proxy 三个上游工具插件的 Dubbo 挂载实现
- Dubbo provider 靶场、Dubbo 客户端与容器集成测试

## 模块

| 模块 | 内容 |
| --- | --- |
| `dubbo-plugin` | `ServerProvider` 与 `ToolMapping` 扩展，Dubbo shell、接口 helper、注入器；`shadowJar` 产出可安装插件 |
| `dubbo-stub` | 注入器模板引用的 Dubbo 2.6/2.7 类型签名桩，运行期由目标环境 Dubbo 提供真身 |
| `dubbo-range` | 六个 provider fat jar（Alibaba 2.6.12、Apache 2.7.6/2.7.7/2.7.8/2.7.23、3.3.6） |
| `dubbo-client` | command 与 proxy 两条客户端链路，按 Alibaba / Apache 依赖隔离 |
| `plugin-verification` | 不带插件工程依赖，通过 `PluginBootstrap` 加载真实 shadow jar 验证注册与生成 |
| `integration-test` | Dubbo URL 解析单测与 `dubboContainerTest` 容器验收 |

## 工作原理

插件只注册一个 server，不改上游工具插件：

- `DubboServerProvider` 注册 `Dubbo` server。
- `Dubbo#getToolMappingOverrides()` 为 `Command`、`Godzilla`、`Proxy` 三个工具名补充
  `ApacheDubboService` / `AlibabaDubboService` 的 shell 模板。
- 上游工具插件仍各自注册工具、配置与生成器；宿主通过 override 把 Dubbo 类型并入对应工具。

因此宿主必须能加载 `memshell-party-shelltool-command`、`memshell-party-shelltool-godzilla` 与 `memshell-party-shelltool-proxy`。
插件以 `compileOnly` 依赖它们，不把它们的类打进自己的 jar。

宿主 `memshell-party-core` 需要包含 `ByteBuddyShellGenerator` 对
`AbstractServer#getInjectorTemplateClassLoader()` 的查询，否则插件提供的 shell 模板在生成期无法解析父类。
该修复已随 `3.2.0-SNAPSHOT` 发布到 Sonatype snapshots 仓库，CI 与本机构建都从远端解析。

## 构建与测试

```bash
./gradlew :dubbo-plugin:shadowJar
./gradlew :dubbo-plugin:test :plugin-verification:test
./gradlew :integration-test:test
./gradlew :integration-test:dubboContainerTest   # 需要 Docker
```

产物在 `dubbo-plugin/build/libs/memshell-party-dubbo-plugin-1.0.0-SNAPSHOT.jar`。把它放入宿主加载插件的
`plugins/` 目录即可；宿主启动时由 `PluginBootstrap` 校验并注册。

## CI

`.github/workflows/ci.yml` 在每次 push、PR 与手动触发时执行：

1. 单元测试：`dubbo-plugin`、`dubbo-client`、`plugin-verification`
2. 集成测试：URL 解析用例与六个 provider 的真实容器场景
3. 打包：构建 shadow jar，校验 jar 布局和 SPI 注册，生成 `SHA256SUMS` 并上传 `dubbo-plugin` artifact
4. 产物验收：重新下载 artifact，校验 checksum，再用 `PluginBootstrap` 加载下载到的 jar 生成每个工具与传输

下载最近一次 CI 产物：

```bash
run_id=$(gh run list --repo MemShellParty/memshell-party-dubbo --workflow CI --limit 1 --json databaseId --jq '.[0].databaseId')
gh run download "$run_id" --repo MemShellParty/memshell-party-dubbo --name dubbo-plugin
sha256sum -c SHA256SUMS
```

## 版本

| 依赖 | 版本 |
| --- | --- |
| 宿主 `memshell-party-api` / `memshell-party-core` | `3.2.0-SNAPSHOT` |
| `memshell-party-shelltool-command` | `3.1.0-SNAPSHOT` |
| `memshell-party-shelltool-godzilla` | `3.0.0` |
| `memshell-party-shelltool-proxy` | `3.0.0` |
