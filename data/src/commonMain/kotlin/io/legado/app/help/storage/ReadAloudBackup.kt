package io.legado.app.help.storage

import io.legado.app.constant.AppLog
import io.legado.app.data.AppDbAccessor
import io.legado.app.data.entities.BgmPoolEntity
import io.legado.app.data.entities.BgmPoolGroupEntity
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmSceneMark
import io.legado.app.data.entities.BgmTrackEntity
import io.legado.app.data.entities.BookCastMemory
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.data.entities.CloudTtsEngineEntity
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember
import io.legado.app.utils.GSON
import io.legado.app.utils.toJson
import io.legado.app.utils.fromJsonObject
import kotlinx.serialization.Serializable

/**
 * 多角色朗读 / 云 TTS / 背景音乐域的备份包。
 *
 * 为什么聚合成一个文件而不是一表一个 JSON：
 * 这 16 张表是一个整体（角色→分配→音色→池→规则），拆开恢复会出现「角色回来了但池没了」
 * 的半截状态；聚合成单文件后要么整包恢复，要么整包跳过，语义更干净，也让备份 zip 的
 * 条目数不至于从 20 涨到 36。
 *
 * 兼容口径（决策 D3「导入超集容错」）：
 * - 读取用 `ignoreUnknownKeys = true`，将来字段增减不会让旧包恢复失败；
 * - 每个字段都有默认空列表，包内缺哪个域就跳过哪个域；
 * - [BgmTrackEntity.path] 是设备绝对路径，换设备后文件不存在，恢复时按文件是否真在过滤。
 *
 * 不备份的派生数据（可重算/仅本机有效）：`chapter_speech_analysis`、`chapter_speech_segments`、
 * `read_aloud_audio_downloads`、`exact_chapter_page_counts`。
 */
@Serializable
data class ReadAloudBackupPack(
    val characters: List<CastCharacter> = emptyList(),
    val assignments: List<ChapterRoleAssignment> = emptyList(),
    val castMemories: List<BookCastMemory> = emptyList(),
    val voicePoolGroups: List<VoicePoolGroupEntity> = emptyList(),
    val voicePools: List<VoicePoolEntity> = emptyList(),
    val voicePoolMembers: List<VoicePoolMember> = emptyList(),
    val voiceEffects: List<VoiceEffectPreset> = emptyList(),
    val regexGroups: List<RegexCastGroup> = emptyList(),
    val regexRules: List<RegexCastRule> = emptyList(),
    val bgmPoolGroups: List<BgmPoolGroupEntity> = emptyList(),
    val bgmPools: List<BgmPoolEntity> = emptyList(),
    val bgmPoolMembers: List<BgmPoolMember> = emptyList(),
    val bgmTracks: List<BgmTrackEntity> = emptyList(),
    val bgmSceneMarks: List<BgmSceneMark> = emptyList(),
    val voices: List<ReadAloudVoiceEntity> = emptyList(),
    val voiceBindings: List<BookVoiceBindingEntity> = emptyList(),
    val cloudTtsEngines: List<CloudTtsEngineEntity> = emptyList(),
) {

    val isEmpty: Boolean
        get() = characters.isEmpty() && assignments.isEmpty() && castMemories.isEmpty() &&
            voicePoolGroups.isEmpty() && voicePools.isEmpty() && voicePoolMembers.isEmpty() &&
            voiceEffects.isEmpty() && regexGroups.isEmpty() && regexRules.isEmpty() &&
            bgmPoolGroups.isEmpty() && bgmPools.isEmpty() && bgmPoolMembers.isEmpty() &&
            bgmTracks.isEmpty() && bgmSceneMarks.isEmpty() && voices.isEmpty() &&
            voiceBindings.isEmpty() && cloudTtsEngines.isEmpty()
}

object ReadAloudBackup {

    /** 备份 zip 内的文件名。 */
    const val FILE_NAME = "readAloudCast.json"

    private const val TAG = "ReadAloudBackup"

    /** 采集全量数据；返回 null 表示这一域是空的（不写文件，保持旧备份形状）。 */
    suspend fun export(db: AppDbAccessor): String? {
        val pack = ReadAloudBackupPack(
            characters = db.castCharacterDao.all(),
            assignments = db.castCharacterDao.allAssignments(),
            castMemories = db.castCharacterDao.allCastMemories(),
            voicePoolGroups = db.voicePoolDao.groups(),
            voicePools = db.voicePoolDao.pools(),
            voicePoolMembers = db.voicePoolDao.allMembers(),
            voiceEffects = db.voicePoolDao.allEffects(),
            regexGroups = db.regexCastDao.allGroups(),
            regexRules = db.regexCastDao.allRules(),
            bgmPoolGroups = db.bgmPoolDao.groups(),
            bgmPools = db.bgmPoolDao.pools(),
            bgmPoolMembers = db.bgmPoolDao.allMembers(),
            bgmTracks = db.bgmPoolDao.tracks(),
            bgmSceneMarks = db.bgmPoolDao.allMarks(),
            voices = db.readAloudVoiceDao.all(),
            voiceBindings = db.readAloudVoiceDao.allBindings(),
            cloudTtsEngines = db.cloudTtsEngineDao.all(),
        )
        if (pack.isEmpty) return null
        return GSON.toJson(pack)
    }

    /**
     * 恢复整包。返回是否真的写入了数据。
     *
     * 顺序按依赖走：分组/池 → 成员 → 角色 → 分配 → 规则 → BGM。
     * 单表失败只记日志、继续后面的表（同一个包里各域相互独立，
     * 不该因为一处字段对不上整包作废）。
     */
    suspend fun restore(db: AppDbAccessor, json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        val pack = GSON.fromJsonObject<ReadAloudBackupPack>(json).getOrElse {
            AppLog.put("恢复朗读配置包失败\n${it.message}", it, tag = TAG)
            return false
        }
        runCatching {
            if (pack.voicePoolGroups.isNotEmpty()) {
                db.voicePoolDao.upsertGroups(*pack.voicePoolGroups.toTypedArray())
            }
            if (pack.voicePools.isNotEmpty()) {
                db.voicePoolDao.upsertPools(*pack.voicePools.toTypedArray())
            }
            if (pack.voicePoolMembers.isNotEmpty()) {
                db.voicePoolDao.upsertMembers(*pack.voicePoolMembers.toTypedArray())
            }
            if (pack.voiceEffects.isNotEmpty()) {
                db.voicePoolDao.upsertEffects(*pack.voiceEffects.toTypedArray())
            }
        }.onFailure { AppLog.put("恢复声音池出错\n${it.message}", it, tag = TAG) }

        runCatching {
            if (pack.voices.isNotEmpty()) {
                db.readAloudVoiceDao.upsert(*pack.voices.toTypedArray())
            }
            if (pack.voiceBindings.isNotEmpty()) {
                db.readAloudVoiceDao.upsertBindings(*pack.voiceBindings.toTypedArray())
            }
        }.onFailure { AppLog.put("恢复朗读音色出错\n${it.message}", it, tag = TAG) }

        runCatching {
            if (pack.characters.isNotEmpty()) {
                db.castCharacterDao.insert(*pack.characters.toTypedArray())
            }
            if (pack.assignments.isNotEmpty()) {
                db.castCharacterDao.upsertAssignments(*pack.assignments.toTypedArray())
            }
            if (pack.castMemories.isNotEmpty()) {
                db.castCharacterDao.upsertCastMemory(pack.castMemories.first())
                pack.castMemories.drop(1).forEach { db.castCharacterDao.upsertCastMemory(it) }
            }
        }.onFailure { AppLog.put("恢复角色分配出错\n${it.message}", it, tag = TAG) }

        runCatching {
            if (pack.regexGroups.isNotEmpty()) {
                db.regexCastDao.upsertGroups(*pack.regexGroups.toTypedArray())
            }
            if (pack.regexRules.isNotEmpty()) {
                db.regexCastDao.upsertRules(*pack.regexRules.toTypedArray())
            }
        }.onFailure { AppLog.put("恢复正则角色出错\n${it.message}", it, tag = TAG) }

        runCatching {
            if (pack.bgmPoolGroups.isNotEmpty()) {
                db.bgmPoolDao.upsertGroups(*pack.bgmPoolGroups.toTypedArray())
            }
            if (pack.bgmPools.isNotEmpty()) {
                db.bgmPoolDao.upsertPools(*pack.bgmPools.toTypedArray())
            }
            // 曲目文件不随备份走：目标设备上不存在的路径直接跳过，
            // 保留行会变成"列表里有、点了播不出"的哑条目。
            val usableTracks = pack.bgmTracks.filter { BackupFileOps.exists(it.path) }
            if (usableTracks.isNotEmpty()) {
                db.bgmPoolDao.upsertTracks(*usableTracks.toTypedArray())
                val usableIds = usableTracks.map { it.id }.toSet()
                val usableMembers = pack.bgmPoolMembers.filter { it.trackId in usableIds }
                if (usableMembers.isNotEmpty()) {
                    db.bgmPoolDao.upsertMembers(*usableMembers.toTypedArray())
                }
            }
            if (pack.bgmSceneMarks.isNotEmpty()) {
                db.bgmPoolDao.upsertMarks(*pack.bgmSceneMarks.toTypedArray())
            }
        }.onFailure { AppLog.put("恢复背景音乐出错\n${it.message}", it, tag = TAG) }

        runCatching {
            if (pack.cloudTtsEngines.isNotEmpty()) {
                db.cloudTtsEngineDao.upsert(*pack.cloudTtsEngines.toTypedArray())
            }
        }.onFailure { AppLog.put("恢复云 TTS 引擎出错\n${it.message}", it, tag = TAG) }

        return true
    }
}
