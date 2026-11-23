package com.kraftplay.openiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KeyboardLayoutMode { RU, EN, NUM }

@Composable
fun CarOnScreenKeyboardDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf(initialText) }
    var isCaps by remember { mutableStateOf(false) }
    var layoutMode by remember { mutableStateOf(KeyboardLayoutMode.RU) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xF2181820),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок и текстовое поле
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2B2B36))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = inputText.ifEmpty { tr("Введите текст...") },
                            color = if (inputText.isEmpty()) Color.Gray else Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            com.kraftplay.openiptv.util.VoiceInputHelper.startSpeechRecognition(
                                context = context,
                                onResult = { spoken -> inputText = spoken }
                            )
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF00A2E8))
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Кнопки клавиатуры
                val rows = when (layoutMode) {
                    KeyboardLayoutMode.RU -> listOf(
                        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
                        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
                        listOf("⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫")
                    )
                    KeyboardLayoutMode.EN -> listOf(
                        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                        listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫")
                    )
                    KeyboardLayoutMode.NUM -> listOf(
                        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                        listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"),
                        listOf("!", "\"", "'", ":", ";", "?", "/", ".", ",", "⌫")
                    )
                }

                for (row in rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (key in row) {
                            val isSpecial = key in listOf("⇧", "⌫")
                            val keyWeight = if (isSpecial) 1.4f else 1.0f
                            val keyText = if (isCaps && !isSpecial) key.uppercase() else key

                            Box(
                                modifier = Modifier
                                    .weight(keyWeight)
                                    .height(42.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSpecial) Color(0xFF3B3B4A) else Color(0xFF2B2B36))
                                    .clickable {
                                        when (key) {
                                            "⇧" -> isCaps = !isCaps
                                            "⌫" -> if (inputText.isNotEmpty()) inputText = inputText.dropLast(1)
                                            else -> {
                                                inputText += keyText
                                                if (isCaps) isCaps = false
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "⇧" -> Icon(
                                        Icons.Default.KeyboardCapslock,
                                        contentDescription = "Caps",
                                        tint = if (isCaps) Color(0xFFFF9800) else Color.White
                                    )
                                    "⌫" -> Icon(
                                        Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                    else -> Text(
                                        text = keyText,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Нижний ряд клавиш (Смена раскладки, Пробел, Найти)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка переключения языка / цифр
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3B3B4A))
                            .clickable {
                                layoutMode = when (layoutMode) {
                                    KeyboardLayoutMode.RU -> KeyboardLayoutMode.EN
                                    KeyboardLayoutMode.EN -> KeyboardLayoutMode.NUM
                                    KeyboardLayoutMode.NUM -> KeyboardLayoutMode.RU
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (layoutMode) {
                                KeyboardLayoutMode.RU -> "RU / EN"
                                KeyboardLayoutMode.EN -> "EN / 123"
                                KeyboardLayoutMode.NUM -> "123 / RU"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    // Клавиша Пробел
                    Box(
                        modifier = Modifier
                            .weight(2.5f)
                            .height(44.dp)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2B2B36))
                            .clickable { inputText += " " },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr("Пробел"), color = Color.Gray, fontSize = 14.sp)
                    }

                    // Кнопка Найти / Поиск
                    Button(
                        onClick = {
                            onConfirm(inputText)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .padding(start = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tr("Найти"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
