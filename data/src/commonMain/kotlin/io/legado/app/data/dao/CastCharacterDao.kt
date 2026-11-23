package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import io.legado.app.data.entities.BookCastMemory
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import kotlinx.coroutines.flow.Flow

/**
 * 多角色朗读：角色档案、正文分配、本书角色记忆。
 *
 * 三者生命周期一致（都按 bookUrl 挂），删除书时由上层统一清。
 */
@Dao
interface CastCharacterDao {

    @Query("select * from cast_characters order by sortOrder, name")
    fun flowAll(): Flow<List<CastCharacter>>

    @Query("select * from cast_characters order by sortOrder, name")
    suspend fun all(): List<CastCharacter>

    @Query("select * from cast_characters where bookUrl = :bookUrl order by sortOrder, name")
    fun flowByBook(bookUrl: String): Flow<List<CastCharacter>>

    @Query("select * from cast_characters where bookUrl = :bookUrl order by sortOrder, name")
    suspend fun byBook(bookUrl: String): List<CastCharacter>

    @Query("select * from cast_characters where id = :id")
    suspend fun get(id: String): CastCharacter?

    @Query("select * from cast_characters where bookUrl = :bookUrl and name = :name and poolLabel = :poolLabel")
    suspend fun find(bookUrl: String, name: String, poolLabel: String): CastCharacter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg characters: CastCharacter)

    @Update
    suspend fun update(vararg characters: CastCharacter)

    @Delete
    suspend fun delete(vararg characters: CastCharacter)

    @Query("delete from cast_characters where bookUrl = :bookUrl")
    suspend fun deleteByBook(bookUrl: String)

    @Query("select max(sortOrder) from cast_characters where bookUrl = :bookUrl")
    suspend fun maxSortOrder(bookUrl: String): Int?

    // ---- 正文分配 ----

    @Query("select * from chapter_role_assignments where bookUrl = :bookUrl and chapterIndex = :chapterIndex order by quoteOrdinal")
    suspend fun assignments(bookUrl: String, chapterIndex: Int): List<ChapterRoleAssignment>

    @Query("select * from chapter_role_assignments where bookUrl = :bookUrl and chapterIndex = :chapterIndex order by quoteOrdinal")
    fun flowAssignments(bookUrl: String, chapterIndex: Int): Flow<List<ChapterRoleAssignment>>

    @Query("select * from chapter_role_assignments")
    suspend fun allAssignments(): List<ChapterRoleAssignment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignments(vararg assignments: ChapterRoleAssignment)

    @Query("delete from chapter_role_assignments where bookUrl = :bookUrl and chapterIndex = :chapterIndex and quoteOrdinal = :quoteOrdinal")
    suspend fun deleteAssignment(bookUrl: String, chapterIndex: Int, quoteOrdinal: Int)

    @Query("delete from chapter_role_assignments where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    suspend fun deleteAssignments(bookUrl: String, chapterIndex: Int)

    @Query("delete from chapter_role_assignments where characterId = :characterId")
    suspend fun deleteAssignmentsOfCharacter(characterId: String)

    @Query("delete from chapter_role_assignments where bookUrl = :bookUrl")
    suspend fun deleteAssignmentsByBook(bookUrl: String)

    // ---- 本书角色记忆 ----

    @Query("select * from book_cast_memory where bookUrl = :bookUrl")
    suspend fun castMemory(bookUrl: String): BookCastMemory?

    @Query("select * from book_cast_memory")
    suspend fun allCastMemories(): List<BookCastMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCastMemory(memory: BookCastMemory)

    @Query("delete from book_cast_memory where bookUrl = :bookUrl")
    suspend fun deleteCastMemory(bookUrl: String)
}
