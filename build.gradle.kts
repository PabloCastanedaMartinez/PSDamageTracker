plugins {
    kotlin("jvm") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.elplatano0871"
version = "2.2.0"

repositories {
    mavenCentral()

    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigotmc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    
    maven("https://mvn.lumine.io/repository/maven-public/") {
        name = "lumine-repo"
    }
    
    maven("https://repo.extendedclip.com/releases/")

    maven("https://repo.fancyinnovations.com/releases") {
        name = "fancy-repo"
    }
    
}

dependencies {
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")

    compileOnly("org.spigotmc:spigot-api:" + providers.gradleProperty("spigotVersion").getOrElse("1.19-R0.1-SNAPSHOT"))

    implementation(kotlin("stdlib-jdk8"))

    compileOnly("io.lumine:Mythic-Dist:5.4.1")

    compileOnly("me.clip:placeholderapi:2.11.6")
    testImplementation("me.clip:placeholderapi:2.11.6")
    
    implementation("net.kyori:adventure-text-minimessage:4.21.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.21.0")
    
    compileOnly("de.oliver:FancyHolograms:2.4.2")
}

val targetJavaVersion = 17
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

tasks.test { useJUnitPlatform() }
