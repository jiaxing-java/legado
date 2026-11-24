package io.legado.app.help.readaloud

import io.legado.app.data.AppDbProviders
import io.legado.app.data.entities.BookCastMemory
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember
import io.legado.app.help.tts.ReadAloudQueue
import io.legado.app.help.tts.SystemTtsEngineInfo
import io.legado.app.help.tts.SystemTtsVoice
import io.legado.app.utils.randomUUIDString
import io.legado.app.utils.systemCurrentTimeMillis
import kotlinx.coroutines.flow.Flow

/**
 * 多角色朗读的数据入口：声音池、角色、正文分配、音色目录、正则规则、变声预设。
 *
 * 只做「读出来 / 写进去」，不含 UI 状态；UI 层直接用返回的 Flow，写入后由 Room 推新值。
 *
 * # 为什么要种子化声音池
 *
 * MD3 首次进入「角色配音」页会铺 13 个默认池（男童/女少女/女中年…），用户看到的是「能直接
 * 给角色选池」，而不是一张空表。种子只铺一次（[VoicePoolDao.poolCount] 为 0），之后用户
 * 把池删光也不会重新冒出来 —— 这是「已初始化」与「空库」的区别，靠 count 判断即可。
 */
class ReadAloudCastRepository {

    private val db get() = AppDbProviders.get()

    // region 声音池

    fun flowPools(): Flow<List<VoicePoolEntity>> = db.voicePoolDao.flowPools()

    fun flowPoolGroups(): Flow<List<VoicePoolGroupEntity>> = db.voicePoolDao.flowGroups()

    suspend fun pools(): List<VoicePoolEntity> = db.voicePoolDao.pools()

    suspend fun enabledPools(): List<VoicePoolEntity> = db.voicePoolDao.enabledPools()

    /** 首次使用时铺默认池；已存在任何池（含用户自建）就不动。 */
    suspend fun seedDefaultPoolsIfEmpty() {
        if (db.voicePoolDao.poolCount() > 0) return
        val now = systemCurrentTimeMillis()
        val pools = DEFAULT_POOL_NAMES.mapIndexed { index, name ->
            VoicePoolEntity(
                id = randomUUIDString(),
                name = name,
                order = index,
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            )
        }
        db.voicePoolDao.upsertPools(*pools.toTypedArray())
    }

    suspend fun savePool(pool: VoicePoolEntity) = db.voicePoolDao.upsertPools(pool)

    suspend fun deletePool(pool: VoicePoolEntity) {
        db.voicePoolDao.deleteMembersOfPool(pool.id)
        db.voicePoolDao.deletePool(pool)
    }

    /**
     * 新建声音池并返回（id 在这里生成，UI 只给名字）。
     *
     * 重名会撞 `voice_pools.name` 的唯一索引而抛异常 —— 调用方据此提示「已存在同名池」，
     * 不在这里静默改成「xx(2)」：用户下次还得再找一遍自己建的是哪个。
     */
    suspend fun createPool(name: String): VoicePoolEntity {
        val now = systemCurrentTimeMillis()
        val pool = VoicePoolEntity(
            id = randomUUIDString(),
            name = name,
            order = (db.voicePoolDao.pools().maxOfOrNull { it.order } ?: -1) + 1,
            createdAt = now,
            updatedAt = now,
        )
        db.voicePoolDao.upsertPools(pool)
        return pool
    }

    // region 池成员

    fun flowPoolMembers(): Flow<List<VoicePoolMember>> = db.voicePoolDao.flowAllMembers()

    suspend fun poolMembers(poolId: String): List<VoicePoolMember> =
        db.voicePoolDao.members(poolId)

    /**
     * 池子 → 启用中的音色 id 集合，角色表单据此收窄候选。
     *
     * 池里一个音色都没有时调用方应回落到「全部音色」：空池收窄成空列表会让角色一个音色都
     * 挑不到，看起来像功能坏了。
     */
    suspend fun enabledVoiceIdsByPool(): Map<String, Set<String>> =
        db.voicePoolDao.allMembers()
            .filter { it.enabled }
            .groupBy({ it.poolId }, { it.voiceId })
            .mapValues { it.value.toSet() }

    suspend fun setPoolMember(poolId: String, voiceId: String, enabled: Boolean) =
        db.voicePoolDao.upsertMembers(VoicePoolMember(poolId, voiceId, enabled))

    suspend fun removePoolMember(poolId: String, voiceId: String) =
        db.voicePoolDao.deleteMember(poolId, voiceId)

    // endregion

    suspend fun savePoolGroup(group: VoicePoolGroupEntity) = db.voicePoolDao.upsertGroups(group)

    suspend fun deletePoolGroup(group: VoicePoolGroupEntity) = db.voicePoolDao.deleteGroup(group)

    // endregion

    // region 音色目录

    fun flowAvailableVoices(): Flow<List<ReadAloudVoiceEntity>> =
        db.readAloudVoiceDao.flowAvailable()

    suspend fun voicesById(): Map<String, ReadAloudVoiceEntity> =
        db.readAloudVoiceDao.all().associateBy { it.id }

    suspend fun voice(id: String): ReadAloudVoiceEntity? =
        if (id.isBlank()) null else db.readAloudVoiceDao.get(id)

    suspend fun voicesOfEngine(engineType: String, engineId: String): List<ReadAloudVoiceEntity> =
        db.readAloudVoiceDao.byEngine(engineType, engineId)

    /**
     * 把引擎枚举出来的音色写进目录。
     *
     * 用 `(engineType, engineId, speakerId)` 唯一键做 upsert：引擎侧新增音色会补进来，已有的
     * 保留用户改过的 `excluded`（主动排除的音色不会因为一次同步又冒出来）。
     *
     * @return 本次新增的音色数
     */
    suspend fun syncEngineVoices(
        engineType: String,
        engineId: String,
        voices: List<SystemTtsVoice>,
        managedBy: String = "engine",
    ): Int {
        if (voices.isEmpty()) return 0
        val dao = db.readAloudVoiceDao
        val now = systemCurrentTimeMillis()
        var added = 0
        val rows = voices.map { voice ->
            val existing = dao.find(engineType, engineId, voice.id)
            if (existing == null) added++
            ReadAloudVoiceEntity(
                id = existing?.id ?: randomUUIDString(),
                engineType = engineType,
                engineId = engineId,
                speakerId = voice.id,
                displayName = voice.label.ifBlank { voice.id },
                traitsJson = existing?.traitsJson ?: "[]",
                emotionCatalogJson = existing?.emotionCatalogJson ?: "[]",
                managedBy = existing?.managedBy ?: managedBy,
                enabled = existing?.enabled ?: true,
                available = true,
                excluded = existing?.excluded ?: false,
                revision = (existing?.revision ?: 0L) + 1,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        }
        dao.upsert(*rows.toTypedArray())
        return added
    }

    /** 引擎被卸载/换引擎时清掉它托管的音色；用户自建（managedBy = user）保留。 */
    suspend fun dropEngineVoices(engineType: String, engineId: String) =
        db.readAloudVoiceDao.deleteEngineManaged(engineType, engineId)

    suspend fun setVoiceExcluded(id: String, excluded: Boolean) =
        db.readAloudVoiceDao.setExcluded(id, excluded, systemCurrentTimeMillis())

    /** 写音色的发言人特征与情绪目录（外部 TTS 配置导入时用）。 */
    suspend fun updateVoiceCatalog(
        id: String,
        traitsJson: String? = null,
        emotionCatalogJson: String? = null,
    ) {
        val dao = db.readAloudVoiceDao
        val voice = dao.get(id) ?: return
        dao.upsert(
            voice.copy(
                traitsJson = traitsJson ?: voice.traitsJson,
                emotionCatalogJson = emotionCatalogJson ?: voice.emotionCatalogJson,
                revision = voice.revision + 1,
                updatedAt = systemCurrentTimeMillis(),
            ),
        )
    }

    // endregion

    // region 角色

    fun flowCharacters(bookUrl: String): Flow<List<CastCharacter>> =
        db.castCharacterDao.flowByBook(bookUrl)

    suspend fun characters(bookUrl: String): List<CastCharacter> =
        db.castCharacterDao.byBook(bookUrl)

    /**
     * 保存角色。
     *
     * 唯一键是 `(bookUrl, name, poolLabel)`：同一个名字在不同声音池下是不同角色（「李星菲（少女）」
     * 与「李星菲（中年）」）。命中已有行时沿用原 id，避免把 [ChapterRoleAssignment.characterId]
     * 指丢；[CastCharacter.id] 为空串也走同一路径。
     */
    suspend fun saveCharacter(character: CastCharacter): CastCharacter {
        val dao = db.castCharacterDao
        val existing = dao.find(character.bookUrl, character.name, character.poolLabel)
        val now = systemCurrentTimeMillis()
        val row = when {
            existing == null -> character.copy(
                id = character.id.ifBlank { randomUUIDString() },
                createdAt = if (character.createdAt == 0L) now else character.createdAt,
                updatedAt = now,
            )

            else -> character.copy(
                id = existing.id,
                createdAt = existing.createdAt,
                updatedAt = now,
            )
        }
        dao.insert(row)
        return row
    }

    /** 删除角色，连带清掉引用它的分配（否则按 quoteOrdinal 仍会指向已消失的角色）。 */
    suspend fun deleteCharacter(character: CastCharacter) {
        db.castCharacterDao.deleteAssignmentsOfCharacter(character.id)
        db.castCharacterDao.delete(character)
    }

    suspend fun reorderCharacters(characters: List<CastCharacter>) {
        val now = systemCurrentTimeMillis()
        db.castCharacterDao.update(
            *characters.mapIndexed { index, character ->
                character.copy(sortOrder = index + 1, updatedAt = now)
            }.toTypedArray(),
        )
    }

    // endregion

    // region 正文分配

    fun flowAssignments(bookUrl: String, chapterIndex: Int): Flow<List<ChapterRoleAssignment>> =
        db.castCharacterDao.flowAssignments(bookUrl, chapterIndex)

    suspend fun assignments(bookUrl: String, chapterIndex: Int): List<ChapterRoleAssignment> =
        db.castCharacterDao.assignments(bookUrl, chapterIndex)

    /** 把一个引号句指给某角色；重复指定同一条目时覆盖。 */
    suspend fun assign(
        bookUrl: String,
        chapterIndex: Int,
        quoteOrdinal: Int,
        character: CastCharacter,
        voiceEffect: String = "",
    ) {
        val now = systemCurrentTimeMillis()
        db.castCharacterDao.upsertAssignments(
            ChapterRoleAssignment(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                quoteOrdinal = quoteOrdinal,
                characterId = character.id,
                characterName = character.name,
                voicePoolLabel = character.poolLabel,
                voiceEffect = voiceEffect,
                updatedAt = now,
            ),
        )
    }

    suspend fun unassign(bookUrl: String, chapterIndex: Int, quoteOrdinal: Int) =
        db.castCharacterDao.deleteAssignment(bookUrl, chapterIndex, quoteOrdinal)

    suspend fun clearAssignments(bookUrl: String, chapterIndex: Int) =
        db.castCharacterDao.deleteAssignments(bookUrl, chapterIndex)

    // endregion

    // region 正则角色规则

    fun flowRegexRules(): Flow<List<RegexCastRule>> = db.regexCastDao.flowRules()

    suspend fun regexRules(): List<RegexCastRule> = db.regexCastDao.allRules()

    suspend fun enabledRegexRules(): List<RegexCastRule> = db.regexCastDao.enabledRules()

    suspend fun saveRegexRule(rule: RegexCastRule) = db.regexCastDao.upsertRules(
        if (rule.updatedAt == 0L) rule.copy(updatedAt = systemCurrentTimeMillis()) else rule,
    )

    suspend fun deleteRegexRule(rule: RegexCastRule) = db.regexCastDao.deleteRule(rule)

    // endregion

    // region 变声器预设

    fun flowVoiceEffects(): Flow<List<VoiceEffectPreset>> = db.voicePoolDao.flowEffects()

    suspend fun voiceEffects(): List<VoiceEffectPreset> = db.voicePoolDao.allEffects()

    suspend fun voiceEffect(name: String): VoiceEffectPreset? =
        if (name.isBlank()) null else db.voicePoolDao.effect(name)

    suspend fun saveVoiceEffect(preset: VoiceEffectPreset) =
        db.voicePoolDao.upsertEffects(preset.copy(updatedAt = systemCurrentTimeMillis()))

    suspend fun deleteVoiceEffect(preset: VoiceEffectPreset) =
        db.voicePoolDao.deleteEffect(preset)

    // endregion

    // region 书级绑定与角色记忆

    suspend fun bindings(bookUrl: String): List<BookVoiceBindingEntity> =
        db.readAloudVoiceDao.bindings(bookUrl)

    suspend fun bindSubjectVoice(
        bookUrl: String,
        subjectType: String,
        subjectId: String,
        voiceId: String,
        locked: Boolean = true,
    ) {
        val now = systemCurrentTimeMillis()
        val dao = db.readAloudVoiceDao
        val existing = dao.binding(bookUrl, subjectType, subjectId)
        dao.upsertBindings(
            BookVoiceBindingEntity(
                bookUrl = bookUrl,
                subjectType = subjectType,
                subjectId = subjectId,
                voiceId = voiceId,
                locked = locked,
                source = "user",
                confidence = 1f,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun castMemory(bookUrl: String): String =
        db.castCharacterDao.castMemory(bookUrl)?.memory.orEmpty()

    suspend fun saveCastMemory(bookUrl: String, memory: String) {
        db.castCharacterDao.upsertCastMemory(
            BookCastMemory(bookUrl = bookUrl, memory = memory, updatedAt = systemCurrentTimeMillis()),
        )
    }

    // endregion

    // region 解析入口

    /**
     * 为某一章构建解析器所需的输入并解析出「每段用谁的音色」。
     *
     * 多角色总开关关掉时直接返回全 null（走引擎默认音色），不查库、不做识别的意义是：
     * 关着开关的书不该因为读一章而多出一张分析结果。
     *
     * @param multiRoleEnabled 取 `PreferKey.multiRoleCast`
     * @param narratorVoiceId 用户指定的旁白音色；空 = 引擎默认
     */
    suspend fun resolveChapterVoices(
        bookUrl: String,
        chapterIndex: Int,
        text: String,
        segments: List<ReadAloudQueue.Segment>,
        multiRoleEnabled: Boolean,
        narratorVoiceId: String = "",
    ): List<SpeechVoiceRef?> = resolveChapterSegments(
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        text = text,
        segments = segments,
        multiRoleEnabled = multiRoleEnabled,
        narratorVoiceId = narratorVoiceId,
    ).map { it.voice }

    /**
     * 同 [resolveChapterVoices]，但保留每段的台词序号 —— 「手动分配」页要按序号落库。
     *
     * 两个入口共用同一份解析，避免「播放时按 A 结果、UI 显示 B 结果」的不一致。
     */
    suspend fun resolveChapterSegments(
        bookUrl: String,
        chapterIndex: Int,
        text: String,
        segments: List<ReadAloudQueue.Segment>,
        multiRoleEnabled: Boolean,
        narratorVoiceId: String = "",
    ): List<CastSegment> {
        if (!multiRoleEnabled) return segments.map { CastSegment(it.text, null, null) }
        val characters = characters(bookUrl)
        val assignments = assignments(bookUrl, chapterIndex)
        val rules = enabledRegexRules()
        if (characters.isEmpty() && assignments.isEmpty() && rules.isEmpty()) {
            return segments.map { CastSegment(it.text, null, null) }
        }
        val voices = voicesById()
        val narrator = narratorVoiceId.takeIf { it.isNotBlank() }?.let { id ->
            voices[id]?.let { voice ->
                SpeechVoiceRef(
                    voiceId = voice.id,
                    speakerId = voice.speakerId,
                    roleType = SpeechVoiceRef.ROLE_NARRATOR,
                    confidence = 0f,
                )
            }
        }
        return ChapterSpeechPlanResolver(
            characters = characters,
            voicesById = voices,
            assignments = assignments,
            regexRules = rules,
            narratorVoice = narrator,
        ).resolveSegments(text, segments)
    }

    // endregion

    companion object {

        /** 首次使用的默认声音池（对齐 MD3 的 13 个种子池，全部可删可改）。 */
        private val DEFAULT_POOL_NAMES = listOf(
            "男童", "女童", "男青年", "女青年", "男中年", "女中年", "男老年", "女老年",
            "旁白", "卡通人物", "机器人", "解说", "其他",
        )

        /** 系统 TTS 的引擎类型标识（`read_aloud_voices.engineType`）。 */
        const val ENGINE_TYPE_SYSTEM = "system"

        /** HTTP TTS 的引擎类型标识。 */
        const val ENGINE_TYPE_HTTP = "http"

        /** 云 TTS 的引擎类型标识（本轮不接 provider，仅占位）。 */
        const val ENGINE_TYPE_CLOUD = "cloud"

        @Volatile
        private var instance: ReadAloudCastRepository? = null

        private val lock = kotlinx.atomicfu.locks.SynchronizedObject()

        fun get(): ReadAloudCastRepository =
            instance ?: kotlinx.atomicfu.locks.synchronized(lock) {
                instance ?: ReadAloudCastRepository().also { instance = it }
            }
    }
}
