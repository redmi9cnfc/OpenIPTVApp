package com.kraftplay.openiptv.data

import android.content.Context
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.model.EpgSource
import com.kraftplay.openiptv.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream

class EpgRepository(
    private val context: Context,
    private val epgDao: EpgDao,
    private val client: OkHttpClient
) {

    suspend fun getCachedPrograms(): List<EpgProgram> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            epgDao.pruneWindow(now - 14L * 24L * 3600L * 1000L, now + 14L * 24L * 3600L * 1000L)
            epgDao.getAllValidPrograms(now)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchAndStoreEpg(
        source: EpgSource,
        onProgress: (Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OpenIPTV/5.2")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                val rawStream = body.byteStream()

                var bytesReadTotal = 0L
                val countingStream = object : java.io.FilterInputStream(rawStream) {
                    override fun read(): Int {
                        val b = super.read()
                        if (b != -1) {
                            bytesReadTotal++
                            onProgress(bytesReadTotal, contentLength)
                        }
                        return b
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val r = super.read(b, off, len)
                        if (r > 0) {
                            bytesReadTotal += r
                            onProgress(bytesReadTotal, contentLength)
                        }
                        return r
                    }
                }

                val bis = BufferedInputStream(countingStream, 64 * 1024)
                bis.mark(10)
                val b1 = bis.read()
                val b2 = bis.read()
                bis.reset()

                val isGzipped = (b1 == 0x1f && b2 == 0x8b)

                XmlTvParser.parseStreaming(bis, isGzipped) { batch ->
                    batch.chunked(300).forEach { chunk ->
                        runCatching { epgDao.insertAll(chunk) }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            epgDao.deleteAll()
            runCatching { EpgDatabase.getDatabase(context).compactStorage() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
