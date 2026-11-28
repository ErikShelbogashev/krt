# Зависимости автономной сборки

В `maven/` лежат оригинальные JAR, POM и Gradle Module Metadata для сборки приложения, тестов, detekt, JMH и Metal smoke. Там же находятся плагины Gradle. Проект разрешает плагины и библиотеки из этого каталога. Gradle проверяет SHA-256 выбранных файлов по [verification-metadata.xml](../gradle/verification-metadata.xml).

Здесь 173 JAR и 504 файла метаданных. Compose Desktop выбирает нативный Skiko для Linux, macOS и Windows на x64 или arm64. При запуске в classpath попадает версия для текущей платформы. Для работы нужен установленный JDK, для графического окна — доступный рабочий стол. Дистрибутив Gradle хранится [рядом с Wrapper](../gradle/wrapper/distribution).

## Происхождение и лицензии

- [artifacts.tsv](artifacts.tsv) содержит исходный URL и SHA-256 каждого Maven-файла. Публикации получены из Maven Central, Google Maven и Gradle Plugin Portal. У некоторых плагинов публикации одной версии в разных репозиториях различаются. В таблице указан источник именно той копии, которая включена в сборку.
- [components.tsv](components.tsv) перечисляет координаты библиотек и плагинов с бинарными файлами и лицензии из их публикаций. Оригинальные POM и встроенные в JAR файлы лицензий сохранены.
- [notices](notices) содержит тексты лицензий и уведомления правообладателей. Для Kotlin и Compose Multiplatform включены уведомления поставщиков, для Skiko 0.150.2 — уведомления Skia m150-a3f509bf52 и её компонентов.
- [sources](sources) содержит исходные деревья JMH 1.37 и JUnit 5.10.1 вместе с файлами их сборки. Источники — [OpenJDK JMH](https://github.com/openjdk/jmh/tree/1.37) и [JUnit](https://github.com/junit-team/junit5/tree/r5.10.1).

## Проверка

После клонирования выполните `./gradlew --offline check` из корня проекта. На Windows используйте `gradlew.bat --offline check`. Для проверки каждого файла зеркала, уведомлений и архивов исходников выполните из каталога `third-party` одну из команд:

```sh
shasum -a 256 -c SHA256SUMS     # macOS
sha256sum -c SHA256SUMS        # Linux
```

При обновлении библиотеки обновите версию в каталоге Gradle, её транзитивные файлы, лицензии и контрольные суммы. Для смены Compose или Skiko включите артефакты всех шести платформ. Сверьте файлы с исходными репозиториями, затем проверьте сборку из чистого checkout с пустым `GRADLE_USER_HOME`.

Skia включает FreeType и libjpeg-turbo. Portions of this software are copyright © The FreeType Project (www.freetype.org). All rights reserved. This software is based in part on the work of the Independent JPEG Group.
