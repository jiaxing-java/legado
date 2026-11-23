package io.legado.app.ui.settings.search

/**
 * 设置搜索的可检索文档。
 *
 * 这里是**已解析成字符串**的形态：资源 id 到文案的解析必须在 Composable 层完成
 * (stringResource 只能在组合里读)，纯算法层只认字符串，方便单测。
 *
 * @property path 结果里第二行展示的归属路径 (如 "主题设置")，也参与低权重匹配
 * @property description 页面/条目的一句话说明，参与中权重匹配
 * @property keywords 补充同义词 (如「语速 tts 朗读」)，中文场景下比标题匹配更关键
 */
data class SettingsSearchDoc(
    val id: String,
    val title: String,
    val path: String,
    val description: String = "",
    val keywords: String = "",
)

/**
 * 设置搜索打分。
 *
 * 规则与 MD3 版 `searchSettings` 保持一致，避免同一个关键词在两个版本里排序不同：
 * - 空白查询返回空表 (不返回全量，避免一打开就刷满列表)；
 * - 查询按空白切成多个 token，**每个** token 都必须在某个字段命中，否则淘汰；
 * - 完全相等 1000 / 前缀 400，再按命中字段累加 (标题 100 > 关键词 60 > 说明 30 > 路径 10)。
 *
 * 排序用 `sortedByDescending` (稳定排序)，同分时保持目录原序 —— 目录是按用户心智
 * 顺序手写的，同分保序比再引入一个字段权重更符合预期。
 */
fun searchSettings(docs: List<SettingsSearchDoc>, query: String): List<SettingsSearchDoc> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return emptyList()
    val tokens = normalized.split(WHITESPACE).filter { it.isNotEmpty() }.distinct()
    if (tokens.isEmpty()) return emptyList()
    return docs
        .mapNotNull { doc ->
            val title = doc.title.lowercase()
            val keywords = doc.keywords.lowercase()
            val description = doc.description.lowercase()
            val path = doc.path.lowercase()
            var score = when {
                title == normalized -> 1000
                title.startsWith(normalized) -> 400
                else -> 0
            }
            for (token in tokens) {
                score += when {
                    title.contains(token) -> 100
                    keywords.contains(token) -> 60
                    description.contains(token) -> 30
                    path.contains(token) -> 10
                    // 有一个 token 没命中就整条淘汰：多词查询是"缩小范围"，不是"扩大范围"
                    else -> return@mapNotNull null
                }
            }
            doc to score
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

private val WHITESPACE = Regex("\\s+")
