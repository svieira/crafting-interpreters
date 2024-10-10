plugins {
    id("java")
}

group = "com.craftinginterpreters.lox"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.craftinginterpreters.lox.Lox"
}

tasks.test {
    useJUnitPlatform()
}