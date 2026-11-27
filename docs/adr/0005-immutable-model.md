# 0005. Неизменяемые модели, сцена — ресурс

Статус: принято, 2026-11-27.

## Контекст
`Point3` — `data class` с `var` и мутирующими `translate`/`divAssign`; `Point3.ZERO`/`ONE` — разделяемые изменяемые константы. Объекты сцены мутируются на месте, а `World.objects` (`MutableList`) пишется из UI-потока и читается из пула рендера. Вики: [principles/domain/use-immutable-data.adoc](https://github.com/ergonomic-code/ergo-wiki/blob/2b77b9c/kb/production-code/principles/domain/use-immutable-data.adoc), [ergo-code-imperatives.adoc, «Пиши в прагматичном функциональном стиле»](https://github.com/ergonomic-code/ergo-wiki/blob/2b77b9c/kb/production-code/ergo-code-imperatives.adoc) — изменяемое состояние не выходит за границы вычисления.

## Решение
- `Point3`/`Color`, `Ray`, `Hit`, `Sphere`, `Triangle`, материалы — неизменяемые (`val`). `translate` возвращает копию.
- `Camera` разделяется на неизменяемое значение (поза + оптика + размер кадра, методы `ray`/`project`) и ресурс `CameraState`, хранящий текущее значение.
- `Scene` — ресурс с одной изменяемой ссылкой (`@Volatile`/атомарной) на неизменяемый список объектов. Запись заменяет список целиком, рендер получает снимок.
- Локальная мутация внутри вычислений допустима (вики).

## Последствия
- Устраняется гонка данных между UI и рендером (побочный эффект, поведение для пользователя не меняется).
- Операторы `Point3` уже создают новые объекты, дополнительных аллокаций почти нет.
