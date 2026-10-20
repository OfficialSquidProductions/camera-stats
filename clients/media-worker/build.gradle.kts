plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
dependencies {
    implementation(project(":core"));implementation(project(":jdbc"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"));testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
application {mainClass.set("org.squidproductions.camerastats.media.WorkerKt")}
tasks.test {useJUnitPlatform();systemProperty("workerTestClasspath",sourceSets.test.get().runtimeClasspath.asPath);systemProperty("sampleMedia",rootProject.projectDir.parentFile.resolve("fixtures/media/numbered-24.mp4").absolutePath);systemProperty("fixtures",rootProject.projectDir.parentFile.resolve("fixtures/camreport").absolutePath)}
