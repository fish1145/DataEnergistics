# DataEnergistics

DataEnergistics 是一个面向 AE2 的数据流自动化扩展。它围绕 AE2 网络、样板、机器状态和物质数据化处理，探索更高层的数据化自动化玩法。

**数据流不是能源，而是 AE2 上层的数据化自动化信息层。**

## Project Summary

DataEnergistics is an AE2-oriented data-flow automation addon. It explores data-driven automation around AE2 networks, patterns, machine state, and matter-like data handling.

Data Flow is not an energy type. It is an automation information layer built on top of AE2 concepts.

## Current Status

- Mainline development baseline is stable on **JDK 21** and **Gradle 8.14.5**.
- `clean build` and `runData` have passed.
- `runClient` and `runServer` have startup-level validation, but that is **not** the same as full gameplay verification.
- AE2LT Lightning capability remains **deferred**.
- The current repository **cannot** claim that all machines can connect to each other and share energy.
- The full Data Flow system is **not** implemented yet.

## Documentation

- [Design Book](docs/design-book.md)
- [Documentation Index](docs/README.md)
- [Data Flow System Design](docs/data-flow-system-design.md)
- [Energy System Audit](docs/energy-system-audit.md)
- [Compatibility Test Matrix](docs/compatibility-test-matrix.md)
- [License Audit](docs/license-audit.md)
- [AE2LT Lightning Capability Decision](docs/ae2lt-lightning-capability-design.md)
- [Third-party Notices](NOTICE.md)

## Development Environment

This project targets **Java 21**.

Use **JDK 21** to run Gradle for local development. Do not rely on JDK 25 for the mainline Gradle 8.x build.

Do not commit machine-specific `org.gradle.java.home` paths to the repository.

For local development, configure one of:

- `JAVA_HOME`
- IntelliJ IDEA **Gradle JVM**
- user-level `~/.gradle/gradle.properties`

Example user-level Gradle config:

```properties
org.gradle.java.home=C:/path/to/jdk-21
```

## License

DataEnergistics original code is licensed under the **MIT License**. The repository root `LICENSE` file is the authoritative project license text.

The current license audit did **not** find vendored ExtendedAE Plus source code in this repository.
