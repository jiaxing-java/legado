package io.legado.app.ui.settings.search

import androidx.compose.runtime.Immutable
import io.legado.app.ui.root.AppRoute
import legado.ui.generated.resources.Res
import legado.ui.generated.resources.aloud_config
import legado.ui.generated.resources.backup_restore
import legado.ui.generated.resources.cover_config
import legado.ui.generated.resources.other_setting
import legado.ui.generated.resources.read_config
import legado.ui.generated.resources.settings_search_summary_aloud
import legado.ui.generated.resources.settings_search_summary_backup
import legado.ui.generated.resources.settings_search_summary_cover
import legado.ui.generated.resources.settings_search_summary_other
import legado.ui.generated.resources.settings_search_summary_reading
import legado.ui.generated.resources.settings_search_summary_theme
import legado.ui.generated.resources.theme_setting
import org.jetbrains.compose.resources.StringResource

/**
 * 搜索结果点击后的可达方式。
 *
 * 必须在目录里**逐条显式声明**，不能默认"打不开就算了"：fork 的阅读类设置
 * (更多设置/排版/样式) 只能在阅读界面里打开，从主界面进不去。这类结果仍然要能被搜到
 * (否则用户以为没有这个设置)，但点击时给出明确说明而不是静默无反应。
 */
sealed interface SettingsSearchReach {
    /** 独立设置页，主界面可直接 push */
    data class Push(val route: AppRoute) : SettingsSearchReach

    /** 自包含的设置对话框（不依赖阅读状态），任何位置都能弹 */
    data object ReadAloudDialog : SettingsSearchReach

    /** 只能在阅读界面中打开：结果项标注「仅阅读中」，点击弹说明 */
    data object ReaderOnly : SettingsSearchReach
}

/**
 * 一个可搜索的设置页。
 *
 * ## 为什么索引到"页"而不是"条目"
 *
 * MD3 版为 200+ 个设置条目手写了一份静态目录 (`SettingsSearchCatalog.items`)，
 * 每条都要维护 id / 标题资源 / 分组序号 / 父项 id。fork 的设置项还在快速增长
 * (B4 云 TTS、B5 漫画设置都是成批新增)，条目级目录必然腐化：新增设置忘了登记，
 * 用户就搜不到，而这种缺失不会报错、只会静默失灵。
 *
 * 这里改成**页面级索引 + 页内即时过滤**：
 * - 索引只有 6 条，新增设置项**不需要**改这里；
 * - 点击结果把关键词带进目标页，页内搜索栏自动过滤到匹配项 —— 到达效果与条目级一致；
 * - 代价是结果列表只显示页面，不直接显示条目名。对 6 条的列表来说这反而更清爽。
 *
 * [keywords] 是搜索质量的关键：中文用户搜「语速」而不是「朗读设置」，所以每个页面
 * 都要把该页包含的高频词、同义词、英文缩写列进来。
 */
@Immutable
data class SettingsSearchPageDoc(
    val id: String,
    val title: StringResource,
    val summary: StringResource,
    val keywords: String,
    val reach: SettingsSearchReach,
)

/**
 * 设置搜索目录：fork 真实存在、且有入口的设置页。
 *
 * 不收录：AI / 主题包 / 段评气泡包 / 隐私 / 实验性功能 —— fork 没有这些页；
 * 漫画设置 —— B5 才有，届时补一条即可。
 */
val settingsSearchCatalog: List<SettingsSearchPageDoc> = listOf(
    SettingsSearchPageDoc(
        id = "theme",
        title = Res.string.theme_setting,
        summary = Res.string.settings_search_summary_theme,
        keywords = "主题 外观 界面 颜色 配色 深色 夜间 暗色 亮色 白天 字体 字号 图标 " +
            "布局 书架 书架布局 搜索布局 底部导航 导航栏 欢迎页 主题列表 自定义 莫奈 取色 " +
            "theme dark light font icon layout monet",
        reach = SettingsSearchReach.Push(AppRoute.ThemeConfig),
    ),
    SettingsSearchPageDoc(
        id = "reading",
        title = Res.string.read_config,
        summary = Res.string.settings_search_summary_reading,
        keywords = "阅读 阅读设置 排版 翻页 动画 行距 段距 字距 缩进 对齐 全屏 屏幕方向 " +
            "保持亮屏 状态栏 导航栏 触摸 灵敏度 点击区域 双击 长按 音量键 翻页 双页 " +
            "背景 文字 颜色 字体 字重 简繁 转换 繁简 阅读样式 页眉 页脚 进度 " +
            "reader typing page turn layout",
        reach = SettingsSearchReach.ReaderOnly,
    ),
    SettingsSearchPageDoc(
        id = "aloud",
        title = Res.string.aloud_config,
        summary = Res.string.settings_search_summary_aloud,
        keywords = "朗读 听书 语音 语速 速度 音色 发音 引擎 tts 合成 分段 句读 段落 句子 " +
            "停顿 间隔 后台 播放 朗读引擎 系统语音 朗读设置 多角色 角色 方言 " +
            "speech read aloud voice speed engine",
        reach = SettingsSearchReach.ReadAloudDialog,
    ),
    SettingsSearchPageDoc(
        id = "other",
        title = Res.string.other_setting,
        summary = Res.string.settings_search_summary_other,
        keywords = "其它 其他 语言 多语言 更新 检查更新 自动更新 网络 代理 web 服务 端口 " +
            "日志 调试 缓存 webview 直链 上传 输入框 行数 后台 电池 唤醒 " +
            "language update network proxy log cache",
        reach = SettingsSearchReach.Push(AppRoute.OtherConfig),
    ),
    SettingsSearchPageDoc(
        id = "backup",
        title = Res.string.backup_restore,
        summary = Res.string.settings_search_summary_backup,
        keywords = "备份 恢复 还原 同步 webdav 网盘 云 上传 下载 设备名 子目录 路径 " +
            "自动备份 备份路径 导入 导出 旧版本 覆盖 " +
            "backup restore sync webdav",
        reach = SettingsSearchReach.Push(AppRoute.BackupConfig),
    ),
    SettingsSearchPageDoc(
        id = "cover",
        title = Res.string.cover_config,
        summary = Res.string.settings_search_summary_cover,
        keywords = "封面 封面规则 阴影 描边 圆角 比例 默认封面 文字颜色 书名 作者 " +
            "网络封面 wifi 仅wifi 网络书籍 角标 日夜 " +
            "cover shadow stroke",
        reach = SettingsSearchReach.Push(AppRoute.CoverConfig),
    ),
)
