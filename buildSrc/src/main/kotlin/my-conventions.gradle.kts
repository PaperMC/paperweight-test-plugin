import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

plugins {
  `java-library`
}

java {
  // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems that only have JDK 17 installed for example.
  // If you need to compile to for example JVM 8 or 17 bytecode, adjust the 'release' option below and keep the toolchain at 21.
  toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
  maven("https://repo.papermc.io/repository/maven-public/")
}

tasks {
  withType<JavaCompile>().configureEach {
    // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
    // See https://openjdk.java.net/jeps/247 for more information.
    options.release = 17
  }
  withType<Javadoc>().configureEach {
    options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
  }
}
