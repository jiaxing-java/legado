package io.legado.app.data.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import io.legado.app.utils.systemCurrentTimeMillis

/**
 * 正则角色的分组：与角色声音池 / 背景音乐池的分组同一种形状（可嵌套的文件夹树）。
 *
 * 字段与 [VoicePoolGroupEntity] 保持一致：parentId 空串 = 根层，order 是同一父级下的手动顺序，
 * enabled 关掉等于整棵子树停用。被 [RegexCastRule.groupId] 引用。
 */
@Serializable
@Entity(
    tableName = "regex_cast_groups",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["parentId", "name"], unique = true),
        Index(value = ["parentId", "order"]),
    ],
)
data class RegexCastGroup(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 父分组 id，空串 = 根层。 */
    @ColumnInfo(defaultValue = "")
    var parentId: String = "",
    /** 父级内手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
)

/**
 * 正则角色：命中正文里的一段文字，就换这一段的读法。
 *
 * 与 [CastCharacter] 的区别是归属方式：角色表按「哪一句台词」（引号锚点 + 分配表）认人，
 * 这一张按文字本身认——正文里出现「爆炸」这两个字，不管它在谁的台词里，都按这条规则处理。
 *
 * 两种去向由 [poolKind] 决定：
 * - [POOL_ROLE]：命中的文字用 [itemId] 那个音色念。
 * - [POOL_BGM]：命中的文字不念，改放 [itemId] 那段音频。
 */
@Serializable
@Entity(
    tableName = "regex_cast_rules",
    indices = [
        Index(value = ["enabled", "order"]),
        Index(value = ["groupId", "order"]),
    ],
)
data class RegexCastRule(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    /** 角色名称：只在列表里认得出这条规则是干什么的，不参与匹配。 */
    var name: String = "",
    /** 匹配的地方；怎么解释这一串由 [useRegex] 决定。 */
    var pattern: String = "",
    /** 是否按正则匹配（false = 整串按字面量匹配）。 */
    @ColumnInfo(defaultValue = "1")
    var useRegex: Boolean = true,
    @ColumnInfo(defaultValue = "role")
    var poolKind: String = POOL_ROLE,
    /** 声音池 id（角色池或配乐池，看 [poolKind]）。 */
    @ColumnInfo(defaultValue = "")
    var poolId: String = "",
    /** 音色 id / 配乐 id。空 = 只选了池，朗读时按池内启用的随机取一条。 */
    @ColumnInfo(defaultValue = "")
    var itemId: String = "",
    /** 变声器预设名，空 = 不变声（只对 [POOL_ROLE] 有意义）。 */
    @ColumnInfo(defaultValue = "")
    var voiceEffect: String = "",
    /** 所属分组 id，指向 [RegexCastGroup.id]，空串 = 未分组。 */
    @ColumnInfo(defaultValue = "")
    var groupId: String = "",
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 特定范围：书名或书源 URL 的子串，空 = 不限。 */
    var scope: String? = null,
    /** 排除范围：命中这些子串的书不应用本条。 */
    var excludeScope: String? = null,
    val createdAt: Long = systemCurrentTimeMillis(),
    var updatedAt: Long = systemCurrentTimeMillis(),
) {

    companion object {
        const val POOL_ROLE = "role"
        const val POOL_BGM = "bgm"
    }
}
