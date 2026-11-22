package com.kraftplay.openiptv.util

import com.kraftplay.openiptv.model.IptvChannel

/** Recent channels with actual viewing time, newest first, bounded for a horizontal live row. */
fun continueWatchingChannels(
    recent: List<IptvChannel>,
    viewingTimes: Map<String, Long>,
    limit: Int = 5
): List<IptvChannel> = recent.filter { (viewingTimes[it.url] ?: 0L) > 0L }.take(limit)
