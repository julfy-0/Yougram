# Журнал переноса функций Telegram → Yougram
Обновлён: 2026-11-07 (этап 7)

## Статус

| № | Этап | Статус | Дата |
|---|------|--------|------|
| 1 | Использование сети | сделано | — |
| 2 | Стикеры и реакции | сделано | — |
| 3 | Исключения уведомлений | сделано | — |
| — | Анимации открытия/закрытия | сделано | — |
| — | Магазин плагинов: поиск и категории | сделано | — |
| 4 | Saved Messages по темам | сделано (без скрытия ввода в темах-источниках) | 2026-11-07 |
| 5 | Хэштеги | сделано | 2026-11-07 |
| 6 | Instant View | сделано | 2026-11-07 |
| 7 | Геолокация | сделано | 2026-11-07 |
| 8 | Группа обсуждения, мои публичные каналы | не начат | |
| 9 | Статусы эмодзи и цвета профиля | не начат | |
| 10 | Эффекты сообщений, платные реакции | не начат | |
| 11 | Бусты, розыгрыши, статистика | не начат | |
| 12 | Групповые звонки | не начат | |
| 13 | Редактор фото и видео | не начат | |
| 14 | Паспорт, люди рядом | не начат | |

## Записи (выполнено)

### Этап 1 — Использование сети
- Новые: `feature/settings/general/NetworkUsageScreen.kt`
- Изменены: `feature/settings/SettingsHomeScreen.kt` (SettingsPage.NetworkUsage), `feature/settings/SettingsPages.kt`, `feature/settings/hub/SystemHubScreen.kt`
- Что делает: трафик по Wi-Fi, мобильной сети, роумингу, звонкам; переключатель «только с запуска»; сброс статистики.
- Риски: имена полей TDLib (getNetworkStatistics, NetworkStatisticsEntryFile/Call, resetNetworkStatistics) взяты из документации, не из tdl-coroutines 13.0.0.

### Этап 2 — Стикеры и реакции
- Новые: `feature/settings/general/StickersScreen.kt`
- Изменены: `SettingsHomeScreen.kt` (SettingsPage.Stickers), `SettingsPages.kt`, `hub/SystemHubScreen.kt` (раздел «Контент»)
- Что делает: вкладки «Мои / Архив / Популярные», перестановка, архивация, удаление, добавление; быстрая реакция (setDefaultReactionType).
- Риски: текущая реакция из TDLib не читается; имена getArchivedStickerSets / getTrendingStickerSets / reorderInstalledStickerSets не проверены компилятором.

### Этап 3 — Исключения уведомлений
- Новые: `feature/settings/general/NotificationExceptionsScreen.kt`
- Изменены: `SettingsHomeScreen.kt` (SettingsPage.NotificationExceptions, parent = Notifications), `SettingsPages.kt`, `general/NotificationsScreen.kt` (параметр onNavigate с значением по умолчанию)
- Что делает: чаты с особыми настройками по типам (личные / группы / каналы), переход в чат, включение и выключение уведомлений.
- Допущение: вместо полного сброса исключения — переключатель звука (через ChatRepository.setMuted).

### Анимации
- Изменены: `feature/main/ui/MainScreen.kt` (направление по глубине страницы: открытие справа налево, закрытие слева направо, закрываемый экран уезжает поверх), `navigation/YougramNavHost.kt` (истории), `core/ui/adaptive/TwoPane.kt` (правая панель на планшете).

### Магазин плагинов
- Изменены: `plugin/PluginStoreScreen.kt` (PluginStoreTopBar, состояние поиска и категории в PluginStoreSearch), `feature/main/ui/MainScreen.kt` (поиск и категории в верхней панели, плавная высота).

### Этап 4 — Saved Messages по темам — 2026-11-07 (частично)
- Файлы (новые): `core/settings/SavedViewPrefs.kt`, `feature/chat/ui/SavedTopicsScreen.kt`
- Файлы (изменены): `feature/chat/data/ChatRepository.kt`, `feature/chat/ui/ChatViewModel.kt`, `feature/chat/ui/ForumTopicsScreen.kt` (ChatOrTopics), `feature/settings/general/ChatSettingsScreen.kt`
- Что делает: переключатель «Избранное по темам» (Настройки → Чаты → Прочие настройки, по умолчанию выключен). При включении «Избранное» открывается списком: «Все сообщения», «Мои заметки», «Скрытый автор» и чаты-источники пересылки; нажатие открывает чат только с сообщениями этой темы. Открытие справа налево, закрытие слева направо; системная «Назад» из темы возвращает к списку.
- Допущения: экран тем без нового маршрута навигации (состояние выбранной темы внутри SavedMessagesHost), т.к. id темы — Long, а маршрут чата хранит Int. Отправка из экрана темы идёт в общие заметки (в тему-источник писать нельзя).
- Риски / не проверено компилятором: loadSavedMessagesTopics, getSavedMessagesTopicHistory, client.savedMessagesTopicUpdates, SavedMessagesTopic (id, type, isPinned, order, lastMessage), SavedMessagesTopicTypeMyNotes / AuthorHidden / SavedFromChat(chatId), MessageTopicSavedMessages.savedMessagesTopicId, message.topicId для сообщений «Избранного». Если в tdl-coroutines 13.0.0 имена иные — пришлите ошибки `e:`.
- Осталось: теги сохранённых сообщений (getSavedMessagesTags, фильтр по тегу), скрытие поля ввода в темах-источниках, закрепление тем (toggleSavedMessagesTopicIsPinned).

### Этап 4, часть 2 — теги и закрепление тем — 2026-11-07
- Файлы (изменены): `feature/chat/data/ChatRepository.kt`, `feature/chat/ui/ChatViewModel.kt`, `feature/chat/ui/SavedTopicsScreen.kt`
- Что делает: над списком тем — чипы тегов (эмодзи-теги, подпись или число); нажатие открывает «Избранное», отфильтрованное по тегу. Долгое нажатие на тему закрепляет или открепляет её.
- Допущения: премиум-эмодзи теги пропускаются; при фильтре по тегу новые входящие в ленту не подмешиваются.
- Риски / не проверено компилятором: getSavedMessagesTags(savedMessagesTopicId), SavedMessagesTag (tag, label, count), searchSavedMessages(savedMessagesTopicId, tag, query, fromMessageId, offset, limit), toggleSavedMessagesTopicIsPinned(savedMessagesTopicId, isPinned). Сигнатура searchSavedMessages — самая сомнительная.
- Осталось: скрытие поля ввода в темах-источниках; переименование тегов (setSavedMessagesTagLabel).

### Этап 5 — Хэштеги — 2026-11-07
- Файлы (новые): `feature/chat/search/ui/HashtagSearchScreen.kt`
- Файлы (изменены): `core/ui/component/Links.kt` (токен #тега, LocalOpenHashtag, параметр onHashtag), `navigation/YougramNavHost.kt` (маршрут hashtag/{tag}, LocalOpenHashtag), `feature/chat/ui/ChatScreen.kt` (подсказки над полем ввода), `feature/chat/data/ChatRepository.kt` (searchHashtags, removeRecentHashtag)
- Что делает: #теги в тексте сообщений подсвечены и кликабельны; нажатие открывает экран результатов поиска сообщений по тегу (на базе глобального поиска). При наборе «#…» в конце черновика над полем появляются подсказки (недавние при пустом префиксе); нажатие подставляет тег, долгое нажатие убирает его из недавних.
- Допущения: подсказки считаются по хвосту черновика (курсор считается стоящим в конце); числовые «#1» тегами не считаются; в планшетном режиме экран тега закрывается при открытии найденного сообщения.
- Риски / не проверено компилятором: client.searchHashtags(prefix, limit) и поле hashtags у результата, client.removeRecentHashtag(hashtag). Нажатие на тег в сообщении полагается на то, что LinkAnnotation.Clickable срабатывает внутри ClickableText (как у @упоминаний).
- Осталось: нажатие на #тег в подписях к медиа и в других местах, где текст не проходит через rememberLinkified; запись набранного тега в недавние делает сам TDLib при отправке.

### Этап 6 — Instant View — 2026-11-07
- Файлы (новые): `feature/browser/instantview/InstantViewModel.kt`, `InstantViewMapper.kt`, `InstantViewScreen.kt`
- Файлы (изменены): `feature/chat/data/ChatRepository.kt` (loadInstantView, cachedInstantView), `navigation/YougramNavHost.kt` (маршрут iv/{url}, проверка Instant View в openLink)
- Что делает: при нажатии на веб-ссылку сначала проверяется Instant View (telegra.ph, graph.org — из сети; остальные сайты — если страница уже в кэше TDLib после превью в чате). Если есть — открывается нативная статья (заголовки, текст со стилями и ссылками, фото, списки, цитаты, таблицы, раскрывающиеся блоки, похожие статьи); иначе ссылка открывается как раньше. Кнопка «В браузере» открывает исходную страницу.
- Допущения: видео, анимации, аудио, встраивания и карты показываются заглушкой с переходом в браузер; якоря внутри статьи не поддерживаются; настройки отключения Instant View нет.
- Риски / не проверено компилятором: client.getWebPageInstantView(url, onlyLocal), WebPageInstantView.pageBlocks, все PageBlock* и RichText* DTO и их поля (title, text, credit, caption, items, pageBlocks, cells, articles и др.), Photo.sizes / PhotoSize.photo.id. Все обращения к DTO собраны в InstantViewMapper.kt. В этой версии TDLib pageBlocks переименован в blocks; у PageBlockBlockQuote вместо text — вложенные blocks.
- Осталось: якоря и сноски, видео и встраивания, галереи с листанием, отдельный переключатель в настройках.

### Этап 7 — Геолокация — 2026-11-07
- Файлы (новые): `feature/chat/location/LocationModels.kt`, `LocationDialog.kt`, `LocationMessage.kt`, `MapPreview.kt`, `LiveLocationSharer.kt`
- Файлы (изменены): `feature/chat/data/ChatRepository.kt` (MessageItem.location, ContentEvent.location, sendLocation с livePeriod, stopLiveLocation, updateLiveLocations), `feature/chat/ui/ChatViewModel.kt`, `feature/chat/ui/ChatScreen.kt`
- Что делает: кнопка «Геопозиция» в меню вложений открывает окно: карта с текущим положением, «Отправить моё местоположение» и трансляция на 15 минут / 1 час / 8 часов (запрос разрешения встроен). В чате геопозиции, места и трансляции показываются карточкой с картой (плитки OpenStreetMap), названием и адресом; нажатие открывает приложение карт (geo:), для трансляции — остаток времени и кнопка «Остановить трансляцию» у своих. Координаты своих трансляций обновляются каждые ~10 секунд (getActiveLiveLocationMessages + editMessageLiveLocation).
- Допущения: обновление трансляции работает, пока приложение открыто (фоновой службы нет); выбор произвольной точки на карте и поиск мест (Venue) не сделаны; карта рисуется без SDK из 2×2 плиток zoom 15 с подписью © OpenStreetMap.
- Риски / не проверено компилятором: в этой версии TDLib трансляция — отдельные типы (InputMessageLiveLocation, MessageLiveLocation, LiveLocation); editMessageLiveLocation(chatId, messageId, replyMarkup, location: LiveLocation?) — null останавливает. Метода getActiveLiveLocationMessages нет: свои активные трансляции отслеживает LiveLocationSharer.track при разборе сообщений (после перезапуска приложения подхватываются, когда сообщение снова попадёт в чат).
- Осталось: фоновая трансляция (служба с типом location), выбор точки на карте, места рядом, оповещение о приближении (proximityAlertRadius).
