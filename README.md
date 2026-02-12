# PASM—Plugin ASM

**PASM** 是一个轻量级、Mixin 风格的字节码注入框架，专为 **Paper / Folia** 服务端设计。
它允许你通过简单的注解，在运行时修改任意类的字节码——无需反射，无需继承，无性能损耗。

> 🎯 **目标**：让 Bukkit/Paper 插件开发者能像写普通 Java 一样实现热修补、API 增强、事件拦截等操作，而不触碰复杂的 ASM 细节。

---

## ✨ 核心特性
| 特⁠性 | 说⁠明 |
| --- | --- |
| 🚀 Mixin 式⁠注⁠入 | @Pasm + @Inject 定⁠义⁠目⁠标⁠类⁠与⁠方⁠法，框⁠架⁠自⁠动⁠合⁠并⁠字⁠节⁠码 |
| 🩸 全⁠注⁠入⁠类⁠型 | BEFORE / AFTER / REPLACE / HEAD / TAIL（AROUND 降⁠级⁠为 REPLACE） |
| 🏗️ 构⁠造⁠函⁠数⁠注⁠入 | 支⁠持 <init>，自⁠动⁠插⁠入 super() 之⁠后 |
| 🔥 异⁠常⁠处⁠理⁠兼⁠容 | try-catch-finally 完⁠整⁠复⁠制，标⁠签⁠映⁠射⁠正⁠确 |
| 📏 宽⁠类⁠型⁠自⁠动⁠偏⁠移 | long / double 参⁠数⁠自⁠动⁠处⁠理⁠双⁠槽⁠位，无⁠需⁠手⁠动⁠计⁠算 this 偏⁠移 |
| ⚔️ REPLACE 独⁠占 | 同⁠一⁠方⁠法⁠若⁠存⁠在 REPLACE，自⁠动⁠忽⁠略⁠其⁠他⁠注⁠入（符⁠合 Mixin 规⁠范） |
| 🧩 ASM 处⁠理⁠器⁠钩⁠子 | 在 pasm.json 中⁠声⁠明 asms 数⁠组，实⁠现 PasmAsmProcessor 接⁠口，可⁠在 premain 前⁠后⁠执⁠行⁠自⁠定⁠义⁠字⁠节⁠码⁠操⁠作 |
| 📦 插⁠件⁠式⁠扫⁠描 | 将 pasm.json 放⁠入⁠插⁠件 Jar 根⁠目⁠录，PASM 自⁠动⁠扫⁠描⁠所⁠有⁠注⁠入⁠点 |
| 🔄 热⁠加⁠载（实⁠验⁠性） | 支⁠持 AgentManager.reloadPlugins()，动⁠态⁠重⁠扫⁠插⁠件⁠目⁠录 |

---

## 📋 环境要求
- **Java 8 ～ 21**
    
- **Paper 1.17+**（1.16.5 及以下需要把targetCompatibility和sourceCompatibility设置为JavaVersion.VERSION_1_8）
    
- **Maven** 或 **Gradle**（仅编译插件时需要）

---

## 🔧 快速开始（插件开发者）

### 1. 添加依赖（以 Gradle 为例）

```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    implementation "maven.modrinth:pasm:最新的Plugin ASM版本"
}
```

### 2. 创建一个 Mixin 类

```java
package com.example.mixin;

import net.laoli.pasm.annotation.Inject;
import net.laoli.pasm.annotation.InjectionType;
import net.laoli.pasm.annotation.Pasm;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

@Pasm("org.bukkit.event.player.PlayerJoinEvent")
public class PlayerJoinMixin {

    @Inject(
        name = "<init>",
        desc = "(Lorg/bukkit/entity/Player;Lnet/kyori/adventure/text/Component;)V",
        type = InjectionType.HEAD,
        priority = 10
    )
    public static void onConstruct(Player player, Component joinMessage) {
        System.out.println("PlayerJoinEvent 正在创建，玩家：" + player.getName());
    }
}
```

### 3. 编写 `pasm.json`（放在 `src/main/resources/`）

```json
{
  "pasms": [
    "com.example.mixin.PlayerJoinMixin"
  ]
}
```

### 4. 打包插件，放到 `plugins/` 目录

### 5. 启动服务端（添加 PASM Agent）

```bash
java -javaagent:/path/to/pasm-1.0.0-alpha.jar=debug -jar paper-1.xx.x-xx.jar
```

✅ 现在，每次玩家加入时，控制台都会输出自定义信息，无需修改原服务端代码。

---

## 📖 深入指南

### 🏷️ 注解详解

#### `@Pasm`（类级）
- `value`：目标类的全限定名（例如 `org.bukkit.entity.Player`）
    
- `internalName`（可选）：直接指定内部名，如 `org/bukkit/entity/Player`，省略时自动转换

#### `@Inject`（方法级）
- `name`：目标方法名（构造函数为 `<init>`）
    
- `desc`：目标方法描述符（可使用 `javap -s` 或 ASM 插件查看）
    
- `type`：注入类型（`InjectionType`）
    
- `priority`：优先级，**数值越小优先级越高**。对于 `REPLACE`，仅最高优先级生效；对于非 `REPLACE`，按优先级顺序执行。

### 🎨 注入类型对比
| 类⁠型 | 行⁠为 | 适⁠用⁠场⁠景 |
| --- | --- | --- |
| BEFORE | 在⁠目⁠标⁠方⁠法⁠第⁠一⁠条⁠指⁠令⁠前⁠插⁠入 | 前⁠置⁠检⁠查、日⁠志、参⁠数⁠修⁠改 |
| AFTER | 在⁠每⁠个 return 前⁠插⁠入，可⁠保⁠存/恢⁠复⁠返⁠回⁠值 | 后⁠置⁠处⁠理、统⁠计 |
| REPLACE | 完⁠全⁠替⁠换⁠目⁠标⁠方⁠法⁠体 | 彻⁠底⁠重⁠写⁠方⁠法⁠逻⁠辑 |
| HEAD | 同 BEFORE，但⁠对⁠构⁠造⁠函⁠数⁠特⁠殊⁠处⁠理（插⁠在 super() 后） | 构⁠造⁠函⁠数⁠增⁠强 |
| TAIL | 等⁠价⁠于 AFTER（别⁠名） | - |
| AROUND | 暂⁠未⁠实⁠现，降⁠级⁠为 REPLACE（带⁠警⁠告） | - |

### 🧠 ASM 处理器钩子（高级）

如果你需要**在 PASM 执行字节码转换之前或之后**，运行自己的 `ClassFileTransformer` 或其他 JVM 级操作，可以通过 `asms` 数组声明处理器类。

**1. 实现 `PasmAsmProcessor` 接口**

```java
package com.example.hook;

import net.laoli.pasm.api.PasmAsmProcessor;
import java.lang.instrument.Instrumentation;

public class CustomTransformer implements PasmAsmProcessor {

    @Override
    public void beforeInject(Instrumentation inst) {
        System.out.println("[PASM] 自定义 beforeInject 执行，优先级 5");
        inst.addTransformer(new MyClassFileTransformer(), true);
    }

    @Override
    public void afterInject(Instrumentation inst) {
        System.out.println("[PASM] 自定义 afterInject 执行");
    }
}
```

**2. 在 `pasm.json` 中添加 `asms` 数组**

```json
{
  "pasms": [...],
  "asms": [
    { "class": "com.example.hook.CustomTransformer", "priority": 5 },
    { "class": "com.example.hook.AnotherTransformer", "priority": 10 }
  ]
}
```

> 💡 **优先级**：数值越小越先执行（与 `@Inject` 一致）。
> 💡 即使没有 `pasms`，你也可以只配置 `asms`，用来单独注册全局转换器。

---

## 🧪 测试与兼容性

PASM 已在以下场景通过完整测试：
- ✅ BEFORE、AFTER、REPLACE、HEAD、TAIL全类型覆盖
    
- ✅ 构造函数 `<init>` 注入
    
- ✅ 包含 `try-catch-finally` 的复杂方法
    
- ✅ 参数含 `long` / `double` 的宽类型方法（静态→非静态、静态→静态）
    
- ✅ 同一方法的多个 `REPLACE` 冲突检测（仅执行优先级最高者）
    
- ✅ 无 `pasms` 仅有 `asms` 的配置
    
- ✅ Paper 1.21.11 / Java 21

**已知限制**（将在后续版本改进）：
- `AROUND` 注入暂未实现，使用时会降级为 `REPLACE` 并输出警告。
    
- 热加载功能尚不稳定，不建议生产环境使用。
    
- 不支持修改 native 方法或抽象方法。

---

## 📦 如何构建 PASM 本体（仅框架维护者）

```bash
git clone https://github.com/laolizhennui/Plugin-ASM.git
cd Plugin-ASM
mvn clean package
```

生成产物：`target/pasm-1.0.0-alpha.jar`（这就是 Java Agent）

---

## 🤝 参与贡献

PASM 还是一个年轻的项目，欢迎任何形式的贡献！
你可以：
- 提交 Issue：报告 Bug 或提议新功能
    
- Pull Request：修复代码、完善文档
    
- 分享你的使用案例

**期待你的 Star ⭐ 和 Fork 🍴！**

---

## 📄 许可证

[LGPL License](https://www.gnu.org/licenses/old-licenses/lgpl-2.1)

Copyright © 2026 laolizhennui

---

> **最后**——PASM 并不是要取代 Mixin，而是为 Paper 开发者提供一个**更简单、更符合直觉**的字节码注入选择。
> 如果你喜欢它，请告诉你的朋友；如果你遇到问题，请告诉我们。
> **Happy Coding!** 🎮🔧
