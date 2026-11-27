# krt — этап 0: инвентаризация (предварительно)

Сверка от `main` @ `72633767acd167785c6756c70cf097a583ec99aa` (github.com/ErikShelbogashev/krt).

## Что это за проект
Десктопный трассировщик лучей на Kotlin 1.9.22 + Compose Desktop 1.6.0 (+ mpfilepicker). Один Gradle-модуль, ~1 400 строк, 24 файла.
**Backend-кода нет**: нет Spring, БД, HTTP, внешних сервисов. Единственная внешняя система — файловая система (чтение `.obj`).

## Правила проекта
AGENTS.md / CLAUDE.md / docs / ADR отсутствуют. README — только скриншоты. Тестов нет вообще (`src/test` отсутствует), detekt/ktlint/ArchUnit не подключены.

## Карта кода
| Пакет | Содержимое | Роль в терминах ЭА |
| --- | --- | --- |
| `Main.kt` | `main`, `settingsPanel`, `handleKeyEvent` | порты (обработчики UI-событий) + композиция + логика (ресайз буфера, дельты yaw/pitch) |
| `raytracer.view` | `CameraSettings`, `RayTracerSettings`, `RenderControllerSettings`, `ObjectController`, `LoadModelUI` | порты (слайдеры, джойстик, file picker); в `ObjectController` есть чистая логика (`getDirection`, `coerceInCircle`) |
| `raytracer.model` | `World`, `Camera`, `RayTracer`, `RenderController`, `ObjParser`, `Sphere`, `Triangle`, материалы, `Point3`/`Color`, `Hit`, `Ray` | смесь: изменяемое состояние сцены (ресурсы-кандидаты), чистая математика (трансформации), планировщик рендера с пулом потоков, чтение файла |
| `raytracer.model.ImagePanel` | Swing JPanel | не используется (мёртвый код) |

Интерфейсы: `Material` (4 реализации), `Traceable` (2 реализации) — правило «интерфейс только при ≥2 реализациях» соблюдено.

## Первые находки, применимые к ЭА независимо от стека
- Глобальное окружение в вычислениях: `Random` в `Camera.ray`, `Lambert/Metal/Dielectric.scatter`, `Point3.random*`, `RenderController`; `UUID.randomUUID()` в `Sphere`/`Triangle`; `System.currentTimeMillis()` в `RenderController`.
- Изменяемые модели: `Point3` — `data class` с `var` и мутирующими `translate`/`divAssign`; `World`, `Camera`, `RenderController` — изменяемые объекты, которые UI правит напрямую.
- Логика в портах: размерные проверки и пересоздание `BufferedImage` в `settingsPanel`, расчёт дельт камеры в обработчике drag.
- `RenderController` смешивает выбор режима, планирование потоков, отрисовку в буфер и тайминг перерисовки.
- `ObjParser` читает файл и парсит в конструкторе (эффект + трансформация вместе), поддерживает только квады.
- Дублирование `moveObject` / `moveObjectInDirection`.
- Тестов нет, поэтому «поведение не меняется» сейчас нечем проверить: до любого рефакторинга нужны характеризационные тесты (с фиксированным seed).

## Что из брифа неприменимо
Spring Data JDBC, `*Repo`/`*Dao`, транзакции, HTTP-маппинг и Problem Details, Spring-конфигурации в `infra`, раскладка Trainer Advisor `core/<область>/<ресурс>/{persistence,…}`, Testcontainers.
