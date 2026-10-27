plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.dakotad74"
version = "0.1.0"

// Compile with whatever JDK runs Gradle (25 here) against the Java 17 API surface.
java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    // Test-only dependencies. The library itself has ZERO runtime dependencies.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "npub-names"
                description = "Deterministic human names from Nostr public keys (npub/hex). Zero dependencies."
                url = "https://github.com/dakotad74/npub-names"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "dakotad74"
                    }
                }
                scm {
                    url = "https://github.com/dakotad74/npub-names"
                }
            }
        }
    }
}
