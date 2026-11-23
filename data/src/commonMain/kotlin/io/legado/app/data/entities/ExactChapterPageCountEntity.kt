package io.legado.app.data.entities

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * 精确分页计数：某本书某章在某种排版下有几页。
 *
 * 主键 (bookId, chapterId, layoutSignature)：正文变（contentHash）或排版参数变
 * （字号/行距/边距/页面尺寸 → layoutSignature）就不命中，需要重算；
 * engineVersion 让分页算法升级后旧值自然失效。
 *
 * 用它可以把「本章第 N 页」这类跨章位置算准，而不必把前序章节全部重新分页。
 */
@Entity(
    tableName = "exact_chapter_page_counts",
    primaryKeys = ["bookId", "chapterId", "layoutSignature"],
    indices = [Index(value = ["bookId", "layoutSignature"])],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["bookUrl"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExactChapterPageCountEntity(
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val contentHash: Long,
    val layoutSignature: Long,
    val engineVersion: Int,
    val pageCount: Int,
    val updatedAt: Long,
)
