plugins {
  id("java")
  kotlin("jvm")
  kotlin("kapt") version "2.0.21"
}

group = "com.example.hellolime"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(platform("org.junit:junit-bom:5.10.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  implementation(kotlin("stdlib-jdk8"))

  compileOnly(rootProject.fileTree("lib/appinventor") {
    include("*.jar")
  })

  kapt(files("lib/appinventor/AnnotationProcessors.jar"))
}

val javaHome: String = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
val myJava: String = javaHome + "bin/java"

tasks.register("extension") {
  val buildDir = layout.buildDirectory.asFile.get()
  if (buildDir.exists()){
    buildDir.deleteRecursively()
  }
  finalizedBy("buildSource")
}

tasks.register<Jar>("buildSource") {
  layout.buildDirectory.file("extension/")
  archiveClassifier.set("all")
  archiveFileName.set("AndroidRuntimeUnoptimized.jar")
  from({
    configurations.runtimeClasspath.get().filter {
      it.exists()
    }.map {
      if (it.isDirectory) it else project.zipTree(it)
    }
  })
  with(tasks.jar.get())
  destinationDirectory.set(file(layout.buildDirectory.file("extension/build/")))
  duplicatesStrategy = DuplicatesStrategy.WARN

  finalizedBy("proguardJar")
}

tasks.register("proguardJar") {
  // kotlin libraries are fat, we need to make em thin
  val proguardJar = rootProject.file("lib/build-tools/proguard.jar")
  val proguardRules = rootProject.file("proguard-rules.pro")
  val inputJar = layout.buildDirectory.file("extension/build/AndroidRuntimeUnoptimized.jar").get().asFile
  val outputJar = layout.buildDirectory.file("extension/build/AndroidRuntime.jar").get().asFile

  doLast {
    exec {
      commandLine(
        myJava,
        "-jar",
        proguardJar.absolutePath,
        "-injars",
        inputJar.absolutePath,
        "-outjars",
        outputJar,
        "-libraryjars",
        "$javaHome/jmods/java.base.jmod",
        "-include",
        proguardRules.absolutePath
      )
    }
  }

  finalizedBy("d8Jar")
}

tasks.register("d8Jar") {
  val d8Jar = rootProject.file("lib/build-tools/d8.jar")

  val inputJar = layout.buildDirectory.file("extension/build/AndroidRuntime.jar").get()
  val outputJar = layout.buildDirectory.file("extension/build/classes.jar").get()

  doLast {
    exec {
      commandLine(
        myJava,
        "-cp",
        d8Jar.absolutePath,
        "com.android.tools.r8.D8",
        "--output",
        outputJar,
        inputJar
      )
    }
  }
  finalizedBy("makeSkeleton")
}

tasks.register("makeSkeleton") {
  val annotationProcessor = rootProject.file("lib/appinventor/AnnotationProcessors.jar")

  val simpleComponents = layout.buildDirectory.file("generated/source/kapt/main/simple_components.json").get().asFile
  val simpleComponentsBuildInfo =
    layout.buildDirectory.file("generated/source/kapt/main/simple_components_build_info.json").get().asFile
  val skeletonDirectory = layout.buildDirectory.file("extension/").get().asFile

  // Note:
  //  We use a modified version of Annotation Processor
  doLast {
    exec {
      commandLine(
        myJava,
        "-cp",
        annotationProcessor.absolutePath,
        "com.google.appinventor.components.scripts.ExternalComponentGenerator",
        simpleComponents,
        simpleComponentsBuildInfo,
        skeletonDirectory,
        "false"
      )
    }
  }

  finalizedBy("zipExtension")
}

tasks.register("copyToSkeleton") {
  doLast {
    layout.buildDirectory.file("extension/build/AndroidRuntime.jar")
      .get().asFile.copyTo(layout.buildDirectory.file("extension/${project.group}/files/AndroidRuntime.jar").get().asFile)

    layout.buildDirectory.file("extension/build/classes.jar")
      .get().asFile.copyTo(layout.buildDirectory.file("extension/${project.group}/classes.jar").get().asFile)
  }
}

tasks.register<Zip>("zipExtension") {
  dependsOn("copyToSkeleton")

  val toZip = layout.buildDirectory.files("extension/${project.group}/")
  from(toZip)
  archiveFileName.set(project.group.toString() + ".aix") // package name + aix
  destinationDirectory.set(rootProject.file("out/"))
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(11)
}