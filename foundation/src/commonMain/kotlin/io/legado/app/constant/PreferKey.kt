package io.legado.app.constant

@Suppress("ConstPropertyName")
object PreferKey {
    const val language = "language"
    const val fontScale = "fontScale"
    const val themeMode = "themeMode"

    /**
     * MD3 皮肤配色预设 (LegadoMiuixPalette 的枚举名)。
     * 空/不存在 = 用主题强调色作种子 (即原有的自定义色行为)。
     */
    const val miuixPalette = "miuixPalette"
    const val userAgent = "userAgent"
    const val showUnread = "showUnread"
    const val bookGroupStyle = "bookGroupStyle"
    const val useDefaultCover = "useDefaultCover"
    const val coverShowName = "coverShowName"
    const val coverShowAuthor = "coverShowAuthor"
    const val coverShowNameN = "coverShowNameN"
    const val coverShowAuthorN = "coverShowAuthorN"
    const val remoteServerId = "remoteServerId"
    const val hideStatusBar = "hideStatusBar"
    const val clickActionTL = "clickActionTopLeft"
    const val clickActionTC = "clickActionTopCenter"
    const val clickActionTR = "clickActionTopRight"
    const val clickActionML = "clickActionMiddleLeft"
    const val clickActionMC = "clickActionMiddleCenter"
    const val clickActionMR = "clickActionMiddleRight"
    const val clickActionBL = "clickActionBottomLeft"
    const val clickActionBC = "clickActionBottomCenter"
    const val clickActionBR = "clickActionBottomRight"
    const val hideNavigationBar = "hideNavigationBar"
    const val precisionSearch = "precisionSearch"
    // 搜索范围 / 搜索分组: AppConfig 中直接用字符串字面量, 这里补常量供 Desktop 端使用
    const val searchScope = "searchScope"
    const val searchGroup = "searchGroup"
    const val readAloudByPage = "readAloudByPage"
    const val ttsEngine = "appTtsEngine"
    const val ttsFollowSys = "ttsFollowSys"
    const val ttsSpeechRate = "ttsSpeechRate"

    /**
     * 系统 TTS 音色（`read_aloud_voices.id`，空 = 引擎默认）。
     *
     * 与 [ttsEngine] 分开存：引擎是"用哪家的合成器"，音色是"这家里的哪把嗓子"。
     * 换引擎时旧音色 id 自然失效（查不到就退回默认），不需要额外的清理动作。
     */
    const val ttsVoice = "ttsVoice"
    const val prevKeys = "prevKeyCodes"
    const val nextKeys = "nextKeyCodes"
    const val showDiscovery = "showDiscovery"
    const val showHome = "showHome"
    const val bottomNavItemOrder = "bottomNavItemOrder"
    const val bookshelfLayout = "bookshelfLayout"
    const val bookshelfFixedWidthMode = "bookshelfFixedWidthMode"
    const val bookshelfGridWidth = "bookshelfGridWidth"
    const val bookshelfSort = "bookshelfSort"
    const val bookExportFileName = "bookExportFileName"

    const val bookImportFileName = "bookImportFileName"
    const val episodeExportFileName = "episodeExportFileName"
    const val recordLog = "recordLog"
    const val processText = "process_text"
    const val cleanCache = "cleanCache"
    const val saveTabPosition = "saveTabPosition"
    const val fontFolder = "fontFolder"
    const val restoreIgnore = "restoreIgnore"
    const val threadCount = "threadCount"
    const val webPort = "webPort"
    const val keepLight = "keep_light"
    const val webService = "webService"
    const val webDavUrl = "web_dav_url"
    const val webDavAccount = "web_dav_account"
    const val webDavPassword = "web_dav_password"
    const val webDavDir = "webDavDir"
    const val enableCustomExport = "enableCustomExport"
    const val exportToWebDav = "webDavCacheBackup"
    const val exportNoChapterName = "exportNoChapterName"
    const val exportType = "exportType"
    const val changeSourceCheckAuthor = "changeSourceCheckAuthor"
    const val changeSourceLoadToc = "changeSourceLoadToc"
    const val changeSourceLoadInfo = "changeSourceLoadInfo"
    const val changeSourceLoadWordCount = "changeSourceLoadWordCount"
    const val chineseConverterType = "chineseConverterType"
    const val launcherIcon = "launcherIcon"
    const val shareLayout = "shareLayout"
    const val comicStyleSelect = "comicStyleSelect"
    const val readStyleSelect = "readStyleSelect"
    const val systemTypefaces = "system_typefaces"
    const val textFullJustify = "textFullJustify"
    const val textBottomJustify = "textBottomJustify"

    /** 段首标点悬挂（CSS Text 4 §8.5.3 第三方案 hanging alignment / clreq 6.3.2 段首缩进条）。 */
    const val hangFirstPunctuation = "hangFirstPunctuation"
    const val autoReadSpeed = "autoReadSpeed"
    const val barElevation = "barElevation"
    const val defaultCover = "defaultCover"
    const val defaultCoverDark = "defaultCoverDark"
    const val replaceEnableDefault = "replaceEnableDefault"
    const val autoChangeSource = "autoChangeSource"
    const val importKeepName = "importKeepName"
    const val importKeepGroup = "importKeepGroup"
    const val screenOrientation = "screenOrientation"
    const val syncBookProgress = "syncBookProgress"
    const val syncBookProgressPlus = "syncBookProgressPlus"
    const val cronet = "Cronet"
    const val bitmapCacheSize = "bitmapCacheSize"
    const val preDownloadNum = "preDownloadNum"
    const val mangaPreDownloadNum = "mangaPreDownloadNum"
    const val mangaAutoPageSpeed = "mangaAutoPageSpeed"
    const val mangaFooterConfig = "mangaFooterConfig"
    const val enableMangaHorizontalScroll = "enableMangaHorizontalScroll"
    const val hideMangaTitle = "hideMangaTitle"
    const val mangaColorFilter = "mangaColorFilter"
    const val enableMangaGray = "enableMangaGray"
    const val enableMangaGifAutoNext = "enableMangaGifAutoNext"
    const val autoRefresh = "auto_refresh"
    const val autoCheckUpdate = "autoCheckUpdate"
    const val exportCharset = "exportCharset"
    const val exportUseReplace = "exportUseReplace"
    //const val expandTextMenu = "expandTextMenu"
    const val doublePageHorizontal = "doubleHorizontalPage"
    const val readUrlOpenInBrowser = "readUrlInBrowser"
    const val defaultBookTreeUri = "defaultBookTreeUri"
    const val uploadRule = "uploadRule"
    const val tocUiUseReplace = "tocUiUseReplace"
    const val tocCountWords = "tocCountWords"
    const val enableReadRecord = "enableReadRecord"
    const val localBookImportSort = "localBookImportSort"
    const val enableWelcome = "enableWelcome"
    const val welcomeImage = "welcomeImagePath"
    const val welcomeImageDark = "welcomeImagePathDark"
    const val welcomeShowText = "welcomeShowText"
    const val welcomeShowTextDark = "welcomeShowTextDark"
    const val welcomeShowIcon = "welcomeShowIcon"
    const val welcomeShowIconDark = "welcomeShowIconDark"
    const val welcomeShowTime = "welcomeShowTime"
    const val pageTouchSlop = "pageTouchSlop"
    const val showAddToShelfAlert = "showAddToShelfAlert"
    const val bookInfoDeleteAlert = "bookInfoDeleteAlert"
    const val ignoreAudioFocus = "ignoreAudioFocus"

    /**
     * 动态文本辉光。开启后安卓端会关掉 DSP audio offload 以拿到 PCM (后台听书不再省电),
     * 与 [audioEdgeGlow] 任一个开启都需要 PCM。
     */
    const val audioEnergyGlow = "audioEnergyGlow"

    /**
     * 播放页边缘光效。安卓端与 [audioEnergyGlow] 同等: 需要 PCM 才能按频段起伏,
     * 两个开关都关才开省电解码。
     */
    const val audioEdgeGlow = "audioEdgeGlow"

    /** 上面两个辉光开关的默认值 (读取处统一引用它, 不各写一遍字面量) */
    const val audioGlowDefault = true
    const val progressBarBehavior = "progressBarBehavior"
    const val sourceEditMaxLine = "sourceEditMaxLine"
    const val ttsTimer = "ttsTimer"
    const val webDavDeviceName = "webDavDeviceName"
    const val webServiceWakeLock = "webServiceWakeLock"
    const val audioPlayWakeLock = "audioPlayWakeLock"
    const val publishLyric = "publishLyric"
    const val readAloudWakeLock = "readAloudWakeLock"
    const val showLastUpdateTime = "showLastUpdateTime"
    const val showWaitUpCount = "showWaitUpCount"

    /**
     * 书源管理列表是否显示行首书源图标 (默认显示)。
     *
     * 与其它显示类开关 (showLastUpdateTime / enableReadRecord / hideMangaTitle 等) 同一份
     * 默认偏好存储: 随配置备份走, 换机恢复后档位保持; 备份忽略清单 (主题/封面等按需忽略项)
     * 不含本键。
     */
    const val showSourceIcon = "showSourceIcon"
    const val clearWebViewData = "clearWebViewData"
    const val onlyLatestBackup = "onlyLatestBackup"
    const val shrinkDatabase = "shrinkDatabase"
    const val batchChangeSourceDelay = "batchChangeSourceDelay"
    const val defaultHomePage = "defaultHomePage"
    const val bookshelfListShowKind = "bookshelfListShowKind"
    const val bookshelfListShowIntro = "bookshelfListShowIntro"
    const val bookshelfListIntroLines = "bookshelfListIntroLines"
    const val bookshelfCoverHeight = "bookshelfCoverHeight"
    const val searchLayout = "searchLayout"
    const val bottomBarHeight = "bottomBarHeight"
    const val bottomBarIconSize = "bottomBarIconSize"
    const val bottomBarLabelMode = "bottomBarLabelMode"
    const val bookshelfShowGroupCount = "bookshelfShowGroupCount"
    const val importKeepEnable = "importKeepEnable"
    const val previewImageByClick = "previewImageByClick"
    const val recordHeapDump = "recordHeapDump"
    const val updateToVariant = "updateToVariant"
    const val streamReadAloudAudio = "streamReadAloudAudio"
    const val pauseReadAloudWhilePhoneCalls = "pauseReadAloudWhilePhoneCalls"
    const val readAloudByMediaButton = "readAloudByMediaButton"
    const val devFeat = "devFeat"
    const val bookInfoHorizontalLayout = "bookInfoHorizontalLayout"
    const val disableMangaPageAnim = "disableMangaPageAnim"
    const val autoCheckNewBackup = "autoCheckNewBackup"

    const val cPrimary = "colorPrimary"
    const val cAccent = "colorAccent"
    const val cBackground = "colorBackground"
    const val cBBackground = "colorBottomBackground"
    const val bgImage = "backgroundImage"
    const val bgImageBlurring = "backgroundImageBlurring"

    const val cNPrimary = "colorPrimaryNight"
    const val cNAccent = "colorAccentNight"
    const val cNBackground = "colorBackgroundNight"
    const val cNBBackground = "colorBottomBackgroundNight"
    const val bgImageN = "backgroundImageNight"
    const val bgImageNBlurring = "backgroundImageNightBlurring"

    // 莫奈取色自动跟随壁纸开关 (Android 12+): 行为模式开关, 非主题色/背景图载荷, 不入 themePrefKeys
    const val monetWallpaperFollow = "monetWallpaperFollow"
    const val showReadTitleAddition = "showReadTitleAddition"
    const val contentSelectSpeakMod = "contentReadAloudMod"

    // 桌面端窗口状态记忆 (位置/尺寸/是否最大化)
    const val windowX = "desktopWindowX"
    const val windowY = "desktopWindowY"
    const val windowWidth = "desktopWindowWidth"
    const val windowHeight = "desktopWindowHeight"
    const val windowMaximized = "desktopWindowMaximized"

    /**
     * 关闭主窗口时是否缩小到托盘 (false = 退出程序)。
     *
     * 键不存在 = 用户尚未选择过, 首次关闭时弹询问框; 设置页开关与询问框读写同一个键,
     * 任一处选定过就不再询问。
     */
    const val closeToTray = "desktopCloseToTray"

    /**
     * 预测式返回跟手 (仅 Android 消费): 打开时返回手势跟手、且返回统一由根层 + 统一返回链接管,
     * 关闭时回到各页面自己注册的按键式返回回调 (与本改动前一致)。
     */
    const val predictiveBack = "predictiveBack"

    // ==================== 朗读 / 听书（对齐 MD3） ====================

    /**
     * 听书界面形态：空/`dialog` = 沿用对话框，`player` = 全屏播放页。
     * 键名与 MD3 一致，便于以后互导偏好。
     */
    const val defaultReadAloudInterface = "defaultReadAloudInterface"

    /** 退出阅读页后是否继续朗读。 */
    const val keepReadAloudOnExit = "keepReadAloudOnExit"

    /** 朗读时是否把章节标题当作第一段念。 */
    const val readAloudChapterTitle = "readAloudChapterTitle"

    /** 分段模式：0 = 按段落，1 = 按句子。 */
    const val readAloudContentSplitMode = "readAloudContentSplitMode"

    /** 自定义切分符号（[readAloudContentSplitMode] 之外的用户补充）。 */
    const val readAloudContentSplitSymbols = "readAloudContentSplitSymbols"

    /** 离开阅读页时提醒朗读仍在后台。 */
    const val readAloudDetachReminderEnabled = "readAloudDetachReminderEnabled"

    /** 播放页背景模式。 */
    const val readAloudPlayerBgMode = "readAloudPlayerBgMode"

    /** 定时关闭模式：0 = 关，1 = 按分钟，2 = 按章。 */
    const val readAloudTimerMode = "readAloudTimerMode"

    /** 按章定时的章数。 */
    const val readAloudTimerChapters = "readAloudTimerChapters"

    /** 段间停顿毫秒（合成与播放共用）。 */
    const val ttsParagraphInterval = "ttsParagraphInterval"

    /** 预合成并发数（云 TTS 限流的唯一旋钮）。 */
    const val ttsPreSynthesisConcurrency = "ttsPreSynthesisConcurrency"

    /** 多角色朗读总开关。关掉时走单音色，链路与旧版一致。 */
    const val multiRoleCast = "multiRoleCast"

    /** 角色识别模式：`off` / `rule`（正则规则）/ `ai`。 */
    const val speechAnalysisMode = "speechAnalysisMode"

    /** AI 角色识别的推理档位。 */
    const val speechAnalysisReasoningLevel = "speechAnalysisReasoningLevel"

    /** 音色试听文本。 */
    const val voicePreviewText = "voicePreviewText"

    /** 正文里显示角色胶囊。 */
    const val showReadAloudCapsule = "showReadAloudCapsule"

    /** 播放时的背景模式（0 = 纯色，1 = 书封模糊）。 */
    const val audioPlayBgMode = "audioPlayBgMode"

    /** 背景音乐场景标记的编辑模式开关。 */
    const val bgmAssign = "bgmAssign"

    /** 当前选中的云 TTS 引擎 id，空 = 用系统 TTS 或 HTTP TTS。 */
    const val cloudTtsEngineId = "cloudTtsEngineId"

    // ==================== 阅读器补差 ====================

    const val volumeKeyPage = "volumeKeyPage"
    const val reverseVolumeKeyPage = "reverseVolumeKeyPage"
    const val volumeKeyPageOnPlay = "volumeKeyPageOnPlay"
    const val keyPageOnLongPress = "keyPageOnLongPress"
    const val mouseWheelPage = "mouseWheelPage"
    const val noAnimScrollPage = "noAnimScrollPage"
    const val readBarStyleFollowPage = "readBarStyleFollowPage"

    // ==================== 漫画（对齐 MD3） ====================

    /** 阅读页显示漫画专属 UI（页脚进度、缩放提示等）。 */
    const val showMangaUi = "showMangaUi"

    /** 翻页模式：1 = 单页，2 = 双页，3 = 连续横向，4 = 纵向滚动（与 MD3 默认 4 一致）。 */
    const val mangaScrollMode = "mangaScrollMode"

    /** 缩放方式：0 = 适应宽度，1 = 适应高度，2 = 原始尺寸。 */
    const val mangaPageScaleType = "mangaPageScaleType"

    /** 双页模式：0 = 自动，1 = 强制双页，2 = 关闭。 */
    const val mangaDoublePageMode = "mangaDoublePageMode"
    const val mangaDoublePageInvert = "mangaDoublePageInvert"
    const val mangaDoublePageShift = "mangaDoublePageShift"
    const val mangaDoublePageCoverSingle = "mangaDoublePageCoverSingle"

    /** 跨页宽图模式：0 = 跟随双页，1 = 独占一屏。 */
    const val mangaWidePageMode = "mangaWidePageMode"

    /** 放大后的初始落点：0 = 中心，1 = 点击位置，2 = 左上。 */
    const val mangaZoomStartPosition = "mangaZoomStartPosition"

    /** 预取章数（0 = 只取当前章）。 */
    const val mangaChapterPrefetchCount = "mangaChapterPrefetchCount"

    /** 自动离线缓存整章。 */
    const val mangaAutoOfflineCache = "mangaAutoOfflineCache"

    const val disableMangaCrossFade = "disableMangaCrossFade"
    const val disableMangaScale = "disableMangaScale"
    const val disableMangaScrollAnimation = "disableMangaScrollAnimation"

    /** 墨水屏模式（阈值二值化）。 */
    const val enableMangaEInk = "enableMangaEInk"
    const val mangaEInkThreshold = "mangaEInkThreshold"

    /** 背景色自动跟随图片主色。 */
    const val mangaAutoBackground = "mangaAutoBackground"
    const val mangaBackground = "mangaBackground"

    /** 长按翻页。 */
    const val mangaLongClick = "mangaLongClick"
    const val mangaVolumeKeyPage = "mangaVolumeKeyPage"

    /** 整本导出格式：`cbz` / `folder`。 */
    const val mangaExportFormat = "mangaExportFormat"

    /** 点击区域动作：-1 = 无，0 = 菜单，1 = 下一页，2 = 上一页。 */
    const val mangaClickActionTL = "mangaClickActionTopLeft"
    const val mangaClickActionTC = "mangaClickActionTopCenter"
    const val mangaClickActionTR = "mangaClickActionTopRight"
    const val mangaClickActionML = "mangaClickActionMiddleLeft"
    const val mangaClickActionMC = "mangaClickActionMiddleCenter"
    const val mangaClickActionMR = "mangaClickActionMiddleRight"
    const val mangaClickActionBL = "mangaClickActionBottomLeft"
    const val mangaClickActionBC = "mangaClickActionBottomCenter"
    const val mangaClickActionBR = "mangaClickActionBottomRight"

    // ==================== 漫画 AI 翻译 ====================

    /** 翻译总开关（默认关，关掉即回到纯图片阅读）。 */
    const val mangaTranslateEnabled = "mangaTranslateEnabled"

    /** OCR 通道：`cloud`（S1 唯一可用）/ `local`（本机模型，iOS 不支持）。 */
    const val mangaOcrProvider = "mangaOcrProvider"

    /** 翻译通道：`cloud` / `llm`。 */
    const val mangaTranslateProvider = "mangaTranslateProvider"

    /** 目标语言（BCP-47 子集），默认简体中文。 */
    const val mangaTranslateTargetLang = "mangaTranslateTargetLang"

    /** 云端 OCR 凭证 JSON（按 provider 分节，避免为每家加列）。 */
    const val mangaOcrCredentials = "mangaOcrCredentials"
}
