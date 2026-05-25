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
