package io.legado.app.help.readaloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 朗读播放页要展示的那一份状态。
 *
 * # 为什么单独拉一个全局对象
 *
 * 播放页是**独立路由**（不在阅读页的组合树里），而真正的状态机 `ReadAloudControllerShared`
 * 的实例在各平台宿主里（Android 前台服务、iOS `IosReadAloudHost`…），路由层拿不到。
 * 让控制器在更新自己的 StateFlow 时顺手写一份到这里，播放页只订阅这里 —— 四端都不用为了
 * 一个页面去改自己的宿主持有方式；代价是同一份数据存了两处，所以**只在控制器里更新**，
 * 任何 UI 都不得反写。
 *
 * 只放展示所需：章节号/章节总数/章节标题/当前句/段落下标。播放/暂停、语速、定时这些
 * 行为仍走 `ReadAloudControls`（那里有平台的真实副作用：前台服务、音频焦点、媒体键）。
 */
data class ReadAloudUiSnapshot(
    val chapterIndex: Int = -1,
    /** 章节总数；0 = 未知（书还没装载）。 */
    val chapterCount: Int = 0,
    /** 章节标题；空 = 该端拿不到标题，UI 用「第 N 章」兜底。 */
    val chapterTitle: String = "",
    /** 当前正在念的这一段（原文，可能被 notReadAloudRegex 过滤前的文本）。 */
    val currentText: String = "",
    /** 当前段在队列里的下标；-1 = 没在念。 */
    val paragraphIndex: Int = -1,
) {
    /** 章节进度 0f–1f；章节数未知时为 0。 */
    val chapterProgress: Float
        get() = if (chapterCount <= 0) 0f else {
            ((chapterIndex + 1).toFloat() / chapterCount).coerceIn(0f, 1f)
        }

    /** 展示用章节名：有标题用标题，没有就退化成「第 N 章」。 */
    val displayTitle: String
        get() = chapterTitle.ifBlank {
            if (chapterIndex >= 0) "第 ${chapterIndex + 1} 章" else ""
        }
}

object ReadAloudUiState {

    private val _snapshot = MutableStateFlow(ReadAloudUiSnapshot())
    val snapshot: StateFlow<ReadAloudUiSnapshot> = _snapshot.asStateFlow()

    /** 换章：章节号/总数/标题一起换，当前句同时清空（旧章的最后一句不能留在新章标题下）。 */
    fun publishChapter(chapterIndex: Int, chapterCount: Int, chapterTitle: String) {
        _snapshot.value = _snapshot.value.copy(
            chapterIndex = chapterIndex,
            chapterCount = chapterCount,
            chapterTitle = chapterTitle,
            currentText = "",
            paragraphIndex = -1,
        )
    }

    /** 开始念某一段。 */
    fun publishSentence(text: String, paragraphIndex: Int) {
        _snapshot.value = _snapshot.value.copy(
            currentText = text,
            paragraphIndex = paragraphIndex,
        )
    }

    /** 停止朗读：清空章节与当前句，播放页据此回到「未在朗读」态。 */
    fun clear() {
        _snapshot.value = ReadAloudUiSnapshot()
    }
}
