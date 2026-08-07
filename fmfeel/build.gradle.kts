plugins {
    // The application plugin gives us `mainClass` config and a `run` task for
    // development. We do NOT ship its JVM start scripts (see below) — the
    // deliverable is a self-contained native executable built with jpackage.
    application

    // Adds the JavaFX modules to the compile/runtime classpath and wires the
    // required --module-path / --add-modules args into the `run` task.
    alias(libs.plugins.javafx)
}

javafx {
    version = "21.0.4"
    modules("javafx.controls", "javafx.fxml")
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Depend on the sibling jsm library.
    implementation(project(":jsm"))
    // Depend on the sibling device library.
    implementation(project(":device"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.freedesktop.gstreamer:gst1-java-core:1.4.0")
    
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass = "fmfeel.Main"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// --- Native packaging -----------------------------------------------------
// No JVM launcher scripts (the .bat / shell wrappers): disable the start-script
// and script-based distribution tasks so they are never generated.
listOf("startScripts", "distZip", "distTar", "installDist", "assembleDist").forEach {
    tasks.named(it) { enabled = false }
}

// This is a non-modular application, so jpackage sets the app-image classpath
// to the main jar only. Record every runtime dependency (JavaFX included) in
// the main jar's manifest Class-Path so they resolve at launch — the staging
// step below places them all as siblings of the main jar, hence bare names.
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Class-Path" to configurations.runtimeClasspath.get()
                .joinToString(" ") { it.name }
        )
    }
}

// Stage the application jar plus its runtime dependencies into one directory
// for jpackage to consume as its --input.
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")
val stageForJpackage = tasks.register<Copy>("stageForJpackage") {
    from(tasks.named("jar"))
    from(configurations.runtimeClasspath)
    into(jpackageInputDir)
}

val jpackageOutputDir = layout.buildDirectory.dir("jpackage")

// Build a self-contained native executable with a bundled runtime and the
// platform-specific JavaFX jars (native libs are bundled inside those jars and
// extracted at launch). Produces build/jpackage/fmfeel/  (fmfeel.exe on
// Windows, bin/fmfeel on Linux) — no JVM required on the target machine.
tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds a self-contained native executable with a bundled runtime."
    dependsOn(stageForJpackage)

    val launcher = javaToolchains.launcherFor(java.toolchain).get()
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val jpackageExe = launcher.metadata.installationPath
        .file("bin/jpackage" + if (isWindows) ".exe" else "")
        .asFile

    val mainJar = tasks.named<Jar>("jar").flatMap { it.archiveFileName }

    // jpackage refuses to overwrite an existing app image.
    doFirst { delete(jpackageOutputDir) }

	val iconName = if (isWindows) "fmfeel.ico" else "fmfeel.png"
	val iconFile = layout.projectDirectory.file("src/main/packaging/$iconName")
	
	commandLine(
	    jpackageExe.absolutePath,
	    "--type", "app-image",
	    "--name", "fmfeel",
	    "--input", jpackageInputDir.get().asFile.absolutePath,
	    "--main-jar", mainJar.get(),
	    "--main-class", "fmfeel.Main",
	    "--icon", iconFile.asFile.absolutePath,
	    "--dest", jpackageOutputDir.get().asFile.absolutePath,
	)
}
