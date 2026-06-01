# Entrypoint And UI Regression Notes

## 已完成验证

- `./gradlew compileJava` 通过。
- `Clean Server` 运行配置可启动，`data_energistics` 不再触发 dedicated server 加载 `net.minecraft.client.*` 类的错误。
- `All Client` 运行配置可启动到客户端模组加载和渲染初始化阶段，未观察到本轮重构引入的启动级异常。

## 本轮重点影响链路

### 1. 多入口装配

- common 入口：`com.fish_dan_.data_energistics.Data_Energistics`
- client 入口：`com.fish_dan_.data_energistics.bootstrap.client.DataEnergisticsClient`
- client 事件注册改为手动 `modEventBus.register(ClientModEvents.class)`，避免 dedicated server 自动扫描客户端订阅类。

### 2. Pattern Encoding 界面链路

- client routing 入口：`client/screen/PatternEncodingScreenRouter`
- native preview screen：`client/screen/NativePatternEncodingTermScreen`
- wireless preview replace：`integration/Ae2WtLibCompat`
- 打开 provider 快捷键：
  - `client/ModKeyMappings.OPEN_PATTERN_PROVIDER`
  - `client/screen/PatternEncodingPreviewScreen`
  - `client/screen/WirelessPatternEncodingTermScreen`
  - `menu/universal/UniversalPatternEncodingTermMenu`
  - `mixin/core/PatternEncodingTermMenuMixin`

### 3. Universal Terminal 链路

- cycle payload：`network/UniversalTerminalCyclePayload`
- client helper：`client/screen/UniversalTerminalClientHelper`
- screen hook：`client/screen/UniversalTerminalScreenHook`
- menu 入口：
  - `menu/universal/UniversalMEStorageMenu`
  - `menu/universal/UniversalCraftingTermMenu`
  - `menu/universal/UniversalPatternAccessTermMenu`
  - `menu/universal/UniversalPatternEncodingTermMenu`

### 4. Adaptive Pattern Provider 链路

- screen：`client/screen/AdaptivePatternProviderScreen`
- menu：`menu/AdaptivePatternProviderMenu`
- host support：
  - `ae2/AdaptivePatternProviderExternalHandlers`
  - `ae2/AdaptivePatternProviderResolver`

## 后续建议回归项

- 在 client 内实际打开：
  - Adaptive Pattern Provider GUI
  - Universal Pattern Encoding Terminal
  - Wireless Pattern Encoding Terminal
- 验证 `OPEN_PATTERN_PROVIDER` 快捷键仍能从 preview screen 打开 provider 菜单。
- 验证 Universal Terminal 切换按钮和 selector panel 正常出现、可切换、不会重复注入。
- 验证 Pattern Encoding screen replacement 不会递归换屏，也不会在 `Init.Post` / `Opening` 双阶段重复创建 screen。
- 若继续重构 `AdaptivePatternProviderBlockEntity` 的 provider profile 本体，优先复测：
  - provider 显示名
  - provider icon
  - slot 数量
  - attached machine 分组展示
