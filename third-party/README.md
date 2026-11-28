# Зависимости автономной сборки

В `maven/` лежат оригинальные JAR, POM и Gradle Module Metadata для сборки приложения, тестов, detekt, JMH, Compose Hot Reload и Metal smoke. Там же находятся плагины Gradle. При обычном запуске Gradle ищет зависимости в копии, поставляемой с проектом, и обращается к Gradle Plugin Portal, Maven Central и Google Maven за отсутствующими версиями. Загруженные файлы попадают в стандартный кеш Gradle. При `--refresh-dependencies` публичные репозитории проверяются первыми, зеркало служит запасным источником. При `--offline` доступны локальное зеркало и ранее загруженные версии из пользовательского кеша. Gradle проверяет SHA-256 выбранных файлов по [verification-metadata.xml](../gradle/verification-metadata.xml).

Здесь 274 JAR и 838 файлов метаданных. Compose Desktop выбирает нативный Skiko для Linux, macOS и Windows на x64 или arm64. При запуске в classpath попадает версия для текущей платформы. Для работы нужен установленный JDK, для графического окна — доступный рабочий стол. Для задачи разработки `hotRun` нужен JetBrains Runtime 25 с поддержкой HotSwap (подходит JBR из IntelliJ IDEA). Для выпуска с ProGuard используйте JDK с каталогом `jmods`: задача `packageReleaseUberJarForCurrentOS` проверена на таком JDK 24. Обычный `run` проверен на JDK 25. Дистрибутив Gradle хранится [рядом с Wrapper](../gradle/wrapper/distribution).

Команда `./gradlew --refresh-dependencies check` запрашивает актуальные метаданные по обычным правилам Gradle. Параметры корпоративного HTTP(S)-прокси берутся из стандартных свойств Gradle. Новую зависимость для автономного checkout добавляют в зеркало вместе с контрольными суммами. Если рабочая копия впервые открывается через IDE без сети, сначала выполните `./gradlew --offline --version`, чтобы Wrapper собрал локальный ZIP. [Кэш зависимостей Gradle](https://docs.gradle.org/9.9.2/userguide/dependency_caching.html)

## Происхождение и лицензии

- [artifacts.tsv](artifacts.tsv) содержит исходный URL и SHA-256 каждого Maven-файла. Публикации получены из Maven Central, Google Maven и Gradle Plugin Portal. У некоторых плагинов публикации одной версии в разных репозиториях различаются. В таблице указан источник именно той копии, которая включена в сборку.
- [components.tsv](components.tsv) перечисляет координаты библиотек и плагинов с бинарными файлами и лицензии из их публикаций. Оригинальные POM и встроенные в JAR файлы лицензий сохранены.
- [notices](notices) содержит лицензии и уведомления поставщиков Kotlin, Compose и нативных библиотек. Основной рендер использует Skiko 0.150.2 со Skia m150-a3f509bf52. Инструменты Hot Reload включают Skiko 0.144.6 со Skia m144-22f58c9fd4. Для совпадающих лицензий сохранена одна копия, для libpng из m144 приведён [отдельный текст](notices/skia-m144/libpng-LICENSE.txt). [Версия Skia в Skiko 0.144.6](https://github.com/JetBrains/skiko/blob/v0.144.6/skiko/gradle.properties) · [Зависимости Skia m144](https://github.com/JetBrains/skia/blob/m144-22f58c9fd4/DEPS)
- Для инструментов Hot Reload сохранены лицензии [Compose Hot Reload 1.2.0](https://github.com/JetBrains/compose-hot-reload/tree/v1.2.0), [MCP Kotlin SDK 0.13.0](https://github.com/modelcontextprotocol/kotlin-sdk/tree/0.13.0) и [Evas 1.4.0](https://github.com/sellmair/evas/tree/v1.4.0). Условия Javassist допускают распространение по Apache License 2.0. [Уведомление Javassist](notices/javassist-License.html)
- [sources](sources) содержит исходные деревья [OpenJDK JMH 1.37](https://github.com/openjdk/jmh/tree/1.37) и [JUnit 5.10.1](https://github.com/junit-team/junit5/tree/r5.10.1) вместе с файлами сборки.
- Для релизной упаковки включены бинарные файлы ProGuard Base и Gradle 7.8.0, [полное дерево исходников с файлами сборки](sources/proguard-7.8.0.tar.gz) и [текст GPL с исключением для Gradle и других инструментов](notices/proguard-7.8.0/license.md). Архив соответствует тегу v7.8. ProGuardCORE 9.2.0 поставляется по Apache License 2.0. [ProGuard v7.8](https://github.com/Guardsquare/proguard/tree/v7.8) · [Лицензия ProGuardCORE 9.2.0](https://github.com/Guardsquare/proguard-core/blob/v9.2.0/LICENSE)

## Проверка

После клонирования выполните `./gradlew --offline check` из корня проекта. На Windows используйте `gradlew.bat --offline check`. Для проверки каждого файла зеркала, уведомлений и архивов исходников выполните из каталога `third-party` одну из команд:

```sh
shasum -a 256 -c SHA256SUMS     # macOS
sha256sum -c SHA256SUMS        # Linux
```

При обновлении библиотеки обновите версию в каталоге Gradle, её транзитивные файлы, лицензии и контрольные суммы. Для смены Compose или Skiko включите артефакты всех шести платформ. Сверьте файлы с исходными репозиториями, затем проверьте сборку из чистого checkout с пустым `GRADLE_USER_HOME`.

Skia включает FreeType и libjpeg-turbo. Portions of this software are copyright © The FreeType Project (www.freetype.org). All rights reserved. This software is based in part on the work of the Independent JPEG Group.
