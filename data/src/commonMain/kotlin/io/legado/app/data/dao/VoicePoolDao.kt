package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember
import kotlinx.coroutines.flow.Flow

/**
 * 声音池：分组树、池、池内音色归属，以及变声器预设。
 *
 * 分组与池都支持嵌套/排序，列表页需要 Flow 便于拖拽后即时刷新。
 */
@Dao
interface VoicePoolDao {

    // ---- 分组 ----

    @Query("select * from voice_pool_groups order by parentId, `order`, name")
    fun flowGroups(): Flow<List<VoicePoolGroupEntity>>

    @Query("select * from voice_pool_groups order by parentId, `order`, name")
    suspend fun groups(): List<VoicePoolGroupEntity>

    @Query("select * from voice_pool_groups where id = :id")
    suspend fun group(id: String): VoicePoolGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(vararg groups: VoicePoolGroupEntity)

    @Update
    suspend fun updateGroups(vararg groups: VoicePoolGroupEntity)

    @Delete
    suspend fun deleteGroup(group: VoicePoolGroupEntity)

    // ---- 池 ----

    @Query("select * from voice_pools order by groupId, `order`, name")
    fun flowPools(): Flow<List<VoicePoolEntity>>

    @Query("select * from voice_pools order by groupId, `order`, name")
    suspend fun pools(): List<VoicePoolEntity>

    @Query("select * from voice_pools where enabled = 1 order by groupId, `order`, name")
    suspend fun enabledPools(): List<VoicePoolEntity>

    @Query("select * from voice_pools where id = :id")
    suspend fun pool(id: String): VoicePoolEntity?

    @Query("select * from voice_pools where name = :name limit 1")
    suspend fun poolByName(name: String): VoicePoolEntity?

    @Query("select count(*) from voice_pools")
    suspend fun poolCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPools(vararg pools: VoicePoolEntity)

    @Update
    suspend fun updatePools(vararg pools: VoicePoolEntity)

    @Delete
    suspend fun deletePool(pool: VoicePoolEntity)

    // ---- 池成员 ----

    @Query("select * from voice_pool_members where poolId = :poolId order by voiceId")
    fun flowMembers(poolId: String): Flow<List<VoicePoolMember>>

    @Query("select * from voice_pool_members where poolId = :poolId order by voiceId")
    suspend fun members(poolId: String): List<VoicePoolMember>

    @Query("select voiceId from voice_pool_members where poolId = :poolId and enabled = 1")
    suspend fun enabledVoiceIds(poolId: String): List<String>

    @Query("select * from voice_pool_members")
    suspend fun allMembers(): List<VoicePoolMember>

    @Query("select poolId from voice_pool_members where voiceId = :voiceId")
    suspend fun poolIdsOfVoice(voiceId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(vararg members: VoicePoolMember)

    @Query("delete from voice_pool_members where poolId = :poolId and voiceId = :voiceId")
    suspend fun deleteMember(poolId: String, voiceId: String)

    @Query("delete from voice_pool_members where poolId = :poolId")
    suspend fun deleteMembersOfPool(poolId: String)

    @Query("delete from voice_pool_members where voiceId = :voiceId")
    suspend fun deleteMembershipsOfVoice(voiceId: String)

    // ---- 变声器预设 ----

    @Query("select * from voice_effect_presets order by `order`, name")
    fun flowEffects(): Flow<List<VoiceEffectPreset>>

    @Query("select * from voice_effect_presets where name = :name")
    suspend fun effect(name: String): VoiceEffectPreset?

    @Query("select * from voice_effect_presets")
    suspend fun allEffects(): List<VoiceEffectPreset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEffects(vararg presets: VoiceEffectPreset)

    @Delete
    suspend fun deleteEffect(preset: VoiceEffectPreset)
}
