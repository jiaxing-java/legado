package io.legado.app.help.readaloud

import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.help.tts.ReadAloudQueue

/**
 * 一个朗读段该用哪个音色。
 *
 * 只带播放链路真正要用的最小集合：引擎侧只认 [speakerId]，UI 只认 [characterName]，
 * 存库/回溯只认 [voiceId]。角色被删掉后 [voiceId]/[speakerId] 仍能单独生效（快照语义），
 * 不会因为找不到角色而退回默认音色。
 */
data class SpeechVoiceRef(
    /** read_aloud_voices.id；空 = 未绑定，按引擎默认音色播。 */
    val voiceId: String = "",
    /** 引擎侧原始音色标识（系统 TTS 的 voice.name / HTTP TTS 的发言人参数）。 */
    val speakerId: String = "",
    val characterId: String? = null,
    val characterName: String = "",
    /** `narrator` / `character` / `bgm`；[ROLE_BGM] 段不朗读，改放音频。 */
    val roleType: String = ROLE_NARRATOR,
    /** 情绪标记，交给 HTTP TTS 的 emotion 占位符；系统 TTS 忽略。 */
    val emotion: String = "",
    /** 变声器预设名，空 = 不变声。 */
    val voiceEffect: String = "",
    /** 0f–1f：命中分配表 = 1，正则命中 = 0.8，说话人推断 = 0.6，旁白兜底 = 0。 */
    val confidence: Float = 0f,
) {
    val isNarrator: Boolean get() = roleType == ROLE_NARRATOR

    /**
     * 音频缓存文件名里的音色维度（写进 hash，不写进路径）。
     *
     * 三样都算：`voiceId`（哪条音色配置）、`speakerId`（引擎侧音色）、`emotion`（影响合成结果）。
     * `voiceEffect` 不进 —— 变声是拿到音频之后的本地处理，不影响合成产物。
     * 全空时返回空串，调用方据此走旧 hash，单音色用户的存量缓存不失效。
     */
    val cacheTag: String
        get() {
            if (voiceId.isEmpty() && speakerId.isEmpty() && emotion.isEmpty()) return ""
            return "$voiceId|$speakerId|$emotion"
        }

    companion object {
        const val ROLE_NARRATOR = "narrator"
        const val ROLE_CHARACTER = "character"
        const val ROLE_BGM = "bgm"
    }
}

/**
 * 正文里的一个引号对。
 *
 * [ordinal] 是**开引号**在全文中的序号（从 0 起），与
 * [ChapterRoleAssignment.quoteOrdinal] 用的是同一套计数：只要重新拉取的正文引号结构不变，
 * 已有分配就仍然有效。
 */
data class QuoteSpan(
    /** 开引号在章内文本中的下标。 */
    val start: Int,
    /** 闭引号之后的下标（不含）。找不到配对时 = 段尾。 */
    val end: Int,
    val ordinal: Int,
)

/**
 * 一段正文的解析结果：文本、台词序号（旁白段为 null）、该段音色。
 *
 * 给「手动分配」页用：用户看到的是「第 3 句：'你来了。'— 张三」，点一下换人，
 * 落库用 [quoteOrdinal]。
 */
data class CastSegment(
    val text: String,
    /** 本段第一个开引号的章内序号；null = 本段没有引号（旁白段）。 */
    val quoteOrdinal: Int?,
    val voice: SpeechVoiceRef?,
)

/**
 * 引号扫描：把正文切成「旁白 / 对话」两类区间。
 *
 * 支持中文成对引号（“” 「」 『』）与半角双引号（""）。半角引号没有方向，按奇偶配对；
 * 中文引号允许嵌套一层（「他说“好”」），内层不单独计序号 —— 分配锚点是"第几句台词"，
 * 嵌套引号属于同一句。
 *
 * 找不到配对的开引号按「延续到段尾」处理：宁可让这半句用角色音色读完，也不要把后半段
 * 退回旁白（听感上会像换人念错）。
 */
object SpeechTextScanner {

    private const val OPENERS = "“‘「『"
    private const val ASCII_QUOTE = '"'

    /** 与 [OPENERS] 一一对应的闭引号。 */
    private val CLOSERS = mapOf(
        '“' to '”',
        '‘' to '’',
        '「' to '」',
        '『' to '』',
        ASCII_QUOTE to ASCII_QUOTE,
    )

    /**
     * 扫描 [text] 里的引号对。
     *
     * @param text 章节朗读文本（与切段用的是同一份，位置可直接对齐）
     * @param firstOrdinal 本次扫描的起始序号；续扫一个章节的后半段时传前面已用掉的个数
     */
    fun scanQuotes(text: String, firstOrdinal: Int = 0): List<QuoteSpan> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<QuoteSpan>()
        var ordinal = firstOrdinal
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val closer = CLOSERS[ch]
            if (closer == null) {
                i++
                continue
            }
            // 半角引号：自身既是开也是闭，靠"当前是否在引用内"判断
            if (ch == ASCII_QUOTE) {
                val end = text.indexOf(ASCII_QUOTE, i + 1)
                if (end < 0) {
                    result.add(QuoteSpan(i, text.length, ordinal++))
                    break
                }
                result.add(QuoteSpan(i, end + 1, ordinal++))
                i = end + 1
                continue
            }
            // 中文引号：找第一个匹配的闭引号；嵌套的同种开引号跳过（按内层处理）
            var depth = 1
            var j = i + 1
            while (j < text.length) {
                val c = text[j]
                if (c == ch) depth++
                if (c == closer) {
                    depth--
                    if (depth == 0) break
                }
                j++
            }
            val end = if (j >= text.length) text.length else j + 1
            result.add(QuoteSpan(i, end, ordinal++))
            i = end
        }
        return result
    }

    /**
     * 取引号对覆盖的区间内的文本（不含引号本身）。
     *
     * 首尾各裁一个字符是引号；末字符不是闭引号（未配对）时不裁尾，避免吞掉正文。
     */
    fun innerText(text: String, span: QuoteSpan): String {
        val from = (span.start + 1).coerceAtMost(text.length)
        val last = span.end - 1
        val to = if (last > from && last <= text.length && CLOSERS[text[span.start]] == text[last]) {
            last
        } else {
            span.end.coerceAtMost(text.length)
        }
        return if (to <= from) "" else text.substring(from, to)
    }
}

/**
 * 章节朗读计划解析器：把「段落表」变成「每段该用谁的音色」。
 *
 * 纯函数，不碰数据库也不碰设置 —— 调用方把角色、音色目录、分配表、正则规则一次性喂进来，
 * 拿到与段落等长的音色表再落库或直接给播放队列。这样可以在 jvmTest 里逐条验证识别结果，
 * 不用起引擎。
 *
 * # 判定顺序（先命中先生效）
 *
 * 1. **手动分配** [ChapterRoleAssignment]：引号序号精确命中，置信度 1。
 * 2. **正则规则** [RegexCastRule]：同一段里只要 pattern 命中，就按规则的音色念这一段。
 *    规则按 [RegexCastRule.order] 依次尝试，第一条命中的生效（与列表顺序一致，用户可调）。
 * 3. **说话人推断**：对话段从「本段/上一段」里找 `某某说 / 某某道 / 某某问道`，名字与角色表
 *    完全相等（或与该角色的 poolLabel 组合名相等）才算命中，置信度 0.6。
 * 4. **旁白兜底**：以上都不中，用旁白音色。
 *
 * # 为什么不在这里做 AI 识别
 *
 * AI 识别要联网与 key（`RAL-CAST-04` 的 ai 档位），失败路径与缓存失效都复杂。这里只保证
 * 「不联网也能把一章分出人」；AI 档位以后作为第 2.5 步插进来即可，接口不变。
 */
class ChapterSpeechPlanResolver(
    private val characters: List<CastCharacter>,
    private val voicesById: Map<String, ReadAloudVoiceEntity>,
    private val assignments: List<ChapterRoleAssignment>,
    private val regexRules: List<RegexCastRule> = emptyList(),
    /** 旁白音色：未绑定角色时全部段都用它；空 = 交给引擎默认。 */
    private val narratorVoice: SpeechVoiceRef? = null,
) {

    private val characterById = characters.associateBy { it.id }
    private val assignmentByOrdinal = assignments.associateBy { it.quoteOrdinal }
    private val enabledRules = regexRules.filter { it.enabled }
        .sortedBy { it.order }

    /** 预编译的正则（`useRegex = false` 的规则走字面量包含，不编译）。 */
    private val compiledRules: List<Pair<RegexCastRule, Regex?>> = enabledRules.map { rule ->
        rule to if (rule.useRegex && rule.pattern.isNotBlank()) {
            runCatching { Regex(rule.pattern) }.getOrNull()
        } else {
            null
        }
    }

    /**
     * 解析一整章。
     *
     * @param text 章节朗读文本（切段的来源）
     * @param segments 切段结果，位置与 [text] 对齐
     * @return 与 [segments] 等长的音色表；元素为 null 表示该段按引擎默认音色播
     */
    fun resolve(text: String, segments: List<ReadAloudQueue.Segment>): List<SpeechVoiceRef?> =
        resolveSegments(text, segments).map { it.voice }

    /**
     * 同 [resolve]，但每段额外带上「这属于第几句台词」。
     *
     * 台词序号是「手动分配」页的锚点：UI 把这一串带序号的分段列出来，用户点某一句选角色，
     * 落库的 `quoteOrdinal` 与这里同源。只给 [resolve] 的返回值不够 —— 那只有音色，
     * UI 没法知道该写哪条分配。
     */
    fun resolveSegments(
        text: String,
        segments: List<ReadAloudQueue.Segment>,
    ): List<CastSegment> {
        if (segments.isEmpty()) return emptyList()
        // 引号序号必须按全文扫一遍再按位置归属：分段模式不同（段落/句子）不会改变序号。
        val quotes = SpeechTextScanner.scanQuotes(text)
        if (quotes.isEmpty() && assignmentByOrdinal.isEmpty() &&
            compiledRules.isEmpty() && narratorVoice == null
        ) {
            return segments.map { CastSegment(it.text, null, null) }
        }
        val result = ArrayList<CastSegment>(segments.size)
        var quoteCursor = 0
        var previousTailSpeaker: String? = null
        for (index in segments.indices) {
            val segment = segments[index]
            val segStart = segment.start
            val segEnd = segment.start + segment.text.length
            // 推进到这个段里第一个开引号；段首之前的引号属于上一段
            while (quoteCursor < quotes.size && quotes[quoteCursor].start < segStart) quoteCursor++
            val firstQuoteInSegment = quotes.getOrNull(quoteCursor)
                ?.takeIf { it.start < segEnd }

            val fromAssignment = firstQuoteInSegment?.let { quote ->
                assignmentByOrdinal[quote.ordinal]?.let { assignment ->
                    voiceOfAssignment(assignment)
                }
            }
            val voice = fromAssignment
                ?: matchRule(segment.text)
                ?: inferSpeaker(
                    segmentText = segment.text,
                    segmentGlobalStart = segStart,
                    previousTailSpeaker = previousTailSpeaker,
                    quoteInSegment = firstQuoteInSegment,
                )
                ?: narratorVoice

            result.add(CastSegment(segment.text, firstQuoteInSegment?.ordinal, voice))
            // 供下一段推断「某某说」落在段尾的情况（中文小说极常见：引号句后接「张三说。」）
            previousTailSpeaker = tailSpeakerOf(segment.text)
        }
        return result
    }

    /** 手动分配：角色还在就用角色当前绑定的音色，角色被删就用分配里的冗余字段。 */
    private fun voiceOfAssignment(assignment: ChapterRoleAssignment): SpeechVoiceRef {
        val character = characterById[assignment.characterId]
        val voiceId = character?.voiceId.orEmpty()
        val voice = voicesById[voiceId]
        return SpeechVoiceRef(
            voiceId = voiceId,
            speakerId = voice?.speakerId.orEmpty(),
            characterId = assignment.characterId,
            characterName = character?.name ?: assignment.characterName,
            roleType = SpeechVoiceRef.ROLE_CHARACTER,
            voiceEffect = assignment.voiceEffect.ifEmpty { character?.voiceEffect.orEmpty() },
            confidence = 1f,
        )
    }

    /** 正则规则：命中即用规则指定的音色；规则只选了池没选音色时不算命中（池内随机在别处做）。 */
    private fun matchRule(segmentText: String): SpeechVoiceRef? {
        if (segmentText.isEmpty()) return null
        for ((rule, regex) in compiledRules) {
            if (rule.poolKind != RegexCastRule.POOL_ROLE) continue
            if (rule.itemId.isBlank()) continue
            val hit = if (regex != null) {
                regex.containsMatchIn(segmentText)
            } else {
                rule.pattern.isNotEmpty() && segmentText.contains(rule.pattern)
            }
            if (!hit) continue
            val voice = voicesById[rule.itemId] ?: continue
            return SpeechVoiceRef(
                voiceId = voice.id,
                speakerId = voice.speakerId,
                characterName = rule.name,
                roleType = SpeechVoiceRef.ROLE_CHARACTER,
                voiceEffect = rule.voiceEffect,
                confidence = 0.8f,
            )
        }
        return null
    }

    /**
     * 说话人推断。
     *
     * 只在「这一段是对话」时推断：旁白段里出现「张三说」通常是在转述，按角色念会让旁白断成两人。
     * 名字来源两处：段内引号之前的引导语（「张三说：“…”」），以及上一段的段尾（「…。」张三说）。
     */
    private fun inferSpeaker(
        segmentText: String,
        segmentGlobalStart: Int,
        previousTailSpeaker: String?,
        quoteInSegment: QuoteSpan?,
    ): SpeechVoiceRef? {
        if (quoteInSegment == null) return null
        // 段文本是原文切片，引号位置是全文坐标：换算出引号在段内的位置，取其左侧引导语
        val relativeStart = (quoteInSegment.start - segmentGlobalStart).coerceIn(0, segmentText.length)
        val leadText = if (relativeStart > 0) segmentText.substring(0, relativeStart) else ""
        val name = speakerInLead(leadText) ?: previousTailSpeaker ?: return null
        val character = findCharacter(name) ?: return null
        val voice = voicesById[character.voiceId]
        return SpeechVoiceRef(
            voiceId = character.voiceId,
            speakerId = voice?.speakerId.orEmpty(),
            characterId = character.id,
            characterName = character.name,
            roleType = SpeechVoiceRef.ROLE_CHARACTER,
            voiceEffect = character.voiceEffect,
            confidence = 0.6f,
        )
    }

    /** `“……”，张三说。` 这类段尾引导语里的名字。 */
    private fun tailSpeakerOf(segmentText: String): String? {
        val tail = segmentText.takeLast(24)
        return speakerInLead(tail)
    }

    /**
     * 从一段引导语里取说话人名字。
     *
     * 只认「名字 + 说/道/问/答/喊/叫」这种最稳的形态；「他/她/他们」这类代词不推断 ——
     * 猜错会让整段换人，比不换更糟。修饰语可能夹在名字与动词之间（「张三冷冷地说道」），
     * 也可能在名字前（「对张三说」），故按「前缀或后缀命中角色名」双向匹配。
     */
    private fun speakerInLead(text: String): String? {
        if (text.isEmpty()) return null
        val match = SPEAKER_PATTERN.find(text) ?: return null
        val raw = match.groupValues[1].trim()
        if (raw.isEmpty() || raw in PRONOUNS) return null
        return characters.asSequence()
            .map { it.name }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
            .firstOrNull { raw == it || raw.startsWith(it) || raw.endsWith(it) }
    }

    private fun findCharacter(name: String): CastCharacter? =
        characters.firstOrNull { it.name == name }

    private companion object {
        /**
         * 说话人引导语：`张三说` / `李四问道` / `王五冷冷道` / `赵六：`。
         *
         * 名字限制 1–6 个汉字，避免把整句旁白吞进来。
         */
        val SPEAKER_PATTERN = Regex(
            "([\\u4e00-\\u9fa5]{1,6})(?:[^\\u4e00-\\u9fa5]{0,4})?(?:说|道|问|答|喊|叫|嘟囔|嘀咕|笑道|哭道|骂道)",
        )

        val PRONOUNS = setOf("他", "她", "它", "他们", "她们", "我", "你", "您", "大家", "众人", "两人", "三人")
    }
}
