package com.kraftplay.openiptv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// ============================================================================
// I18N ENGINE: Material 3 Expressive Settings
// Supports real-time language switching between Russian and English.
// Source strings are written in Russian; tr() returns English when selected.
// ============================================================================

object I18n {
    var lang by mutableStateOf(systemLang())
        private set

    private fun systemLang(): String =
        if (Locale.getDefault().language in setOf("ru", "uk", "be", "kk", "ky", "tg", "tt", "ba", "uz")) {
            "ru"
        } else {
            "en"
        }

    /** Setting value: "system", "ru", or "en". */
    fun apply(setting: String) {
        val next = when (setting.lowercase()) {
            "ru" -> "ru"
            "en" -> "en"
            else -> systemLang()
        }
        if (next != lang) lang = next
    }

    val isEnglish: Boolean
        get() = lang == "en"
}

/** Translates string from Russian to English if active language is English. */
fun tr(ru: String): String = if (I18n.isEnglish) EN_STRINGS[ru] ?: ru else ru

/** Supports parameter substitutions {0}, {1}… e.g., tr("Загружено {0} программ", count) */
fun tr(ru: String, vararg args: Any?): String {
    var s = tr(ru)
    args.forEachIndexed { i, a ->
        s = s.replace("{$i}", a.toString())
    }
    return s
}

/** Translation dictionary mapping Russian keys to English UI values */
internal val EN_STRINGS: Map<String, String> = hashMapOf(
    // Categories & Sections
    "Настройки" to "Settings",
    "Плейлисты и EPG" to "Playlists & EPG",
    "Управление плейлистами M3U, источниками EPG и синхронизация" to "Manage M3U playlists, EPG sources, and sync",
    "Внешний вид и тема" to "Appearance & Theme",
    "Цветовые темы, Material You 3, стили интерфейса, формы значков, шрифты и размер списка" to "Color themes, Material You 3, UI styles, icon shapes, fonts, and list size",
    "Плеер и воспроизведение" to "Player & Playback",
    "Соотношение сторон, декодирование, буферизация, фоновый режим и таймер сна" to "Aspect ratio, decoding, buffering, background mode, and sleep timer",
    "Категории и каналы" to "Categories & Channels",
    "Режим отображения, сортировка групп, скрытие категорий и каналов" to "Display mode, group sorting, category and channel visibility",
    "Навигация и управление" to "Navigation & Controls",
    "Поведение кнопки Назад, жесты управления и отображение ссылок" to "Back button behavior, gesture controls, and URL visibility",
    "Резервное копирование и сеть" to "Backup & Network",
    "Экспорт и импорт настроек, передача по Wi-Fi, LSPosed и Android Auto" to "Export/import settings, Wi-Fi transfer, LSPosed, and Android Auto",
    "О приложении и сообщество" to "About & Community",
    "Обновления приложения, Telegram, GitHub и сброс данных" to "App updates, Telegram, GitHub, and data reset",

    // Main Headers & Search
    "Поиск настроек: тема, EPG, плеер..." to "Search settings: theme, EPG, player...",
    "Найдено результатов для «{0}»" to "Results found for \"{0}\"",
    "Ничего не найдено" to "Nothing found",
    "Всё готово" to "All set",
    "Почти готово" to "Almost there",
    "Отличная работа! Все службы настроены и готовы к работе." to "Great job! All services are configured and ready to go.",
    "Настройте плейлисты или EPG для полноценной работы." to "Configure playlists or EPG sources for full functionality.",
    "Активные плейлисты" to "Active playlists",
    "Источники EPG" to "EPG sources",
    "Версия приложения" to "App version",
    "Тема оформл." to "Theme style",
    "Категории настроек" to "Settings categories",

    // Playlists & EPG
    "Плейлисты" to "Playlists",
    "Управление списком IPTV провайдеров" to "Manage IPTV provider lists",
    "Добавить плейлист" to "Add playlist",
    "Передача по Wi-Fi" to "Wi-Fi Device Transfer",
    "Импорт из файла" to "Import from file",
    "Обновить плейлист" to "Refresh playlist",
    "Настройки плейлиста" to "Playlist settings",
    "Отключен" to "Disabled",
    "каналов" to "channels",
    "Имя" to "Name",
    "URL адрес" to "URL address",
    "Включить элемент" to "Enable item",
    "Защита паролем" to "Password lock",
    "Интервал автообновления" to "Auto-update interval",
    "При запуске" to "On launch",
    "15 мин" to "15 min",
    "30 мин" to "30 min",
    "1 час" to "1 hour",
    "1 день" to "1 day",
    "Каждые 2 дня" to "Every 2 days",
    "Каждые 3 дня" to "Every 3 days",
    "Выключено" to "Off",
    "Введите пароль" to "Enter password",
    "Пароль" to "Password",
    "Неверный пароль" to "Incorrect password",
    "Плейлист отключен" to "Playlist is disabled",
    "Источники EPG (Телепрограмма)" to "EPG Sources (TV Guide)",
    "Синхронизация программ телепередач" to "Sync TV guide programs",
    "Добавить EPG" to "Add EPG",
    "Обновить EPG" to "Refresh EPG",
    "Очистить EPG кэш ({0})" to "Clear EPG cache ({0})",
    "Загрузка EPG..." to "Loading EPG...",
    "Очистка EPG..." to "Clearing EPG...",
    "EPG очищен" to "EPG cleared",
    "EPG обновлен" to "EPG updated",
    "Загружено {0} программ ({1})" to "Loaded {0} programs ({1})",
    "Настройки EPG" to "EPG settings",
    "Автообновление EPG" to "EPG auto-update",
    "Периодичность проверки телепрограммы" to "TV guide refresh schedule",
    "Каждые 12 часов" to "Every 12 hours",
    "Каждые 24 часа" to "Every 24 hours",

    // Appearance & Custom UI
    "Язык и дизайн" to "Language & Design",
    "Язык приложения" to "App language",
    "Выберите язык отображения интерфейса" to "Choose interface language",
    "Как в системе" to "System",
    "Русский" to "Russian",
    "Английский" to "English",
    "Стиль UI" to "UI Style",
    "Оформление поверхностей и карточек" to "Surface and card styling",
    "Форма значков" to "Icon badge shape",
    "Форма фонового значка рядом с описанием" to "Background shape badge next to description",
    "Печенька" to "Cookie",
    "Ромб" to "Diamond",
    "Квадрат" to "Square",
    "Круг" to "Circle",
    "Тема" to "Theme",
    "Цветовая палитра приложения" to "App color palette",
    "Светлая" to "Light",
    "Тёмная" to "Dark",
    "Хеллоуин" to "Halloween",
    "Зима" to "Winter",
    "Цвет акцента" to "Accent color",
    "Основной акцентный цвет кнопок и переключателей" to "Primary accent color for buttons and switches",
    "Анимация и эффекты Material You 3" to "Material You 3 Motion & Effects",
    "Анимация поворота значков" to "Icon rotation motion",
    "Красивое вращение геометрических значков при нажатии" to "Expressive 360 rotation on icon press",
    "Подсветка фокуса карточек" to "Animated focus glow on cards",
    "Светящийся контур при нажатии или фокусе" to "Glowing gradient border on press or focus",
    "Анимированный фоновый свет" to "Ambient animated background light",
    "Плавающие мягкие градиентные фигуры на фоне" to "Floating soft gradient orbs on background",
    "Закругление карточек" to "Card corner radius",
    "Сила отклика нажатия (Scale)" to "Press bounce scale intensity",
    "Стиль прогресса EPG" to "EPG progress bar style",
    "Волнистый" to "Wavy",
    "Светящийся" to "Glowing",
    "Сплошной" to "Solid",
    "Стиль стекла плеера" to "Player glass tint style",
    "Тёмное стекло" to "Dark Glass",
    "AMOLED Черный" to "AMOLED Pitch Black",
    "Прозрачный" to "Transparent",
    "Списки и шрифты" to "Lists & Fonts",
    "Размер элементов списка" to "List item size",
    "Вариант отображения карточек каналов" to "Channel card layout style",
    "Простой список" to "Simple list",
    "Компактный" to "Compact",
    "Рядом" to "Side by side",
    "Большие карточки" to "Large cards",
    "Размер шрифта" to "Font size",
    "Показывать номера каналов" to "Show channel numbers",
    "Отображать порядковые номера каналов в списке" to "Display ordinal channel numbers in list",
    "Показывать часы" to "Show clock",
    "Отображать часы на панели плеера" to "Show clock on player bar",
    "Положение часов" to "Clock position",
    "Угол экрана для индикатора времени" to "Screen corner for time display",
    "Сверху слева" to "Top left",
    "Сверху справа" to "Top right",
    "Снизу слева" to "Bottom left",
    "Снизу справа" to "Bottom right",
    "Своя тема (JSON)" to "Custom Theme (JSON)",
    "Настройка пользовательской темы JSON" to "Configure custom JSON theme",
    "Редактировать тему JSON" to "Edit custom theme JSON",
    "Сбросить тему" to "Reset custom theme",
    "Вставить образец" to "Paste sample",
    "Случайный цвет" to "Random color",
    "Тема обновлена" to "Theme updated",
    "Некорректный JSON" to "Invalid JSON",
    "Сброшено" to "Reset done",

    // Folder & Channel Actions
    "Передвинуть категорию" to "Move category",
    "Передвинуть категорию: {0}" to "Move category: {0}",
    "Запаролить папку" to "Lock folder",
    "Снять пароль с папки" to "Unlock folder",
    "Пароль с папки снят" to "Folder password removed",
    "Папка запаролена" to "Folder locked with password",
    "Скрыть категорию" to "Hide category",
    "Передвинуть канал" to "Move channel",
    "Передвинуть канал: {0}" to "Move channel: {0}",
    "Текущая позиция: #{0}" to "Current position: #{0}",
    "Новая позиция" to "New position",
    "Новая позиция (номер в списке)" to "New position (number in list)",
    "Вверх ↑" to "Up ↑",
    "Вниз ↓" to "Down ↓",
    "Передвинуть" to "Move",
    "Папка защищена паролем" to "Folder is password protected",
    "Введите новый пароль для папки" to "Enter new password for folder",
    "Установить пароль" to "Set password",

    // Player & Playback
    "Воспроизведение" to "Playback",
    "Аудиодорожки" to "Audio tracks",
    "Скорость воспроизведения" to "Playback speed",
    "Архив" to "Archive",
    "Канал" to "Channel",
    "Разрешение" to "Resolution",
    "Видео" to "Video",
    "Аудио" to "Audio",
    "Битрейт" to "Bitrate",
    "Источник" to "Source",
    "Кадры/сек" to "FPS",
    "Следующая:" to "Next:",
    "Нет данных телепрограммы" to "No programme information",
    "Повторить" to "Retry",
    "Назад к каналам" to "Back to channels",
    "Перейти в прямой эфир" to "Jump to live",
    "Нет доступных аудиодорожек" to "No audio tracks available",
    "Соотношение сторон" to "Aspect ratio",
    "Масштабирование видео на экране" to "Video scaling on screen",
    "По размеру" to "Fit",
    "Заполнить" to "Fill",
    "Аппаратный декодер" to "Hardware decoder",
    "Настройка декодирования видеопотока" to "Stream video decoding setup",
    "Авто" to "Auto",
    "Аппаратный (HW)" to "Hardware (HW)",
    "Программный (SW)" to "Software (SW)",
    "Буферизация" to "Buffering",
    "Размер сетевого буфера для стабильности потока" to "Network buffer size for stream stability",
    "Низкая" to "Low",
    "Стандартная" to "Standard",
    "Высокая" to "High",
    "Таймер сна" to "Sleep timer",
    "Автоматическое выключение плеера по таймеру" to "Automatic player shutdown timer",
    "Параметры инфопанели" to "Info panel parameters",
    "Показывать источник" to "Show source",
    "Имя плейлиста или имя сервера в строке информации" to "Playlist or server name in info bar",
    "Показывать разрешение" to "Show resolution",
    "Разрешение видеопотока" to "Video stream resolution",
    "Показывать FPS" to "Show FPS",
    "Частота кадров в секунду" to "Frames per second rate",
    "Показывать кодеки" to "Show codecs",
    "Тип видео и аудио кодека в плеере" to "Video and audio codec type in player",
    "Показывать битрейт" to "Show bitrate",
    "Битрейт потока в килобитах" to "Stream bitrate in kilobits",
    "Формат разрешения" to "Resolution format",
    "Отображение качества в пикселях или метках" to "Display quality in pixels or badges",
    "Размерность (1920x1080)" to "Dimensions (1920x1080)",
    "Метка (FHD, 4K)" to "Label (FHD, 4K)",
    "Автозапуск последнего канала" to "Auto-play last channel",
    "Запускать воспроизведение при открытии приложения" to "Resume playback when opening app",
    "Принудительный ландшафт" to "Force landscape orientation",
    "Фиксирует альбомный режим в плеере" to "Locks landscape mode in player",
    "Фоновое воспроизведение" to "Background playback",
    "Продолжать воспроизведение при сворачивании (PiP)" to "Continue playback when minimized (PiP)",
    "Автоскрытие инфопанели" to "Auto-hide info bar",
    "Время скрытия инфопанели во время воспроизведения" to "Info bar timeout during playback",
    "Никогда" to "Never",
    "3 сек" to "3 sec",
    "5 сек" to "5 sec",
    "10 сек" to "10 sec",

    // Categories & Channels
    "Отображение категорий" to "Category display",
    "Режим категорий" to "Category mode",
    "Внешний вид списка категорий на главном экране" to "Category list style on home screen",
    "Чипы" to "Chips",
    "Папки" to "Folders",
    "Сортировка групп" to "Group sorting",
    "Порядок категорий в списке" to "Category list ordering",
    "По провайдеру" to "By provider",
    "По алфавиту" to "Alphabetical",
    "Режим архива (Catchup)" to "Archive mode (Catchup)",
    "Настройки формата ссылок для просмотра архива телепередач" to "URL format configuration for catchup TV playback",
    "Управление категориями" to "Manage categories",
    "Скрытие и показ групп каналов" to "Hide or show channel groups",
    "Управление каналами" to "Manage channels",
    "Скрытие отдельных каналов из списка" to "Hide individual channels from list",
    "Отключена" to "Disabled",
    "Поиск каналов" to "Search channels",
    "Закрыть" to "Close",

    // Controls & Navigation
    "Управление и ссылки" to "Controls & Links",
    "Действие кнопки Назад" to "Back button action",
    "Поведение при нажатии системы Назад на главном экране" to "Behavior when pressing Back on home screen",
    "Выход" to "Exit",
    "Возврат к списку" to "Back to list",
    "Дважды нажать для выхода" to "Double press to exit",
    "Жесты управления" to "Gesture controls",
    "Жесты свайпа для громкости и яркости" to "Swipe gestures for volume and brightness",
    "Скрывать URL плейлиста" to "Hide playlist URL",
    "Скрывать прямые ссылки на плейлист в интерфейсе" to "Hide direct playlist URLs in UI",

    // Backup & Network
    "Резервная копия и сеть" to "Backup & Network",
    "Экспорт настроек" to "Export settings",
    "Скопировать резервную копию в буфер обмена" to "Copy backup to clipboard",
    "Импорт настроек" to "Import settings",
    "Восстановить настройки из текста JSON" to "Restore settings from JSON text",
    "Передача настроек между устройствами по локальной сети" to "Transfer settings between devices on local network",
    "Резервная копия скопирована" to "Backup copied to clipboard",
    "Настройки успешно импортированы" to "Settings imported successfully",
    "Ошибка импорта настроек" to "Failed to import settings",
    "Состояние LSPosed и Android Auto" to "LSPosed & Android Auto Status",
    "Состояние LSPosed" to "LSPosed Status",
    "Модуль LSPosed активен" to "LSPosed module is active",
    "Модуль LSPosed не активен" to "LSPosed module is not active",

    // Community & Updates
    "Сообщество и обновления" to "Community & Updates",
    "Telegram" to "Telegram",
    "Официальный канал и чат поддержки" to "Official channel and support chat",
    "GitHub" to "GitHub",
    "Исходный код проекта OpenIPTV" to "OpenIPTV project source code",
    "Проверить обновления" to "Check for updates",
    "Проверка обновлений..." to "Checking for updates...",
    "Загрузка и установка свежих выпусков KitTV" to "Download and install latest KitTV releases",
    "Доступно обновление KitTV {0}" to "KitTV update available: {0}",
    "Загрузить APK и открыть системное окно установки? Приложение останется открытым до подтверждения установки." to "Download APK and open package installer? The app will remain open until confirmed.",
    "Обновить" to "Update",
    "Позже" to "Later",
    "Загрузка началась. После загрузки появится системное окно установки." to "Download started. Installation prompt will appear once finished.",
    "Очистить данные приложения" to "Clear app data",
    "Сброс всех сохраненных плейлистов и EPG" to "Reset all saved playlists and EPG",
    "Сохранить" to "Save",
    "Отмена" to "Cancel",
    "Готово" to "Done",
    "Назад" to "Back"
)
