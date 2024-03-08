@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.ajoberstar.grgit.Grgit

plugins {
    id("org.ajoberstar.grgit") version "5.2.0"
    id("de.undercouch.download") version "5.4.0"
    alias(libs.plugins.maven.publish.base) apply false
}

val (gitVersion, release) = versionFromGit()
logger.lifecycle("Version: $gitVersion (release: $release)")

allprojects {
    group = "net.hycrafthd"
    version = gitVersion

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

subprojects {
    if (project.name == "natives" || project.name == "extensions-project") {
        return@subprojects
    }

    apply<JavaPlugin>()
    apply<MavenPublishPlugin>()

    configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(8)
    }

    configure<PublishingExtension> {
        if (findProperty("MAVEN_PASSWORD") != null && findProperty("MAVEN_USERNAME") != null) {
            repositories {
                val snapshots = "https://repo.u-team.info"
                val releases = "https://repo.u-team.info"

                maven(if (release) releases else snapshots) {
                    credentials {
                        password = findProperty("MAVEN_PASSWORD") as String?
                        username = findProperty("MAVEN_USERNAME") as String?
                    }
                }
            }
        } else {
            logger.lifecycle("Not publishing to repo.u-team.info because credentials are not set")
        }
    }

    afterEvaluate {
        plugins.withId(libs.plugins.maven.publish.base.get().pluginId) {
            configure<MavenPublishBaseExtension> {
                coordinates(group.toString(), project.the<BasePluginExtension>().archivesName.get(), version.toString())

                pom {
                    name = "lavaplayer"
                    description = "A Lavaplayer fork maintained by Lavalink forked again by HyCraftHD to support java 8"
                    url = "https://github.com/HyCraftHD/Lavaplayer"

                    licenses {
                        license {
                            name = "The Apache License, Version 2.0"
                            url = "https://github.com/HyCraftHD/Lavaplayer/blob/main/LICENSE"
                        }
                    }

                    developers {
                        developer {
                            id = "freyacodes"
                            name = "Freya Arbjerg"
                            url = "https://www.arbjerg.dev"
                        }
                        developer {
                            id = "hycrafthd"
                            name = "HyCraftHD"
                            url = "https://www.hycrafthd.net"
                        }
                    }

                    scm {
                        url = "https://github.com/HyCraftHD/Lavaplayer/"
                        connection = "scm:git:git://github.com/HyCraftHD/Lavaplayer.git"
                        developerConnection = "scm:git:ssh://git@github.com/HyCraftHD/Lavaplayer.git"
                    }
                }
            }
        }
    }
}

@SuppressWarnings("GrMethodMayBeStatic")
fun versionFromGit(): Pair<String, Boolean> {
    Grgit.open(mapOf("currentDir" to project.rootDir)).use { git ->
        val headTag = git.tag
            .list()
            .find { it.commit.id == git.head().id }

        val clean = git.status().isClean || System.getenv("CI") != null
        if (!clean) {
            logger.lifecycle("Git state is dirty, version is a snapshot.")
        }

        return if (headTag != null && clean) headTag.name to true else "${git.head().id}-SNAPSHOT" to false
    }
}
