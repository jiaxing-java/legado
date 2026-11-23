package io.legado.app.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.legado.app.data.entities.ExactChapterPageCountEntity

/** 精确分页计数（按书 + 排版签名缓存，用于跨章页码换算）。 */
@Dao
interface ExactChapterPageCountDao {

    @Query("select * from exact_chapter_page_counts where bookId = :bookId and layoutSignature = :layoutSignature")
    suspend fun byLayout(bookId: String, layoutSignature: Long): List<ExactChapterPageCountEntity>

    @Query("select * from exact_chapter_page_counts where bookId = :bookId and chapterId = :chapterId and layoutSignature = :layoutSignature")
    suspend fun get(bookId: String, chapterId: String, layoutSignature: Long): ExactChapterPageCountEntity?

    @Query("select sum(pageCount) from exact_chapter_page_counts where bookId = :bookId and layoutSignature = :layoutSignature and chapterIndex < :chapterIndex")
    suspend fun pagesBefore(bookId: String, layoutSignature: Long, chapterIndex: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg counts: ExactChapterPageCountEntity)

    @Query("delete from exact_chapter_page_counts where bookId = :bookId and layoutSignature = :layoutSignature")
    suspend fun clearLayout(bookId: String, layoutSignature: Long)

    @Query("delete from exact_chapter_page_counts where bookId = :bookId")
    suspend fun clearBook(bookId: String)

    @Query("delete from exact_chapter_page_counts")
    suspend fun clearAll()
}
