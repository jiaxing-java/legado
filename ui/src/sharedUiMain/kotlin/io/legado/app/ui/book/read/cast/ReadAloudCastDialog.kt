package io.legado.app.ui.book.read.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.help.readaloud.CastSegment
import io.legado.app.help.readaloud.ReadAloudCastPlanner
import io.legado.app.help.readaloud.ReadAloudCastRepository
import io.legado.app.ui.compose.component.AppDialogScaffold
import io.legado.app.ui.compose.component.AppSwitch
import io.legado.app.ui.compose.component.AppTextButton
import io.legado.app.ui.compose.component.AppTextField
import io.legado.app.ui.compose.theme.AppTheme
import io.legado.app.ui.compose.theme.AppTheme.DesignTokens
import kotlinx.coroutines.launch
import legado.ui.generated.resources.Res
import legado.ui.generated.resources.cancel
import legado.ui.generated.resources.delete
import legado.ui.generated.resources.ok
import org.jetbrains.compose.resources.stringResource

/**
 * 角色配音（多角色朗读）管理页。
 *
 * 三个页签对应矩阵里三条独立能力：
 * - **角色**：`cast_characters` 的增删改（名字 / 声音池 / 音色 / 变声预设）—— `RAL-CAST-01`
 * - **本章台词**：按朗读链路已解析出的引号句逐句指定角色 —— `RAL-CAST-02`
 * - **正则规则**：命中正文文字就换音色（`regex_cast_rules`）—— `RAL-CAST-03`
 *
 * # 为什么「本章台词」不自己切段
 *
 * 切段规则有四种模式（段落 / 句读 / 自定义符号），UI 再实现一遍必然与播放链路不一致：
 * 同一句在页面上是第 3 句、播放时算第 4 句，落库的 `quoteOrdinal` 就指错人。这里直接读
 * [ReadAloudCastPlanner.cachedPlan] —— 那是**朗读真正用过的那份解析结果**，序号同源。
 * 代价是本章还没播过时没有数据，此时给的是操作提示而不是空列表。
 *
 * # 音色选择为什么直接列引擎音色
 *
 * 声音池在本轮的语义是「角色分类标签」：池 → 音色的多对多表（`voice_pool_members`）还没
 * 有分配入口，先按「角色直接绑音色 + 池只做归类」落地，等池内成员管理补齐后再插一层筛选。
 * 这样用户现在就能给角色挑音色并立刻听到多角色效果，而不是等全链路做完才有得用。
 */
@Composable
fun ReadAloudCastDialog(
    bookUrl: String,
    bookName: String,
    chapterIndex: Int,
    onDismissRequest: () -> Unit,
) {
    val colors = AppTheme.colors
    val repo = remember { ReadAloudCastRepository.get() }
    val scope = rememberCoroutineScope()

    val characters by repo.flowCharacters(bookUrl).collectAsState(emptyList())
    val pools by repo.flowPools().collectAsState(emptyList())
    val voices by repo.flowAvailableVoices().collectAsState(emptyList())
    val effects by repo.flowVoiceEffects().collectAsState(emptyList())
    val assignments by repo.flowAssignments(bookUrl, chapterIndex).collectAsState(emptyList())
    val rules by repo.flowRegexRules().collectAsState(emptyList())

    // 首次进入铺 13 个默认池：空表会让「声音池」选择器里只有一个「未分组」，像是功能坏了
    // 同一次进入把引擎音色同步进目录：目录为空时角色根本挑不到音色，多角色就是空转
    LaunchedEffect(bookUrl) {
        repo.seedDefaultPoolsIfEmpty()
        runCatching {
            val voices = io.legado.app.help.tts.TtsEngineProvider.get()?.listVoices().orEmpty()
            if (voices.isNotEmpty()) {
                repo.syncEngineVoices(
                    engineType = ReadAloudCastRepository.ENGINE_TYPE_SYSTEM,
                    engineId = "system_default",
                    voices = voices,
                )
            }
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var editingCharacter by remember { mutableStateOf<CastCharacter?>(null) }
    var editingRule by remember { mutableStateOf<RegexCastRule?>(null) }

    val title = if (editingCharacter != null || editingRule != null) {
        if (editingRule != null) "编辑规则" else "编辑角色"
    } else {
        "角色配音"
    }

    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = bookName.ifBlank { null },
        fullHeight = true,
        onBack = {
            // 表单态下返回箭头退回列表，而不是直接关掉整个页面
            when {
                editingCharacter != null -> editingCharacter = null
                editingRule != null -> editingRule = null
                else -> onDismissRequest()
            }
        },
        bottomBar = {
            val character = editingCharacter
            val rule = editingRule
            when {
                character != null -> {
                    AppTextButton(
                        text = stringResource(Res.string.cancel),
                        color = colors.secondaryText,
                        onClick = { editingCharacter = null },
                    )
                    Spacer(Modifier.weight(1f))
                    AppTextButton(
                        text = stringResource(Res.string.ok),
                        enabled = character.name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                repo.saveCharacter(character)
                                ReadAloudCastPlanner.invalidate()
                                editingCharacter = null
                            }
                        },
                    )
                }

                rule != null -> {
                    AppTextButton(
                        text = stringResource(Res.string.cancel),
                        color = colors.secondaryText,
                        onClick = { editingRule = null },
                    )
                    Spacer(Modifier.weight(1f))
                    AppTextButton(
                        text = stringResource(Res.string.ok),
                        enabled = rule.pattern.isNotBlank() || !rule.useRegex,
                        onClick = {
                            scope.launch {
                                repo.saveRegexRule(rule)
                                ReadAloudCastPlanner.invalidate()
                                editingRule = null
                            }
                        },
                    )
                }

                else -> {
                    AppTextButton(
                        text = if (tab == 2) "新增规则" else "新增角色",
                        color = colors.accent,
                        onClick = {
                            if (tab == 2) {
                                editingRule = RegexCastRule(name = "", pattern = "")
                            } else {
                                editingCharacter = newCharacter(bookUrl)
                            }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        },
    ) {
        val character = editingCharacter
        val rule = editingRule
        when {
            character != null -> CharacterForm(
                character = character,
                pools = pools,
                voices = voices,
                effects = effects,
                onChange = { editingCharacter = it },
            )

            rule != null -> RuleForm(
                rule = rule,
                voices = voices,
                onChange = { editingRule = it },
            )

            else -> {
                TabRow(tab = tab, onSelect = { tab = it })
                when (tab) {
                    0 -> NarratorVoiceRow(voices = voices)
                    else -> Unit
                }
                when (tab) {
                    0 -> CharacterList(
                        characters = characters,
                        voices = voices,
                        onEdit = { editingCharacter = it },
                        onDelete = { target ->
                            scope.launch {
                                repo.deleteCharacter(target)
                                ReadAloudCastPlanner.invalidate()
                            }
                        },
                    )

                    1 -> ChapterAssignmentList(
                        bookUrl = bookUrl,
                        chapterIndex = chapterIndex,
                        characters = characters,
                        assignments = assignments,
                        onAssign = { ordinal, target ->
                            scope.launch {
                                repo.assign(bookUrl, chapterIndex, ordinal, target)
                                ReadAloudCastPlanner.invalidate()
                            }
                        },
                        onUnassign = { ordinal ->
                            scope.launch {
                                repo.unassign(bookUrl, chapterIndex, ordinal)
                                ReadAloudCastPlanner.invalidate()
                            }
                        },
                    )

                    else -> RuleList(
                        rules = rules,
                        onEdit = { editingRule = it },
                        onToggle = { target, enabled ->
                            scope.launch {
                                repo.saveRegexRule(target.copy(enabled = enabled))
                                ReadAloudCastPlanner.invalidate()
                            }
                        },
                        onDelete = { target ->
                            scope.launch {
                                repo.deleteRegexRule(target)
                                ReadAloudCastPlanner.invalidate()
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun newCharacter(bookUrl: String) = CastCharacter(
    id = "",
    bookUrl = bookUrl,
    name = "",
    poolLabel = "",
)

@Composable
private fun TabRow(tab: Int, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacingDefault),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        listOf("角色", "本章台词", "正则规则").forEachIndexed { index, label ->
            val selected = tab == index
            Text(
                text = label,
                color = if (selected) colors.accent else colors.secondaryText,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(vertical = DesignTokens.spacingDefault),
            )
        }
    }
}

/**
 * 旁白音色：多数书 90% 的段落是旁白，换一个更合适的嗓子比给配角挑音色更影响听感。
 *
 * 存 `PreferKey.ttsVoice`（值 = `read_aloud_voices.id`），由
 * [io.legado.app.help.readaloud.ReadAloudCastPlanner] 在建计划时读一次。改完不重排当前章：
 * 已经播出去的音频不受影响，下一章或重新点播放才生效 —— 与语速、分段方式的处理一致。
 */
@Composable
private fun NarratorVoiceRow(voices: List<ReadAloudVoiceEntity>) {
    val prefs = remember { io.legado.app.help.config.PreferenceProviders.get() }
    var selected by remember {
        mutableStateOf(prefs.getString(io.legado.app.constant.PreferKey.ttsVoice, ""))
    }
    PickerRow(
        label = "旁白音色",
        value = voices.firstOrNull { it.id == selected }?.displayName ?: "引擎默认",
        options = listOf("") + voices.map { it.id },
        optionLabel = { id -> voices.firstOrNull { it.id == id }?.displayName ?: "引擎默认" },
        onSelect = {
            selected = it
            prefs.putString(io.legado.app.constant.PreferKey.ttsVoice, it)
        },
    )
}

@Composable
private fun CharacterList(
    characters: List<CastCharacter>,
    voices: List<ReadAloudVoiceEntity>,
    onEdit: (CastCharacter) -> Unit,
    onDelete: (CastCharacter) -> Unit,
) {
    val colors = AppTheme.colors
    val voiceNames = remember(voices) { voices.associate { it.id to it.displayName } }
    if (characters.isEmpty()) {
        EmptyHint("还没有角色。新增角色并绑定音色后，本章台词才能按人换声。")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxWidth()) {
        items(characters, key = { it.id }) { character ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val voice = voiceNames[character.voiceId] ?: "未绑定音色"
                    val pool = character.poolLabel.ifBlank { "未分组" }
                    Text(
                        text = "$pool · $voice",
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppTextButton(text = "编辑", color = colors.secondaryText, onClick = { onEdit(character) })
                AppTextButton(
                    text = stringResource(Res.string.delete),
                    color = colors.secondaryText,
                    onClick = { onDelete(character) },
                )
            }
        }
    }
}

@Composable
private fun CharacterForm(
    character: CastCharacter,
    pools: List<VoicePoolEntity>,
    voices: List<ReadAloudVoiceEntity>,
    effects: List<VoiceEffectPreset>,
    onChange: (CastCharacter) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacingDefault),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
    ) {
        AppTextField(
            value = character.name,
            onValueChange = { onChange(character.copy(name = it)) },
            label = "角色名",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PickerRow(
            label = "声音池",
            value = character.poolLabel.ifBlank { "未分组" },
            options = listOf("") + pools.map { it.name },
            optionLabel = { it.ifBlank { "未分组" } },
            onSelect = { onChange(character.copy(poolLabel = it)) },
        )
        PickerRow(
            label = "音色",
            value = voices.firstOrNull { it.id == character.voiceId }?.displayName ?: "引擎默认",
            options = listOf("") + voices.map { it.id },
            optionLabel = { id ->
                voices.firstOrNull { it.id == id }?.displayName ?: "引擎默认"
            },
            onSelect = { onChange(character.copy(voiceId = it)) },
        )
        PickerRow(
            label = "变声效果",
            value = character.voiceEffect.ifBlank { "不变声" },
            options = listOf("") + effects.map { it.name },
            optionLabel = { it.ifBlank { "不变声" } },
            onSelect = { onChange(character.copy(voiceEffect = it)) },
        )
        if (voices.isEmpty()) {
            Text(
                text = "音色目录为空：先到「朗读设置 → 系统语音引擎」里选一次引擎，音色会随引擎一起读出来。",
                color = AppTheme.colors.secondaryText,
                modifier = Modifier.padding(vertical = DesignTokens.spacingXs),
            )
        }
    }
}

@Composable
private fun ChapterAssignmentList(
    bookUrl: String,
    chapterIndex: Int,
    characters: List<CastCharacter>,
    assignments: List<ChapterRoleAssignment>,
    onAssign: (Int, CastCharacter) -> Unit,
    onUnassign: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    // 分配变化后 planner 缓存已失效，重新取一次才会显示新角色
    val plan: List<CastSegment>? = remember(bookUrl, chapterIndex, assignments, characters) {
        ReadAloudCastPlanner.cachedPlan(bookUrl, chapterIndex)
    }
    var assigningOrdinal by remember { mutableStateOf<Int?>(null) }

    if (plan.isNullOrEmpty()) {
        EmptyHint(
            "本章还没有解析结果。\n可以先去听一段（朗读会自动识别台词），再回来逐句指定角色。",
        )
        return
    }
    val assignmentByOrdinal = remember(assignments) { assignments.associateBy { it.quoteOrdinal } }
    LazyColumn(Modifier.fillMaxWidth().fillMaxWidth()) {
        items(plan.size) { index ->
            val segment = plan[index]
            val ordinal = segment.quoteOrdinal
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = segment.text.trim(),
                        color = colors.primaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val speaker = if (ordinal == null) {
                        "旁白"
                    } else {
                        assignmentByOrdinal[ordinal]?.characterName
                            ?: segment.voice?.characterName?.takeIf { it.isNotBlank() }
                            ?: "未指定"
                    }
                    Text(text = speaker, color = colors.secondaryText)
                }
                if (ordinal != null) {
                    AppTextButton(
                        text = "指定",
                        color = colors.secondaryText,
                        onClick = { assigningOrdinal = ordinal },
                    )
                    if (assignmentByOrdinal[ordinal] != null) {
                        AppTextButton(
                            text = "清除",
                            color = colors.secondaryText,
                            onClick = { onUnassign(ordinal) },
                        )
                    }
                }
            }
        }
    }
    val target = assigningOrdinal
    if (target != null) {
        AppDialogScaffold(
            onDismissRequest = { assigningOrdinal = null },
            title = "第 ${target + 1} 句由谁念",
            fullHeight = false,
        ) {
            if (characters.isEmpty()) {
                EmptyHint("还没有角色，先到「角色」页签新增。")
            } else {
                characters.forEach { character ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAssign(target, character)
                                assigningOrdinal = null
                            }
                            .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingMd),
                    ) {
                        Text(character.name, color = colors.primaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleList(
    rules: List<RegexCastRule>,
    onEdit: (RegexCastRule) -> Unit,
    onToggle: (RegexCastRule, Boolean) -> Unit,
    onDelete: (RegexCastRule) -> Unit,
) {
    val colors = AppTheme.colors
    if (rules.isEmpty()) {
        EmptyHint("还没有规则。规则按正文文字认人：出现「系统提示」这两个字就换成机械音，不论在谁的台词里。")
        return
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxWidth()) {
        items(rules, key = { it.id }) { rule ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rule.name.ifBlank { rule.pattern },
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = rule.pattern,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppSwitch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
                AppTextButton(text = "编辑", color = colors.secondaryText, onClick = { onEdit(rule) })
                AppTextButton(
                    text = stringResource(Res.string.delete),
                    color = colors.secondaryText,
                    onClick = { onDelete(rule) },
                )
            }
        }
    }
}

@Composable
private fun RuleForm(
    rule: RegexCastRule,
    voices: List<ReadAloudVoiceEntity>,
    onChange: (RegexCastRule) -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacingDefault),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
    ) {
        AppTextField(
            value = rule.name,
            onValueChange = { onChange(rule.copy(name = it)) },
            label = "规则名（只用于辨认）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = rule.pattern,
            onValueChange = { onChange(rule.copy(pattern = it)) },
            label = "匹配内容",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("按正则匹配", color = colors.primaryText, modifier = Modifier.weight(1f))
            AppSwitch(
                checked = rule.useRegex,
                onCheckedChange = { onChange(rule.copy(useRegex = it)) },
            )
        }
        PickerRow(
            label = "命中后用音色",
            value = voices.firstOrNull { it.id == rule.itemId }?.displayName ?: "未选择（规则不生效）",
            options = listOf("") + voices.map { it.id },
            optionLabel = { id -> voices.firstOrNull { it.id == id }?.displayName ?: "未选择" },
            onSelect = { onChange(rule.copy(itemId = it)) },
        )
        PickerRow(
            label = "变声效果",
            value = rule.voiceEffect.ifBlank { "不变声" },
            options = listOf("", "慢速", "低沉", "机械"),
            optionLabel = { it.ifBlank { "不变声" } },
            onSelect = { onChange(rule.copy(voiceEffect = it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用", color = colors.primaryText, modifier = Modifier.weight(1f))
            AppSwitch(
                checked = rule.enabled,
                onCheckedChange = { onChange(rule.copy(enabled = it)) },
            )
        }
    }
}

/**
 * 一行「标签 — 当前值 ▾」。点开是 Material 下拉菜单。
 *
 * 没有用通用选择对话框：表单里连续三个选择项，弹层套弹层会让返回键行为变得不可预期
 * （外层对话框也可能一起关掉），下拉菜单让整张表单始终留在同一层。
 */
@Composable
private fun <T> PickerRow(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = DesignTokens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = colors.primaryText, modifier = Modifier.weight(1f))
            Text(value, color = colors.secondaryText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(option)
                }) {
                    Text(optionLabel(option), color = colors.primaryText)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = AppTheme.colors.secondaryText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(DesignTokens.spacingLg),
    )
}
