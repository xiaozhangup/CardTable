plugins {
    kotlin("jvm") version "2.3.20"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
    `maven-publish`
}

group = "me.xiaozhangup.cardtable"
version = "1.3.49"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.nostal.ink/repository/maven-public")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(25)
    compilerOptions { freeCompilerArgs.add("-Xjdk-release=25") }
}

dependencies {
    paperweight.devBundle("me.xiaozhangup.octopus", "26.3-R0.1-SNAPSHOT")
    compileOnly("me.xiaozhangup.crab:CrabKotlin:2.3.20:paper")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("net.momirealms:craft-engine-bukkit:26.9.1") { isTransitive = false }
    compileOnly("net.momirealms:craft-engine-core:26.9.1") { isTransitive = false }
    compileOnly(kotlin("stdlib"))
}

tasks.processResources {
    from("craftengine/cardtable/music.yml")
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
    from("THIRD_PARTY_NOTICES.md") { into("META-INF") }
}

val apiJar = tasks.register<Jar>("apiJar") {
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("me/xiaozhangup/cardtable/api/**", "me/xiaozhangup/cardtable/table/TableConfig*", "META-INF/*.kotlin_module")
    }
}
val craftEnginePack = tasks.register<Zip>("craftEnginePack") {
    archiveBaseName.set("CardTable-CraftEngine")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("craftengine")
}
val aiBundle = tasks.register<Zip>("aiBundle") {
    archiveBaseName.set("CardTable-AI")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("src/main/resources/ai")
    from("docs/AI_RUNTIME.md") { rename { "README.md" } }
    from("THIRD_PARTY_NOTICES.md")
}
tasks.assemble { dependsOn(apiJar, craftEnginePack, aiBundle) }
publishing {
    publications {
        create<MavenPublication>("plugin") { from(components["java"]); artifact(apiJar) }
    }
}
