package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import io.legado.app.data.entities.BgmPoolEntity
import io.legado.app.data.entities.BgmPoolGroupEntity
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmSceneMark
import io.legado.app.data.entities.BgmTrackEntity
import kotlinx.coroutines.flow.Flow

/** 背景音乐池：分组树、池、池内曲目归属、曲目库、正文场景标记。 */
@Dao
interface BgmPoolDao {

    // ---- 分组 ----

    @Query("select * from bgm_pool_groups order by parentId, `order`, name")
    fun flowGroups(): Flow<List<BgmPoolGroupEntity>>

    @Query("select * from bgm_pool_groups order by parentId, `order`, name")
    suspend fun groups(): List<BgmPoolGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(vararg groups: BgmPoolGroupEntity)

    @Update
    suspend fun updateGroups(vararg groups: BgmPoolGroupEntity)

    @Delete
    suspend fun deleteGroup(group: BgmPoolGroupEntity)

    // ---- 池 ----

    @Query("select * from bgm_pools order by groupId, `order`, name")
    fun flowPools(): Flow<List<BgmPoolEntity>>

    @Query("select * from bgm_pools order by groupId, `order`, name")
    suspend fun pools(): List<BgmPoolEntity>

    @Query("select * from bgm_pools where name = :name limit 1")
    suspend fun poolByName(name: String): BgmPoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPools(vararg pools: BgmPoolEntity)

    @Delete
    suspend fun deletePool(pool: BgmPoolEntity)

    // ---- 池成员 ----

    @Query("select * from bgm_pool_members where poolId = :poolId")
    fun flowMembers(poolId: String): Flow<List<BgmPoolMember>>

    @Query("select * from bgm_pool_members where poolId = :poolId and enabled = 1")
    suspend fun enabledMembers(poolId: String): List<BgmPoolMember>

    @Query("select * from bgm_pool_members")
    suspend fun allMembers(): List<BgmPoolMember>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(vararg members: BgmPoolMember)

    @Query("delete from bgm_pool_members where poolId = :poolId and trackId = :trackId")
    suspend fun deleteMember(poolId: String, trackId: String)

    @Query("delete from bgm_pool_members where trackId = :trackId")
    suspend fun deleteMembersOfTrack(trackId: String)

    // ---- 曲目库 ----

    @Query("select * from bgm_tracks order by `order`, name")
    fun flowTracks(): Flow<List<BgmTrackEntity>>

    @Query("select * from bgm_tracks order by `order`, name")
    suspend fun tracks(): List<BgmTrackEntity>

    @Query("select * from bgm_tracks where id = :id")
    suspend fun track(id: String): BgmTrackEntity?

    @Query("select * from bgm_tracks where name = :name limit 1")
    suspend fun trackByName(name: String): BgmTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(vararg tracks: BgmTrackEntity)

    @Update
    suspend fun updateTracks(vararg tracks: BgmTrackEntity)

    @Delete
    suspend fun deleteTrack(track: BgmTrackEntity)

    // ---- 正文场景标记 ----

    @Query("select * from bgm_scene_marks where bookUrl = :bookUrl and chapterIndex = :chapterIndex order by paragraphOrdinal")
    suspend fun marks(bookUrl: String, chapterIndex: Int): List<BgmSceneMark>

    @Query("select * from bgm_scene_marks where bookUrl = :bookUrl and chapterIndex = :chapterIndex order by paragraphOrdinal")
    fun flowMarks(bookUrl: String, chapterIndex: Int): Flow<List<BgmSceneMark>>

    @Query("select * from bgm_scene_marks")
    suspend fun allMarks(): List<BgmSceneMark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarks(vararg marks: BgmSceneMark)

    @Query("delete from bgm_scene_marks where bookUrl = :bookUrl and chapterIndex = :chapterIndex and paragraphOrdinal = :ordinal")
    suspend fun deleteMark(bookUrl: String, chapterIndex: Int, ordinal: Int)

    @Query("delete from bgm_scene_marks where bookUrl = :bookUrl")
    suspend fun deleteMarksByBook(bookUrl: String)
}
