# 中文

# Plugin ASM

**PASM** 是一个轻量级、Mixin 风格的字节码注入框架，专为 **Paper / Folia** 服务端设计。
它允许你通过简单的注解，在运行时修改任意类的字节码——无需反射，无需继承，无性能损耗。

> 🎯 **目标**：让 Bukkit/Paper 插件开发者能像写普通 Java 一样实现热修补、API 增强、事件拦截等操作，而不触碰复杂的 ASM 细节。

---

## ✨ 核心特性

| 特性            | 说明                                                                     |
|---------------|------------------------------------------------------------------------|
| 🚀 Mixin 式注入  | @Pasm + @Inject 定义目标类与方法，框架自动合并字节码                                     |
| 🩸全注入类型       | BEFORE / AFTER / REPLACE / HEAD / TAIL（AROUND 降级为 REPLACE）             |
| 🏗️ 构造函数注入    | 支持 <init>，自动插入 super() 之后                                              |
| 🔥异常处理兼容      | try-catch-finally 完整复制，标签映射正确                                          |
| 📏宽类型自动偏移     | long / double 参数自动处理双槽位，无需手动计算 this 偏移                                 |
| ⚔️ REPLACE 独占 | 同一方法若存在 REPLACE，自动忽略其他注入（符合 Mixin 规范）                                  |
| 🧩 ASM 处理器钩子  | 在 pasm.json 中声明 asms 数组，实现 PasmAsmProcessor 接口，可在 premain 前后执行自定义字节码操作 |
| 📦插件式扫描       | 将 pasm.json 放入插件 Jar 根目录，PASM 自动扫描所有注入点                                |

---

## 📋 环境要求

- **Java 8 - 21**

- **Minecraft 1.0+**

- **Maven** 或 **Gradle**（仅编译插件时需要）

---

## 📥 使用方法

1. 去Github或Modrinth下载最新的Plugin ASM

2. 添加到服务器根目录，添加后，结构应该如下：

```textmate
server
| -> paper-1.xx.x-xx.jar
| -> pasm-x.x.x.jar
| -> plugins
    | -> 一些插件.jar
```

3. 修改你的启动命令，修改后，应该如下：

```shell
java -javaagent:pasm-x.x.x.jar -jar paper-1.xx.x-xx.jar
```

---

## 使用Plugin ASM来字节码注入

参考[Wiki](https://github.com/laolizhennui/Plugin-ASM/wiki)

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

生成产物：`target/pasm-x.x.x.jar`（这就是 Java Agent）

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

---

# English

# Plugin ASM

**PASM** is a lightweight, Mixin‑style bytecode injection framework designed specifically for **Paper / Folia** server
environments.
It allows you to modify the bytecode of arbitrary classes at runtime using simple annotations—**no reflection, no
inheritance, zero performance penalty**.

> 🎯 **Goal**: Let Bukkit/Paper plugin developers implement hot‑patches, API enhancements, event interceptors, and more—*
*just by writing plain Java, without touching complex ASM internals**.

---

## ✨ Core Features

| Feature                            | Description                                                                                                                   |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| 🚀 Mixin‑style injection           | @Pasm + @Inject define target class & method; the framework merges bytecode automatically.                                    |
| 🩸 Full injection types            | BEFORE / AFTER / REPLACE / HEAD / TAIL (AROUND falls back to REPLACE with a warning).                                         |
| 🏗️ Constructor injection          | Supports <init>; automatically inserted after super() call.                                                                   |
| 🔥 Exception‑handler compatibility | Full replication of try‑catch‑finally blocks; correct label mapping.                                                          |
| 📏 Automatic wide‑type offset      | long / double parameters are handled as double‑slots; no manual calculation of this offset.                                   |
| ⚔️ Exclusive REPLACE               | If a method has multiple REPLACE injections, only the one with highest priority is applied.                                   |
| 🧩 ASM processor hooks             | Declare asms array in pasm.json; implement PasmAsmProcessor interface to run custom bytecode operations before/after premain. |
| 📦 Plugin‑style scanning           | Place pasm.json in your plugin JAR root; PASM automatically discovers all injection points.                                   |

---

## 📋 Environment Requirements

- **Java 8–21**

- **Minecraft 1.0+**

- **Maven** or **Gradle**(only needed when compiling your plugin)

---

## 📥 Installation & Setup

1. Download the latest Plugin ASM from GitHub or Modrinth

2. Place it in your server root directory. The structure should look like this:

```textmate
server
| -> paper-1.xx.x-xx.jar
| -> pasm-x.x.x.jar
| -> plugins
    | -> some-plugin.jar
```

3. Modify your startup command accordingly:

```shell
java -javaagent:pasm-x.x.x.jar -jar paper-1.xx.x-xx.jar
```

## 🛠️ Using Plugin ASM for Bytecode Injection

Refer to the [Wiki](https://github.com/laolizhennui/Plugin-ASM/wiki) for detailed usage and examples.

## 🧪 Testing & Compatibility

PASM has been fully tested in the following scenarios:

- ✅ BEFORE, AFTER, REPLACE, HEAD, TAIL–all types covered

- ✅ Constructor(`<init>`) injection

- ✅ Methods containing `try‑catch‑finally` blocks

- ✅ Methods with `long` / `double` parameters(static → non‑static, static → static)

- ✅ Conflict detection for multiple `REPLACE` injectors on the same method(only the highest priority runs)

- ✅ Configuration with `asms` only(no `pasms`)

- ✅ Paper 1.21.11 / Java 21

**Known limitations**(to be improved in future versions):

- `AROUND` injection is not yet implemented; using it will fall back to `REPLACE` and emit a warning.

- Hot‑reload is experimental and **not recommended for production**.

- Native methods and abstract methods cannot be modified.

---

## 📦 How to Build PASM Itself(Framework Maintainers Only)

```bash
git clone https://github.com/laolizhennui/Plugin-ASM.git
cd Plugin-ASM
mvn clean package
```

Output artifact: `target/pasm-x.x.x.jar`–this is the Java Agent.

---

## 🤝 Contributing

PASM is still a young project, and contributions of any form are welcome!
You can:

- Submit an issue: report bugs or suggest new features

- Pull Request: fix code, improve documentation

- Share your use cases

**We look forward to your Star ⭐ and Fork 🍴!**

---

## 📄 License

[LGPL License](https://www.gnu.org/licenses/old-licenses/lgpl-2.1)

Copyright © 2026 laolizhennui

---

> **Finally**–PASM is not meant to replace Mixin, but to offer Paper developers **a simpler, more intuitive choice** for
> bytecode injection.
> If you like it, tell your friends; if you encounter problems, tell us.
> **Happy Coding!** 🎮🔧