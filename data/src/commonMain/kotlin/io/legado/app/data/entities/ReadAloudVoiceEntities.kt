package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import io.legado.app.utils.systemCurrentTimeMillis

/**
 * 朗读音色：某个引擎（系统 TTS / 云 TTS / HTTP TTS）下的一个可播报音色。
 *
 * engineType 用来区分引擎种类（system/cloud/http），engineId 指向具体引擎行
 * （cloud_tts_engines.id 或 httpTTS.id），speakerId 是引擎侧的原始音色标识。
 * traitsJson / emotionCatalogJson 存该音色的特征标签与情绪目录（JSON 数组字符串），
 * 供「发言人/情绪」选择器读取——外部 TTS 配置导入时可携带。
 */
@Serializable
@Entity(
    tableName = "read_aloud_voices",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["engineType", "engineId", "speakerId"], unique = true),
        Index(value = ["enabled", "displayName"]),
    ],
)
data class ReadAloudVoiceEntity(
    val id: String,
    val engineType: String,
    @ColumnInfo(defaultValue = "")
    val engineId: String = "",
    @ColumnInfo(defaultValue = "")
    val speakerId: String = "",
    val displayName: String,
    @ColumnInfo(defaultValue = "[]")
    val traitsJson: String = "[]",
    @ColumnInfo(defaultValue = "[]")
    val emotionCatalogJson: String = "[]",
    @ColumnInfo(defaultValue = "user")
    val managedBy: String = "user",
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val available: Boolean = true,
    /** 用户主动排除：行留着，避免下次同步把它当新音色重新插回来。 */
    @ColumnInfo(defaultValue = "0")
    val excluded: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val revision: Long = 0L,
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 书 → 朗读主体的音色绑定。
 *
 * subjectType 区分主体种类（book/narrator/character），subjectId 是主体 id；
 * locked = 用户手动指定（自动分配不覆盖）。
 */
@Serializable
@Entity(
    tableName = "book_voice_bindings",
    primaryKeys = ["bookUrl", "subjectType", "subjectId"],
    indices = [
        Index(value = ["bookUrl", "subjectType"]),
        Index(value = ["voiceId"]),
    ],
)
data class BookVoiceBindingEntity(
    val bookUrl: String,
    val subjectType: String,
    val subjectId: String,
    val voiceId: String,
    @ColumnInfo(defaultValue = "0")
    val locked: Boolean = false,
    @ColumnInfo(defaultValue = "user")
    val source: String = "user",
    @ColumnInfo(defaultValue = "1")
    val confidence: Float = 1f,
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 一章的朗读切分分析结果（旁白/角色/情绪的段落划分）。
 *
 * 用 (bookUrl, chapterIndex, contentHash, resolverVersion) 唯一：
 * 正文变了或切分算法升级了就重新分析，旧结果自然失效。
 */
@Serializable
@Entity(
    tableName = "chapter_speech_analysis",
    primaryKeys = ["id"],
    indices = [
        Index(
            value = ["bookUrl", "chapterIndex", "contentHash", "resolverVersion"],
            unique = true,
        ),
        Index(value = ["bookUrl", "chapterIndex", "updatedAt"]),
    ],
)
data class ChapterSpeechAnalysisEntity(
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val contentHash: String,
    val resolverVersion: String,
    @ColumnInfo(defaultValue = "")
    val characterRevision: String = "",
    @ColumnInfo(defaultValue = "pending")
    val status: String = "pending",
    @ColumnInfo(defaultValue = "")
    val error: String = "",
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 分析结果里的一段朗读单元：章节内 [start, end) 的一段文字及其归属角色/情绪。
 */
@Serializable
@Entity(
    tableName = "chapter_speech_segments",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["analysisId", "paragraphIndex", "start", "end"], unique = true),
        Index(value = ["bookUrl", "chapterIndex", "paragraphIndex"]),
        Index(value = ["characterId"]),
    ],
)
data class ChapterSpeechSegmentEntity(
    val id: String,
    val analysisId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val chapterPosition: Int,
    val text: String,
    val roleType: String,
    val characterId: String? = null,
    @ColumnInfo(defaultValue = "")
    val characterName: String = "",
    @ColumnInfo(defaultValue = "")
    val emotion: String = "",
    @ColumnInfo(defaultValue = "0")
    val confidence: Float = 0f,
    val source: String,
    @ColumnInfo(defaultValue = "0")
    val userLocked: Boolean = false,
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)
