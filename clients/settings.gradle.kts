pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "CameraStatsClients"
include(":core", ":jdbc", ":desktop")
if (!providers.gradleProperty("skipAndroid").isPresent) include(":android")
