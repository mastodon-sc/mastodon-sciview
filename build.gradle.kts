import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.rootPublicationComponentName
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.20"
    `maven-publish`
    java
}

repositories {
    mavenCentral()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
    maven("https://artifactory.cs.vsb.cz/it4i/")
}

dependencies {

    testImplementation(kotlin("test"))

    val sceneryVersion = "84dfb997fc"
    api("graphics.scenery:scenery:$sceneryVersion")


    val sciview = "efe9902ab7"
    api("com.github.scenerygraphics:sciview:$sciview")

    val mastodon = "2f1572c"
    api("com.github.mastodon-sc:mastodon:$mastodon")

    api("sc.fiji:bigdataviewer-core:10.2.0")
//    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-28")

    implementation("net.imagej:imagej:2.2.0")

    // Kotlin dependencies
    //implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.5.20")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.20")
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    implementation("org.slf4j:slf4j-simple:1.7.30")


}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group.toString()
            artifactId = rootProject.name
            version = rootProject.version.toString()

            from(components["java"])

            pom {
                name.set(rootProject.name)
                description.set(rootProject.description)
            }
        }
    }

    repositories {
        maven {
        }
    }
}
