import org.gradle.kotlin.dsl.internal.sharedruntime.support.classFilePathCandidatesFor

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }

    classFilePathCandidatesFor( "de.undercouch:gradle-download-task:3.2.0")
}

rootProject.name = "FaceDetectionMediaPipe"
include(":app")
 