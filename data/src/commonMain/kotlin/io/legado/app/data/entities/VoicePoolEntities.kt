package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import io.legado.app.utils.systemCurrentTimeMillis

/**
 * 声音池分组：独立实体，可嵌套（parentId 指回本表，空串 = 根层），行为等同文件夹。
 *
 * 旧版是 voice_pools.groupName 那个纯字符串（只能一层、改名要全表改写）。
 */
@Serializable
@Entity(
    tableName = "voice_pool_groups",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["parentId", "name"], unique = true),
    ],
)
data class VoicePoolGroupEntity(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 父分组 id，空串 = 根层。 */
    @ColumnInfo(defaultValue = "")
    var parentId: String = "",
    /** 同级内的手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 组开关：关掉后整棵子树不再作为新建角色分配的候选池，已建好的角色不动。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 声音池：音色的粗分类集合（男童/女少女/卡通人物…），用户可增删改。
 *
 * 默认 13 池首次使用时种子化，全部可删除。
 */
@Serializable
@Entity(
    tableName = "voice_pools",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["groupId", "order"]),
        Index(value = ["groupName", "name"]),
    ],
)
data class VoicePoolEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** 所属分组 id，空串 = 未分组。 */
    @ColumnInfo(defaultValue = "")
    var groupId: String = "",
    /** 旧的管理分组名，仅保留给备份/兼容读取。 */
    var groupName: String = "",
    /** 分组内手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    val enabled: Boolean = true,
    /** 种子默认池标记（仅信息用途，不影响删除）。 */
    val isDefault: Boolean = false,
    val createdAt: Long = systemCurrentTimeMillis(),
    val updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 音色 → 声音池归属（多对多）。
 *
 * 独立表而不给 read_aloud_voices 加列：音色行会被引擎同步 REPLACE 重建，
 * 成员关系必须与音色行生命周期解耦。
 */
@Serializable
@Entity(
    tableName = "voice_pool_members",
    primaryKeys = ["poolId", "voiceId"],
    indices = [Index(value = ["voiceId"]), Index(value = ["poolId"])],
)
data class VoicePoolMember(
    val poolId: String,
    val voiceId: String,
    /** 池内启用开关：停用不出现在分配角色的音色菜单。 */
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
)

/**
 * 变声器预设：给某个角色的声音再加一层「音高 / 语速 / 混响 / 金属感」。
 *
 * 主键用名字而不是 id：角色表里存的就是预设名，导库换设备后仍认得出来；
 * 找不到预设时按「不变声」处理。
 */
@Serializable
@Entity(
    tableName = "voice_effect_presets",
    indices = [Index(value = ["enabled", "order"])],
)
data class VoiceEffectPreset(
    /** 预设名 = 身份。 */
    @PrimaryKey
    val name: String,
    /** 音高倍率，与音色自带音高相乘（0.25–4）。 */
    @ColumnInfo(defaultValue = "1")
    var pitch: Float = 1f,
    /** 语速倍率，与朗读语速相乘（0.5–2）。 */
    @ColumnInfo(defaultValue = "1")
    var speed: Float = 1f,
    /** 平台混响预设号，0 = 不加混响。 */
    @ColumnInfo(defaultValue = "0")
    var reverbPreset: Int = 0,
    /** 中频带通：机器人/电话音那种金属感。 */
    @ColumnInfo(defaultValue = "0")
    var metal: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 内置预设：删除时连带回收，导入同名记录会覆盖参数。 */
    @ColumnInfo(defaultValue = "0")
    var builtin: Boolean = false,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
)
