plugins {
  id("java")
  kotlin("jvm")
  kotlin("kapt") version "2.0.21"
}

group = "me.ekita.hellolime"
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
  implementation(rootProject.fileTree("lib/deps") { include("*.jar") })

  kapt(files("lib/appinventor/AnnotationProcessors.jar"))
}

val javaHome: String = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
val myJava: String = javaHome + "/bin/java"

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

  finalizedBy("r8Jar")
}
tasks.register("r8Jar") {
  val r8Jar = rootProject.file("lib/build-tools/d8.jar")
  val proguardRules = rootProject.file("proguard-rules.pro")
  val inputJar = layout.buildDirectory.file("extension/build/AndroidRuntimeUnoptimized.jar").get().asFile
  val outputJar = layout.buildDirectory.file("extension/build/AndroidRuntime.jar").get().asFile

  val androidJar = rootProject.file("lib/appinventor/android.jar")

  doLast {
    // First: R8 with --classfile to produce optimized Java bytecode
    exec {
      commandLine(
        myJava,
        "-cp",
        r8Jar.absolutePath,
        "com.android.tools.r8.R8",
        "--classfile",
        "--release",
        "--output",
        outputJar.absolutePath,
        "--pg-conf",
        proguardRules.absolutePath,
        "--lib",
        androidJar,
        inputJar.absolutePath
      )
    }
  }

  finalizedBy("r8Dex")
}

tasks.register("r8Dex") {
  val r8Jar = rootProject.file("lib/build-tools/d8.jar")
  val proguardRules = rootProject.file("proguard-rules.pro")
  val inputJar = layout.buildDirectory.file("extension/build/AndroidRuntimeUnoptimized.jar").get().asFile
  val outputDir = layout.buildDirectory.file("extension/build/").get().asFile

  val androidJar = rootProject.file("lib/appinventor/android.jar")

  doLast {
    // Second: R8 without --classfile to produce DEX
    exec {
      commandLine(
        myJava,
        "-cp",
        r8Jar.absolutePath,
        "com.android.tools.r8.R8",
        "--release",
        "--output",
        outputDir.absolutePath,
        "--pg-conf",
        proguardRules.absolutePath,
        "--lib",
        androidJar,
        inputJar.absolutePath
      )
    }

    // Wrap classes.dex in classes.jar
    val jarFile = layout.buildDirectory.file("extension/build/classes.jar").get().asFile
    ant.invokeMethod("zip", mapOf(
      "destfile" to jarFile.absolutePath,
      "basedir" to outputDir.absolutePath,
      "includes" to "classes.dex"
    ))
  }

  finalizedBy("makeSkeleton")
}

tasks.register("makeSkeleton") {
  val annotationProcessor = rootProject.file("lib/appinventor/AnnotationProcessors.jar")

  val simpleComponents = layout.buildDirectory.file("generated/source/kapt/main/simple_components.json").get().asFile
  val simpleComponentsBuildInfo =
    layout.buildDirectory.file("generated/source/kapt/main/simple_components_build_info.json").get().asFile
  val skeletonDirectory = layout.buildDirectory.file("extension/").get().asFile

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
        "false",
        "false",
        "false",
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
  from(toZip) {
    into(project.group.toString())
  }
  archiveFileName.set(project.group.toString() + ".aix") // package name + aix
  destinationDirectory.set(rootProject.file("out/"))
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(11)
}