# Standalone Waves Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Запускать волны выбранного типа без задания и исправить Tab для test loot.

**Architecture:** Хранить самостоятельные запуски в GameManager по UUID отдельно от QuestManager. Переиспользовать существующие создание рифта, спавн мобов, таймеры, эффекты и лут через небольшие точки выбора состояния запуска; не создавать фиктивных заданий.

**Tech Stack:** Java 21, Paper API 1.21+, Maven, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-11-standalone-waves-design.md`

## Global Constraints
- Paper API 1.21+, Java 21, Maven.
- Новые сообщения — Adventure Component/MiniMessage.
- В долгоживущих коллекциях хранить UUID, не Player/Entity.
- Не изменять прогресс заданий самостоятельным запуском.
- Репозиторий Git отсутствует: коммиты не выполнять; target/ и jar не добавлять в исходники.

## Review Focus
- Повторный запуск во время ожидания рифта или таймера отклоняется, как и во время боя.
- Водный или занятый чанк отклоняется без оставшихся сущностей и таймеров.
- Выход игрока, отмена и выключение очищают временный запуск и chunk tickets.
- Ошибка спавна кастомного моба не меняет задания и очищает временный запуск.
- Перезагрузка конфига не оставляет запуск с исчезнувшим типом в зависшем состоянии.

### Task 1: Самостоятельный жизненный цикл волн

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/game/GameManager.java`
- Test: `src/test/java/VLADMIX90/towers/game/GameManagerStandaloneWaveTest.java`

**Interfaces:**
- Consumes: WaveConfig.type(String), typeIds(), lootForType(String), существующие методы рифта и спавна мобов.
- Produces: `public boolean startStandaloneWaves(Player player, String typeId)`; false при невозможности запуска, без изменения существующего запуска.

- [ ] Написать тесты запуска без selected quest, выбранного типа, полного цикла серии и соответствующего лута; проверить отсутствие вызовов startWaves/waveDefeated/cancelRun/save у QuestManager для самостоятельного запуска.
- [ ] Дополнить тестами неизвестного типа, повторного запуска на всех стадиях, конфликта с квестовыми волнами, воды и занятого чанка; проверить отсутствие побочных эффектов при отказе.
- [ ] Запустить `mvn -Dtest=GameManagerStandaloneWaveTest test`, убедиться в падении из-за отсутствия нового API.
- [ ] Реализовать отдельное временное состояние по UUID и startStandaloneWaves. Подключить выбор временного состояния и типа к существующему жизненному циклу без изменения квестового поведения. Использовать обычное число волн и лут выбранного типа.
- [ ] Добавить и выполнить тесты отмены, выхода, выключения, ошибки кастомного спавна и удаления типа при reload. Очистка должна освобождать мобов, рифт, таймеры и chunk tickets, не менять задания.
- [ ] Запустить `mvn -Dtest=GameManagerStandaloneWaveTest,GameManagerWaveStartTest test` и `mvn test-compile`; ожидается PASS.

### Task 2: Команда и автодополнение

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/command/TowersCommand.java`
- Modify: `src/main/java/VLADMIX90/towers/command/TowersTabCompleter.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `README.md`
- Modify: `src/test/java/VLADMIX90/towers/command/TowersTabCompleterTest.java`
- Create: `src/test/java/VLADMIX90/towers/command/TowersCommandWaveTest.java`

**Interfaces:**
- Consumes: `GameManager.startStandaloneWaves(Player, String)` и `waveTypeIds()`.
- Produces: `/towers wave <type>` и подсказки типов для этой команды и `/towers test loot <type>`.

- [ ] Написать тесты передачи типа в GameManager, отсутствия прав, вызова из консоли, пропущенного типа и неуспешного запуска. Для Tab проверить корневой wave, оба пути типов, фильтрацию префикса, актуальные ID и отсутствие подсказок без прав/на лишнем аргументе.
- [ ] Запустить `mvn -Dtest=TowersTabCompleterTest,TowersCommandWaveTest test`; ожидается FAIL по новым сценариям.
- [ ] Добавить обработку команды с русскими Adventure-сообщениями; исправить test loot на args.length == 3 / args[2]. Обновить справку, plugin.yml и README точным синтаксисом.
- [ ] Запустить целевые тесты, затем `mvn test` и `mvn compile`; ожидается BUILD SUCCESS.
- [ ] Проверить итоговые изменения на соответствие спецификации, отсутствие изменений соседних функций и прямых ссылок Player/Entity в новом состоянии; отметить ограничения проверки без живого сервера.
