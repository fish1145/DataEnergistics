# DataEnergistics

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

The main branch should stay on the latest validated Gradle 8.x line. Gradle 9.x should only be evaluated in a separate experiment branch after `clean build`, `runClient`, `runServer`, and `runData` all pass with the NeoForge toolchain.

## License

DataEnergistics original code is licensed under the **MIT License**. The repository root `LICENSE` file is the authoritative project license text.

The generated NeoForge mod metadata also exposes the project license through `mod_license=MIT` in `gradle.properties`, which is expanded into `src/main/templates/META-INF/neoforge.mods.toml`.

## Third-party compatibility

This project integrates with several third-party Minecraft mods at build time and runtime. Those projects retain their own licenses.

The current license audit did **not** find vendored ExtendedAE Plus source code in this repository. DataEnergistics keeps its own original code under MIT and uses third-party mods through normal dependency, optional compatibility, mixin, and reflection-based integration paths unless a file is explicitly marked otherwise.

See `NOTICE.md` for third-party notices and `docs/license-audit.md` for the audit summary.
