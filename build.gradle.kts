import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "4.5.11"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "com.example.starter.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-core")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-openapi:$vertxVersion")
  implementation("io.vertx:vertx-web-openapi-router:$vertxVersion")
  implementation("io.vertx:vertx-web-api-contract:$vertxVersion")
  implementation("io.vertx:vertx-mongo-client:$vertxVersion")
  implementation("io.vertx:vertx-auth-mongo:$vertxVersion")
  implementation("org.slf4j:slf4j-api:1.7.36")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  implementation ("com.itextpdf:itextpdf:5.5.13.3")
  implementation ("org.apache.poi:poi:5.2.3")
  implementation ("org.apache.poi:poi-ooxml:5.2.3")
  implementation("org.apache.commons:commons-compress:1.24.0")
  implementation ("commons-io:commons-io:2.13.0")
  implementation ("org.apache.commons:commons-collections4:4.4")
  implementation ("org.apache.xmlbeans:xmlbeans:5.1.1")
  implementation ("org.apache.logging.log4j:log4j-core:2.20.0")


}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}
