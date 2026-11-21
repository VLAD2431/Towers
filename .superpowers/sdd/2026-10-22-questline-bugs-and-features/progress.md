# SDD ledger — plan: нет (план сформулирован в чате, утверждён пользователем 2026-10-22)

Задача (7 пунктов): B1 волна всегда в одних и тех же секторах; B2 оборудование дублируется после рестарта/краша; B3 `give` отказывает после выполненного задания; F1 объекты — только хитбокс без модели; F2 разлом спит до прихода игрока + облако частиц на 128 блоков; F3 пауза 5–7 с между волнами; F4 6 кастомных звуков.

Решения пользователя (до реализации): `chunk-area.sector-size` остаётся 20; все объекты (вышки, компьютеры, центральная вышка) — только зоны клика, без моделей.

Setup ruling: проект не под git — скрипты навыка (task-start/task-done/брифы) не работают; веду леджер вручную, проверка — `mvn clean package` (JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot, maven=C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd). Стоимость ошибки: нет истории коммитов — восстановимо диффами в разговоре.

Pre-flight: общие интерфейсы между задачами — `CustomDisplayManager`/`DisplayObject` (задача 1 меняет тип объекта, задача 4 читает `displays.tower(...)` только через `interaction()`), `GameManager` (задачи 2–5), `WaveChunkSelector` (задача 3 меняет правило выбора, задача 4 читает выбранный чанк), `PlayerQuestData` (задача 4 добавляет `waveStarted`, задача 5 — нет). Рулings по ходу ниже.

Состояние репозитория на 2026-10-22: леджер прошлой задачи `2026-10-22-sector-size-20` заявлял случайный выбор сектора, но в коде `WaveChunkSelector` всё ещё кольцевой поиск (`MAX_SEARCH_DISTANCE=6`), а дефолты `chunk-area.sector-size` в коде = 5 (в config.yml = 20). То есть часть той задачи в код не попала — B1 реализуется заново, с учётом того же правила.

## Задача 1 — F1 + B2 (объекты без моделей, без дублей) — готово

- `CustomDisplayManager` переписан: только `Interaction` (без `ItemDisplay`), метка PDC `questline:object` = key объекта, UUID в `displays.yml`; `place()` перемещает существующий объект вместо дубля; `adopt()` — по UUID → по метке рядом → нетегированный `Interaction` в той же точке → спавн; `removeLegacyModel` чистит легаси-`ItemDisplay`; `towerIdAt(Location)` вместо рефлексии в `GameManager`.
- `DisplayObject` → `record DisplayObject(Interaction interaction, String key, int towerId)`.
- `config.yml`: из `displays.*` убраны `material`/`item-model`/`custom-model-data`.
- Тесты: `CustomDisplayManagerTest` — 7 зелёных.

## Задача 2 — B3 (`give` после выполненного задания) — готово

- `QuestManager.give()` заменяет завершённое задание, отказывает только при активном задании этой же вышки; чистится устаревшая гарантия дропа и сброс выделения.
- `ConfigManager.message(String, Map<String,?>)`; в `QuestlineCommand` — `import java.util.Map;`; в `config.yml` — `quest-given`, `quest-given-active`.
- Тесты: `QuestManagerGiveTest` — 4 зелёных.

## Задача 3 — B1 (разброс секторов волны) — готово

- `WaveChunkSelector`: сектор выбирается первым (24 не-центральных сектора), чанк внутри — вторым (центр сектора, иначе сосед, обрезанный по границам сектора); откат — кольцевой поиск возле компьютера; `Picker` кэширует зондирование (чанк не зондируется дважды); `canKeep(int[], ChunkProbe)`.
- Дефолты `sector-size` 5→20: `MenuManager.SECTOR_SIZE = 20` (новое поле), используется в `MenuManager` и `GameManager`.
- Тесты: `WaveChunkSelectorTest` — 20 зелёных.

## Задача 4 — F2 + F3 (спящий разлом, облако частиц, пауза между волнами) — готово

- Новые файлы: `wave/RiftOpenMode.java` (DISTANCE/CHUNK), `wave/RiftCloud.java` (type/count/radius/interval/viewDistance + `DISABLED`).
- `RiftSpec` → добавлены поля `openMode`, `cloud`. `Rift`: фаза `DORMANT` (спит, пока игрок не вошёл в его чанк), `showCloud()` персонально через `Player#spawnParticle` в радиусе `view-distance` (128), `playerInChunk()` (сравнение `getBlockX() >> 4`), колбэк `onOpened` (вызывается один раз в `enterOpen()`), новая перегрузка `Rift.spawn(..., Runnable)`.
- `WaveConfig.parseRift` → `rift.open-mode` + `rift.cloud.*`; неизвестный режим/частица → предупреждение и безопасный фолбэк (спящий разлом / облако выключено).
- `GameManager`: `startWaves` больше не спавнит мобов (ставит `data.waveStarted(false)`), `onRiftOpened(player)` (ставит `waveStarted(true)` и планирует волну), `scheduleWave(player)` через `runTaskLater`, `waveDelayTicks(Random, min, max)` → 100–140 тиков (5–7 с), `onDeath`/`finishWave` → `scheduleWave`, `resumeWaves` → если волна уже была запущена, планирует её заново, иначе разлом продолжает спать.
- `PlayerQuestData.waveStarted` + `PlayerDataStorage` (`wave-started`).
- `config.yml`: `chunk-area.wave-interval-min-seconds: 5`, `chunk-area.wave-interval-max-seconds: 7`, `rift.open-mode: chunk`, `rift.cloud.{type,count,radius,interval,view-distance}`; мёртвый ключ `chunk-area.spawn-radius` удалён.
- Тесты: `RiftTest` — 20 зелёных (5 новых: сон, пробуждение по чанку, чужие чанки, облако видно/не видно, облако гаснет после открытия); `GameManagerWaveStartTest` — 5 зелёных (мобы ждут разлома, пауза 5–7 с, волна после паузы, разлом не открывается дважды); `WaveConfigRiftTest` — 14 зелёных (8 новых на open-mode и облако).
- Сборка: `mvn -o clean test` → BUILD SUCCESS, 158 тестов, 0 падений.

Правило сборки: всегда `mvn -o clean ...` — `target/test-classes` иногда содержит классы, скомпилированные IDE (ECJ) с ошибками, и Maven их переиспользует.

## Задача 5 — F4 (6 кастомных звуков ресурспака) — готово

- `Sounds.resourcePath(raw)` — путь кастомного звука (`questline:quest_given` → `questline:quest_given`), null для некорректного имени; `Sounds.isCustom(raw)` — отличает звук ресурспака от звука игры (namespace ≠ `minecraft`).
- `ConfigManager.soundName(path)` — имя кастомного звука из конфига; `ConfigManager.playSound(player, path)` — играет кастомный звук по имени (`Player#playSound(Location, String, float, float)`) или обычный по enum `Sound`; неизвестное имя кастомного звука → предупреждение в консоль.
- Шесть звуков и их места: `quest-given` (`QuestlineCommand.give`), `quest-selected` (`MenuClickListener`), `quest-complete` и `wave-clear` (`GameManager.finishWave`), `rift-open` (`GameManager.onRiftOpened`), `wave-start` (`GameManager.spawnWave`). Звуки клика калибровки переведены на `ConfigManager.playSound` — там тоже можно кастомный звук.
- `config.yml`: `sounds.{quest-given,quest-selected,quest-complete,rift-open,wave-start,wave-clear}` с плейсхолдерами `questline:*` + ключи `-volume`/`-pitch`. Пользователь подставляет свои id из `sounds.json`.
- Тесты: `SoundsTest` — 11 (6 новых), `ConfigManagerSoundTest` — 6 новых, `ShippedConfigSoundsTest` — 2 новых.

## Задача 6 — README и финальная сборка — готово

- `README.md`: обновлены механика (выбор сектора, спящий разлом, пауза между волнами), раздел «Объекты плагина» (только зоны клика, без моделей, без дублей), «Волны» (новый порядок выбора чанка), `chunk-area` (сектор 20, `wave-interval-*`), «Разлом и лут» (`open-mode`, `cloud`), звуки (6 кастомных), `/questline give` (замена выполненного задания), «Обновление существующих объектов».
- Финальная сборка: `mvn -o clean package` → BUILD SUCCESS, 172 теста, 0 падений, `target/Questline-1.0-SNAPSHOT.jar`.

### Итог по всем пунктам

| Пункт | Что сделано |
|---|---|
| B1 | Сектор выбирается случайно (24 не-центральных), чанк внутри — центр/сосед сектора; откат — кольцевой поиск у компьютера |
| B2 | PDC-метка `questline:object`, UUID в `displays.yml`, «усыновление» существующей сущности, `place()` = перемещение, чистка легаси-моделей |
| B3 | `give()` заменяет завершённое задание; отказ только при активном задании этой вышки |
| F1 | Все объекты — невидимые зоны клика (`Interaction`), моделей нет |
| F2 | Разлом спит до входа игрока в его чанк; облако частиц видно на 128 блоков |
| F3 | Пауза 5–7 с перед первой волной и между волнами |
| F4 | 6 кастомных звуков ресурспака с плейсхолдерами в конфиге |

Финальная полировка: логика «волна отбита → следующая волна / задание выполнено» вынесена в `GameManager.advanceAfterWave(owner)` — раньше звук `wave-clear` играл только на одном из двух путей (`onDeath` и `finishWave` считались по-разному). `ConfigManager.playSound` получил проверку на `null`-игрока (владелец волны может быть офлайн) и единое место предупреждения об испорченном имени кастомного звука. Итог: `mvn -o clean package` → BUILD SUCCESS, 173 теста, 0 падений.
