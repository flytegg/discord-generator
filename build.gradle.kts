plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
    `maven-publish`
}

group = "gg.flyte"
version = "2.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The sample-transcript test writes an HTML file here that you can open in a browser.
    systemProperty("sampleOutput", layout.buildDirectory.file("sample-transcript.html").get().asFile.absolutePath)
}

publishing {
    repositories {
        maven {
            name = "flyte-repository"
            url = uri(
                "https://repo.flyte.gg/${
                    if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"
                }"
            )
            credentials {
                username = System.getenv("MAVEN_NAME")
                password = System.getenv("MAVEN_SECRET")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = "discordgenerator"
            version = version.toString()

            from(components["java"])
        }
    }
}
