package io.legado.app.ui.book.read.config

import androidx.compose.runtime.Composable
import io.legado.app.constant.PreferKey
import io.legado.app.help.tts.ReadAloudQueue
import io.legado.app.ui.compose.preference.PreferenceScreen
import io.legado.app.ui.compose.preference.editTextPreference
import io.legado.app.ui.compose.preference.listPreference
import io.legado.app.ui.compose.preference.preference
import io.legado.app.ui.compose.preference.preferenceCategory
import io.legado.app.ui.compose.preference.switchPreference
import io.legado.app.ui.compose.theme.AppTheme
import legado.ui.generated.resources.Res
import legado.ui.generated.resources.pause_read_aloud_while_phone_calls_summary
import legado.ui.generated.resources.pause_read_aloud_while_phone_calls_title
import legado.ui.generated.resources.pref_media_button_per_next
import legado.ui.generated.resources.pref_media_button_per_next_summary
import legado.ui.generated.resources.read_aloud_by_page
import legado.ui.generated.resources.read_aloud_by_page_summary
import legado.ui.generated.resources.read_aloud_category_engine
import legado.ui.generated.resources.read_aloud_category_play
import legado.ui.generated.resources.read_aloud_category_split
import legado.ui.generated.resources.read_aloud_split_mode
import legado.ui.generated.resources.read_aloud_split_mode_paragraph
import legado.ui.generated.resources.read_aloud_split_mode_sentence
import legado.ui.generated.resources.read_aloud_split_mode_summary
import legado.ui.generated.resources.read_aloud_split_symbols
import legado.ui.generated.resources.read_aloud_split_symbols_summary
import legado.ui.generated.resources.read_aloud_wake_lock
import legado.ui.generated.resources.read_aloud_wake_lock_summary
import legado.ui.generated.resources.speak_engine
import legado.ui.generated.resources.stream_read_aloud_audio
import legado.ui.generated.resources.stream_read_aloud_audio_summary
import legado.ui.generated.resources.sys_tts_config
import legado.ui.generated.resources.sys_tts_config_summary
import org.jetbrains.compose.resources.stringResource

/**
 * 朗读设置（迁 pref_config_aloud.xml）。逐条对齐原条目顺序/key/默认值，
 * 但"忽略音频焦点"只留其它设置页那一处（原版两页各挂一份同 key 开关）。
 * pauseReadAloudWhilePhoneCalls 仍联动 ignoreAudioFocus 的值（enabled），
 * 朗读引擎 summary 动态（SpeakEngineDialog 回调刷新），事件广播由宿主承接。
 *
 * # 分组（对齐 MD3 的四段结构）
 *
 * MD3 把朗读设置拆成「界面 / 角色 / 缓存与定时」几组。本轮只落地**播放**与**分段**两组：
 * 角色组依赖多角色链路（B4），缓存与定时组依赖音频下载表（已在 v90 建好但还没有写入方），
 * 先摆空组会点了没反应，等对应批次补齐后再加。
 *
 * # 未移植的开关
 *
 * MD3 的 `keepReadAloudOnExit`（退出阅读页是否继续朗读）在本仓库是**恒真行为**：
 * 朗读由控制器持有，离开阅读页不会 stop（Android 前台服务 / iOS AVAudioSession 都如此），
 * 因此这里不放这个开关 —— 拨了不改变任何行为的开关比没有更糟。
 */
@Composable
fun ReadAloudConfigScreen(
    pausePhoneCallsEnabled: Boolean,
    speakEngineSummary: String,
    onTtsEngine: () -> Unit,
    onSysTtsConfig: () -> Unit,
) {
    val titlePausePhoneCalls = stringResource(Res.string.pause_read_aloud_while_phone_calls_title)
    val summaryPausePhoneCalls =
        stringResource(Res.string.pause_read_aloud_while_phone_calls_summary)
    val titleWakeLock = stringResource(Res.string.read_aloud_wake_lock)
    val summaryWakeLock = stringResource(Res.string.read_aloud_wake_lock_summary)
    val titleMediaButtonPerNext = stringResource(Res.string.pref_media_button_per_next)
    val summaryMediaButtonPerNext = stringResource(Res.string.pref_media_button_per_next_summary)
    val titleReadAloudByPage = stringResource(Res.string.read_aloud_by_page)
    val summaryReadAloudByPage = stringResource(Res.string.read_aloud_by_page_summary)
    val titleStreamReadAloud = stringResource(Res.string.stream_read_aloud_audio)
    val summaryStreamReadAloud = stringResource(Res.string.stream_read_aloud_audio_summary)
    val titleSpeakEngine = stringResource(Res.string.speak_engine)
    val titleSysTtsConfig = stringResource(Res.string.sys_tts_config)
    val summarySysTtsConfig = stringResource(Res.string.sys_tts_config_summary)

    val titleCategoryPlay = stringResource(Res.string.read_aloud_category_play)
    val titleCategoryEngine = stringResource(Res.string.read_aloud_category_engine)
    val titleCategorySplit = stringResource(Res.string.read_aloud_category_split)
    val titleSplitMode = stringResource(Res.string.read_aloud_split_mode)
    val summarySplitMode = stringResource(Res.string.read_aloud_split_mode_summary)
    val entrySplitParagraph = stringResource(Res.string.read_aloud_split_mode_paragraph)
    val entrySplitSentence = stringResource(Res.string.read_aloud_split_mode_sentence)
    val titleSplitSymbols = stringResource(Res.string.read_aloud_split_symbols)
    val summarySplitSymbols = stringResource(Res.string.read_aloud_split_symbols_summary)

    AppTheme {
        PreferenceScreen {
            // "忽略音频焦点"不在此重复: 原版 pref_config_aloud.xml 与 pref_config_other.xml
            // 各挂一份同 key 开关, 2026-09-04 用户拍板只留其它设置那一处。
            // pauseReadAloudWhilePhoneCalls 仍按它的值联动 enabled (值由其它设置页改)
            preferenceCategory(titleCategoryPlay)
            switchPreference(
                prefKey = PreferKey.pauseReadAloudWhilePhoneCalls,
                title = titlePausePhoneCalls,
                summary = summaryPausePhoneCalls,
                defaultValue = false,
                enabled = pausePhoneCallsEnabled,
            )
            switchPreference(
                prefKey = PreferKey.readAloudWakeLock,
                title = titleWakeLock,
                summary = summaryWakeLock,
                defaultValue = false,
            )
            switchPreference(
                prefKey = "mediaButtonPerNext",
                title = titleMediaButtonPerNext,
                summary = summaryMediaButtonPerNext,
                defaultValue = false,
            )
            switchPreference(
                prefKey = PreferKey.readAloudByPage,
                title = titleReadAloudByPage,
                summary = summaryReadAloudByPage,
                defaultValue = false,
            )
            switchPreference(
                prefKey = PreferKey.streamReadAloudAudio,
                title = titleStreamReadAloud,
                summary = summaryStreamReadAloud,
                defaultValue = false,
            )

            preferenceCategory(titleCategoryEngine)
            preference(
                title = titleSpeakEngine,
                summary = speakEngineSummary,
                onClick = onTtsEngine,
            )
            preference(
                title = titleSysTtsConfig,
                summary = summarySysTtsConfig,
                onClick = onSysTtsConfig,
            )

            preferenceCategory(titleCategorySplit)
            // 分段方式改动后要点一次"重新朗读"才生效: 已排好的段落表是这一章的快照,
            // 切模式重建整章队列会让当前句被打断, 不做自动重建。
            listPreference(
                prefKey = PreferKey.readAloudContentSplitMode,
                title = titleSplitMode,
                summary = summarySplitMode,
                entries = listOf(entrySplitParagraph, entrySplitSentence),
                values = listOf(
                    ReadAloudQueue.SPLIT_MODE_PARAGRAPH.toString(),
                    ReadAloudQueue.SPLIT_MODE_SENTENCE.toString(),
                ),
                defaultValue = ReadAloudQueue.SPLIT_MODE_PARAGRAPH.toString(),
            )
            editTextPreference(
                prefKey = PreferKey.readAloudContentSplitSymbols,
                title = titleSplitSymbols,
                summary = summarySplitSymbols,
                defaultValue = "",
            )
        }
    }
}
