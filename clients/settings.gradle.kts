pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "CameraStatsClients"
include(":core", ":jdbc", ":desktop", ":media-worker")
if (!providers.gradleProperty("skipAndroid").isPresent) include(":android")
