package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import io.legado.app.utils.systemCurrentTimeMillis

/**
 * 背景音乐分组：独立表，可嵌套（parentId 指回本表，空串 = 根层）。
 *
 * 结构与 [VoicePoolGroupEntity] 完全一致，但两套表分开存放，
 * 角色候选池的读取链路一行都不碰。
 */
@Serializable
@Entity(
    tableName = "bgm_pool_groups",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["parentId", "name"], unique = true),
    ],
)
data class BgmPoolGroupEntity(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 父分组 id，空串 = 根层。 */
    @ColumnInfo(defaultValue = "")
    var parentId: String = "",
    /** 同级内的手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 关掉 = 整棵子树不作为背景音乐候选，已分配好的场景不动。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
)

/** 背景音乐池：一组配乐的集合（战斗 / 日常 / 悲伤…），场景分配时选到这一层。 */
@Serializable
@Entity(
    tableName = "bgm_pools",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["groupId", "order"]),
    ],
)
data class BgmPoolEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** 所属分组 id，空串 = 未分组。 */
    @ColumnInfo(defaultValue = "")
    var groupId: String = "",
    /** 分组内手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 配乐 → 背景音乐池归属（多对多），与 [VoicePoolMember] 同构。
 *
 * 一首曲子可以进多个池，所以不占用 [BgmTrackEntity] 的列；配乐库本身保持扁平。
 */
@Serializable
@Entity(
    tableName = "bgm_pool_members",
    primaryKeys = ["poolId", "trackId"],
    indices = [Index(value = ["trackId"]), Index(value = ["poolId"])],
)
data class BgmPoolMember(
    val poolId: String,
    val trackId: String,
    /** 池内启用开关：停用的曲目不进随机抽取。 */
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
)

/**
 * 背景音乐池里的一条配乐：用户从本机导入的音频文件，导入时复制进应用目录。
 *
 * 只存副本路径，不记 content uri —— uri 权限在部分 ROM 上不可持久化，
 * 而且原文件被删/移动会让播放直接失败。
 */
@Serializable
@Entity(
    tableName = "bgm_tracks",
    indices = [Index(value = ["name"], unique = true)],
)
data class BgmTrackEntity(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 应用私有目录下的绝对路径。 */
    var path: String,
    /** 时长，毫秒；0 = 导入时读不出来。 */
    @ColumnInfo(defaultValue = "0")
    var durationMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    var sizeBytes: Long = 0L,
    /** 列表内手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 关掉即不作为背景音乐候选。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    /** 这条配乐自身的音量（0f–1f），场景还能再压一档，实际播放取乘积。 */
    @ColumnInfo(defaultValue = "1.0")
    var volume: Float = 1f,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 正文背景音乐场景标记：某章某一段起，背景音乐换成这一条。
 *
 * 与 [ChapterRoleAssignment] 同一套锚点思路（章内段落序号，不存字符偏移），
 * 差别在于它不往正文里注入任何字符，所以正文一字不改、也不会被 TTS 念出来。
 *
 * 池名/曲名存名字而不是 id：导库、换设备后仍认得出来，找不到时按「没分配」处理。
 */
@Serializable
@Entity(
    tableName = "bgm_scene_marks",
    primaryKeys = ["bookUrl", "chapterIndex", "paragraphOrdinal"],
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["bookUrl"]),
    ],
)
data class BgmSceneMark(
    val bookUrl: String,
    val chapterIndex: Int,
    /** 正文段落序号，从 0 开始（跳过空行；章节标题不算正文）。 */
    val paragraphOrdinal: Int,
    /** 背景音乐池名：朗读时从池内启用的配乐里随机取一首。 */
    @ColumnInfo(defaultValue = "")
    var poolName: String = "",
    /** 指定配乐名，非空时优先于 [poolName]。 */
    @ColumnInfo(defaultValue = "")
    var trackName: String = "",
    /** false = 这段起「不要背景音乐」（用来掐掉从上一章延续过来的场景）。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    /** 本段音量（0f–1f），与配乐曲目自身音量相乘。 */
    @ColumnInfo(defaultValue = "1.0")
    var volume: Float = 1f,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
)
