package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class ChatRepository(
    private val context: Context,
    private val chatDao: ChatDao
) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: Int): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun createSession(title: String): Int {
        return withContext(Dispatchers.IO) {
            chatDao.insertSession(ChatSession(title = title)).toInt()
        }
    }

    suspend fun deleteSession(sessionId: Int) {
        withContext(Dispatchers.IO) {
            chatDao.deleteMessagesForSession(sessionId)
            val session = ChatSession(id = sessionId, title = "")
            chatDao.deleteSession(session)
        }
    }

    suspend fun insertMessage(message: ChatMessage) {
        withContext(Dispatchers.IO) {
            chatDao.insertMessage(message)
        }
    }

    suspend fun translateMessage(
        sessionId: Int,
        userText: String,
        mediaPath: String? = null,
        mediaMimeType: String? = null,
        style: String = "Alami & Kontekstual",
        outputFormat: String = "Per Paragraf"
    ): ChatMessage = withContext(Dispatchers.IO) {
        // Save the user's message first
        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = "user",
            text = userText,
            mediaPath = mediaPath,
            mediaMimeType = mediaMimeType
        )
        chatDao.insertMessage(userMessage)

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val errorMsg = "API Key is missing or default. Please configure GEMINI_API_KEY in the Secrets panel in AI Studio."
            val errorResponse = ChatMessage(
                sessionId = sessionId,
                role = "model",
                text = "Error: $errorMsg",
                indonesian = errorMsg
            )
            chatDao.insertMessage(errorResponse)
            return@withContext errorResponse
        }

        // Build Gemini Request Parts
        val parts = mutableListOf<Part>()

        // Add media if exists
        if (mediaPath != null && mediaMimeType != null) {
            try {
                val mediaFile = File(mediaPath)
                if (mediaFile.exists()) {
                    val bytes = mediaFile.readBytes()
                    val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    parts.add(Part(inlineData = InlineData(mimeType = mediaMimeType, data = base64Data)))
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Error reading media file for base64", e)
            }
        }

        // Add text prompt
        val promptText = if (userText.isBlank() && mediaPath != null) {
            "Terjemahkan konten dari file media yang terlampir sesuai instruksi sistem."
        } else {
            userText
        }
        parts.add(Part(text = promptText))

        // Custom System Instruction for translation context
        val systemInstructionText = """
            Anda adalah AI Penerjemah Dua Arah (Jepang ↔ Indonesia) tingkat ahli. Anda beroperasi sebagai mesin utama untuk aplikasi chat dengan antarmuka (UI) mirip ChatGPT, dan memiliki kemampuan memproses input multi-modal tanpa batas (teks ketikan, transkripsi suara, foto, tangkapan layar, dokumen, hingga video).

            TUGAS UTAMA: Setiap kali menerima input dari pengguna, Anda harus LANGSUNG memberikan hasil terjemahan tanpa basa-basi, tanpa sapaan awal, dan tanpa penutup. Pahami dan sesuaikan segala jenis konteks (kasual, slang internet, anime/manga, keigo/bisnis, hingga teknis). Terjemahan harus terdengar sangat alami, luwes, dan tidak kaku.

            PENGATURAN SAAT INI:
            - Gaya Bahasa: $style
            - Format Keluaran: $outputFormat

            FITUR DAN PERINTAH KHUSUS APLIKASI (SYSTEM OVERRIDES):
            1. FITUR RIWAYAT (HISTORY SAFEGUARD): Format keluaran Anda tidak boleh berubah. Setiap baris hasil akhir harus murni Markdown agar parser aplikasi mudah mengekstrak data dari respons Anda secara permanen.
            2. KUSTOMISASI GAYA BAHASA:
               - Formal (Default): Bahasa Indonesia baku dan Jepang standar (Teineigo).
               - Informal/Kasual-Slang: Gunakan bahasa gaul internet (Wakamono kotoba), partikel anime (da, ze, zo), dan bahasa Indonesia santai (gue, lu, dll).
               - Keigo (Polite/Bisnis): Gunakan Sonkeigo/Kenjougo untuk Jepang dan tata bahasa sangat sopan untuk Indonesia.
            3. KAMUS BAWAAN (DICTIONARY LOOKUP): Jika sistem mengirimkan highlight teks dengan tag [KAMUS] <kata>, JANGAN terjemahkan seperti biasa. Beralih ke Mode Kamus dan berikan:
               - Arti kata (Jepang ↔ Indonesia).
               - Penjelasan nuansa/cara penggunaannya.
               - 2-3 Contoh kalimat (dengan Jepang, Romaji, Indonesia).
            4. JENDELA MENGAMBANG (FLOATING WINDOW MODE): Jika pengguna mengaktifkan mode ini (misal via tag [MODE: FLOAT]), ubah format menjadi mode padat dalam satu baris per kalimat, tanpa spasi vertikal ekstra (tanpa enter berlebih):
               `[Jepang] | *[Romaji]* | [Indonesia]`
            5. DUKUNGAN TEMA UI (MODE GELAP & TERANG): WAJIB memastikan seluruh format teks dan tabel yang Anda hasilkan bersifat theme-agnostic (netral). DILARANG KERAS menggunakan hard-coded warna HTML (seperti <font color="black"> atau <span style="...">). Gunakan HANYA format murni Markdown standar (bold, italic, blockquote).
               Adaptasi Dinamis: Jika sistem mengirimkan tag [TEMA: GELAP] gunakan gaya visual "nyaman", ikon malam (🌙/✨). Untuk [TEMA: TERANG], gunakan ikon cerah (☀️/☕) atau pertahankan format minimalis bawaan.
            6. PENCEGAHAN PENGHAPUSAN KONTEKS/KODE (APPEND ONLY): DILARANG KERAS menghapus, mengurangi, atau menimpa informasi sebelumnya jika diminta menambah info/kode.
            7. TAMPILAN OBROLAN BAWAAN (DEFAULT CHAT UI): Respons dengan visual standar ChatGPT: bersih, langsung intinya, Markdown penuh, tanpa basa-basi.
            8. STABILITAS, KECEPATAN & KOREKSI OTOMATIS: Jika input mengandung susunan kode, HTML, atau sintaks Markdown rusak/eror, WAJIB diperbaiki otomatis sehingga bisa dieksekusi/ditampilkan.

            FORMAT TAMPILAN WAJIB (URUTAN MUTLAK) KECUALI DALAM MODE KAMUS ATAU FLOAT:
            Anda WAJIB memformat hasil dengan header markdown berikut ini agar aplikasi dapat memisahkannya dengan rapi:

            [Jepang]
            (Teks Jepang asli atau terjemahan Kanji/Kana. Berikan anotasi furigana di atas setiap Kanji menggunakan format `[Kanji]{furigana}` atau `[Kanji Kanji]{furigana furigana}`. CONTOH: `[今日]{きょう}は[良]{よ}い[天気]{てんき}ですね。` atau `[私]{わたし}は[日本語]{にほんご}を[勉強]{べんきょう}します。`. Anotasikan seluruh Kanji agar pembaca dapat belajar membacanya!)

            [Romaji]
            *(Teks Romaji dalam huruf miring/italic)*

            [Indonesia]
            (Hasil terjemahan bahasa Indonesia yang sangat alami, luwes, dan sesuai konteks.)

            Aturan Penting: Meskipun pengguna memasukkan input dalam bahasa Indonesia, Anda TETAP harus mematuhi urutan di atas: Bahasa Jepang di atas, Romaji di tengah, dan Bahasa Indonesia di bawah. Jika inputnya adalah lirik lagu, berikan terjemahan lirik yang puitis dan mempertahankan makna mendalam di bagian bahasa Indonesia.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
            generationConfig = GenerationConfig(temperature = 0.4f)
        )

        try {
            // Using gemini-3.5-flash as the active model for fast and high quality translation
            val response = RetrofitClient.api.generateContent("gemini-3.5-flash", apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Error: Tidak ada respons dari model."

            val (jepang, romaji, indonesia) = parseTranslation(responseText)

            val modelMessage = ChatMessage(
                sessionId = sessionId,
                role = "model",
                text = responseText,
                japanese = jepang,
                romaji = romaji,
                indonesian = indonesia
            )
            chatDao.insertMessage(modelMessage)
            return@withContext modelMessage
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error calling Gemini API", e)
            val errorResponse = ChatMessage(
                sessionId = sessionId,
                role = "model",
                text = "Error: ${e.localizedMessage ?: "Terjadi kesalahan saat menghubungi server."}",
                indonesian = "Terjadi kesalahan koneksi atau konfigurasi."
            )
            chatDao.insertMessage(errorResponse)
            return@withContext errorResponse
        }
    }

    /**
     * Custom robust parsing for the strict three-section output.
     */
    private fun parseTranslation(text: String): Triple<String, String, String> {
        var jepang = ""
        var romaji = ""
        var indonesia = ""

        val lines = text.lines()
        var currentSection = ""
        val jepangLines = mutableListOf<String>()
        val romajiLines = mutableListOf<String>()
        val indonesiaLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            if (lower.contains("[jepang]") || (lower.contains("jepang") && trimmed.startsWith("[") && trimmed.endsWith("]")) || (lower.contains("jepang") && trimmed.startsWith("**"))) {
                currentSection = "jepang"
                continue
            } else if (lower.contains("[romaji]") || (lower.contains("romaji") && trimmed.startsWith("[") && trimmed.endsWith("]")) || (lower.contains("romaji") && trimmed.startsWith("**"))) {
                currentSection = "romaji"
                continue
            } else if (lower.contains("[indonesia]") || (lower.contains("indonesia") && trimmed.startsWith("[") && trimmed.endsWith("]")) || (lower.contains("indonesia") && trimmed.startsWith("**"))) {
                currentSection = "indonesia"
                continue
            }

            when (currentSection) {
                "jepang" -> jepangLines.add(line)
                "romaji" -> romajiLines.add(line)
                "indonesia" -> indonesiaLines.add(line)
            }
        }

        jepang = jepangLines.joinToString("\n").trim()
        romaji = romajiLines.joinToString("\n").trim()
        indonesia = indonesiaLines.joinToString("\n").trim()

        // Fallback if parsing failed completely (didn't respect sections)
        if (jepang.isEmpty() && romaji.isEmpty() && indonesia.isEmpty()) {
            // Let's check if there are sections by double newlines or similar
            // If completely flat, return all as Japanese and let it be.
            return Triple(text, "", "")
        }

        return Triple(jepang, romaji, indonesia)
    }

    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: API Key is missing. Please configure it in the Secrets panel in AI Studio."
        }

        try {
            val bytes = audioFile.readBytes()
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val parts = listOf(
                Part(inlineData = InlineData(mimeType = "audio/mp4", data = base64Data)),
                Part(text = "Transkripsikan rekaman suara ini secara tepat tanpa komentar tambahan, pembuka, atau penutup. Kembalikan transkripnya dalam teks murni.")
            )

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = parts)),
                generationConfig = GenerationConfig(temperature = 0.2f)
            )

            val response = RetrofitClient.api.generateContent("gemini-3.5-flash", apiKey, request)
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Error: Tidak ada hasil transkripsi."
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error transcribing audio", e)
            return@withContext "Error transcribing audio: ${e.localizedMessage}"
        }
    }
}
