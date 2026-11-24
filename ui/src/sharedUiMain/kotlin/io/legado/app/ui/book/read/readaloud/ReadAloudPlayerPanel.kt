package io.legado.app.ui.book.read.readaloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.readaloud.ReadAloudUiState
import io.legado.app.ui.compose.component.AppDialogScaffold
import io.legado.app.ui.compose.component.AppSlider
import io.legado.app.ui.compose.component.AppSwitch
import io.legado.app.ui.compose.component.AppTextButton
import io.legado.app.ui.compose.platform.rememberPainter
import io.legado.app.ui.compose.platform.rememberString
import io.legado.app.ui.compose.theme.AppTheme
import io.legado.app.ui.compose.theme.AppTheme.DesignTokens

/**
 * 朗读播放页（全屏）：章节、进度、当前句，加上完整控制。
 *
 * # 为什么是「面板」而不是一条独立路由
 *
 * 状态机 `ReadAloudControllerShared` 的实例在平台宿主里（Android 前台服务、iOS
 * `IosReadAloudHost`…），而 `ReadAloudControls` 是**每个阅读页**在组合期创建的桥。独立路由
 * 拿不到这个桥，四端都得为它新增一次全局注入 —— 代价远大于收益。这里让播放页从朗读面板
 * 展开（`AppDialogScaffold(fullHeight = true)`，占满窗口），行为与独立页一致，
 * 而控制动作仍走调用方已经接好的那一套回调。
 *
 * 章节标题/当前句来自 [ReadAloudUiState]：那是控制器在换章、换段时顺手写的只读快照，
 * 播放页不用知道当前是哪本书、哪个播放器。
 */
@Composable
fun ReadAloudPlayerPanel(
    isPlaying: Boolean,
    initialTimer: Int,
    initialSpeechRate: Int,
    initialFollowSys: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevParagraph: () -> Unit,
    onNextParagraph: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onAdjustSpeed: (Float) -> Unit,
    onFollowSysChange: (Boolean) -> Unit,
    onOpenCast: () -> Unit,
    onOpenSettings: () -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = AppTheme.colors
    val snapshot by ReadAloudUiState.snapshot.collectAsState()
    var timer by remember(initialTimer) { mutableIntStateOf(initialTimer) }
    var speechRate by remember(initialSpeechRate) { mutableIntStateOf(initialSpeechRate) }
    var followSys by remember(initialFollowSys) { mutableStateOf(initialFollowSys) }

    AppDialogScaffold(
        onDismissRequest = onCollapse,
        title = "正在朗读",
        subtitle = snapshot.displayTitle.ifBlank { null },
        fullHeight = true,
        bottomBar = {
            AppTextButton(text = "角色配音", color = colors.secondaryText, onClick = onOpenCast)
            AppTextButton(text = "朗读设置", color = colors.secondaryText, onClick = onOpenSettings)
            Spacer(Modifier.weight(1f))
            AppTextButton(text = "收起", color = colors.accent, onClick = onCollapse)
        },
    ) {
        // 章节进度：章节号 + 总数，配一条细进度条（Material LinearProgressIndicator 在
        // 桌面端会带一层动画，这里用纯 Box 画，避免每秒重组时进度条自己抖）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingDefault),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (snapshot.chapterCount > 0) {
                    "第 ${snapshot.chapterIndex + 1} / ${snapshot.chapterCount} 章"
                } else {
                    "章节未知"
                },
                color = colors.secondaryText,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            if (isPlaying) {
                Text(text = "朗读中", color = colors.accent, fontSize = 12.sp)
            } else {
                Text(text = "已暂停", color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        ProgressLine(progress = snapshot.chapterProgress)

        // 当前句：整块可滚动，长段落不会被截断成一句
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingLg),
        ) {
            Text(
                text = snapshot.currentText.ifBlank {
                    "还没有开始朗读。点下方播放按钮，从当前进度开始。"
                },
                color = colors.primaryText,
                fontSize = 18.sp,
            )
        }

        // 主控制行：上一句 / 播放暂停 / 下一句 / 停止
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIcon("ic_skip_previous", "上一句", colors.primaryText, onPrevParagraph)
            PlayerIcon(
                if (isPlaying) "ic_pause_24dp" else "ic_play_24dp",
                rememberString(if (isPlaying) "pause" else "audio_play"),
                colors.primaryText,
                onPlayPause,
            )
            PlayerIcon("ic_skip_next", "下一句", colors.primaryText, onNextParagraph)
            PlayerIcon(
                "ic_stop_black_24dp",
                rememberString("stop"),
                colors.secondaryText,
                {
                    onStop()
                    onCollapse()
                },
            )
        }

        // 章节切换
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingDefault),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextButton(text = "上一章", color = colors.secondaryText, onClick = onPrevChapter)
            Spacer(Modifier.weight(1f))
            AppTextButton(text = "下一章", color = colors.secondaryText, onClick = onNextChapter)
        }

        // 语速 + 跟随系统
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingDefault),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "语速", color = colors.primaryText, fontSize = 14.sp)
            AppSlider(
                value = speechRate,
                max = 45,
                onValueChange = { speechRate = it },
                onValueChangeFinished = { onAdjustSpeed(it.toFloat()) },
                color = DesignTokens.arcoBlue6,
                trackColor = DesignTokens.arcoBlue6.copy(alpha = 0.2f),
                modifier = Modifier.weight(1f),
            )
            Text(text = "$speechRate", color = colors.primaryText, fontSize = 14.sp)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "跟随系统语速", color = colors.primaryText, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            AppSwitch(
                checked = followSys,
                onCheckedChange = {
                    followSys = it
                    onFollowSysChange(it)
                },
            )
        }

        // 定时
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingDefault),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "定时关闭", color = colors.primaryText, fontSize = 14.sp)
            AppSlider(
                value = timer,
                max = 180,
                onValueChange = { timer = it },
                onValueChangeFinished = { onSetTimer(it) },
                color = DesignTokens.arcoBlue6,
                trackColor = DesignTokens.arcoBlue6.copy(alpha = 0.2f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (timer <= 0) "关" else "${timer} 分钟",
                color = colors.primaryText,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onSetTimer(timer) },
            )
        }
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacingDefault, vertical = DesignTokens.spacingXs)
            .height(3.dp)
            .background(colors.secondaryText.copy(alpha = 0.2f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(colors.accent),
        )
    }
}

@Composable
private fun PlayerIcon(
    iconKey: String,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Icon(
        painter = rememberPainter(iconKey),
        contentDescription = description,
        tint = tint,
        modifier = Modifier
            .padding(horizontal = DesignTokens.spacingMd)
            .size(32.dp)
            .clickable(onClick = onClick),
    )
}
