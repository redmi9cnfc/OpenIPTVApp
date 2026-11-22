package com.kraftplay.openiptv.util

import java.io.File

fun cacheSizeBytes(dir: File): Long =
    if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** User-initiated cache maintenance. Deletes only cache contents, never the cache directory itself. */
fun clearCacheFiles(dir: File): Long {
    val before = cacheSizeBytes(dir)
    dir.listFiles()?.forEach { it.deleteRecursively() }
    return before - cacheSizeBytes(dir)
}
