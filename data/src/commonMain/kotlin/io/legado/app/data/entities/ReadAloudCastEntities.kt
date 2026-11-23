package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import io.legado.app.utils.systemCurrentTimeMillis

/**
 * 配音角色：多角色朗读功能自己的角色档案。
 *
 * 身份 = (bookUrl, name, poolLabel)——同名不同声音池可以并存
 * （如「李星菲（女少女）」与「李星菲（女中年）」），voiceId 是该角色绑定的音色。
 * chapter_role_assignments.characterId 指向本表 id。
 *
 * 移植说明：MD3 版本带 `bubbleRule()` 反序列化辅助方法（依赖 GSON + HighlightRule），
 * 这里只保留列；气泡规则的解析放到消费侧（B2 段评/气泡落地时再接）。
 */
@Serializable
@Entity(
    tableName = "cast_characters",
    indices = [
        Index(value = ["bookUrl", "name", "poolLabel"], unique = true),
        Index(value = ["bookUrl"]),
    ],
)
data class CastCharacter(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val name: String,
    /** 声音池标签（女少女/女中年等，见 [VoicePoolEntity.name]）。 */
    val poolLabel: String,
    /** 音色 id（read_aloud_voices.id），空 = 未选。 */
    @ColumnInfo(defaultValue = "")
    val voiceId: String = "",
    /** 变声器预设名（voice_effect_presets.name），空 = 不变声。 */
    @ColumnInfo(defaultValue = "")
    val voiceEffect: String = "",
    /** 「人物与角色配音」页的手动排序位（1 起，0 = 没排过）。 */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    /** 角色气泡样式：只填气泡栏的一条 HighlightRule JSON，空串 = 不设气泡。 */
    @ColumnInfo(defaultValue = "")
    val bubbleRuleJson: String = "",
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 多角色分配：一句话（章节内第 N 个开引号引导的对话）与角色的绑定。
 *
 * 锚点是章内开引号序号（对内容处理后的文本计数）：重新从源站拉取正文后，
 * 只要段落引号结构不变分配就仍然有效；构建时越界的序号自动忽略。
 */
@Serializable
@Entity(
    tableName = "chapter_role_assignments",
    primaryKeys = ["bookUrl", "chapterIndex", "quoteOrdinal"],
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["characterId"]),
    ],
)
data class ChapterRoleAssignment(
    val bookUrl: String,
    val chapterIndex: Int,
    /** 章内开引号序号，从 0 开始。 */
    val quoteOrdinal: Int,
    val characterId: String,
    /** 冗余显示名：角色被删除时胶囊仍能显示。 */
    @ColumnInfo(defaultValue = "")
    val characterName: String = "",
    /** 冗余声音池标签，同上。 */
    @ColumnInfo(defaultValue = "")
    val voicePoolLabel: String = "",
    /** 只作用于这一句的变声器预设名，空 = 不变声。 */
    @ColumnInfo(defaultValue = "")
    val voiceEffect: String = "",
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 本书角色记忆：AI 分配角色跨章节读写的轻量人物档案（主名｜别名/身份｜人物关系｜声音池）。
 *
 * 只属于本书（主键 = bookUrl）。用于把「同一个人的不同称呼」归并成同一个角色。
 */
@Serializable
@Entity(tableName = "book_cast_memory")
data class BookCastMemory(
    @PrimaryKey
    val bookUrl: String,
    @ColumnInfo(defaultValue = "")
    val memory: String = "",
    val updatedAt: Long = systemCurrentTimeMillis(),
)
