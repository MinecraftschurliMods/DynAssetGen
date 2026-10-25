plugins {
    idea
    id("net.neoforged.gradle.userdev")
    id("at.minecraftschurli.gradle.modhelper")
}

subsystems.conventions.runs.shouldDefaultRunsBeCreated.set(false)

helper {
    withClientRun()
    withServerRun()
    // The test source set holds the test mod, so the JUnit run loads it as a mod as well.
    withTestSourceSet {
        modSources.add(sourceSets.test.get())
    }

    publication.pom {
        organization {
            name = "Minecraftschurli Mods"
            url = "https://github.com/MinecraftschurliMods"
        }
        developers {
            developer {
                id = "minecraftschurli"
                name = "Minecraftschurli"
                email = "minecraftschurli@gmail.com"
                url = "https://github.com/Minecraftschurli"
                organization = "Minecraftschurli Mods"
                organizationUrl = "https://github.com/MinecraftschurliMods"
                timezone = "Europe/Vienna"
            }
            developer {
                id = "ichhabehunger54"
                name = "IchHabeHunger54"
                url = "https://github.com/IchHabeHunger54"
                organization = "Minecraftschurli Mods"
                organizationUrl = "https://github.com/MinecraftschurliMods"
                timezone = "Europe/Vienna"
            }
        }
    }
}