plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// Зависимость добавляется здесь, чтобы не изменять конфигурацию подписи приложения.
subprojects {
    afterEvaluate {
        if (project.name == "app") {
            project.dependencies.add(
                "implementation",
                "com.google.android.gms:play-services-nearby:19.3.0",
            )
        }
    }
}
