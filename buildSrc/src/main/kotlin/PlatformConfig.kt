import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

fun Project.applyPlatformAndCoreConfiguration(javaRelease: Int = 17) {
    applyCommonConfiguration()
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "com.jfrog.artifactory")
    applyCommonJavaConfiguration(
        sourcesJar = true,
        javaRelease = javaRelease,
        banSlf4j = false
    )

    ext["internalVersion"] = "$version+${rootProject.ext["gitCommitHash"]}"

    configure<CheckstyleExtension> {
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        toolVersion = "10.3"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "compileOnly"("com.google.code.findbugs:jsr305:3.0.2")
        "testCompileOnly"("com.google.code.findbugs:jsr305:3.0.2")
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:${Versions.JUNIT}")
        "testImplementation"("org.junit.jupiter:junit-jupiter-params:${Versions.JUNIT}")
        "testImplementation"("org.mockito:mockito-core:${Versions.MOCKITO}")
        "testImplementation"("org.mockito:mockito-junit-jupiter:${Versions.MOCKITO}")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:${Versions.JUNIT}")
    }

    // Java 8 turns on doclint which we fail
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            tags(
                "apiNote:a:API Note:",
                "implSpec:a:Implementation Requirements:",
                "implNote:a:Implementation Note:"
            )
        }
    }

    the<JavaPluginExtension>().withJavadocJar()

    if (name == "worldguard-core" || name == "worldguard-bukkit") {
        the<JavaPluginExtension>().withSourcesJar()
    }

    tasks.named("check").configure {
        dependsOn("checkstyleMain", "checkstyleTest")
    }

    configure<PublishingExtension> {
        publications {
            register<MavenPublication>("maven") {
                from(components["java"])
                versionMapping {
                    usage("java-api") {
                        fromResolutionOf("runtimeClasspath")
                    }
                    usage("java-runtime") {
                        fromResolutionResult()
                    }
                }
            }
        }
    }

    applyCommonArtifactoryConfig()
}

fun Project.applyShadowConfiguration() {
    apply(plugin = "com.github.johnrengelman.shadow")
    tasks.named<ShadowJar>("shadowJar") {
        archiveClassifier.set("dist")
        dependencies {
            include(project(":worldguard-libs:core"))
            //include(project(":worldguard-libs:${project.name.replace("worldguard-", "")}"))
            include(project(":worldguard-core"))

            relocate("org.flywaydb", "com.sk89q.worldguard.internal.flywaydb") {
                include(dependency("org.flywaydb:flyway-core:3.0"))
            }
            exclude("com.google.code.findbugs:jsr305")
        }
        exclude("GradleStart**")
        exclude(".cache")
        exclude("LICENSE*")
        exclude("META-INF/maven/**")
    }
    val javaComponent = components["java"] as AdhocComponentWithVariants
    // I don't think we want this published (it's the shadow jar)
    javaComponent.withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
        skip()
    }
}
