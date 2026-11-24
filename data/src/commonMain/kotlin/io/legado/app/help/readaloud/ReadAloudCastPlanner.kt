package io.legado.app.help.readaloud

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.PreferenceProviders
import io.legado.app.help.tts.ReadAloudQueue
import kotlinx.coroutines.runBlocking
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * 段级音色解析的同步入口。
 *
 * 朗读链路（[io.legado.app.help.tts.ReadAloudHostShared]、Android
 * `BaseReadAloudService`）在**同步**函数里构建章节计划，而角色/音色都在数据库里。这里提供一个
 * 带内存缓存的同步门面：命中缓存直接返回，未命中时同步查一次库（本地 SQLite，毫秒级），
 * 并把结果缓存到「同一章同一份正文」失效为止。
 *
 * # 为什么不用异步预取
 *
 * 预取要在「本章开始播」之前完成，而进入朗读的路径有四条（点播放、锁屏继续、媒体键、
 * 定时唤醒），每条都要记得先预取；漏一条就退化成全程单音色，且很难从现象上判断。
 * 同步查库只有换章一次开销，代价可控，行为对所有入口一致。
 *
 * # 缓存失效
 *
 * key 含正文长度与哈希：重新拉取正文、切换书源、改分段规则都会换 key。改角色/分配/规则
 * 之后需要立即生效的，由写入方调 [invalidate]（设置页保存时调用）。
 */
object ReadAloudCastPlanner {

    /** 缓存容量：够覆盖「当前章 + 前后各一章 + 刚听过的几章」，再多也只是白占内存。 */
    private const val MAX_CACHE_ENTRIES = 12

    private val lock = SynchronizedObject()

    /**
     * 缓存值：分段 + 该段音色 + 台词序号。
     *
     * 保留分段本身（不只是音色表）是为了让「手动分配」页能列出用户刚听过的那一章 ——
     * 章节正文在 UI 侧不一定拿得到，而朗读链路已经切过一次段了，直接复用这份坐标最省事。
     */
    private val cache = object : LinkedHashMap<String, List<CastSegment>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<CastSegment>>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    /** 上一次解析用的开关状态；总开关或旁白音色变了就整体作废。 */
    private var lastMultiRole: Boolean? = null
    private var lastNarratorVoiceId: String? = null

    /**
     * 解析本章每段的音色。
     *
     * @return 与 [segments] 等长的表；未开启多角色、书里没建角色、解析失败都返回空表，
     *   调用方按「全部默认音色」处理 —— 失败绝不阻断朗读。
     */
    fun voicesFor(
        bookUrl: String,
        chapterIndex: Int,
        chapterText: String,
        segments: List<ReadAloudQueue.Segment>,
    ): List<SpeechVoiceRef?> = planFor(bookUrl, chapterIndex, chapterText, segments)
        .map { it.voice }

    /**
     * 同 [voicesFor]，但返回带台词序号的分段表（「手动分配」页与播放共用同一份解析）。
     */
    fun planFor(
        bookUrl: String,
        chapterIndex: Int,
        chapterText: String,
        segments: List<ReadAloudQueue.Segment>,
    ): List<CastSegment> {
        if (segments.isEmpty() || bookUrl.isEmpty()) return emptyList()
        val prefs = runCatching { PreferenceProviders.get() }.getOrNull() ?: return emptyList()
        val multiRole = runCatching {
            prefs.getBoolean(PreferKey.multiRoleCast, false)
        }.getOrDefault(false)
        if (!multiRole) return emptyList()
        val narratorVoiceId = runCatching {
            prefs.getString(PreferKey.ttsVoice, "")
        }.getOrDefault("")

        val key = buildKey(bookUrl, chapterIndex, chapterText)
        synchronized(lock) {
            if (lastMultiRole != multiRole || lastNarratorVoiceId != narratorVoiceId) {
                cache.clear()
                lastMultiRole = multiRole
                lastNarratorVoiceId = narratorVoiceId
            }
            cache[key]?.let { return it }
        }

        val resolved = runCatching {
            runBlocking {
                ReadAloudCastRepository.get().resolveChapterSegments(
                    bookUrl = bookUrl,
                    chapterIndex = chapterIndex,
                    text = chapterText,
                    segments = segments,
                    multiRoleEnabled = true,
                    narratorVoiceId = narratorVoiceId,
                )
            }
        }.getOrElse {
            // 查库失败按「没有多角色」继续播：朗读比换声重要
            emptyList()
        }
        synchronized(lock) { cache[key] = resolved }
        return resolved
    }

    /**
     * 最近一次解析出的本章分段（含台词序号与当前音色）。
     *
     * 只命中缓存 —— 不为了给 UI 看一眼而同步查库；没有缓存说明这一章还没在本次进程里播过。
     */
    fun cachedPlan(bookUrl: String, chapterIndex: Int): List<CastSegment>? {
        if (bookUrl.isEmpty()) return null
        val prefix = "$bookUrl|$chapterIndex|"
        synchronized(lock) {
            return cache.entries.firstOrNull { it.key.startsWith(prefix) }?.value
        }
    }

    /** 角色/分配/规则/音色改动后调用；下一次建计划会重新解析。 */
    fun invalidate() {
        synchronized(lock) {
            cache.clear()
            lastMultiRole = null
            lastNarratorVoiceId = null
        }
    }

    private fun buildKey(bookUrl: String, chapterIndex: Int, text: String): String =
        "$bookUrl|$chapterIndex|${text.length}|${text.hashCode()}"
}
