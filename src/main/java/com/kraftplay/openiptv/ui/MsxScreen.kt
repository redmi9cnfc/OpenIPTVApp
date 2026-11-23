package com.kraftplay.openiptv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MsxAppItem(
    val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val iconVector: ImageVector? = null,
    val iconUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MsxScreen(
    viewModel: MainViewModel,
    onLaunchUrl: (String) -> Unit,
    onBack: () -> Unit,
    isAuto: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedStartUrl by viewModel.msxStartUrl.collectAsState("")
    var startUrlInput by remember(savedStartUrl) { mutableStateOf(savedStartUrl) }
    var showKeyboardDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<MsxAppItem>>(emptyList()) }

    fun loadMsxPortalJson(targetUrl: String) {
        if (targetUrl.isBlank()) return
        val finalUrl = if (targetUrl.startsWith("http://", ignoreCase = true) || targetUrl.startsWith("https://", ignoreCase = true)) targetUrl else "http://$targetUrl"
        isLoading = true
        statusText = tr("Загрузка MSX портала...")
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "MediaStationX/1.0 (Android Auto)")
                }
                try {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val items = mutableListOf<MsxAppItem>()
                    if (text.trim().startsWith("{")) {
                        val json = JSONObject(text)
                        val arr = json.optJSONArray("items") ?: json.optJSONArray("content")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                val name = item.optString("title", item.optString("name", "App ${i + 1}"))
                                val link = item.optString("action", item.optString("link", item.optString("url", "")))
                                val icon = item.optString("icon", item.optString("image", ""))
                                if (link.isNotBlank()) {
                                    items.add(MsxAppItem("msx_$i", name, link, "MSX App", Icons.Default.Public, iconUrl = icon.ifBlank { null }))
                                }
                            }
                        }
                    }
                    items
                } finally {
                    conn.disconnect()
                }
            }
            withContext(Dispatchers.Main) {
                isLoading = false
                result.onSuccess {
                    parsedItems = it
                    if (it.isEmpty()) {
                        onLaunchUrl(finalUrl)
                    } else {
                        statusText = tr("Загружено MSX приложений: {0}", it.size)
                    }
                }.onFailure {
                    statusText = tr("Портал открыт напрямую")
                    onLaunchUrl(finalUrl)
                }
            }
        }
    }

    LaunchedEffect(savedStartUrl) {
        val target = savedStartUrl.ifBlank { "http://msx.benzac.de" }
        val finalUrl = if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) target else "http://$target"
        onLaunchUrl(finalUrl)
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101018))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "MSX",
                            tint = Color(0xFF00A2E8),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Media Station X", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                if (savedStartUrl.isBlank()) tr("Введите стартовый параметр") else savedStartUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Назад"), tint = Color.White)
                    }
                },
                actions = {
                    if (savedStartUrl.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.updateSetting("msx_start_url", "")
                            startUrlInput = ""
                            parsedItems = emptyList()
                            statusText = ""
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = tr("Изменить стартовый параметр"), tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF181824))
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF00A2E8))
            }

            if (savedStartUrl.isBlank() && parsedItems.isEmpty()) {
                // Пустой начальный экран MSX с вводом собственного стартового параметра
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E1E2C),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Public, null, tint = Color(0xFF00A2E8), modifier = Modifier.size(48.dp))
                            }
                        }

                        Text(
                            text = "Media Station X",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = tr("Для работы выберите или введите ваш стартовый параметр (Start Parameter) / ссылку на портал MSX (Media Station X):"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = startUrlInput,
                                onValueChange = { startUrlInput = it },
                                placeholder = { Text(tr("например: msx.benzac.de или ваша ссылка"), color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E1E2C),
                                    unfocusedContainerColor = Color(0xFF181824),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(Modifier.width(8.dp))

                            IconButton(onClick = {
                                showKeyboardDialog = true
                            }) {
                                Icon(Icons.Default.Search, contentDescription = tr("Клавиатура"), tint = Color.White)
                            }

                            IconButton(onClick = {
                                com.kraftplay.openiptv.util.VoiceInputHelper.startSpeechRecognition(
                                    context = context,
                                    onResult = { spoken ->
                                        startUrlInput = spoken
                                        viewModel.updateSetting("msx_start_url", spoken)
                                        loadMsxPortalJson(spoken)
                                    }
                                )
                            }) {
                                Icon(Icons.Default.Mic, contentDescription = tr("Голосовой ввод"), tint = Color(0xFF00A2E8))
                            }
                        }

                        Button(
                            onClick = {
                                val target = startUrlInput.trim()
                                if (target.isNotBlank()) {
                                    val finalUrl = if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) target else "http://$target"
                                    viewModel.updateSetting("msx_start_url", target)
                                    onLaunchUrl(finalUrl)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A2E8))
                        ) {
                            Text(tr("Указать стартовый параметр"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Быстрый выбор стандартного MSX узла
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val official = "http://msx.benzac.de"
                                    startUrlInput = official
                                    viewModel.updateSetting("msx_start_url", official)
                                    onLaunchUrl(official)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(tr("msx.benzac.de (Официальный)"), color = Color.LightGray)
                            }
                            OutlinedButton(
                                onClick = {
                                    val ru = "https://msx.lnka.ru"
                                    startUrlInput = ru
                                    viewModel.updateSetting("msx_start_url", ru)
                                    onLaunchUrl(ru)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("msx.lnka.ru (RU)", color = Color.LightGray)
                            }
                        }
                    }
                }
            } else {
                // Загруженные MSX приложения
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(parsedItems, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clickable { onLaunchUrl(item.url) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF00A2E8).copy(alpha = 0.2f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.iconVector ?: Icons.Default.Public,
                                            contentDescription = item.name,
                                            tint = Color(0xFF00A2E8),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showKeyboardDialog) {
            CarOnScreenKeyboardDialog(
                initialText = startUrlInput,
                onDismiss = { showKeyboardDialog = false },
                onConfirm = { input ->
                    startUrlInput = input
                    viewModel.updateSetting("msx_start_url", input)
                    showKeyboardDialog = false
                    if (input.isNotBlank()) {
                        loadMsxPortalJson(input)
                    }
                }
            )
        }
    }
}
