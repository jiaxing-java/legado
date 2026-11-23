package io.legado.app.help.tts

import io.legado.app.constant.AppPattern
import kotlinx.atomicfu.atomic

/**
 * 朗读播放队列的纯状态机: 段落列表 + 位置推进计算,从 ReadAloud 服务类平移。
 * 页跟踪(pageIndex/TextChapter)与章节切换仍由 Service 驱动,这里只做段级纯计算。
 *
 * 线程: 引擎回调线程(系统 TTS 的 binder 线程 / 播放器回调线程)与调用方线程都会读推进状态。
 * 四个字段互相定义 (readAloudNumber 按前一段长度 + 1 个换行累加), 分字段读写会取到跨版本的
 * 混合值, 故整份存为不可变 [Snapshot] 并原子发布: 读方一次 [snapshot] 取值 (无锁、无分配),
 * 写方在 CAS 环内由当次读到的快照生成新快照。每个推进方法返回自己发布的那份快照, 调用方
 * 无需二次读队列 (二次读会跨版本)。
 */
class ReadAloudQueue {

    /**
     * 队列快照(不可变)。
     *
     * @param contentList 待朗读段落列表(按 \n 分段并过滤空段; 句子模式下段内再按句读切分)
     * @param nowSpeak 当前朗读段落下标
     * @param readAloudNumber 已朗读进度(章节内字符位置)
     * @param paragraphStartPos 当前段落内的起始偏移
     * @param separators 每段之后到下一段之间的原始字符数(换行/空白)。
     *   句子模式切出的同位段之间是 0, 段落之间是真实换行数(通常 1)。
     *   列表为空 = 全部按 1 计(旧行为, 也兼容所有存量调用点)。
     */
    data class Snapshot(
        val contentList: List<String> = emptyList(),
        val nowSpeak: Int = 0,
        val readAloudNumber: Int = 0,
        val paragraphStartPos: Int = 0,
        val separators: List<Int> = emptyList(),
    ) {

        /** 当前段文本; 空表或下标越出段表时为 null (此刻章内已无可朗读段)。 */
        fun currentParagraph(): String? = contentList.getOrNull(nowSpeak)

        /** 第 [index] 段之后的原始分隔符长度(缺省按 1)。 */
        fun separatorAt(index: Int): Int = separators.getOrNull(index) ?: 1
    }

    private val state = atomic(Snapshot())

    /** 零锁读: 一次取值拿到自洽的段落表与游标, 不参与任何锁或 CAS。 */
    val snapshot: Snapshot get() = state.value

    /**
     * 整份发布新队列。
     *
     * 下标钳制在此收口: nowSpeak 落在段表范围内 (空表为 0), paragraphStartPos 落在当前段
     * 长度内 —— 起点可能落在章末, 由各调用点分别补判据会漏。
     */
    fun reset(
        contentList: List<String>,
        nowSpeak: Int = 0,
        readAloudNumber: Int = 0,
        paragraphStartPos: Int = 0,
        separators: List<Int> = emptyList(),
    ) {
        val index = nowSpeak.coerceIn(0, contentList.lastIndex.coerceAtLeast(0))
        state.value = Snapshot(
            contentList = contentList,
            nowSpeak = index,
            separators = separators,
            readAloudNumber = readAloudNumber.coerceAtLeast(0),
            paragraphStartPos = paragraphStartPos.coerceIn(
                0,
                contentList.getOrNull(index)?.length ?: 0,
            ),
        )
    }

    /**
     * 推进一段;已是最后一段时不动 nowSpeak,返回 false 由调用方切章。
     */
    fun stepNextOrEnd(): Boolean = publishResult { cur ->
        val next = cur.advance()
        next to (next.nowSpeak != cur.nowSpeak)
    }

    /**
     * 找当前段起的第一个可朗读段 (跳过空白/纯标点段) 并整份发布, 返回发布后的快照。
     *
     * 章内已无可朗读段时返回快照的 nowSpeak 越过段表末尾 (currentParagraph 为 null),
     * 由调用方切章。
     */
    fun seekSpeakable(): Snapshot = publish { cur ->
        var next = cur
        while (next.currentParagraph()?.matches(AppPattern.notReadAloudRegex) == true) {
            next = next.skipOne()
        }
        next
    }

    /**
     * 回退到上一个非空白段并整份发布。
     *
     * @return 是否发生回退; false = 已在首段 (调用方按跨章处理)
     */
    fun retreatToPrevSpeakable(): Boolean = publishResult { cur ->
        val next = cur.retreat()
        next to (next !== cur)
    }

    // region 纯快照变换: 入参出参都是快照, CAS 失败重跑结果一致

    /**
     * 推进一段: 累加本段已读长度后指针前进; 已在末段只累加长度、指针不动 (由调用方切章)。
     * 下标越出段表时不动 (空表/已越过末尾)。
     */
    private fun Snapshot.advance(): Snapshot {
        if (nowSpeak !in contentList.indices) return this
        return copy(
            nowSpeak = if (nowSpeak < contentList.lastIndex) nowSpeak + 1 else nowSpeak,
            readAloudNumber = readAloudNumber + paragraphStep(),
            paragraphStartPos = 0,
        )
    }

    /** 跳过当前段 (可越过段表末尾), 只用于扫描可朗读段。 */
    private fun Snapshot.skipOne(): Snapshot = copy(
        nowSpeak = nowSpeak + 1,
        readAloudNumber = readAloudNumber + paragraphStep(),
        paragraphStartPos = 0,
    )

    /** 回退一格, 连续跳过非空白段; 停在首段或第一个可朗读段。 */
    private fun Snapshot.retreat(): Snapshot {
        if (nowSpeak !in contentList.indices || nowSpeak <= 0) return this
        var next = this
        do {
            val target = next.nowSpeak - 1
            next = next.copy(
                nowSpeak = target,
                readAloudNumber = next.readAloudNumber -
                    next.contentList[target].length - next.separatorAt(target) -
                    next.paragraphStartPos,
                paragraphStartPos = 0,
            )
        } while (next.nowSpeak > 0 && next.contentList[next.nowSpeak].matches(AppPattern.notReadAloudRegex))
        return next
    }

    /** 本段推进要累加的字符数 (段长 + 1 个换行 - 段内已读偏移)。 */
    private fun Snapshot.paragraphStep(): Int =
        (currentParagraph()?.length ?: 0) + separatorAt(nowSpeak) - paragraphStartPos

    // endregion

    /**
     * CAS 环: 由当次读到的快照生成新快照并原子发布, 返回真正发布的那份。
     * [transform] 可能因 CAS 失败被重跑, 必须是纯函数; 返回同一实例表示本次无需变更。
     */
    private inline fun publish(transform: (Snapshot) -> Snapshot): Snapshot {
        while (true) {
            val cur = state.value
            val next = transform(cur)
            if (next === cur) return cur
            if (state.compareAndSet(cur, next)) return next
        }
    }

    /** 同 [publish], 额外返回一个取自当次计算的结果。 */
    private inline fun publishResult(transform: (Snapshot) -> Pair<Snapshot, Boolean>): Boolean {
        while (true) {
            val cur = state.value
            val (next, result) = transform(cur)
            if (next === cur) return result
            if (state.compareAndSet(cur, next)) return result
        }
    }

    companion object {

        /** 分段模式：按段落（\n）切。 */
        const val SPLIT_MODE_PARAGRAPH = 0

        /** 分段模式：段落内再按句读切。 */
        const val SPLIT_MODE_SENTENCE = 1

        /** 默认句读结束符（切在符号之后，符号留在本段末尾）。 */
        private const val DEFAULT_SENTENCE_ENDERS = "。！？!?…；;"

        /** 句读后仍属于本段的收尾符号（引号/括号），避免把右引号甩到下一段开头。 */
        private const val TRAILING_MARKS = "”’』」）)】》\"'"

        /**
         * 一个待朗读片段及其在原文里的位置。
         *
         * @param text 片段文本（原文切片，未做任何改写）
         * @param start 片段在章节朗读文本中的起始字符下标
         * @param gap 片段之后到下一片段之间的原始字符数；最后一段按 1 计（与旧行为一致）
         */
        data class Segment(val text: String, val start: Int, var gap: Int)

        /**
         * 按段落/句读把章节文本切成片段，并给出每个片段的真实位置。
         *
         * 位置是**原文下标**，所以朗读进度（[Snapshot.readAloudNumber]）继续可以和阅读器里的
         * 字符位置对齐；句子模式只是把一段拆成多段，`text` 仍是原文切片，不做「插入换行」
         * 这类改写 —— 改写会让续读位置逐句漂移。
         *
         * 旧行为等价性：`mode = SPLIT_MODE_PARAGRAPH` 且无自定义符号时，片段文本与
         * `text.split("\n").filter { it.isNotEmpty() }` 逐字相同。
         */
        fun splitSegments(
            text: String,
            mode: Int = SPLIT_MODE_PARAGRAPH,
            customSymbols: String? = null,
        ): List<Segment> {
            if (text.isEmpty()) return emptyList()
            val sentenceMode = mode == SPLIT_MODE_SENTENCE
            val enders = if (customSymbols.isNullOrEmpty()) {
                DEFAULT_SENTENCE_ENDERS
            } else {
                DEFAULT_SENTENCE_ENDERS + customSymbols
            }
            val result = ArrayList<Segment>()
            var lineStart = 0
            var i = 0
            while (i <= text.length) {
                val atEnd = i == text.length
                if (atEnd || text[i] == '\n') {
                    if (i > lineStart) {
                        if (sentenceMode) {
                            appendSentenceSegments(text, lineStart, i, enders, result)
                        } else {
                            result.add(Segment(text.substring(lineStart, i), lineStart, 1))
                        }
                    }
                    lineStart = i + 1
                }
                i++
            }
            if (result.isEmpty()) return result
            // gap 用下一段的真实起点回算：多空行、句子切分都能得到正确的分隔长度。
            for (index in 0 until result.lastIndex) {
                val cur = result[index]
                cur.gap = result[index + 1].start - (cur.start + cur.text.length)
            }
            return result
        }

        /** 把 `[from, to)` 这段按句读切开，追加到 [out]（gap 先占位，最后统一回算）。 */
        private fun appendSentenceSegments(
            text: String,
            from: Int,
            to: Int,
            enders: String,
            out: MutableList<Segment>,
        ) {
            var segStart = from
            var i = from
            while (i < to) {
                if (text[i] in enders) {
                    var end = i + 1
                    // 连续结束符（“什么？！”）与右引号一起吃进来
                    while (end < to && (text[end] in enders || text[end] in TRAILING_MARKS)) end++
                    out.add(Segment(text.substring(segStart, end), segStart, 0))
                    segStart = end
                    i = end
                } else {
                    i++
                }
            }
            if (segStart < to) {
                out.add(Segment(text.substring(segStart, to), segStart, 0))
            }
        }

        /**
         * 从设置里取当前分段参数，供朗读链路直接调用。
         *
         * 放在这里而不是塞进控制器构造参数：分段是"读的时候才知道"的偏好，
         * 控制器不持有配置对象，多传一个参数只会让所有调用点都改一遍。
         */
        fun currentSplitMode(): Int = io.legado.app.help.config.PreferenceProviders.get()
            .getInt(io.legado.app.constant.PreferKey.readAloudContentSplitMode, SPLIT_MODE_PARAGRAPH)

        fun currentSplitSymbols(): String? = io.legado.app.help.config.PreferenceProviders.get()
            .getString(io.legado.app.constant.PreferKey.readAloudContentSplitSymbols, "")

        /** 按段落(\n)切分朗读文本并过滤空段 */
        fun splitParagraphs(text: String): List<String> {
            return text.split("\n").filter { it.isNotEmpty() }
        }

        /** [splitParagraphs] 的惰性版本,预下载 take(n) 场景用 */
        fun splitParagraphsSequence(text: String): Sequence<String> {
            return text.splitToSequence("\n").filter { it.isNotEmpty() }
        }
    }
}
