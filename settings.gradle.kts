pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    
    // When you're using groovy gradle dsl, the above line should be replaced with:
    // maven { url = "https://repo.papermc.io/repository/maven-public/" }
  }
}

rootProject.name = "paperweight-test-plugin"
