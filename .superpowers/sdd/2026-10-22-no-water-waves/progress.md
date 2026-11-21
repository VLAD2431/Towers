# SDD ledger — plan: нет (задача сформулирована в чате, дизайн согласован с пользователем)

Задача: рифт и волны не должны спавниться в биомах с водой.

Pre-flight: общих интерфейсов с прошлыми задачами нет. Новый `wave/WaterGuard` потребляет только `Material`/`Biome` из Bukkit; `WaveChunkSelector.pick` получает новый параметр-пробу; `GameManager` потребляет `WaterGuard` и `WaveChunkSelector`.

Setup ruling: проект не под git — скрипты навыка (task-start/task-done/review-package) не работают; бриф ведётся здесь вручную, проверка — `mvn clean package` (JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot, maven=C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd). Стоимость ошибки: нет истории коммитов — восстановимо диффами в разговоре.

TDD ruling: `Biome` в Paper 1.21.7 — интерфейс с реестром (`Registry.BIOME`), его статический инициализатор читает сервер, поэтому `Biome` нельзя ни создать, ни замокать в юнит-тесте (то же, что ранее с `PotionEffectType`/`Sound`). Поэтому чистая часть вынесена на строки: `WaterGuard.isWaterBiomeKey(String)` (ключ биома) и `WaterGuard.isWaterBlock(Material)` (`Material` в 1.21.7 ещё enum, в тестах доступен). Проверки, которым нужен живой мир (`isWaterColumn`, `isWaterChunk`, выбор сухого столбца), вынесены в `GameManager` и проверяются компиляцией + чек-листом в игре. Стоимость ошибки: поведение в живом мире доказывается только в игре.

Решения пользователя (согласованы до реализации): (1) «вода» = водный биом ИЛИ вода на поверхности; (2) если в области 5×5 секторов нет сухого чанка — волна не запускается (warning в консоль + сообщение игроку).

Задача 1: complete (tests: mvn test -Dtest=WaterGuardTest → 9/9 pass; RED first: «cannot find symbol: variable WaterGuard»)
Задача 2: complete (tests: mvn test -Dtest=WaveChunkSelectorTest → 6/6 pass; RED first: «method pick … cannot be applied to given types»; один тест пришлось переписать — он занимал ВСЕ кандидаты, что и есть fallback-задача, конфликт с «fallsBackToAnOccupiedButDryChunk»)
Задача 3: complete (build: mvn clean package → BUILD SUCCESS, 82/82 pass; GameManager: chooseWaveChunk → boolean + isWaterColumn/isWaterChunk + dryColumns + spawnLocation по сухим столбцам + riftDropLocation по сухому столбцу + createRift с проверкой; QuestlineCommand: test-rift-blocked)
Задача 4: complete (config.yml: messages.wave-no-dry-chunk + messages.test-rift-blocked; раскомментированы messages.test-rift-spawned/test-rift-removed — они были закомментированы, хотя команда их использует, поэтому игрок не видел ответа; README: раздел «Волны» + «Разлом и лут»)

Итог: mvn clean package → BUILD SUCCESS, Tests run: 82, Failures: 0, Errors: 0; target/Questline-1.0-SNAPSHOT.jar собран, в нём config.yml с новыми ключами (проверено чтением UTF-8 из jar).

## Раунд 2 (по итогам ревью)

Замечания ревью и что с ними сделано:
1. Шторм генерации чанков — выбор чанка переписан: кольцами от компьютера, ближние кольца первыми, радиус ограничен `MAX_SEARCH_DISTANCE = 6`, каждый чанк проверяется ровно один раз и поиск останавливается на первом свободном сухом чанке.
2. Сохранённый чанк не перепроверялся — добавлен `GameManager.ensureDryWaveChunk`: сохранённый чанк перепроверяется на воду при старте и при реконнекте; в `resumeWaves` при отсутствии сухого чанка волна прерывается (`quests.cancelRun`), чтобы квест не закрывался бесплатно.
3. Реальная проверка воды не покрыта тестами — `WaterGuard` получил «шов» с функцией `Function<Block,String>` (ключ биома): `Biome` в Paper 1.21.7 нельзя создать/замокать (реестр читает сервер), а `World`/`Block` замокать можно. Тесты проверяют обе ветки правила (водный блок и водный биом) на моках.
4. Тест выбора чанка был вакуумным — 3 теста в `WaveChunkSelectorTest` были написаны с перевёрнутой логикой пробника (probe отвечает «это вода?», а в этих трёх тестах `true` означал «сухо»): `skipsChunksThatAreAlreadyTaken`, `doesNotSearchBeyondTheNearbyArea`, `searchesTheWholeNearbyArea`; `fallsBackToAnOccupiedButDryChunk` требовал выбрать водный чанк. Все четыре переписаны под реальное правило, добавлены `neverPicksTheComputerChunk` и `everyChunkIsProbedOnlyOnce`. Итог раунда: mvn clean package → BUILD SUCCESS, Tests run: 94, Failures: 0, Errors: 0.

Правило выбора чанка волны (итог): ближайший сухой чанк кольцами от компьютера; сектор компьютера (радиус `max(sectorSize/2, sectorSize-1-sectorSize/2)`) пропускается — карта не подсвечивает «найденный» сектор компьютера; радиус поиска ≤ 6 чанков и ≤ дальности внешнего сектора карты; занятые другими игроками чанки пропускаются, но если свободных нет — берётся сухой занятый; сухих чанков нет — возвращается null и волна не запускается.

Не доказано юнит-тестами (нужен живой сервер): реальные биомы/блоки в мире, отказ запуска волны в океане, мобы не в воде, разлом на суше.

Чек-лист проверки в игре:
1. Компьютер на берегу океана: `/questline spawn computer` → клик по компьютеру → волна не стартует в воде; если сухих чанков рядом нет — в консоли «В области компьютера нет сухих чанков — волны не запущены.», игрок получает `messages.wave-no-dry-chunk`, задание остаётся `CALIBRATED`.
2. Компьютер на суше: волна стартует в сухом чанке не дальше 6 чанков от компьютера и не в его секторе; на карте подсвечивается соседний сектор с пометкой «Волна».
3. Мобы стоят на суше (не в воде), разлом висит над сухим столбцом чанка волны.
4. `/questline test rift` на воде → «Здесь вода — тестовый рифт не создан.», на суше → рифт создаётся.
5. Реконнект во время волны: волна возобновляется в том же чанке; если чанк затопило — волна прерывается, задание возвращается в `CALIBRATED`.

## Раунд 3 (замечания ревью раунда 2 — владение чанками)

Замечания и что с ними сделано:
1. Занятость чанков без владельца — `Set<ChunkKey>` заменён на `wave/ChunkOccupancy` (`Map<ChunkKey, Set<UUID>>`): каждый игрок запоминается отдельно, чанк освобождается только когда его покинули все волны. 10 юнит-тестов `ChunkOccupancyTest`.
2. `resumeWaves` не регистрировал чанк — теперь регистрация идёт в `ensureDryWaveChunk` (обе ветки: сохранённый сухой чанк и новый выбранный), поэтому волна после реконнекта снова занимает свой чанк.
3. Заменённый чанк не освобождался — `ensureDryWaveChunk` при затоплении отпускает старый чанк (`releaseWaveChunk` + `clearWaveChunk`) и только потом ищет новый. Проверено `GameManagerOccupancyTest.aChunkThatTurnedIntoWaterIsReleasedAndReplaced` (проверено мутацией: без `releaseWaveChunk` тест падает).
4. `releaseWaveChunk` разгружал чанк, даже если в нём шла чужая волна — теперь чанк остаётся force-loaded, пока в нём есть хоть одна волна. Проверено `GameManagerOccupancyTest.theChunkStaysLoadedWhileAnotherWaveUsesIt` (проверено мутацией).
5. Асимметрия дальности карты при чётном размере сектора — формула дальности оставлена, но её смысл записан в комментарий (берётся меньшая из двух сторон, чтобы волна всегда оставалась на карте) и закрыт тестом `theWaveStaysOnTheMapWithAnEvenSectorSize`.
6. Вакуумный пробник — добавлен тест `theProbeDecidesWhichChunkIsUsed`: пробник обязан быть спрошен, и его ответ определяет выбор чанка.
7. Точки выборки чанка не были покрыты тестами — в `WaterGuardTest` добавлены случаи: мокрый угол (15,15), середина ребра (8,0) и центр (8,8) делают чанк водным; мокрое пятно вне точек выборки (7,7) оставляет чанк сухим; лёд (обычный/битый/синий) — вода; `theSearchStaysInsideTheChunk` теперь проверяет, что найденный столбец сухой.
8. Сохранение после перевыбора чанка — `chooseWaveChunk` сохраняет данные игрока (`quests.save`) после записи нового чанка.
9. Занятый чанк другого игрока — выбор идёт по чанкам, занятым другими (`takenByOthers`); если свободных сухих чанков нет, волна делит чанк: в консоль и игроку уходит предупреждение (`messages.wave-shared-chunk`, новый ключ в config.yml).

Побочно (для согласованности): значение по умолчанию `chunk-area.sector-size` в коде (`GameManager`, `MenuManager`) приведено к 5 — как в поставляемом `config.yml`; в README описан новый выбор чанка (ближайший сухой, кольцами от компьютера) вместо старого «случайный сектор + его центр».

Итог раунда 3: mvn clean package → BUILD SUCCESS, Tests run: 122, Failures: 0, Errors: 0 (было 94). Новые файлы: `wave/ChunkOccupancy.java`, `wave/ChunkOccupancyTest.java`, `game/GameManagerOccupancyTest.java`.
