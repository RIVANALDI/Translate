package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ChatViewModel(private val context: Context) : ViewModel() {
    private val db = AppDatabase.getDatabase(context)
    private val repository = ChatRepository(context, db.chatDao())
    private val audioRecorder = AudioRecorder(context)

    // Sessions flow
    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Session ID state
    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId.asStateFlow()

    // Current Messages flow
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI state states
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    // Attachment states
    private val _attachmentUri = MutableStateFlow<Uri?>(null)
    val attachmentUri: StateFlow<Uri?> = _attachmentUri.asStateFlow()

    private val _attachmentPath = MutableStateFlow<String?>(null)
    val attachmentPath: StateFlow<String?> = _attachmentPath.asStateFlow()

    private val _attachmentMimeType = MutableStateFlow<String?>(null)
    val attachmentMimeType: StateFlow<String?> = _attachmentMimeType.asStateFlow()

    // User settings (Shared Preferences backed)
    private val prefs = context.getSharedPreferences("nihongo_prefs", Context.MODE_PRIVATE)

    private val _gayaBahasa = MutableStateFlow(prefs.getString("gaya_bahasa", "Alami & Kontekstual") ?: "Alami & Kontekstual")
    val gayaBahasa: StateFlow<String> = _gayaBahasa.asStateFlow()

    private val _formatKeluaran = MutableStateFlow(prefs.getString("format_keluaran", "Per Paragraf") ?: "Per Paragraf")
    val formatKeluaran: StateFlow<String> = _formatKeluaran.asStateFlow()

    private val _furiganaEnabled = MutableStateFlow(prefs.getBoolean("furigana_enabled", true))
    val furiganaEnabled: StateFlow<Boolean> = _furiganaEnabled.asStateFlow()

    // Active record file
    private var activeRecordFile: File? = null

    init {
        // Automatically select the last session or create one if none exist
        viewModelScope.launch {
            sessions.collect { sessionList ->
                if (_currentSessionId.value == null && sessionList.isNotEmpty()) {
                    _currentSessionId.value = sessionList.first().id
                }
            }
        }
    }

    fun setGayaBahasa(style: String) {
        _gayaBahasa.value = style
        prefs.edit().putString("gaya_bahasa", style).apply()
    }

    fun setFormatKeluaran(format: String) {
        _formatKeluaran.value = format
        prefs.edit().putString("format_keluaran", format).apply()
    }

    fun setFuriganaEnabled(enabled: Boolean) {
        _furiganaEnabled.value = enabled
        prefs.edit().putBoolean("furigana_enabled", enabled).apply()
    }

    fun selectSession(sessionId: Int) {
        _currentSessionId.value = sessionId
    }

    fun startNewSession(title: String = "Sesi Terjemahan") {
        viewModelScope.launch {
            val newId = repository.createSession(title)
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = sessions.value.firstOrNull { it.id != sessionId }?.id
            }
        }
    }

    fun setAttachment(uri: Uri?, mimeType: String?) {
        if (uri == null) {
            _attachmentUri.value = null
            _attachmentPath.value = null
            _attachmentMimeType.value = null
            return
        }

        viewModelScope.launch {
            val file = copyUriToCache(uri)
            if (file != null) {
                _attachmentUri.value = uri
                _attachmentPath.value = file.absolutePath
                _attachmentMimeType.value = mimeType
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "attach_${System.currentTimeMillis()}")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error copying uri to cache", e)
            null
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        activeRecordFile = audioRecorder.startRecording()
        if (activeRecordFile != null) {
            _isRecording.value = true
        }
    }

    fun stopRecording(onTranscribed: (String) -> Unit) {
        if (!_isRecording.value) return
        audioRecorder.stopRecording()
        _isRecording.value = false

        val file = activeRecordFile ?: return
        _isTranscribing.value = true

        viewModelScope.launch {
            try {
                val transcript = repository.transcribeAudio(file)
                onTranscribed(transcript)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Transcribe failed", e)
                onTranscribed("Error: ${e.localizedMessage}")
            } finally {
                _isTranscribing.value = false
                activeRecordFile = null
            }
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        audioRecorder.stopRecording()
        _isRecording.value = false
        activeRecordFile?.delete()
        activeRecordFile = null
    }

    fun sendMessage(text: String) {
        val sessionId = _currentSessionId.value ?: return
        if (text.isBlank() && _attachmentPath.value == null) return

        _isTranslating.value = true

        val promptText = text.trim()
        val mediaPath = _attachmentPath.value
        val mediaMime = _attachmentMimeType.value

        // Clear attachments from input
        _attachmentUri.value = null
        _attachmentPath.value = null
        _attachmentMimeType.value = null

        viewModelScope.launch {
            try {
                repository.translateMessage(
                    sessionId = sessionId,
                    userText = promptText,
                    mediaPath = mediaPath,
                    mediaMimeType = mediaMime,
                    style = _gayaBahasa.value,
                    outputFormat = _formatKeluaran.value
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending translation message", e)
            } finally {
                _isTranslating.value = false
            }
        }
    }
}

class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
