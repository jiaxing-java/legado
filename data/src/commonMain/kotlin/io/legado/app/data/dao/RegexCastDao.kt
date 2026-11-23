package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.RegexCastRule
import kotlinx.coroutines.flow.Flow

/** 正则角色：规则与分组（按文字匹配换音色/放音频）。 */
@Dao
interface RegexCastDao {

    @Query("select * from regex_cast_rules order by `order`, id")
    fun flowRules(): Flow<List<RegexCastRule>>

    @Query("select * from regex_cast_rules where enabled = 1 order by `order`, id")
    suspend fun enabledRules(): List<RegexCastRule>

    @Query("select * from regex_cast_rules")
    suspend fun allRules(): List<RegexCastRule>

    @Query("select * from regex_cast_rules where id = :id")
    suspend fun rule(id: Long): RegexCastRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(vararg rules: RegexCastRule)

    @Update
    suspend fun updateRules(vararg rules: RegexCastRule)

    @Delete
    suspend fun deleteRule(rule: RegexCastRule)

    @Query("select * from regex_cast_groups order by parentId, `order`, name")
    fun flowGroups(): Flow<List<RegexCastGroup>>

    @Query("select * from regex_cast_groups order by parentId, `order`, name")
    suspend fun allGroups(): List<RegexCastGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(vararg groups: RegexCastGroup)

    @Delete
    suspend fun deleteGroup(group: RegexCastGroup)
}
