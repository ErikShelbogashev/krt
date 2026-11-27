# Architecture Decision Records

Решения, которые меняют структуру модулей krt или отходят от [Эргономичной архитектуры](https://github.com/ergonomic-code/ergo-wiki/blob/2b77b9c/kb/production-code/models/ergo-arch.adoc). Основание для всех — отчёт [docs/ea-review/02-discrepancies.md](../ea-review/02-discrepancies.md), вопросы Q1–Q9, решения владельца от 2026-11-27.

| № | Решение | Вопрос | Отличие от вики |
| --- | --- | --- | --- |
| [0001](0001-package-layout.md) | Раскладка пакетов app/domain/i9ns/infra/platform | Q1 | нет |
| [0002](0002-randomness-as-parameter.md) | Случайность передаётся в вычисления параметром | Q2 | нет |
| [0003](0003-ports-in-compose.md) | Порт = обработчик UI-события Compose | Q3 | адаптация к desktop |
| [0004](0004-explicit-render.md) | Перерисовка вызывается явно из операций | Q4 | нет |
| [0005](0005-immutable-model.md) | Неизменяемые модели, сцена — ресурс | Q5 | нет |
| [0006](0006-manual-composition-root.md) | Ручной корень композиции без DI-контейнера | Q6 | да, осознанно |
| [0007](0007-testing.md) | JUnit 5, golden-тест кадра, без моков | Q7 | нет |
| [0008](0008-trace-kernel.md) | Вычислительное ядро и скомпилированная сцена | — | да: исключения для горячего кода |
| [0009](0009-gpu-mode.md) | Отдельный GPU-режим рендера на SkSL | — | нет, исключение из правила «пиксели на CPU» |

Пороги (Q8) приняты как в вики, отдельного ADR нет: см. [AGENTS.md](../../AGENTS.md#пороги). Дефекты поведения (Q9) исправляются отдельными PR после рефакторинга.

Ссылки на вики зафиксированы на коммит `2b77b9c` репозитория ergonomic-code/ergo-wiki.
