# 0001. Раскладка пакетов

Статус: принято, 2026-11-27.

## Контекст
Весь код лежит в `raytracer.model` (ядро, рендерер, чтение файлов, мёртвая Swing-панель) и `raytracer.view`, `Main.kt` — в корневом пакете. Имя `model` не отражает содержимого. Вики предлагает раскладку `app / domain / i9ns / infra / platform` ([ergo-arch.adoc, «Раскладка кода по пакетам»](https://github.com/ergonomic-code/ergo-wiki/blob/2b77b9c/kb/production-code/models/ergo-arch.adoc)), помечая её мягкой рекомендацией. Trainer Advisor использует то же, но `core` вместо `domain` и `tech` вместо `platform`.

## Решение
Корневой пакет `krt`. Раскладка по вики:

```
krt
├── Main.kt                 точка входа, вызывает infra
├── app                     порты (обработчики UI-событий) и операции, по фичам UI
│   ├── camera              вращение/перемещение камеры, настройки камеры
│   ├── scene               загрузка .obj, перемещение объектов
│   └── render              режим wireframe, настройки трассировщика и рендера
├── domain                  ресурсы и их модели (ядро)
│   ├── scene               ресурс Scene; model: Traceable, Sphere, Triangle, Hit, материалы
│   ├── camera              ресурс CameraState; model: Camera (неизменяемая), CameraSettings
│   └── render              ресурс Renderer (пул, задачи, кадр); model: RenderSettings; трансформации трассировки
├── i9ns
│   └── objfile             чтение .obj с диска (ввод) + разбор (трансформация)
├── infra                   корень композиции (ADR 0006)
└── platform
    ├── math                Vec3/Point3, Color, Ray — переиспользуемая математика
    └── awt                 конвертация в AWT-типы
```

Подпакеты ресурса — как в вики: класс ресурса в корне пакета, `model` для сущностей/значений, `views`/`commands`/`queries` только при появлении соответствующих DTO.

Выбор `domain` вместо `core`: вики — первоисточник, имена Trainer Advisor — локальный выбор автора.

## Последствия
- Перенос делается механически, отдельными коммитами без правок логики, после рефакторинга поведения (иначе diff нечитаем).
- `mainClass` в `build.gradle.kts` меняется на `krt.MainKt`.
- Konsist-проверки (этап 6) опираются на эти имена пакетов.
