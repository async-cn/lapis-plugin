# 修复事件数据中玩家和方块 NBT 始终为空的问题

## Summary

事件数据（EventSerializer）中玩家 `nbt` 字段目前只塞入了 `name`/`display_name` 两个非NBT假数据，方块 `nbt` 字段只读取插件自有的 `lapis:cdata_*` PDC 键——两者都**没有捕获 Minecraft 原生 NBT**。本计划通过引入 item-nbt-api（shaded）实现真实的 NBT 捕获：玩家（Inventory、Attributes、Health 等）和方块实体（Items、自定义附加数据等），并提供统一错误兜底，确保 NBT 获取失败不影响事件正常发送。

## Current State Analysis

### 问题根因

1. **玩家 NBT 假数据** — [EventSerializer.java#L63-L81](file:///c:/Users/Clarus/Desktop/Projects/lapis-dev/lapis-plugin/src/main/java/org/asdf/lapisPlugin/event/EventSerializer.java#L63-L81)
   `serializePlayer` 中 `nbt` 对象仅手动写入 `name`、`display_name`（来自 Bukkit API，非 NBT），从未读取玩家原生 NBT（物品栏/属性/状态等完全缺失）。

2. **方块 NBT 只读 PDC** — [EventSerializer.java#L111-L119](file:///c:/Users/Clarus/Desktop/Projects/lapis-dev/lapis-plugin/src/main/java/org/asdf/lapisPlugin/event/EventSerializer.java#L111-L119)
   `serializeBlock` 中仅当 `block.getState() instanceof TileState` 时读取 `lapis:cdata_*` 命名空间的 PDC；普通方块返回空 `{}`，方块实体也只含插件自己写入的自定义键，不含原生 Tile NBT（如箱子 Items）。

3. **异常被静默吞掉** — `catch (Exception ignored) {}` 违背"失败可观测"要求。

### 平台与约束

* Paper API `1.21.11-R0.1-SNAPSHOT`，Java 21，maven-shade-plugin 3.5.3（[pom.xml](file:///c:/Users/Clarus/Desktop/Projects/lapis-dev/lapis-plugin/pom.xml)）

* Paper 1.20+ 不经 Bukkit API 暴露实体/Tile 原生 NBT → 必须借助 NBT 库

* 项目已配置 maven-shade-plugin（无 relocation 配置）

* 事件链路：[EventBridge.handleEvent](file:///c:/Users/Clarus/Desktop/Projects/lapis-dev/lapis-plugin/src/main/java/org/asdf/lapisPlugin/event/EventBridge.java#L111-L162) 同步调用 `EventSerializer.serialize`；注意 `AsyncPlayerChatEvent` 在异步线程触发（见 Assumptions）

### 已确认的 item-nbt-api 事实（基于源码验证）

* 稳定读取 API：`NBT.get(Entity, Function<ReadableNBT, T>)` 与 `NBT.get(BlockState, Function<ReadableNBT, T>)`（完整原生 NBT 读取）

* `ReadableNBT` 接口提供：`getKeys()`、`getType(key)`（返回 `NBTType`）、`getCompound`、各标量 getter、`getByteArray/getIntArray/getLongArray`、`getStringList/getIntegerList/getFloatList/getDoubleList/getLongList/getCompoundList/getIntArrayList/getUUIDList`、`getListType`

* 库内**无**通用 NBT→Gson 公开转换器（`NBTJsonUtil` 仅支持 1.20.3-1.21.4 的 ItemStack→JSON），需自写递归转换器

* 官方 Maven 仓库为 CodeMC（`https://repo.codemc.io/repository/maven-public/`），坐标 `de.tr7zw:item-nbt-api:2.16.0`（2.16.0 为最新，2026-07-27 发布，支持 1.21.x/26.x）

* Shaded 用法必须：① shade 时 relocation `de.tr7zw.changeme.nbtapi`；② 在最终 jar 的 `META-INF/` 下放置空的 `.mojang-mapped` 标记文件（否则 1.20+ Paper 上 shaded 版本无法工作）

## Proposed Changes

### 1. pom.xml — 引入依赖与 shade 配置

* `<repositories>` 新增 CodeMC 仓库：

  ```xml
  <repository>
      <id>codemc-repo</id>
      <url>https://repo.codemc.io/repository/maven-public/</url>
  </repository>
  ```

* `<dependencies>` 新增（compile scope，随 shade 打包）：

  ```xml
  <dependency>
      <groupId>de.tr7zw</groupId>
      <artifactId>item-nbt-api</artifactId>
      <version>2.16.0</version>
  </dependency>
  ```

  （若 CodeMC 上 2.16.0 拉取失败，回退至 2.15.7）

* maven-shade-plugin（升级 3.5.3 → 3.6.0，官方 shaded 指南推荐）增加 `<configuration>`：

  ```xml
  <relocations>
      <relocation>
          <pattern>de.tr7zw.changeme.nbtapi</pattern>
          <shadedPattern>org.asdf.lapisPlugin.libs.nbtapi</shadedPattern>
      </relocation>
  </relocations>
  ```

### 2. 新增 src/main/resources/META-INF/.mojang-mapped（空文件）

Shaded item-nbt-api 在 Paper 1.20+ 上的必需标记，缺失会导致运行时反射初始化失败。资源过滤对空文件无影响。

### 3. 新增 NbtCollector（org.asdf.lapisPlugin.event 包）

新建 `src/main/java/org/asdf/lapisPlugin/event/NbtCollector.java`，职责单一：真实 NBT 采集 + 递归 JSON 转换 + 统一错误兜底。

```java
public final class NbtCollector {
    private static boolean warned = false; // 首次失败告警，避免热路径刷屏

    /** 玩家完整原生 NBT（Inventory、Attributes、Health、food、abilities 等） */
    public static JsonObject collectPlayerNbt(Player player);
    /** 方块实体原生 NBT；非 TileState 直接返回空对象 */
    public static JsonObject collectBlockNbt(Block block);
    /** 通用：ReadonlyNBT -> Gson 递归转换 */
    static JsonObject toJsonObject(ReadableNBT nbt);
}
```

实现要点：

* 读取用 `NBT.get(player, nbt -> toJsonObject(nbt))` / `NBT.get(tileState, nbt -> toJsonObject(nbt))`（源代码 import 原始包 `de.tr7zw.changeme.nbtapi.NBT`，shade 构建时自动重定位）

* `collectBlockNbt` 先判 `block.getState() instanceof TileState`，非方块实体返回空 `JsonObject`

* `toJsonObject(ReadableNBT)` 递归遍历 `getKeys()`，按 `getType(key)` 分支：

  * `NBTTagString/Short/Int/Long/Float/Double/Byte` → `JsonPrimitive` 对应数值（Byte 按数值输出保持 SNBT `1b` 语义无损）

  * `NBTTagByteArray/IntArray/LongArray` → `JsonArray`（逐元素数值）

  * `NBTTagCompound` → 递归

  * `NBTTagList` → 先 `getListType(key)` 确定元素类型，再选对应 `getXxxList` 读取，compound 列表逐元素递归

  * `NBTTagEnd` → 跳过

* **错误处理**：`collectPlayerNbt`/`collectBlockNbt` 整体 try-catch（含 `NbtApiException`/`Exception`），失败时记录一次 warning（带事件上下文与异常消息，用 `warned` 标志节流），返回空 `JsonObject` —— 保证事件 payload 结构完整、永不因 NBT 失败中断发送链路

### 4. EventSerializer.java — 接入真实 NBT 并调整字段契约

按用户确认的"纯NBT + 字段上移"契约调整：

**serializePlayer（L63-L81 重写）**

```java
obj.addProperty("uuid", ...);       // 保持
obj.addProperty("name", ...);       // 保持（顶层）
obj.addProperty("display_name", player.getDisplayName());  // 新增顶层（从 nbt 上移）
// custom_data（PDC）逻辑保持不变
obj.add("nbt", NbtCollector.collectPlayerNbt(player));     // 替换假数据为真实原生 NBT
```

删除原 `nbt.addProperty("name"/"display_name", ...)` 两行。

**serializeBlock（L111-L119 重写）**

```java
// 原 try { ... readAllData(tileState.getPDC()) } catch (ignored) {} 整段删除，替换为：
obj.add("nbt", NbtCollector.collectBlockNbt(block));

// 保留插件自定义数据可观测性（原 nbt 中的 PDC 数据迁移到独立字段，消费者不丢失）：
if (block.getState() instanceof TileState tileState) {
    JsonObject custom = LapisPlugin.getInstance().getPdcManager().readAllData(tileState.getPersistentDataContainer());
    if (custom.size() > 0) obj.add("custom_data", custom);
}
```

`state`（方块状态属性）解析逻辑保持不变（已正确实现）。

### 5. LapisPlugin.java — onEnable 提前初始化 NBT-API（可选预热）

在 `onEnable` 开头（`saveDefaultConfig()` 之后）调用：

```java
if (!NBT.preloadApi()) {
    getLogger().warning("NBT-API init failed, event nbt fields will be empty");
}
```

失败仅告警不禁用插件（事件链路有兜底，符合需求 4）。源码 import 原始包路径，构建时由 shade 自动重定位。

## Assumptions & Decisions

| 决策              | 说明                                                                                                                      |
| --------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 方案选型            | 用户确认使用 item-nbt-api 库（shaded），而非 `data get` 命令或 Bukkit 手动构建                                                             |
| 字段契约            | 用户确认 `nbt` 字段只放真实原生 NBT；`display_name` 上移为玩家对象顶层字段（`name` 原本就在顶层）。**消费端需同步适配此契约变更**                                     |
| 方块 custom\_data | 原方块 `nbt` 携带的 PDC 数据迁移至独立 `custom_data` 字段（与玩家侧结构对称），避免已有消费者丢失该信息                                                       |
| Byte 语义         | NBT Byte 按数值输出（保持 SNBT 无损）；布尔语义由消费端解释                                                                                   |
| 异步事件风险          | `AsyncPlayerChatEvent` 在异步线程触发序列化，异步读取玩家 NBT 非官方线程安全保证，但 item-nbt-api 读取在实践上可用；万一竞态失败由 collect 层 catch 兜底降级为空对象，不影响事件发送 |
| 性能              | 完整玩家 NBT（含物品栏组件）单次序列化体积可能较大；当前不做裁剪/缓存（未在需求范围内），如后续需要可加 config 开关                                                        |
| 事件类型范围          | 仅修复现有 6 种已注册事件的序列化输出，不新增事件类型                                                                                            |

## Verification

1. **构建**：`mvn clean package` 成功；解包检查最终 jar 含 `org/asdf/lapisPlugin/libs/nbtapi/`（重定位成功）与 `META-INF/.mojang-mapped` 存在。
2. **运行时冒烟**（Paper 1.21.11 服务端）：

   * 启动日志无 NBT-API 初始化报错

   * 通过 TCP `register_event_listener` 注册 `PlayerJoin`、`BlockBreak`、`PlayerInteract`

   * 玩家加入 → payload `data.player.nbt` 含 `Inventory`、`Attributes`、`Health` 等真实键；`display_name` 出现在顶层

   * 破坏箱子 → `data.block.nbt` 含 `Items` 等 Tile NBT；破坏石头 → `nbt` 为 `{}`、无报错

   * 触发一次预期失败路径（可选）：确认日志仅告警一次且事件仍正常发出
3. **回归**：`custom_data`（玩家 PDC）与方块 `custom_data` 字段按预期出现；filter/subscription 对新结构过滤行为验证一次。

