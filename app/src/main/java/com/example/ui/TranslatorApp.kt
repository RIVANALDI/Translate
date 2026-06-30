package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.ChatViewModel
import com.example.data.TtsManager
import kotlinx.coroutines.launch
import java.io.File

// Parser helper classes
sealed class TextSegment {
    data class Ruby(val base: String, val ruby: String) : TextSegment()
    data class Normal(val text: String) : TextSegment()
}

fun parseFurigana(text: String): List<TextSegment> {
    val segments = mutableListOf<TextSegment>()
    val regex = Regex("\\[([^\\]]+)\\]\\{([^}]+)\\}")
    var lastIndex = 0

    val matches = regex.findAll(text).toList()
    if (matches.isEmpty()) {
        return listOf(TextSegment.Normal(text))
    }

    matches.forEach { matchResult ->
        // Add preceding normal text
        if (matchResult.range.first > lastIndex) {
            val normalText = text.substring(lastIndex, matchResult.range.first)
            segments.add(TextSegment.Normal(normalText))
        }
        val base = matchResult.groupValues[1]
        val ruby = matchResult.groupValues[2]
        segments.add(TextSegment.Ruby(base, ruby))
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < text.length) {
        segments.add(TextSegment.Normal(text.substring(lastIndex)))
    }

    return segments
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaText(
    text: String,
    showFurigana: Boolean,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    rubyColor: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 16.sp
) {
    val segments = remember(text) { parseFurigana(text) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center
    ) {
        segments.forEach { segment ->
            when (segment) {
                is TextSegment.Normal -> {
                    Text(
                        text = segment.text,
                        color = baseColor,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.5f).sp,
                        modifier = Modifier.padding(vertical = if (showFurigana) 6.dp else 0.dp)
                    )
                }
                is TextSegment.Ruby -> {
                    if (showFurigana) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        ) {
                            Text(
                                text = segment.ruby,
                                color = rubyColor,
                                fontSize = (fontSize.value * 0.6f).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = segment.base,
                                color = baseColor,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = segment.base,
                            color = baseColor,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = if (showFurigana) 6.dp else 0.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorApp(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Collect States
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isTranslating by viewModel.isTranslating.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()

    val attachmentUri by viewModel.attachmentUri.collectAsStateWithLifecycle()
    val attachmentMimeType by viewModel.attachmentMimeType.collectAsStateWithLifecycle()

    val gayaBahasa by viewModel.gayaBahasa.collectAsStateWithLifecycle()
    val formatKeluaran by viewModel.formatKeluaran.collectAsStateWithLifecycle()
    val furiganaEnabled by viewModel.furiganaEnabled.collectAsStateWithLifecycle()

    // Clipboard and TTS managers
    val clipboardManager = LocalClipboardManager.current
    val ttsManager = remember { TtsManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Media Picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.setAttachment(uri, "image/jpeg")
            }
        }
    )

    // Layout
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Asisten JP-ID AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Action: New Chat
                    Button(
                        onClick = {
                            viewModel.startNewSession("Percakapan Baru")
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("new_chat_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Percakapan Baru", fontWeight = FontWeight.Bold)
                    }

                    // Past Sessions Title
                    Text(
                        text = "Riwayat Percakapan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Sessions List
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == currentSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .clickable {
                                        viewModel.selectSession(session.id)
                                        coroutineScope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Chat Session",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected || sessions.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteSession(session.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus Sesi",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))

                    // Settings Section inside Drawer
                    Text(
                        text = "Pengaturan AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Style preference
                    Text(
                        text = "Gaya Bahasa",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    var showStyleMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showStyleMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(gayaBahasa, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.Settings, contentDescription = "Gaya", modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showStyleMenu,
                            onDismissRequest = { showStyleMenu = false },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            listOf(
                                "Alami & Kontekstual",
                                "Formal (Keigo/Bisnis)",
                                "Kasual (Anime/Manga)",
                                "Bahasa Gaul / Slang"
                            ).forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style) },
                                    onClick = {
                                        viewModel.setGayaBahasa(style)
                                        showStyleMenu = false
                                        Toast.makeText(context, "Gaya bahasa diatur ke: $style", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Format toggle: Paragraph / Sentence
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Format Keluaran",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Saat ini: $formatKeluaran",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = formatKeluaran == "Per Kalimat",
                            onCheckedChange = { isSentence ->
                                val target = if (isSentence) "Per Kalimat" else "Per Paragraf"
                                viewModel.setFormatKeluaran(target)
                                Toast.makeText(context, "Format diubah ke: $target", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Global Furigana Toggle in settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tampilkan Furigana",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Membantu membaca Kanji",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = furiganaEnabled,
                            onCheckedChange = { viewModel.setFuriganaEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Asisten JP-ID",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("sidebar_toggle")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu Sidebar")
                        }
                    },
                    actions = {
                        // Quick Toggle Furigana on Toolbar!
                        IconButton(
                            onClick = { viewModel.setFuriganaEnabled(!furiganaEnabled) },
                            modifier = Modifier.testTag("furigana_toggle")
                        ) {
                            Icon(
                                imageVector = if (furiganaEnabled) Icons.Default.Language else Icons.Default.Translate,
                                contentDescription = "Toggle Furigana",
                                tint = if (furiganaEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (messages.isEmpty()) {
                        // Welcome Screen / Empty State
                        Box(modifier = Modifier.weight(1f)) {
                            EmptyStateView(
                                gayaBahasa = gayaBahasa,
                                formatKeluaran = formatKeluaran
                            )
                        }
                    } else {
                        // Message list
                        val listState = rememberLazyListState()
                        // Scroll to bottom on new messages
                        LaunchedEffect(messages.size) {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                        ) {
                            items(messages) { msg ->
                                MessageBubble(
                                    message = msg,
                                    furiganaEnabled = furiganaEnabled,
                                    onCopy = { textToCopy ->
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        Toast.makeText(context, "Salin ke papan klip!", Toast.LENGTH_SHORT).show()
                                    },
                                    onSpeak = { textToSpeak, isJp ->
                                        ttsManager.speak(textToSpeak, isJp)
                                    }
                                )
                            }

                            if (isTranslating) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "AI sedang menerjemahkan...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Chat Input Area
                    ChatInputBar(
                        attachmentUri = attachmentUri,
                        isRecording = isRecording,
                        isTranscribing = isTranscribing,
                        onAttachmentClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRemoveAttachment = {
                            viewModel.setAttachment(null, null)
                        },
                        onRecordStart = {
                            (context as? com.example.MainActivity)?.checkAndStartRecording() ?: viewModel.startRecording()
                        },
                        onRecordStop = { callback ->
                            viewModel.stopRecording { transcript ->
                                if (transcript.isNotBlank() && !transcript.startsWith("Error")) {
                                    Toast.makeText(context, "Suara berhasil ditranskripsi!", Toast.LENGTH_SHORT).show()
                                }
                                callback(transcript)
                            }
                        },
                        onRecordCancel = {
                            viewModel.cancelRecording()
                            Toast.makeText(context, "Perekaman dibatalkan", Toast.LENGTH_SHORT).show()
                        },
                        onSend = { text ->
                            if (currentSessionId == null) {
                                viewModel.startNewSession(if (text.length > 20) text.take(20) + "..." else text)
                            }
                            viewModel.sendMessage(text)
                        }
                    )
                }

                // Overlay warning during audio processing
                if (isTranscribing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(32.dp).testTag("voice_record_overlay")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Mentranskripsi Suara Anda...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Gemini sedang mendengarkan dan memproses rekaman audio...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    gayaBahasa: String,
    formatKeluaran: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Large Premium Icon Card
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.size(96.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "AI Penerjemah Jepang ↔ Indonesia",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Asisten multi-modal tingkat ahli. Ketik teks, tempel lirik lagu, upload screenshot, atau rekam suara Anda secara real-time.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status pill indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text("Gaya: $gayaBahasa") },
                leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = {},
                label = { Text("Format: $formatKeluaran") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp)) }
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    furiganaEnabled: Boolean,
    onCopy: (String) -> Unit,
    onSpeak: (String, Boolean) -> Unit
) {
    val isUser = message.role == "user"

    var localFuriganaEnabled by remember { mutableStateOf(furiganaEnabled) }

    // Sync local furigana with global state when global changes
    LaunchedEffect(furiganaEnabled) {
        localFuriganaEnabled = furiganaEnabled
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI Icon Avatar
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(32.dp).padding(top = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 24.dp else 8.dp,
                    topEnd = if (isUser) 8.dp else 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Render Attached Image
                    if (message.mediaPath != null) {
                        val file = File(message.mediaPath)
                        if (file.exists()) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Attached Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(bottom = 8.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (isUser) {
                        // User message is plain text
                        Text(
                            text = message.text,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        // Structured translation response from Gemini
                        if (message.text.startsWith("Error")) {
                            Text(
                                text = message.text,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            val hasJp = !message.japanese.isNullOrBlank()
                            val hasRomaji = !message.romaji.isNullOrBlank()
                            val hasIndo = !message.indonesian.isNullOrBlank()

                            if (hasJp) {
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Text(
                                        text = "JEPANG",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FuriganaText(
                                        text = message.japanese!!,
                                        showFurigana = localFuriganaEnabled,
                                        fontSize = 17.sp,
                                        baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        rubyColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (hasRomaji) {
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Text(
                                        text = "ROMAJI",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.romaji!!,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            if (hasIndo) {
                                Column {
                                    Text(
                                        text = "INDONESIA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.indonesian!!,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // If no structure extracted, display the raw markdown text
                            if (!hasJp && !hasRomaji && !hasIndo) {
                                Text(
                                    text = message.text,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Action row under AI bubble or User bubble
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val timeString = sdf.format(java.util.Date(message.timestamp))

            if (!isUser && !message.text.startsWith("Error")) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                ) {
                    // Furigana toggle button for this message specifically
                    if (!message.japanese.isNullOrBlank()) {
                        IconButton(
                            onClick = { localFuriganaEnabled = !localFuriganaEnabled },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (localFuriganaEnabled) Icons.Default.Language else Icons.Default.Translate,
                                contentDescription = "Furigana",
                                tint = if (localFuriganaEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // TTS Button Japanese
                    if (!message.japanese.isNullOrBlank()) {
                        IconButton(
                            onClick = { onSpeak(message.japanese, true) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Listen Japanese",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // TTS Button Indonesian
                    if (!message.indonesian.isNullOrBlank()) {
                        IconButton(
                            onClick = { onSpeak(message.indonesian, false) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Dengarkan Terjemahan",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Copy translation
                    IconButton(
                        onClick = {
                            val plainText = buildString {
                                if (!message.japanese.isNullOrBlank()) {
                                    append("Jepang:\n")
                                    // Strip furigana format [Kanji]{furigana}
                                    val regex = Regex("\\[([^\\]]+)\\]\\{([^}]+)\\}")
                                    append(regex.replace(message.japanese) { it.groupValues[1] })
                                    append("\n\n")
                                }
                                if (!message.romaji.isNullOrBlank()) {
                                    append("Romaji:\n${message.romaji}\n\n")
                                }
                                if (!message.indonesian.isNullOrBlank()) {
                                    append("Indonesia:\n${message.indonesian}\n")
                                }
                            }
                            onCopy(plainText.ifBlank { message.text })
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "AI Interpreter • $timeString",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp, start = 8.dp)
                )
            } else if (isUser) {
                Text(
                    text = timeString,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            // User Avatar Icon
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.size(32.dp).padding(top = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = "User",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    attachmentUri: Uri?,
    isRecording: Boolean,
    isTranscribing: Boolean,
    onAttachmentClick: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: ((String) -> Unit) -> Unit,
    onRecordCancel: () -> Unit,
    onSend: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Active Attachment Preview Strip
            if (attachmentUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = attachmentUri,
                        contentDescription = "Attachment preview",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "File Foto Terlampir",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Siap diterjemahkan & dianalisis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRemoveAttachment) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus lampiran")
                    }
                }
            }

            // Quick Shortcut Row above textbox
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Short key: Terjemahkan Lirik preset
                InputChip(
                    selected = false,
                    onClick = {
                        textInput = "Terjemahkan lirik lagu ini ke Bahasa Indonesia yang puitis dan indah:\n\n[Tempel Lirik di Sini]"
                    },
                    label = { Text("🎵 Terjemahkan Lirik", fontSize = 11.sp) },
                    modifier = Modifier.testTag("lyric_translate_button")
                )

                // Short key: Percakapan Kasual
                InputChip(
                    selected = false,
                    onClick = {
                        textInput = "Terjemahkan ungkapan kasual/anime berikut secara alami: "
                    },
                    label = { Text("🌸 Kasual / Anime", fontSize = 11.sp) }
                )
            }

            // Main Row: Attachment, Input Box, Mic / Send Button
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Attachment Button
                IconButton(
                    onClick = onAttachmentClick,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag("attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Lampirkan Foto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (isRecording) {
                    // Recording Mode Controls
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Merekam Suara...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Row {
                            TextButton(onClick = onRecordCancel) {
                                Text("Batal", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    onRecordStop { transcript ->
                                        if (transcript.isNotBlank() && !transcript.startsWith("Error")) {
                                            textInput = if (textInput.isBlank()) transcript else "$textInput $transcript"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Selesai & Transkrip")
                            }
                        }
                    }
                } else {
                    // Text Box Input
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ketik kata, lirik lagu, atau gunakan mik...") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 120.dp)
                            .testTag("text_input"),
                        shape = RoundedCornerShape(32.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (textInput.isNotBlank() || attachmentUri != null) {
                        // Send Button
                        IconButton(
                            onClick = {
                                val msgToSend = textInput.trim()
                                onSend(msgToSend)
                                textInput = ""
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(44.dp)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Microphone button
                        IconButton(
                            onClick = onRecordStart,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .size(44.dp)
                                .testTag("mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Rekam Suara",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
