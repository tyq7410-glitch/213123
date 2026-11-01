# Aurora Cheat Client (Fabric, Minecraft 1.21.11)

Модульный клиент: меню в стиле Pulse с плавными анимациями, описания модулей на русском,
свой фон меню, HUD-виджеты, косметика без частиц (неоновые линии), пресеты, профили и помощник по настройке.

## Как получить мод (сборка через GitHub)
1. Распакуй zip на компьютере.
2. На github.com создай аккаунт и пустой репозиторий (New repository).
3. Загрузка файлов (в проекте теперь 33 файла, лимит веб-загрузки GitHub 100 файлов за раз):
   - через сайт: репозиторий -> **Add file** -> **Upload files** -> перетащи всё содержимое распакованной папки
     (включая папки `src` и `.github`) -> **Commit changes**;
   - или командами в папке проекта:

         git init
         git add .
         git commit -m "Aurora Cheat Client"
         git branch -M main
         git remote add origin https://github.com/НИК/РЕПО.git
         git push -u origin main

   Проверь, что в репозитории есть `.github/workflows/build.yml`. Если папка `.github` не загрузилась (она скрытая),
   создай файл вручную: **Add file** -> **Create new file** -> имя `.github/workflows/build.yml` -> вставь содержимое
   из этого файла в zip.
4. Открой вкладку **Actions** -> запуск **Build mod**. Через 3-5 минут внутри запуска будет раздел **Artifacts** -> `aurora-cheat-client`. Скачай, внутри jar.
5. Установка: Fabric Installer (fabricmc.net) для Minecraft 1.21.11 -> Fabric API `0.141.1+1.21.11` и jar мода положи в `.minecraft/mods` -> запускай профиль Fabric.
6. В игре жми **Right Shift**.

Релиз с jar на странице репозитория: `git tag v1.0.0 && git push origin v1.0.0`.

## Меню
- Слева категории, посередине модули, справа настройки выбранного модуля и его описание.
- Переключатель у модуля — вкл/выкл. «Бинд» — клавиша. «Показывать в списке» — скрыть из HUD-списка.
- «Интерфейс» — фон меню (затемнение, градиент, закат, океан, неон, свой RGB), прозрачность, частицы, цвет акцента.
- «Помощник» — пресеты, 3 профиля настроек, советы по твоей конфигурации и ИИ-чат.

## ИИ-помощник (по желанию)
Советы и пресеты работают без интернета. Для чата с Claude создай файл `.minecraft/config/aurora_ai_key.txt`
и вставь туда свой API-ключ Anthropic (console.anthropic.com). Запросы платные по тарифу Anthropic,
никому не показывай этот файл. Помощник видит список модулей и твои значения, чтобы подсказывать конкретные числа.

## Модули (78)
PvP: KillAura, AimAssist, TriggerBot, AutoClicker, WTap, CritTiming, AutoShield, AutoPotion, AutoGap, Teams, AntiBot
Движение: Sprint, Fly, Speed, Step, HighJump, AirJump, AutoJump
Визуалы: ESP, BoxESP, Tracers, Nametags, TargetESP, ChestESP, FovCircle, Fullbright, Zoom, HUD, Keystrokes, ArmorHUD,
PotionHUD, TargetHUD, Radar, InfoHUD, BindList, Crosshair, Notifications, InventoryHUD, ItemCounter, ComboCounter, ReachDisplay
PvE: AutoEat, AutoFish, AutoFarm, AutoArmor, AutoTotem, AutoTool, Nuker, AutoRespawn, AutoTorch, TreeChopper, AutoBonemeal,
AutoShear, AutoBreed, EdgeSneak, AutoDrop, DurabilityWarning, AutoWalk, AutoMine, AutoUse, DeathPoint, HomePoint,
LightLevel, AutoRefill, AutoSapling, FoodHUD, SessionStats, HeldCounter, LowHealthAlert
Утилиты: FastPlace, Friends (средняя кнопка мыши по игроку)
Косметика (неоновые линии, без частиц): Wings, Hat, Aura, Trail, HitEffects, JumpCircles
Интерфейс: Interface

Косметика и визуалы рисуются через проекцию мира на экран, а не частицами: в первом лице точно, в третьем приблизительно.
Для одиночной игры и своего сервера. Обходов античитов в клиенте нет.

## Красный крестик в Actions
Открой упавший запуск -> шаг, помеченный красным. Что значит:
- **Find the project folder** — не загрузились `build.gradle` или папка `src`: залей СОДЕРЖИМОЕ папки AuroraCheatClient.
  Если файлы лежат во вложенной папке, это допустимо, workflow найдёт её сам.
- **Build** — ошибка компиляции: скопируй строки, начинающиеся с `error:` (имя файла и номер строки), и пришли их.
- **Check the jar** — jar вышел неполным.

## Если сборка дала крошечный jar (1 КБ)
Workflow теперь проверяет размер jar и число классов и падает с понятной ошибкой, если файл неполный.
Открой упавший запуск в Actions и скопируй текст ошибки из шага Build.

## Если не компилируется
Код писался под Yarn 1.21.11+build.4 без доступа к компилятору Minecraft. Скопируй ошибку из Actions.
Чаще всего придётся править: `Click`/`KeyInput` (ClickGuiScreen), `HudElementRegistry` (AuroraClient),
`getTickProgress`, `getLerpedPos`, `getBodyYaw`, `Perspective.isFrontView` (Proj/ProjCosmetic), `PotionContentsComponent.getEffects` (AutoPotion), `PlayerAbilities`/`fallDistance` (Fly).
