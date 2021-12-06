# paperweight-test-plugin

jmp's test plugin for `paperweight-userdev` development

(also serves as an example until more thorough documentation is created)

### note

- `build.gradle.kts` and `settings.gradle.kts` both contain important configuration.
- `paperweight-userdev` automatically detects shadow and will use `shadowJar` as input for `reobfJar`. This means no extra configuration is required to use `paperweight-userdev` with shadow. See the `shadow` branch on this repository for an exmaple usage of shadow with `paperweight-userdev`.
- The `plugin-yml` and `run-paper` Gradle plugins are both optional, however I use them in almost all my plugin projects and recommend at least trying them out. `plugin-yml` auto-generates your plugin.yml file from configuration in the build file, and `run-paper` allows for launching a test server with your plugin through the `runServer` and `runMojangMappedServer` tasks.
