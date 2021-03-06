import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("multiplatform") version "1.3.71" apply false
    id("de.undercouch.download").version("3.4.3")
    id("base")
}

buildscript {
    repositories {
        jcenter()
        google()
        gradlePluginPortal()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("de.undercouch:gradle-download-task:4.0.4")
        classpath("com.adarshr:gradle-test-logger-plugin:2.0.0")
    }
}

val targetSdkVersion by extra(28)
val minSdkVersion by extra(16)

tasks {
    val updateVersions by registering {
        dependsOn(
            "firebase-app:updateVersion", "firebase-app:updateDependencyVersion",
            "firebase-auth:updateVersion", "firebase-auth:updateDependencyVersion",
            "firebase-common:updateVersion", "firebase-common:updateDependencyVersion",
            "firebase-database:updateVersion", "firebase-database:updateDependencyVersion",
            "firebase-firestore:updateVersion", "firebase-firestore:updateDependencyVersion",
            "firebase-functions:updateVersion", "firebase-functions:updateDependencyVersion"
        )
    }
}

subprojects {

    group = "com.stevesoltys"

    apply(plugin = "com.adarshr.test-logger")

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        jcenter()
    }


    tasks.withType<Sign>().configureEach {
        onlyIf {
            !project.gradle.startParameter.taskNames.contains("publishToMavenLocal")
        }
    }


    tasks {

        val updateVersion by registering(Exec::class) {
            commandLine(
                "npm",
                "--allow-same-version",
                "--prefix",
                projectDir,
                "version",
                "${project.property("${project.name}.version")}"
            )
        }

        val updateDependencyVersion by registering(Copy::class) {
            mustRunAfter("updateVersion")
            val from = file("package.json")
            from.writeText(
                from.readText()
                    .replace(
                        "firebase-common\": \"([^\"]+)".toRegex(),
                        "firebase-common\": \"${project.property("firebase-common.version")}"
                    )
                    .replace(
                        "firebase-app\": \"([^\"]+)".toRegex(),
                        "firebase-app\": \"${project.property("firebase-app.version")}"
                    )
            )
        }

        val copyReadMe by registering(Copy::class) {
            from(rootProject.file("README.md"))
            into(file("$buildDir/node_module"))
        }

        val copyPackageJson by registering(Copy::class) {
            from(file("package.json"))
            into(file("$buildDir/node_module"))
        }

        val unzipJar by registering(Copy::class) {
            val zipFile = File("$buildDir/libs", "${project.name}-js-${project.version}.jar")
            from(this.project.zipTree(zipFile))
            into("$buildDir/classes/kotlin/js/main/")
        }

        val copyJS by registering {
            mustRunAfter("unzipJar", "copyPackageJson")
            doLast {
                val from =
                    File("$buildDir/classes/kotlin/js/main/${rootProject.name}-${project.name}.js")
                val into = File("$buildDir/node_module/${project.name}.js")
                into.createNewFile()
                into.writeText(
                    from.readText()
                        .replace("require('firebase-kotlin-sdk-", "require('@gitlive/")
                    //                .replace("require('kotlinx-serialization-kotlinx-serialization-runtime')", "require('@gitlive/kotlinx-serialization-runtime')")
                )
            }
        }

        val copySourceMap by registering(Copy::class) {
            from(file("$buildDir/classes/kotlin/js/main/${project.name}.js.map"))
            into(file("$buildDir/node_module"))
        }

        val prepareForNpmPublish by registering {
            dependsOn(
                unzipJar,
                copyPackageJson,
                copySourceMap,
                copyReadMe,
                copyJS
            )
        }

        val publishToNpm by creating(Exec::class) {
            workingDir("$buildDir/node_module")
            isIgnoreExitValue = true
            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                commandLine("cmd", "/c", "npm publish")
            } else {
                commandLine("npm", "publish")
            }
        }

        withType<Test> {
            testLogging {
                showExceptions = true
                exceptionFormat = TestExceptionFormat.FULL
                showStandardStreams = true
                showCauses = true
                showStackTraces = true
                events = setOf(
                    TestLogEvent.STARTED,
                    TestLogEvent.FAILED,
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_OUT,
                    TestLogEvent.STANDARD_ERROR
                )
            }
        }

        listOf("bootstrap", "update").forEach {
            task<Exec>("carthage${it.capitalize()}") {
                group = "carthage"
                executable = "carthage"
                args(
                    it,
                    "--project-directory", "src/iosMain/c_interop",
                    "--platform", "iOS",
                    "--cache-builds"
                )
            }
        }

        if (Os.isFamily(Os.FAMILY_MAC)) {
            withType(org.jetbrains.kotlin.gradle.tasks.CInteropProcess::class) {
                dependsOn("carthageBootstrap")
            }
        }

        create("carthageClean", Delete::class.java) {
            group = "carthage"
            delete(File("$projectDir/src/iosMain/c_interop/Carthage"))
            delete(File("$projectDir/src/iosMain/c_interop/Cartfile.resolved"))
        }
    }

//    tasks.withType<KotlinCompile<*>> {
//        kotlinOptions.freeCompilerArgs += listOf(
//            "-Xuse-experimental=kotlin.Experimental",
//            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
//            "-Xuse-experimental=kotlinx.serialization.ImplicitReflectionSerializer"
//        )
//    }

    afterEvaluate {
        // create the projects node_modules if they don't exist
        if (!File("$buildDir/node_module").exists()) {
            mkdir("$buildDir/node_module")
        }

        tasks.named<Delete>("clean") {
            dependsOn("carthageClean")
        }

        dependencies {
            "commonMainImplementation"(kotlin("stdlib-common"))
            "commonMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:1.3.8")
            "jsMainImplementation"(kotlin("stdlib-js"))
            "jsMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.3.8")
            "androidMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.8")
            "androidMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.3.6")
            "iosMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:1.3.8")
            "iosMainImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:1.3.8")
            "commonTestImplementation"(kotlin("test-common"))
            "commonTestImplementation"(kotlin("test-annotations-common"))
            "commonTestImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:1.3.8")
            "commonTestImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.8")
            "jsTestImplementation"(kotlin("test-js"))
            "androidAndroidTestImplementation"(kotlin("test-junit"))
            "androidAndroidTestImplementation"("junit:junit:4.13")
            "androidAndroidTestImplementation"("androidx.test:core:1.2.0")
            "androidAndroidTestImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.8")
            "androidAndroidTestImplementation"("androidx.test.ext:junit:1.1.1")
            "androidAndroidTestImplementation"("androidx.test:runner:1.2.0")
        }

    }

    apply(plugin = "maven-publish")

    configure<PublishingExtension> {

        repositories.maven("https://api.bintray.com/maven/stevesoltys/maven/firebase-kotlin-sdk") {
            name = "bintray"

            credentials {
                username = project.findProperty("bintrayUsername") as String?
                    ?: System.getenv("bintrayUsername")
                password =
                    project.findProperty("bintrayKey") as String? ?: System.getenv("bintrayKey")
            }
        }
    }

}

