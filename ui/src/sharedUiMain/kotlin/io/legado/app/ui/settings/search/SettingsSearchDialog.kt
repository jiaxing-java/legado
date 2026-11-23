package io.legado.app.ui.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.compose.component.AlertButton
import io.legado.app.ui.compose.component.AppAlertDialog
import io.legado.app.ui.compose.component.AppDialogScaffold
import io.legado.app.ui.compose.component.AppSearchField
import io.legado.app.ui.compose.preference.PreferenceRow
import io.legado.app.ui.compose.theme.AppTheme
import io.legado.app.ui.compose.theme.AppTheme.DesignTokens
import legado.ui.generated.resources.Res
import legado.ui.generated.resources.ok
import legado.ui.generated.resources.setting
import legado.ui.generated.resources.settings_search_clear
import legado.ui.generated.resources.settings_search_count
import legado.ui.generated.resources.settings_search_empty
import legado.ui.generated.resources.settings_search_filter_count
import legado.ui.generated.resources.settings_search_filter_empty
import legado.ui.generated.resources.settings_search_filter_hint
import legado.ui.generated.resources.settings_search_hint
import legado.ui.generated.resources.settings_search_prompt
import legado.ui.generated.resources.settings_search_reader_only
import legado.ui.generated.resources.settings_search_reader_only_message
import legado.ui.generated.resources.settings_search_title
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置搜索对话框。
 *
 * 检索范围见 [settingsSearchCatalog]：fork 真实存在、有入口的设置页。点击结果后由
 * 调用方决定怎么打开（[onOpenPage]），本组件不碰导航，也不改任何设置。
 *
 * 结果仍然是"页"粒度：点进去后目标页的搜索栏会自动带上关键词并过滤到匹配项。
 * 这样新增设置项不需要回来改索引 —— 索引腐化是条目级搜索最容易踩的坑。
 *
 * 「仅阅读中」的那条**不禁用**：禁用会让用户以为这个设置有 bug。点击弹说明，
 * 告诉他从哪儿进，属于显式降级而不是静默失败。
 */
@Composable
fun SettingsSearchDialog(
    onDismissRequest: () -> Unit,
    onOpenPage: (SettingsSearchPageDoc) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var readerOnlyPage by remember { mutableStateOf<SettingsSearchPageDoc?>(null) }
    val colors = AppTheme.colors
    val settingPath = stringResource(Res.string.setting)

    // 资源到文案的解析只能在组合里做 (stringResource 是 @Composable)。目录只有 6 条，
    // 每次重组重算的开销远小于加一层 remember 缓存带来的引用比较成本。
    val resolved = settingsSearchCatalog.map { page ->
        SettingsSearchDoc(
            id = page.id,
            title = stringResource(page.title),
            path = settingPath,
            description = stringResource(page.summary),
            keywords = page.keywords,
        )
    }
    val results = searchSettings(resolved, query)
    val readerOnlyLabel = stringResource(Res.string.settings_search_reader_only)

    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.settings_search_title),
        fullHeight = true,
        contentPadding = PaddingValues(0.dp),
    ) {
        AppSearchField(
            value = query,
            onValueChange = { query = it },
            hint = stringResource(Res.string.settings_search_hint),
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingLg,
                bottom = DesignTokens.spacingMd,
            ),
        )
        Text(
            text = when {
                query.isBlank() -> stringResource(Res.string.settings_search_prompt)
                results.isEmpty() -> stringResource(Res.string.settings_search_empty)
                else -> stringResource(Res.string.settings_search_count, results.size)
            },
            color = colors.secondaryText,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingLg,
                bottom = DesignTokens.spacingDefault,
            ),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingLg,
                bottom = DesignTokens.spacingLg,
            ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingDefault),
        ) {
            items(results, key = { it.id }) { doc ->
                val page = settingsSearchCatalog.firstOrNull { it.id == doc.id }
                if (page != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        PreferenceRow(
                            title = doc.title,
                            summary = doc.description,
                            onClick = {
                                if (page.reach is SettingsSearchReach.ReaderOnly) {
                                    readerOnlyPage = page
                                } else {
                                    onOpenPage(page)
                                }
                            },
                            widget = if (page.reach is SettingsSearchReach.ReaderOnly) {
                                {
                                    Text(
                                        text = readerOnlyLabel,
                                        color = colors.accent,
                                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    )
                                }
                            } else null,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = DesignTokens.spacingLg),
                        )
                    }
                }
            }
        }
    }

    readerOnlyPage?.let { page ->
        AppAlertDialog(
            onDismissRequest = { readerOnlyPage = null },
            title = stringResource(page.title),
            message = stringResource(
                Res.string.settings_search_reader_only_message,
                stringResource(page.title),
            ),
            okButton = AlertButton(
                text = stringResource(Res.string.ok),
                onClick = { readerOnlyPage = null },
            ),
            cancelButton = null,
        )
    }
}

/** 页内搜索栏：设置页从搜索结果跳进来时显示，带关键词时同时给出筛选计数。 */
@Composable
fun SettingsPageSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matched: Int,
    total: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        AppSearchField(
            value = query,
            onValueChange = onQueryChange,
            hint = stringResource(Res.string.settings_search_filter_hint),
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingLg,
                top = DesignTokens.spacingDefault,
            ),
        )
        Text(
            text = if (matched == 0 && total > 0) {
                stringResource(Res.string.settings_search_filter_empty)
            } else {
                stringResource(
                    Res.string.settings_search_filter_count,
                    matched,
                    total,
                )
            },
            color = AppTheme.colors.secondaryText,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(
                start = DesignTokens.spacingLg,
                end = DesignTokens.spacingLg,
                bottom = DesignTokens.spacingDefault,
            ),
        )
    }
}
