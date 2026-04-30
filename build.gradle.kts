plugins {
    //? if >=26.1 {
    id("net.fabricmc.fabric-loom")
    //?} else {
    /*id("net.fabricmc.fabric-loom-remap")
    *///?}

    // `maven-publish`
    // id("me.modmuss50.mod-publish-plugin")
}

val isUnobfuscated = !project.hasProperty("deps.yarn_mappings")

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava = when {
    isUnobfuscated -> JavaVersion.VERSION_25
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
    fun fapi(configurationName: String, vararg modules: String) {
        for (it in modules) add(configurationName, fabricApi.module(it, property("deps.fabric_api") as String))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")

    if (!isUnobfuscated) {
        add("mappings", "net.fabricmc:yarn:${property("deps.yarn_mappings")}:v2")
        add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    } else {
        implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    }

    val commandApiModule = if (sc.current.parsed >= "1.19") "fabric-command-api-v2" else "fabric-command-api-v1"
    val fabricApiConfiguration = if (isUnobfuscated) "implementation" else "modImplementation"
    fapi(fabricApiConfiguration, "fabric-lifecycle-events-v1", "fabric-resource-loader-v0", "fabric-content-registries-v0", "fabric-data-generation-api-v1", commandApiModule)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    include("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.6.0")
    include("com.squareup.okio:okio:3.6.0")
    implementation("com.google.code.gson:gson:2.10.1")
    include("com.google.code.gson:gson:2.10.1")
    implementation("com.typesafe:config:1.4.3")
    include("com.typesafe:config:1.4.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/lumichat.accesswidener")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
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
    val mixinJava = "JAVA_${requiredJava.majorVersion}"

    processResources {
        inputs.property("id", project.property("mod.id"))
        inputs.property("name", project.property("mod.name"))
        inputs.property("version", project.property("mod.version"))
        inputs.property("minecraft", project.property("mod.mc_dep"))
        inputs.property("java", mixinJava)

        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep")
        )

        filesMatching("fabric.mod.json") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    named<ProcessResources>("processClientResources") {
        inputs.property("java", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    withType<Test> { useJUnitPlatform() }

    register<Copy>("buildAndCollect") {
        group = "build"
        val mainJarTaskName = if (isUnobfuscated) "jar" else "remapJar"
        val sourcesJarTaskName = if (isUnobfuscated) "sourcesJar" else "remapSourcesJar"
        val mainJarTask = named<org.gradle.jvm.tasks.Jar>(mainJarTaskName)
        val sourcesJarTask = named<org.gradle.jvm.tasks.Jar>(sourcesJarTaskName)
        from(mainJarTask.map { it.archiveFile }, sourcesJarTask.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
