package com.kraftplay.openiptv.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.lsposed.LSPosedStatus
import com.kraftplay.openiptv.model.CustomThemeConfig
import com.kraftplay.openiptv.model.EpgSource
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

enum class SettingsPage(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    PLAYLISTS("Плейлисты и EPG", "Управление плейлистами M3U, источниками EPG и синхронизация", Icons.Rounded.ListAlt),
    APPEARANCE("Внешний вид и тема", "Цветовые темы, Material You 3, стили интерфейса, формы значков, шрифты и размер списка", Icons.Rounded.Palette),
    PLAYER("Плеер и воспроизведение", "Соотношение сторон, декодирование, буферизация, фоновый режим и таймер сна", Icons.Rounded.PlayCircle),
    CATEGORIES("Категории и каналы", "Режим отображения, сортировка групп, скрытие категорий и каналов", Icons.Rounded.Category),
    NAVIGATION("Навигация и управление", "Поведение кнопки Назад, жесты управления и отображение ссылок", Icons.Rounded.Navigation),
    BACKUP("Резервное копирование и сеть", "Экспорт и импорт настроек, передача по Wi-Fi, LSPosed и Android Auto", Icons.Rounded.Backup),
    ABOUT("О приложении и сообщество", "Обновления приложения, Telegram, GitHub и сброс данных", Icons.Rounded.Info)
}

data class SearchSettingItem(
    val title: String,
    val subtitle: String,
    val pageName: String,
    val targetPage: SettingsPage,
    val keywords: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Observe settings and viewmodel states
    val playlists by viewModel.playlists.collectAsState()
    val epgSources by viewModel.epgSources.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val clockPosition by viewModel.clockPosition.collectAsState()
    val language by viewModel.language.collectAsState()
    val hidePlaylistUrl by viewModel.hidePlaylistUrl.collectAsState()
    val listSize by viewModel.listSize.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val uiStyle by viewModel.uiStyle.collectAsState()
    val catDisplayMode by viewModel.categoryDisplayMode.collectAsState()
    val customThemeJson by viewModel.customThemeJson.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()

    val showSource by viewModel.showSource.collectAsState()
    val showResolution by viewModel.showResolution.collectAsState()
    val showFps by viewModel.showFps.collectAsState()
    val showCodecs by viewModel.showCodecs.collectAsState()
    val showBitrate by viewModel.showBitrate.collectAsState()
    val resFormat by viewModel.resolutionFormat.collectAsState()

    val autoStartLast by viewModel.autoStartLast.collectAsState()
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()
    val backBehavior by viewModel.backBehavior.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val hiddenCategories by viewModel.hiddenCategories.collectAsState()
    val catchupMode by viewModel.catchupMode.collectAsState()
    val sleepMinutes by viewModel.sleepTimer.collectAsState()
    val groupSortOrder by viewModel.groupSortOrder.collectAsState()

    val hardwareDecoder by viewModel.hardwareDecoder.collectAsState()
    val showChannelNumbers by viewModel.showChannelNumbers.collectAsState()
    val volumeGestureEnabled by viewModel.volumeGestureEnabled.collectAsState()
    val epgUpdateInterval by viewModel.epgUpdateInterval.collectAsState()
    val badgeShape by viewModel.badgeShape.collectAsState()
    val badgeColor by viewModel.badgeColor.collectAsState()
    val panelColor by viewModel.panelColor.collectAsState()

    val expressiveGlow by viewModel.expressiveGlow.collectAsState()
    val waveAmplitude by viewModel.waveAmplitude.collectAsState()
    val waveSpeed by viewModel.waveSpeed.collectAsState()
    val cardCornerRadius by viewModel.cardCornerRadius.collectAsState()
    val springScale by viewModel.springScale.collectAsState()
    val ambientCanvas by viewModel.ambientCanvas.collectAsState()
    val epgProgressStyle by viewModel.epgProgressStyle.collectAsState()
    val playerGlassTint by viewModel.playerGlassTint.collectAsState()
    val cardBadgeStyle by viewModel.cardBadgeStyle.collectAsState()
    val iconRotationEnabled by viewModel.iconRotationEnabled.collectAsState()

    val isEpgLoading by viewModel.isEpgLoading.collectAsState()
    val epgStatus by viewModel.epgStatus.collectAsState()
    val epgDbSize by viewModel.epgDbSize.collectAsState()

    // Sync language with I18n
    LaunchedEffect(language) {
        I18n.apply(language)
    }

    var activePage by remember { mutableStateOf<SettingsPage?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Always intercept back press cleanly to navigate subpages first
    BackHandler(enabled = true) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (activePage != null) {
            activePage = null
        } else {
            onBack()
        }
    }

    // Update Download Receiver
    var activeUpdateDownloadId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(context, activeUpdateDownloadId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != activeUpdateDownloadId) return
                val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(completedId)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_SUCCESSFUL) return
                }
                val apkUri = manager.getUriForDownloadedFile(completedId) ?: return
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    clipData = ClipData.newUri(ctx.contentResolver, "KitTV update APK", apkUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { ctx.startActivity(installIntent) }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Dialog state variables
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showWifiDeviceTransfer by remember { mutableStateOf(false) }
    var showAddEpgDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCustomUiDialog by remember { mutableStateOf(false) }
    var customJsonText by remember { mutableStateOf(customThemeJson) }

    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var editingEpgSource by remember { mutableStateOf<EpgSource?>(null) }
    var showPasswordPrompt by remember { mutableStateOf<Playlist?>(null) }
    var inputPass by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var tempName by remember { mutableStateOf("") }
    var tempUrl by remember { mutableStateOf("") }
    var checkingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val playlistFilePicker = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "playlist.m3u"
                    viewModel.importPlaylistFile(uri, fileName)
                    Toast.makeText(context, tr("Импорт плейлиста: $fileName"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    } else null

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (activePage != null) tr(activePage!!.title) else tr("Настройки"),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) searchQuery = ""
                            else if (activePage != null) activePage = null
                            else onBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Назад")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Search Input
            SettingsSearch(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )

            if (searchQuery.isNotBlank()) {
                // Deep Search Results View
                val q = searchQuery.trim().lowercase()
                val allSearchItems = remember {
                    listOf(
                        SearchSettingItem("Язык интерфейса", "Выбор языка приложения (Русский / English)", "Внешний вид", SettingsPage.APPEARANCE, "language lang"),
                        SearchSettingItem("Стиль UI", "Material You 3 / Liquid Glass оформление", "Внешний вид", SettingsPage.APPEARANCE, "style ui theme"),
                        SearchSettingItem("Форма значков Material You 3", "Печенька, Клевер, Вспышка, Волна, Звезда, Ромб...", "Внешний вид", SettingsPage.APPEARANCE, "badge shape icon"),
                        SearchSettingItem("Тема оформления", "Светлая, Тёмная, AMOLED, Хеллоуин, Зима", "Внешний вид", SettingsPage.APPEARANCE, "theme color dark light"),
                        SearchSettingItem("Цвет акцента", "Синий, Зеленый, Красный, Фиолетовый, Оранжевый, Розовый, Бирюзовый", "Внешний вид", SettingsPage.APPEARANCE, "accent color"),
                        SearchSettingItem("Цвет панелей и карточек", "Фоновый оттенок панелей: Авто, Синий, Графит, Фиолетовый, Полуночный, Изумрудный", "Внешний вид", SettingsPage.APPEARANCE, "panel color background"),
                        SearchSettingItem("Цвет бейджиков", "Оттенок значков и иконок разделов", "Внешний вид", SettingsPage.APPEARANCE, "badge color icon"),
                        SearchSettingItem("Анимация поворота значков", "Поворот геометрической формы значка при фокусе", "Внешний вид", SettingsPage.APPEARANCE, "rotation animation icon"),
                        SearchSettingItem("Выразительное свечение", "Мягкая подсветка фокуса карточек", "Внешний вид", SettingsPage.APPEARANCE, "glow expressive focus"),
                        SearchSettingItem("Анимированный фон", "Живое плавающее градиентное свечение", "Внешний вид", SettingsPage.APPEARANCE, "ambient canvas bg"),
                        SearchSettingItem("Стиль списка каналов", "Компактный, стандартный, плитка, сетка", "Внешний вид", SettingsPage.APPEARANCE, "list size grid card"),
                        SearchSettingItem("Размер шрифта", "Размер текста каналов и меню", "Внешний вид", SettingsPage.APPEARANCE, "font size text"),
                        SearchSettingItem("Номера каналов", "Отображать порядковые номера рядом с названием", "Внешний вид", SettingsPage.APPEARANCE, "channel numbers number"),
                        SearchSettingItem("Показывать часы", "Индикатор времени на экране", "Внешний вид", SettingsPage.APPEARANCE, "clock time show"),
                        SearchSettingItem("Положение часов", "Сверху слева, Сверху справа, Снизу слева, Снизу справа, Спрятать", "Внешний вид", SettingsPage.APPEARANCE, "clock position place hide"),
                        SearchSettingItem("Соотношение сторон", "По умолчанию, Заполнить, Растянуть, 16:9, 4:3", "Плеер", SettingsPage.PLAYER, "aspect ratio player 16:9"),
                        SearchSettingItem("Аппаратное декодирование", "Auto, Hardware, Software декодер", "Плеер", SettingsPage.PLAYER, "hardware decoder exoplayer"),
                        SearchSettingItem("Уровень буферизации", "Низкий, Средний, Высокий буфер", "Плеер", SettingsPage.PLAYER, "buffer stream network"),
                        SearchSettingItem("Информация о потоке", "Источник, разрешение, FPS, кодеки, битрейт", "Плеер", SettingsPage.PLAYER, "info resolution fps codec bitrate"),
                        SearchSettingItem("Автозапуск последнего канала", "Включать эфир при входе в приложение", "Плеер", SettingsPage.PLAYER, "autostart last channel"),
                        SearchSettingItem("Таймер сна", "Автоотключение через 15, 30, 60, 90 минут", "Плеер", SettingsPage.PLAYER, "sleep timer auto off"),
                        SearchSettingItem("Режим архива (Catchup)", "Auto, Flussonic, Shift, Append", "Категории", SettingsPage.CATEGORIES, "catchup archive epg"),
                        SearchSettingItem("Сортировка категорий", "По умолчанию, Алфавит, Сначала популярные", "Категории", SettingsPage.CATEGORIES, "group category sort order"),
                        SearchSettingItem("Скрывать ссылку плейлиста", "Скрыть URL у плейлиста в шапке главного экрана", "Навигация", SettingsPage.NAVIGATION, "hide url playlist link"),
                        SearchSettingItem("Поведение кнопки Назад", "Выход из приложения, открытие списка категорий", "Навигация", SettingsPage.NAVIGATION, "back button exit behavior"),
                        SearchSettingItem("Жесты свайпа громкости", "Управление громкостью свайпом по экрану", "Навигация", SettingsPage.NAVIGATION, "volume gesture swipe"),
                        SearchSettingItem("Wi-Fi Передача и бэкап", "Передача данных, паролей и настроек по локальной сети Wi-Fi", "Резервное копирование", SettingsPage.BACKUP, "wifi transfer backup sync passwords"),
                        SearchSettingItem("Резервное копирование и экспорт", "Сохранение и импорт настроек плейлистов", "Резервное копирование", SettingsPage.BACKUP, "backup export import json"),
                        SearchSettingItem("Проверить обновления", "Загрузка и установка свежих выпусков KitTV", "О приложении", SettingsPage.ABOUT, "update check github version release"),
                        SearchSettingItem("Плейлисты и EPG", "Управление источниками M3U и телепрограммой", "Плейлисты", SettingsPage.PLAYLISTS, "playlist m3u epg list")
                    )
                }

                val matchedItems = remember(q) {
                    allSearchItems.filter { item ->
                        item.title.lowercase().contains(q) ||
                        item.subtitle.lowercase().contains(q) ||
                        item.keywords.lowercase().contains(q) ||
                        item.pageName.lowercase().contains(q)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            tr("Найдено результатов ({0}) для «{1}»", matchedItems.size, searchQuery),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (matchedItems.isEmpty()) {
                        item {
                            Text(
                                tr("Ничего не найдено по вашему запросу."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(matchedItems, key = { it.title }) { item ->
                            ActionItem(
                                icon = when (item.targetPage) {
                                    SettingsPage.PLAYLISTS -> Icons.Rounded.ListAlt
                                    SettingsPage.APPEARANCE -> Icons.Rounded.Palette
                                    SettingsPage.PLAYER -> Icons.Rounded.PlayCircle
                                    SettingsPage.CATEGORIES -> Icons.Rounded.Category
                                    SettingsPage.NAVIGATION -> Icons.Rounded.Navigation
                                    SettingsPage.BACKUP -> Icons.Rounded.Backup
                                    SettingsPage.ABOUT -> Icons.Rounded.Info
                                },
                                title = item.title,
                                subtitle = "${item.subtitle} • ${item.pageName}",
                                onClick = {
                                    activePage = item.targetPage
                                    searchQuery = ""
                                },
                                shapeName = badgeShape,
                                rotationEnabled = iconRotationEnabled
                            )
                        }
                    }
                }
            } else if (activePage == null) {
                // Main Category Grid Overview View
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        val ready = playlists.any { it.enabled }
                        HeroCard(
                            ready = ready,
                            title = if (ready) "Всё готово" else "Почти готово",
                            subtitle = if (ready) "Отличная работа! Все службы настроены и готовы к работе."
                            else "Настройте плейлисты или EPG для полноценной работы.",
                            stats = listOf(
                                "Плейлисты" to "${playlists.count { it.enabled }}",
                                "EPG" to "${epgSources.count { it.enabled }}",
                                "Версия" to "5.5",
                                "Тема" to themeMode
                            ),
                            badgeShape = badgeShape
                        )
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Категории настроек"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    items(SettingsPage.entries.toTypedArray()) { page ->
                        CategoryTile(
                            icon = page.icon,
                            title = page.title,
                            subtitle = page.subtitle,
                            badgeCount = when (page) {
                                SettingsPage.PLAYLISTS -> playlists.size
                                SettingsPage.CATEGORIES -> categories.size
                                else -> 0
                            },
                            onClick = { activePage = page },
                            shapeName = badgeShape,
                            rotationEnabled = iconRotationEnabled
                        )
                    }
                }
            } else {
                // Detail Page View
                val page = activePage
                if (page != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (page) {
                            SettingsPage.PLAYLISTS -> {
                                item {
                                    SettingsSection(
                                        title = "Плейлисты",
                                        subtitle = "Управление списком IPTV провайдеров"
                                    ) {
                                        playlists.forEach { playlist ->
                                            val displayUrl = if (com.kraftplay.openiptv.model.isKitTvPlaylist(playlist.name, playlist.url, playlist.id)) "" else playlist.url
                                            PlaylistCard(
                                                name = playlist.name,
                                                url = displayUrl,
                                                selected = playlist.isSelected,
                                                channelCount = playlist.channelCount,
                                                enabled = playlist.enabled,
                                                hasPassword = !playlist.password.isNullOrBlank(),
                                                onSelect = {
                                                    if (!playlist.enabled) {
                                                        Toast.makeText(context, context.getString(R.string.playlist_disabled), Toast.LENGTH_SHORT).show()
                                                    } else if (!playlist.password.isNullOrBlank()) {
                                                        showPasswordPrompt = playlist
                                                        inputPass = ""
                                                        passError = false
                                                    } else {
                                                        viewModel.selectPlaylist(playlist.id)
                                                    }
                                                },
                                                onSettings = { editingPlaylist = playlist },
                                                onDelete = { viewModel.deletePlaylist(playlist.id) },
                                                badgeShape = badgeShape,
                                                badgeColor = badgeColor,
                                                iconRotationEnabled = iconRotationEnabled
                                            )
                                        }

                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { showAddPlaylistDialog = true; tempName = ""; tempUrl = "" },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(tr("Добавить плейлист"))
                                            }
                                            OutlinedButton(
                                                onClick = { playlists.firstOrNull { it.isSelected && it.enabled }?.let { viewModel.loadPlaylist(it.url) } },
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null)
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { showWifiDeviceTransfer = true },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(Icons.Default.Wifi, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(tr("Передача по Wi-Fi"))
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    if (playlistFilePicker != null) {
                                                        runCatching { playlistFilePicker.launch(arrayOf("*/*")) }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(Icons.Default.Upload, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(tr("Импорт из файла"))
                                            }
                                        }
                                    }
                                }

                                item {
                                    val formattedEpgStatus = when {
                                        epgStatus == "LOADING" -> context.getString(R.string.epg_loading_status)
                                        epgStatus == "CLEARING" -> context.getString(R.string.epg_clearing)
                                        epgStatus == "CLEARED" -> context.getString(R.string.epg_cleared)
                                        epgStatus == "EPG_UPDATED" -> context.getString(R.string.epg_updated)
                                        epgStatus.startsWith("LOADED:") -> {
                                            val parts = epgStatus.split(":")
                                            val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                            val pct = parts.getOrNull(2) ?: "100%"
                                            context.getString(R.string.epg_programs_loaded, count, pct)
                                        }
                                        else -> epgStatus
                                    }

                                    SettingsSection(
                                        title = "Источники EPG (Телепрограмма)",
                                        subtitle = formattedEpgStatus.ifBlank { "Синхронизация программ телепередач" }
                                    ) {
                                        epgSources.forEach { source ->
                                            EpgCard(
                                                name = source.name,
                                                url = source.url,
                                                enabled = source.enabled,
                                                hasPassword = !source.password.isNullOrBlank(),
                                                onSettings = { editingEpgSource = source },
                                                onDelete = { viewModel.deleteEpgSource(source.id) },
                                                badgeShape = badgeShape,
                                                badgeColor = badgeColor,
                                                iconRotationEnabled = iconRotationEnabled
                                            )
                                        }

                                        ChoiceItem(
                                            icon = Icons.Rounded.Update,
                                            title = "Автообновление EPG",
                                            subtitle = "Периодичность проверки телепрограммы",
                                            options = listOf(
                                                "On launch" to "При запуске",
                                                "12 hours" to "Каждые 12 часов",
                                                "24 hours" to "Каждые 24 часа",
                                                "2 days" to "Каждые 2 дня",
                                                "3 days" to "Каждые 3 дня",
                                                "Off" to "Выключено"
                                            ),
                                            selected = epgUpdateInterval,
                                            onSelect = { viewModel.updateSetting("epg_update_interval", it) },
                                            shapeName = badgeShape,
                                            rotationEnabled = iconRotationEnabled
                                        )

                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { showAddEpgDialog = true; tempName = ""; tempUrl = "" },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(tr("Добавить EPG"))
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.refreshAllEpg(force = true) },
                                                shape = RoundedCornerShape(20.dp),
                                                enabled = !isEpgLoading
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null)
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))
                                        val sizeText = String.format(java.util.Locale.US, "%.1f MB", epgDbSize)
                                        OutlinedButton(
                                            onClick = { viewModel.clearAllEpg() },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("Очистить EPG кэш ({0})", sizeText))
                                        }
                                    }
                                }
                            }

                            SettingsPage.APPEARANCE -> {
                                item {
                                    SettingsSection(title = "Язык и дизайн") {
                                        ChoiceItem(
                                            icon = Icons.Rounded.Language,
                                            title = "Язык приложения",
                                            subtitle = "Выберите язык отображения интерфейса",
                                            options = listOf(
                                                "system" to "Как в системе",
                                                "ru" to "Русский",
                                                "en" to "English"
                                            ),
                                            selected = language,
                                            onSelect = { viewModel.updateSetting("language", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Style,
                                            title = "Стиль UI",
                                            subtitle = "Оформление поверхностей и карточек",
                                            options = listOf(
                                                "Material3" to "Material You 3",
                                                "LiquidGlass" to "Liquid Glass"
                                            ),
                                            selected = uiStyle,
                                            onSelect = { viewModel.updateSetting("ui_style", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Category,
                                            title = "Форма значков Material You 3",
                                            subtitle = "Форма фонового геометрического значка рядом с названием",
                                            options = listOf(
                                                "Cookie" to "Печенька (Cookie)",
                                                "Clover" to "Клевер (Clover)",
                                                "Burst" to "Вспышка (Burst)",
                                                "Scallop" to "Волна (Scallop)",
                                                "Sunny" to "Солнце (Sunny)",
                                                "Star" to "Звезда (Star)",
                                                "Pill" to "Капсула (Pill)",
                                                "Teardrop" to "Капля (Teardrop)",
                                                "Pentagon" to "Пятиугольник",
                                                "Hexagon" to "Шестиугольник",
                                                "Octagon" to "Восьмиугольник",
                                                "Circle" to "Круг",
                                                "Square" to "Квадрат",
                                                "Diamond" to "Ромб"
                                            ),
                                            selected = badgeShape,
                                            onSelect = { viewModel.updateSetting("badge_shape", it) },
                                            shapeName = badgeShape,
                                            rotationEnabled = iconRotationEnabled
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Palette,
                                            title = "Тема",
                                            subtitle = "Цветовая палитра приложения",
                                            options = listOf(
                                                "Light" to "Светлая",
                                                "Dark" to "Тёмная",
                                                "AMOLED" to "AMOLED",
                                                "Halloween" to "Хеллоуин",
                                                "Winter" to "Зима"
                                            ),
                                            selected = themeMode,
                                            onSelect = { viewModel.updateSetting("theme_mode", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.ColorLens,
                                            title = "Цвет акцента",
                                            subtitle = "Основной акцентный цвет кнопок и переключателей",
                                            options = listOf(
                                                "Blue" to "Синий",
                                                "Green" to "Зеленый",
                                                "Red" to "Красный",
                                                "Purple" to "Фиолетовый",
                                                "Orange" to "Оранжевый",
                                                "Pink" to "Розовый",
                                                "Cyan" to "Бирюзовый"
                                            ),
                                            selected = accentColor,
                                            onSelect = { viewModel.updateSetting("accent_color", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Palette,
                                            title = "Цвет панелей и карточек",
                                            subtitle = "Фоновый оттенок информационных карточек и диалогов",
                                            options = listOf(
                                                "Auto" to "Авто",
                                                "Dark Blue" to "Синий",
                                                "Graphite" to "Графит",
                                                "Deep Purple" to "Фиолетовый",
                                                "Midnight" to "Полуночный",
                                                "Emerald" to "Изумрудный"
                                            ),
                                            selected = panelColor,
                                            onSelect = { viewModel.updateSetting("panel_color", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Badge,
                                            title = "Цвет бейджиков",
                                            subtitle = "Оттенок значков и иконок разделов",
                                            options = listOf(
                                                "Auto" to "Авто",
                                                "Cyan" to "Бирюзовый",
                                                "Purple" to "Сиреневый",
                                                "Gold" to "Золотой",
                                                "Lime" to "Лаймовый",
                                                "Orange" to "Оранжевый",
                                                "Pink" to "Розовый"
                                            ),
                                            selected = badgeColor,
                                            onSelect = { viewModel.updateSetting("badge_color", it) },
                                            shapeName = badgeShape
                                        )
                                    }
                                }

                                item {
                                    SettingsSection(title = "Анимация и эффекты Material You 3") {
                                        ToggleItem(
                                            icon = Icons.Rounded.ChangeCircle,
                                            title = "Анимация поворота значков",
                                            subtitle = "Красивое вращение геометрических значков при нажатии",
                                            checked = iconRotationEnabled,
                                            onCheckedChange = { viewModel.updateSetting("icon_rotation_enabled", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.AutoAwesome,
                                            title = "Подсветка фокуса карточек",
                                            subtitle = "Светящийся контур при нажатии или фокусе",
                                            checked = expressiveGlow,
                                            onCheckedChange = { viewModel.updateSetting("expressive_glow", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.BlurOn,
                                            title = "Анимированный фоновый свет",
                                            subtitle = "Плавающие мягкие градиентные фигуры на фоне",
                                            checked = ambientCanvas,
                                            onCheckedChange = { viewModel.updateSetting("ambient_canvas", it) },
                                            shapeName = badgeShape
                                        )

                                        SliderItem(
                                            icon = Icons.Rounded.RoundedCorner,
                                            title = "Закругление карточек",
                                            value = cardCornerRadius.toFloat(),
                                            range = 8f..32f,
                                            format = { "${it.toInt()} dp" },
                                            onCommit = { viewModel.updateSetting("card_corner_radius", it.toInt()) },
                                            shapeName = badgeShape
                                        )

                                        SliderItem(
                                            icon = Icons.Rounded.TouchApp,
                                            title = "Сила отклика нажатия (Scale)",
                                            value = springScale,
                                            range = 1.00f..1.15f,
                                            format = { String.format(Locale.US, "%.2fx", it) },
                                            onCommit = { viewModel.updateSetting("spring_scale", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.LinearScale,
                                            title = "Стиль прогресса EPG",
                                            subtitle = "Внешний вид полосы текущей передачи",
                                            options = listOf(
                                                "Wavy" to "Волнистый",
                                                "Glow" to "Светящийся",
                                                "Solid" to "Сплошной"
                                            ),
                                            selected = epgProgressStyle,
                                            onSelect = { viewModel.updateSetting("epg_progress_style", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Layers,
                                            title = "Стиль стекла плеера",
                                            subtitle = "Оформление инфопанели при просмотре",
                                            options = listOf(
                                                "Dark Glass" to "Тёмное стекло",
                                                "OLED Black" to "AMOLED Черный",
                                                "Transparent" to "Прозрачный"
                                            ),
                                            selected = playerGlassTint,
                                            onSelect = { viewModel.updateSetting("player_glass_tint", it) },
                                            shapeName = badgeShape
                                        )
                                    }
                                }

                                item {
                                    SettingsSection(title = "Списки и шрифты") {
                                        ChoiceItem(
                                            icon = Icons.Rounded.GridView,
                                            title = "Размер элементов списка",
                                            subtitle = "Вариант отображения карточек каналов",
                                            options = listOf(
                                                "SimpleList" to "Простой список",
                                                "CompactList" to "Компактный",
                                                "SideBySide" to "Рядом",
                                                "LargeCards" to "Большие карточки"
                                            ),
                                            selected = listSize,
                                            onSelect = { viewModel.updateSetting("list_size", it) },
                                            shapeName = badgeShape
                                        )

                                        SliderItem(
                                            icon = Icons.Rounded.FormatSize,
                                            title = "Размер шрифта",
                                            value = fontSize,
                                            range = 10f..24f,
                                            format = { "${it.toInt()} sp" },
                                            onCommit = { viewModel.updateSetting("font_size", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.FormatListNumbered,
                                            title = "Показывать номера каналов",
                                            subtitle = "Отображать порядковые номера каналов в списке",
                                            checked = showChannelNumbers,
                                            onCheckedChange = { viewModel.updateSetting("show_channel_numbers", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.Schedule,
                                            title = "Показывать часы",
                                            subtitle = "Отображать часы на панели плеера",
                                            checked = showClock,
                                            onCheckedChange = { viewModel.updateSetting("show_clock", it) },
                                            shapeName = badgeShape
                                        )

                                        if (showClock) {
                                            ChoiceItem(
                                                icon = Icons.Rounded.Place,
                                                title = "Положение часов",
                                                subtitle = "Угол экрана для индикатора времени",
                                                options = listOf(
                                                    "Top left" to "Сверху слева",
                                                    "Top right" to "Сверху справа",
                                                    "Bottom left" to "Снизу слева",
                                                    "Bottom right" to "Снизу справа"
                                                ),
                                                selected = clockPosition,
                                                onSelect = { viewModel.updateSetting("clock_position", it) },
                                                shapeName = badgeShape
                                            )
                                        }

                                        ActionItem(
                                            icon = Icons.Rounded.Code,
                                            title = "Своя тема (JSON)",
                                            subtitle = "Настройка пользовательской темы JSON",
                                            onClick = { customJsonText = customThemeJson; showCustomUiDialog = true },
                                            shapeName = badgeShape
                                        )
                                    }
                                }
                            }

                            SettingsPage.PLAYER -> {
                                item {
                                    SettingsSection(title = "Воспроизведение и декодирование") {
                                        ChoiceItem(
                                            icon = Icons.Rounded.AspectRatio,
                                            title = "Соотношение сторон",
                                            subtitle = "Масштабирование видео на экране",
                                            options = listOf(
                                                "Fit" to "По размеру",
                                                "Fill" to "Заполнить",
                                                "16:9" to "16:9",
                                                "4:3" to "4:3"
                                            ),
                                            selected = aspectRatioMode,
                                            onSelect = { viewModel.updateSetting("aspect_ratio_mode", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Hardware,
                                            title = "Аппаратный декодер",
                                            subtitle = "Настройка декодирования видеопотока",
                                            options = listOf(
                                                "Auto" to "Авто",
                                                "HW" to "Аппаратный (HW)",
                                                "SW" to "Программный (SW)"
                                            ),
                                            selected = hardwareDecoder,
                                            onSelect = { viewModel.updateSetting("hardware_decoder", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Speed,
                                            title = "Буферизация",
                                            subtitle = "Размер сетевого буфера для стабильности потока",
                                            options = listOf(
                                                "Low" to "Низкая",
                                                "Standard" to "Стандартная",
                                                "High" to "Высокая"
                                            ),
                                            selected = bufferingLevel,
                                            onSelect = { viewModel.updateSetting("buffering_level", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.Timer,
                                            title = "Таймер сна",
                                            subtitle = "Автоматическое выключение плеера по таймеру",
                                            options = listOf(
                                                "0" to "Выключено",
                                                "15" to "15 мин",
                                                "30" to "30 мин",
                                                "60" to "60 мин"
                                            ),
                                            selected = sleepMinutes.toString(),
                                            onSelect = { viewModel.sleepTimer.value = it.toIntOrNull() ?: 0 },
                                            shapeName = badgeShape
                                        )
                                    }
                                }

                                item {
                                    SettingsSection(title = "Параметры инфопанели") {
                                        ToggleItem(
                                            icon = Icons.Rounded.Dns,
                                            title = "Показывать источник",
                                            subtitle = "Имя плейлиста или имя сервера в строке информации",
                                            checked = showSource,
                                            onCheckedChange = { viewModel.updateSetting("show_source", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.HighQuality,
                                            title = "Показывать разрешение",
                                            subtitle = "Разрешение видеопотока",
                                            checked = showResolution,
                                            onCheckedChange = { viewModel.updateSetting("show_resolution", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.SlowMotionVideo,
                                            title = "Показывать FPS",
                                            subtitle = "Частота кадров в секунду",
                                            checked = showFps,
                                            onCheckedChange = { viewModel.updateSetting("show_fps", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.MovieFilter,
                                            title = "Показывать кодеки",
                                            subtitle = "Тип видео и аудио кодека в плеере",
                                            checked = showCodecs,
                                            onCheckedChange = { viewModel.updateSetting("show_codecs", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.GraphicEq,
                                            title = "Показывать битрейт",
                                            subtitle = "Битрейт потока в килобитах",
                                            checked = showBitrate,
                                            onCheckedChange = { viewModel.updateSetting("show_bitrate", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.DisplaySettings,
                                            title = "Формат разрешения",
                                            subtitle = "Отображение качества в пикселях или метках",
                                            options = listOf(
                                                "Dimensions" to "Размерность (1920x1080)",
                                                "Labels" to "Метка (FHD, 4K)"
                                            ),
                                            selected = resFormat,
                                            onSelect = { viewModel.updateSetting("resolution_format", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.PlayArrow,
                                            title = "Автозапуск последнего канала",
                                            subtitle = "Запускать воспроизведение при открытии приложения",
                                            checked = autoStartLast,
                                            onCheckedChange = { viewModel.updateSetting("auto_start_last", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.ScreenRotation,
                                            title = "Принудительный ландшафт",
                                            subtitle = "Фиксирует альбомный режим в плеере",
                                            checked = forceLandscape,
                                            onCheckedChange = { viewModel.updateSetting("force_landscape", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.PictureInPicture,
                                            title = "Фоновое воспроизведение",
                                            subtitle = "Продолжать воспроизведение при сворачивании (PiP)",
                                            checked = backgroundPlayback,
                                            onCheckedChange = { viewModel.updateSetting("background_playback", it) },
                                            shapeName = badgeShape
                                        )
                                    }
                                }
                            }

                            SettingsPage.CATEGORIES -> {
                                item {
                                    SettingsSection(title = "Отображение категорий") {
                                        ChoiceItem(
                                            icon = Icons.Rounded.Folder,
                                            title = "Режим категорий",
                                            subtitle = "Внешний вид списка категорий на главном экране",
                                            options = listOf(
                                                "Chips" to "Чипы",
                                                "Folders" to "Папки"
                                            ),
                                            selected = catDisplayMode,
                                            onSelect = { viewModel.setCategoryDisplayMode(it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.SortByAlpha,
                                            title = "Сортировка групп",
                                            subtitle = "Порядок категорий в списке",
                                            options = listOf(
                                                "Provider" to "По провайдеру",
                                                "Alphabetical" to "По алфавиту"
                                            ),
                                            selected = groupSortOrder,
                                            onSelect = { viewModel.updateSetting("group_sort_order", it) },
                                            shapeName = badgeShape
                                        )

                                        ChoiceItem(
                                            icon = Icons.Rounded.History,
                                            title = "Режим архива (Catchup)",
                                            subtitle = "Настройки формата ссылок для просмотра архива телепередач",
                                            options = listOf(
                                                "Auto" to "Auto",
                                                "Default" to "Default",
                                                "Append" to "Append",
                                                "Shift" to "Shift"
                                            ),
                                            selected = catchupMode,
                                            onSelect = { viewModel.updateSetting("catchup_mode", it) },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.VisibilityOff,
                                            title = "Управление категориями",
                                            subtitle = "Скрытие и показ групп каналов",
                                            onClick = { showCategoryDialog = true },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.TvOff,
                                            title = "Управление каналами",
                                            subtitle = "Скрытие отдельных каналов из списка",
                                            onClick = { showChannelDialog = true },
                                            shapeName = badgeShape
                                        )
                                    }
                                }
                            }

                            SettingsPage.NAVIGATION -> {
                                item {
                                    SettingsSection(title = "Управление и ссылки") {
                                        ChoiceItem(
                                            icon = Icons.Rounded.ArrowBack,
                                            title = "Действие кнопки Назад",
                                            subtitle = "Поведение при нажатии системы Назад на главном экране",
                                            options = listOf(
                                                "Exit" to "Выход",
                                                "Back to list" to "Возврат к списку",
                                                "Double press to exit" to "Дважды нажать для выхода"
                                            ),
                                            selected = backBehavior,
                                            onSelect = { viewModel.updateSetting("back_behavior", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.SwipeVertical,
                                            title = "Жесты управления",
                                            subtitle = "Жесты свайпа для громкости и яркости",
                                            checked = volumeGestureEnabled,
                                            onCheckedChange = { viewModel.updateSetting("volume_gesture_enabled", it) },
                                            shapeName = badgeShape
                                        )

                                        ToggleItem(
                                            icon = Icons.Rounded.LinkOff,
                                            title = "Скрывать URL плейлиста",
                                            subtitle = "Скрывать прямые ссылки на плейлист в интерфейсе",
                                            checked = hidePlaylistUrl,
                                            onCheckedChange = { viewModel.updateSetting("hide_playlist_url", it) },
                                            shapeName = badgeShape
                                        )
                                    }
                                }
                            }

                            SettingsPage.BACKUP -> {
                                item {
                                    SettingsSection(title = "Резервная копия и сеть") {
                                        ActionItem(
                                            icon = Icons.Rounded.FileUpload,
                                            title = "Экспорт настроек",
                                            subtitle = "Скопировать резервную копию в буфер обмена",
                                            onClick = {
                                                val json = viewModel.exportBackupJson()
                                                clipboardManager.setText(AnnotatedString(json))
                                                Toast.makeText(context, tr("Резервная копия скопирована"), Toast.LENGTH_SHORT).show()
                                            },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.FileDownload,
                                            title = "Импорт настроек",
                                            subtitle = "Восстановить настройки из текста JSON",
                                            onClick = { showImportDialog = true },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.Wifi,
                                            title = "Передача по Wi-Fi",
                                            subtitle = "Передача настроек между устройствами по локальной сети",
                                            onClick = { showWifiDeviceTransfer = true },
                                            shapeName = badgeShape
                                        )
                                    }
                                }

                                item {
                                    SettingsSection(title = "Состояние LSPosed и Android Auto") {
                                        val isLspActive = LSPosedStatus.isActive
                                        ActionItem(
                                            icon = Icons.Rounded.Extension,
                                            title = "Состояние LSPosed",
                                            subtitle = if (isLspActive) "Модуль LSPosed активен" else "Модуль LSPosed не активен",
                                            onClick = {},
                                            shapeName = badgeShape
                                        )
                                    }
                                }
                            }

                            SettingsPage.ABOUT -> {
                                item {
                                    SettingsSection(title = "Сообщество и обновления") {
                                        ActionItem(
                                            icon = Icons.Rounded.Send,
                                            title = "Telegram",
                                            subtitle = "Официальный канал и чат поддержки",
                                            onClick = { openExternalLink(context, "https://t.me/+IB-WMBTo7ZxkNmZi") },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.Code,
                                            title = "GitHub",
                                            subtitle = "Исходный код проекта OpenIPTV",
                                            onClick = { openExternalLink(context, "https://github.com/redmi9cnfc/OpenIPTVApp") },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.SystemUpdate,
                                            title = if (checkingUpdate) "Проверка обновлений..." else "Проверить обновления",
                                            subtitle = "Загрузка и установка свежих выпусков KitTV",
                                            onClick = {
                                                if (!checkingUpdate) {
                                                    checkingUpdate = true
                                                    scope.launch(Dispatchers.IO) {
                                                        val result = runCatching {
                                                            fun readUrl(url: String): Pair<Int, String> {
                                                                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                                                                    connectTimeout = 20000
                                                                    readTimeout = 25000
                                                                    requestMethod = "GET"
                                                                    setRequestProperty("Accept", "application/vnd.github+json, text/html;q=0.9, */*;q=0.8")
                                                                    setRequestProperty("User-Agent", "OpenIPTVApp-Updater/1.0")
                                                                    instanceFollowRedirects = true
                                                                }
                                                                return try {
                                                                    conn.responseCode to (conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
                                                                } finally { conn.disconnect() }
                                                            }
                                                            val api = readUrl("https://api.github.com/repos/redmi9cnfc/OpenIPTVApp/releases/latest")
                                                            var apk: String? = null
                                                            var tag = "latest"
                                                            if (api.first in 200..299) {
                                                                val obj = org.json.JSONObject(api.second)
                                                                tag = obj.optString("tag_name", "latest")
                                                                val body = obj.optString("body", "")
                                                                val assets = obj.optJSONArray("assets")
                                                                if (assets != null) {
                                                                    for (i in 0 until assets.length()) {
                                                                        val asset = assets.optJSONObject(i) ?: continue
                                                                        val u = asset.optString("browser_download_url")
                                                                        if (asset.optString("name").endsWith(".apk", true) && u.startsWith("https://")) {
                                                                            apk = u; break
                                                                        }
                                                                    }
                                                                }
                                                                if (apk.isNullOrBlank()) throw IllegalStateException("APK not found")
                                                                Triple(tag, apk, body)
                                                            } else throw IllegalStateException("HTTP ${api.first}")
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            checkingUpdate = false
                                                            result.onSuccess { pendingUpdate = it }
                                                                .onFailure { Toast.makeText(context, "Update check: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                                                        }
                                                    }
                                                }
                                            },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.DeleteForever,
                                            title = "Очистить данные приложения",
                                            subtitle = "Сброс всех сохраненных плейлистов и EPG",
                                            onClick = { viewModel.clearData() },
                                            shapeName = badgeShape
                                        )

                                        ActionItem(
                                            icon = Icons.Rounded.Info,
                                            title = "Версия 5.5",
                                            subtitle = "OpenIPTV • Material You 3 Expressive",
                                            onClick = {},
                                            shapeName = badgeShape
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dialogs
            if (editingPlaylist != null) {
                val target = editingPlaylist!!
                val isKitTv = com.kraftplay.openiptv.model.isKitTvPlaylist(target.name, target.url, target.id)
                ItemSettingsDialog(
                    title = tr("Настройки плейлиста"),
                    name = target.name,
                    url = if (isKitTv) "" else target.url,
                    enabled = target.enabled,
                    password = target.password ?: "",
                    autoUpdateInterval = target.autoUpdateInterval,
                    onDismiss = { editingPlaylist = null },
                    onSave = { name, url, enabled, pass, interval ->
                        val finalUrl = if (isKitTv && url.isBlank()) target.url else url
                        viewModel.updatePlaylist(
                            target.copy(
                                name = name,
                                url = finalUrl,
                                enabled = enabled,
                                password = pass.ifBlank { null },
                                autoUpdateInterval = interval
                            )
                        )
                        editingPlaylist = null
                    }
                )
            }

            if (editingEpgSource != null) {
                val target = editingEpgSource!!
                ItemSettingsDialog(
                    title = tr("Настройки EPG"),
                    name = target.name,
                    url = target.url,
                    enabled = target.enabled,
                    password = target.password ?: "",
                    autoUpdateInterval = target.autoUpdateInterval,
                    onDismiss = { editingEpgSource = null },
                    onSave = { name, url, enabled, pass, interval ->
                        viewModel.updateEpgSource(
                            target.copy(
                                name = name,
                                url = url,
                                enabled = enabled,
                                password = pass.ifBlank { null },
                                autoUpdateInterval = interval
                            )
                        )
                        editingEpgSource = null
                    }
                )
            }

            if (showPasswordPrompt != null) {
                val target = showPasswordPrompt!!
                AlertDialog(
                    onDismissRequest = { showPasswordPrompt = null },
                    title = { Text(tr("Введите пароль")) },
                    text = {
                        Column {
                            Text(target.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputPass,
                                onValueChange = { inputPass = it },
                                label = { Text(tr("Пароль")) },
                                isError = passError,
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            if (passError) {
                                Text(tr("Неверный пароль"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (inputPass == target.password) {
                                viewModel.selectPlaylist(target.id)
                                showPasswordPrompt = null
                            } else {
                                passError = true
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPasswordPrompt = null }) { Text(tr("Отмена")) }
                    }
                )
            }

            if (showCustomUiDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomUiDialog = false },
                    title = { Text(tr("Редактировать тему JSON")) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = customJsonText,
                                onValueChange = { customJsonText = it },
                                placeholder = { Text("Paste Custom Theme JSON...") },
                                modifier = Modifier.fillMaxWidth().height(180.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { customJsonText = CustomThemeConfig.SAMPLE_CYBERPUNK }, modifier = Modifier.weight(1f)) {
                                    Text(tr("Вставить образец"))
                                }
                                TextButton(onClick = {
                                    val randomTheme = CustomThemeConfig.generateRandom()
                                    customJsonText = Json { prettyPrint = true }.encodeToString(randomTheme)
                                }, modifier = Modifier.weight(1f)) {
                                    Text(tr("Случайная тема"))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (viewModel.updateCustomTheme(customJsonText)) {
                                Toast.makeText(context, tr("Тема обновлена"), Toast.LENGTH_SHORT).show()
                                showCustomUiDialog = false
                            } else {
                                Toast.makeText(context, tr("Некорректный JSON"), Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(tr("Сохранить")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomUiDialog = false }) { Text(tr("Отмена")) }
                    }
                )
            }

            if (showCategoryDialog) {
                AlertDialog(
                    onDismissRequest = { showCategoryDialog = false },
                    title = { Text(tr("Управление категориями")) },
                    text = {
                        LazyColumn(modifier = Modifier.height(280.dp)) {
                            items(categories.filter { it != "All" && it != "Favorites" && it != "Recent" }) { cat ->
                                val isHidden = hiddenCategories.contains(cat)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        if (isHidden) "$cat (${tr("Отключена")})" else cat,
                                        modifier = Modifier.weight(1f),
                                        color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(checked = !isHidden, onCheckedChange = { viewModel.toggleHideCategory(cat) })
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showCategoryDialog = false }) { Text(tr("Закрыть")) } }
                )
            }

            if (showChannelDialog) {
                val manageableChannels by viewModel.channelManagementList.collectAsState()
                val hiddenChannelUrls by viewModel.hiddenChannels.collectAsState()
                var channelSearch by remember { mutableStateOf("") }
                val filteredManageableChannels = remember(manageableChannels, channelSearch) {
                    val query = channelSearch.trim()
                    if (query.isBlank()) manageableChannels
                    else manageableChannels.filter {
                        it.name.orEmpty().contains(query, ignoreCase = true) ||
                        it.url.orEmpty().contains(query, ignoreCase = true)
                    }
                }
                AlertDialog(
                    onDismissRequest = { showChannelDialog = false },
                    title = { Text(tr("Управление каналами")) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = channelSearch,
                                onValueChange = { channelSearch = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(tr("Поиск каналов")) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(filteredManageableChannels) { channel ->
                                    val isVisible = !hiddenChannelUrls.contains(channel.url)
                                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(channel.name.orEmpty(), modifier = Modifier.weight(1f), maxLines = 2)
                                        Switch(checked = isVisible, onCheckedChange = { viewModel.toggleHideChannel(channel.url) })
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showChannelDialog = false }) { Text(tr("Закрыть")) } }
                )
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text(tr("Импорт настроек")) },
                    text = {
                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            placeholder = { Text("Paste JSON here...") },
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (viewModel.importBackupJson(importJsonText)) {
                                Toast.makeText(context, tr("Настройки успешно импортированы"), Toast.LENGTH_SHORT).show()
                                showImportDialog = false
                            } else {
                                Toast.makeText(context, tr("Ошибка импорта настроек"), Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(tr("Импорт настроек")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) { Text(tr("Отмена")) }
                    }
                )
            }

            if (showWifiDeviceTransfer) {
                WifiTransferDialog(viewModel = viewModel, onDismiss = { showWifiDeviceTransfer = false })
            }

            if (showAddPlaylistDialog) {
                AddDialog(
                    title = tr("Добавить плейлист"),
                    name = tempName,
                    url = tempUrl,
                    onName = { tempName = it },
                    onUrl = { tempUrl = it },
                    onDismiss = { showAddPlaylistDialog = false },
                    onConfirm = { viewModel.addPlaylist(tempName, tempUrl); showAddPlaylistDialog = false }
                )
            }
            if (showAddEpgDialog) {
                AddDialog(
                    title = tr("Добавить EPG"),
                    name = tempName,
                    url = tempUrl,
                    onName = { tempName = it },
                    onUrl = { tempUrl = it },
                    onDismiss = { showAddEpgDialog = false },
                    onConfirm = { viewModel.addEpgSource(tempName, tempUrl); showAddEpgDialog = false }
                )
            }

            pendingUpdate?.let { update ->
                AlertDialog(
                    onDismissRequest = { pendingUpdate = null },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = {
                        Text(
                            tr("Доступно обновление KitTV {0}", update.first),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                tr("Что нового в версии {0}:", update.first),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val formattedNotes = remember(update.third) {
                                val rawBody = update.third.trim()
                                if (rawBody.isBlank()) {
                                    tr("Описание изменений от автора отсутствует.")
                                } else {
                                    rawBody
                                        .lines()
                                        .joinToString("\n") { line ->
                                            val trimmed = line.trim()
                                            when {
                                                trimmed.startsWith("## ") -> "\n📌 " + trimmed.removePrefix("## ").trim()
                                                trimmed.startsWith("# ") -> "\n📌 " + trimmed.removePrefix("# ").trim()
                                                trimmed.startsWith("### ") -> "\n🔹 " + trimmed.removePrefix("### ").trim()
                                                trimmed.startsWith("* ") -> "  • " + trimmed.removePrefix("* ").trim()
                                                trimmed.startsWith("- ") -> "  • " + trimmed.removePrefix("- ").trim()
                                                else -> line
                                            }
                                        }
                                        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                                        .replace(Regex("`(.*?)`"), "$1")
                                        .trim()
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = formattedNotes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("Загрузить APK и открыть системное окно установки?"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val downloadUrl = update.second
                                pendingUpdate = null
                                runCatching {
                                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    val request = DownloadManager.Request(Uri.parse(downloadUrl))
                                        .setTitle("KitTV — ${update.first}")
                                        .setDescription("Загрузка APK")
                                        .setMimeType("application/vnd.android.package-archive")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "KitTV-latest.apk")
                                    activeUpdateDownloadId = manager.enqueue(request)
                                    Toast.makeText(context, tr("Загрузка началась. После загрузки появится системное окно установки."), Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) { Text(tr("Обновить")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingUpdate = null }) { Text(tr("Позже")) }
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(
    name: String,
    url: String,
    selected: Boolean,
    channelCount: Int,
    enabled: Boolean = true,
    hasPassword: Boolean = false,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    badgeShape: String = "Cookie",
    badgeColor: String = "Auto",
    iconRotationEnabled: Boolean = false
) {
    GroupItem(onClick = onSelect) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.ListAlt,
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shapeName = badgeShape,
                badgeColorName = badgeColor,
                rotationEnabled = iconRotationEnabled
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!enabled) {
                        Text(" (${tr("Отключен")})", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    } else if (channelCount > 0) {
                        Text(" ($channelCount ${tr("каналов")})", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (hasPassword) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                    }
                }
                if (url.isNotEmpty()) {
                    Text(url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EpgCard(
    name: String,
    url: String,
    enabled: Boolean = true,
    hasPassword: Boolean = false,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    badgeShape: String = "Cookie",
    badgeColor: String = "Auto",
    iconRotationEnabled: Boolean = false
) {
    GroupItem {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = Icons.Rounded.Tv,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shapeName = badgeShape,
                badgeColorName = badgeColor,
                rotationEnabled = iconRotationEnabled
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!enabled) {
                        Text(" (${tr("Отключен")})", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (hasPassword) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                    }
                }
                Text(url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ItemSettingsDialog(
    title: String,
    name: String,
    url: String,
    enabled: Boolean,
    password: String,
    autoUpdateInterval: String,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, enabled: Boolean, password: String, interval: String) -> Unit
) {
    var editName by remember { mutableStateOf(name) }
    var editUrl by remember { mutableStateOf(url) }
    var editEnabled by remember { mutableStateOf(enabled) }
    var isLocked by remember { mutableStateOf(password.isNotBlank()) }
    var editPassword by remember { mutableStateOf(password) }
    var editInterval by remember { mutableStateOf(autoUpdateInterval) }

    val updateIntervals = listOf(
        "On launch" to "При запуске",
        "12 hours" to "12 ч",
        "24 hours" to "24 ч",
        "2 days" to "2 дня",
        "3 days" to "3 дня",
        "Off" to "Выкл"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(tr("Имя")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    label = { Text(tr("URL адрес")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Включить элемент"), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = editEnabled, onCheckedChange = { editEnabled = it })
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Защита паролем"), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = isLocked, onCheckedChange = {
                        isLocked = it
                        if (!it) editPassword = ""
                    })
                }

                if (isLocked) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text(tr("Пароль")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(tr("Интервал автообновления"), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                ButtonGroup(
                    options = updateIntervals,
                    selected = editInterval,
                    onSelect = { editInterval = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(editName, editUrl, editEnabled, if (isLocked) editPassword else "", editInterval)
                onDismiss()
            }) { Text(tr("Сохранить")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Отмена")) }
        }
    )
}

@Composable
fun AddDialog(
    title: String,
    name: String,
    url: String,
    onName: (String) -> Unit,
    onUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text(tr("Имя")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrl,
                    label = { Text(tr("URL адрес")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(tr("Добавить плейлист")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Отмена")) }
        }
    )
}

private fun openExternalLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(baseIntent) }
}
