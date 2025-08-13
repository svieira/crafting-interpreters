plugins {
    id("java")
}

group = "com.craftinginterpreters.lox"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.craftinginterpreters.lox.Lox"
}

//tasks.withType<JavaCompile>().configureEach {
//    options.compilerArgs.add("--enable-preview")
//}
//
//tasks.withType<Test>().configureEach {
//    jvmArgs("--enable-preview")
//}
//
//tasks.withType<JavaExec>().configureEach {
//    jvmArgs("--enable-preview")
//}

tasks.test {
    useJUnitPlatform()
}