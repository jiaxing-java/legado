package io.legado.app.data.entities.rule

import kotlinx.serialization.Serializable

/**
 * 书籍详情页规则
 */
@Serializable
data class BookInfoRule(
    var init: String? = null,
    var name: String? = null,
    var author: String? = null,
    var intro: String? = null,
    var kind: String? = null,
    var lastChapter: String? = null,
    var updateTime: String? = null,
    var coverUrl: String? = null,
    var tocUrl: String? = null,
    var wordCount: String? = null,
    var canReName: String? = null,
    var downloadUrls: String? = null,
    /**
     * 关联书籍模块定义（JSON 数组，元素形如 `{"title":"同类推荐","url":"..."}`）。
     *
     * 与 [bookSource.ruleBookInfo] 同一条 JSON 字符串载体，因此源编辑器里可直接手写；
     * 解析见 `model/webBook/RelatedBooks.kt`。可选内容，缺省不显示推荐位。
     */
    var relatedBooks: String? = null
) {

    companion object {
        // 原 Gson jsonDeserializer 已移除, 反序列化由 RulePolymorphicSerializer 复刻
    }

}
