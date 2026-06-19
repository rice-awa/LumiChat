import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom-remap") apply false
    id("net.fabricmc.fabric-loom") apply false
    // id("me.modmuss50.mod-publish-plugin")
}

val isUnobfuscated = !project.hasProperty("deps.yarn_mappings")
if (isUnobfuscated) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "net.fabricmc.fabric-loom-remap")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    add("minecraft", "com.mojang:minecraft:${sc.current.version}")

    if (!isUnobfuscated) {
        add("mappings", project.extensions.getByType(LoomGradleExtensionAPI::class.java).officialMojangMappings())
        add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    } else {
        add("implementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    }

    add(if (isUnobfuscated) "implementation" else "modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    add("implementation", "com.squareup.okhttp3:okhttp:4.12.0")
    add("include", "com.squareup.okhttp3:okhttp:4.12.0")
    add("implementation", "com.squareup.okio:okio:3.6.0")
    add("include", "com.squareup.okio:okio:3.6.0")
    add("implementation", "com.google.code.gson:gson:2.10.1")
    add("include", "com.google.code.gson:gson:2.10.1")
    add("implementation", "com.typesafe:config:1.4.3")
    add("include", "com.typesafe:config:1.4.3")

    add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

extensions.configure<LoomGradleExtensionAPI>("loom") {
    splitEnvironmentSourceSets()

    mods {
        create(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    fabricModJsonPath.set(rootProject.file("src/main/resources/fabric.mod.json"))
    if (!isUnobfuscated) {
        accessWidenerPath.set(rootProject.file("src/main/resources/lumichat.accesswidener"))
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true")
        runDir = "../../run"
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

tasks {
    withType<Test> { useJUnitPlatform() }

    val mixinJava = "JAVA_${requiredJava.majorVersion}"

    processResources {
        inputs.property("id", project.property("mod.id"))
        inputs.property("name", project.property("mod.name"))
        inputs.property("version", project.property("mod.version"))
        inputs.property("minecraft", project.property("mod.mc_dep"))
        inputs.property("fabric_loader", project.property("deps.fabric_loader"))
        inputs.property("java", mixinJava)

        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep"),
            "fabric_loader" to project.property("deps.fabric_loader")
        )

        filesMatching("fabric.mod.json") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    named<ProcessResources>("processClientResources") {
        inputs.property("java", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        val mainJarTask = named<Jar>(if (isUnobfuscated) "jar" else "remapJar")
        val sourceJarTask = named<Jar>(if (isUnobfuscated) "sourcesJar" else "remapSourcesJar")
        from(mainJarTask.map { it.archiveFile }, sourceJarTask.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
