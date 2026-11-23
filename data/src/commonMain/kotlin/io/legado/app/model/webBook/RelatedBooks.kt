package io.legado.app.model.webBook

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.Debug
import io.legado.app.utils.KS_JSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

/**
 * 详情页「关联书籍」解析（同作者 / 同类推荐等）。
 *
 * 源在 `ruleBookInfo.relatedBooks` 里声明一组模块：
 * ```json
 * [{"title":"同作者","url":"/author/{{$.author}}"},{"title":"同类","url":"/kind/{{$.kind}}"}]
 * ```
 * 每个模块按「发现」的口径取一页书（`isSearch = false`），各自独立请求、独立失败 ——
 * 单个模块的规则写错不能连累其他模块，也不能让详情页整体报错。
 *
 * [load] 的第二个重载把"怎么取书"和"怎么记日志"做成参数，便于单测：真实调用走
 * [WebBook.getBookListAwait]，测试直接塞一个假的 explore。
 */
object RelatedBooks {

    /** 一个已解析出内容的模块。[books] 已剔除当前正在看的这本书。 */
    data class Module(
        /** 稳定身份（源里没配 key 时退回标题），UI 用它做列表 key */
        val key: String,
        val title: String,
        val url: String,
        val books: List<SearchBook>,
    )

    suspend fun load(book: Book, source: BookSource): List<Module> = load(
        book = book,
        modulesJson = source.bookInfoRule.relatedBooks,
        explore = { url ->
            // 官方"发现"口径: isSearch=false 时 key 就是发现 URL, 不再套 searchUrl
            WebBook.getBookListAwait(
                bookSource = source,
                key = url,
                page = 1,
                isSearch = false,
            ).books
        },
        log = { message, error ->
            // 关联模块是可选内容, 不能把 state 置 -1 提前关掉整个调试会话
            Debug.log(
                source.bookSourceUrl,
                message + (error?.let { "\n${it.stackTraceToString()}" } ?: ""),
            )
            if (error != null) AppLog.put("${source.bookSourceName}: $message", error)
        },
    )

    internal suspend fun load(
        book: Book,
        modulesJson: String?,
        explore: suspend (String) -> List<SearchBook>,
        log: (String, Throwable?) -> Unit,
    ): List<Module> {
        if (modulesJson.isNullOrBlank()) return emptyList()
        log("︾开始解析关联书籍 relatedBooks", null)
        val definitions = KS_JSON.fromJsonArray<Definition>(modulesJson).getOrElse {
            log("关联书籍 relatedBooks 配置解析失败", it)
            return emptyList()
        }
        val modules = coroutineScope {
            definitions.mapIndexed { index, def ->
                async {
                    val label = "关联书籍 relatedBooks[${index + 1}] ${def.title.orEmpty()}"
                    // 只裁首尾空白: JS 字符串与关键字之间的空格是规则的一部分, 不能一起删
                    val url = def.url?.trim()
                    if (url.isNullOrBlank()) {
                        log("$label: url 为空，跳过", IllegalArgumentException("url 不能为空"))
                        return@async null
                    }
                    try {
                        log("$label: 开始请求 $url", null)
                        val books = explore(url)
                        // 推荐位里出现"当前这本书"是纯噪声
                        val filtered = books.filter { it.bookUrl != book.bookUrl }
                        log("$label: 获取 ${books.size} 本，过滤当前书籍后 ${filtered.size} 本", null)
                        Module(
                            key = def.key ?: def.title.orEmpty(),
                            title = def.title.orEmpty(),
                            url = url,
                            books = filtered,
                        ).takeIf { filtered.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("$label: 请求或解析失败", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        log("︽关联书籍 relatedBooks 解析完成，显示 ${modules.size} 个模块", null)
        return modules
    }

    @Serializable
    private data class Definition(
        val key: String? = null,
        val title: String? = null,
        val url: String? = null,
    )
}
