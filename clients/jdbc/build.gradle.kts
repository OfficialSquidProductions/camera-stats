plugins { kotlin("jvm") }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { api(project(":core")); implementation("org.xerial:sqlite-jdbc:3.49.1.0"); testImplementation(kotlin("test")); testImplementation("org.junit.jupiter:junit-jupiter:5.11.4") }
tasks.test { useJUnitPlatform(); systemProperty("fixtures",rootProject.projectDir.parentFile.resolve("fixtures/camreport").absolutePath) }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
