# MEMORY（跨会话长期记忆）

> 2026-09-23 压缩（第二十八次）：只留**红线 / 契约 / 踩坑 / 指针**。细则 = `CodeBuddy project_DartVio_PRD/`（19 docx + `_待清理/PRD_*.md`）；过程与实测数字 = `.codebuddy/memory/YYYY-MM-DD.md`。

## 0 工作区 / DoD / 发版
- 三目录：`App_DartVio_Android_CB_V0.1/`（Kotlin+Room+Compose `com.dartvio.app`）、`CodeBuddy project_DartVio_PRD/`、`M12_PoC/`+`DartVision/`。**非 git** ⇒ 时间戳对账；交接产物 = `_待清理/PRD_*.md`。
- **DoD**：①落盘 ②`assembleDebug`/`assembleBeta` SUCCESS ③`testDebugUnitTest` 记「总数/fail/error/skip + 基线」④`read_lints` 0；缺任一 ⇒ 首行标 `⚠️ 未验证：<缺项>`。**基线 71 文件 / 773 例 / 0 fail / 0 skip**。DB **v11**。
- 红线：禁 `fallbackToDestructiveMigration`；迁移发布后不可回改；不删既有断言；新字段不塞老字段。定稿 M2 §4.8 / M4 §6.2–6.4 / M9 §8.3.1；M7 以代码为准。
- ★APK 版本管理：台账 = `2.01 DartVio APK/APK版本清单.md`（顶部有发版 SOP）。`versionCode` **只增**；`versionName`=`0.1.<code>`；文件名 `DartVio_BetaDemo_v0.1.<code>_{特性}_{YYYYMMDD}.apk` + `DartVio_BetaDemo_latest.apk` 字节副本。当前 **v0.1.3 / code 3**。
- 进度：M2–M4、M7、M9、M11、M12 前置 + 工坊 V1.4 + 对抗练习 + 结镖训练 + M5 联机 T1–T10 + 跨网络在线对战（含观战）+ V2 大厅 M1/M2 已交付；M6/M8/M10、V2 M3 未做。

## 1 docx 文档包
- 命名 `NN_M{NN}_模块名_V1.0X`；**三处对齐**（文件名 = 封面 = 变更记录首条数据行；模块 docx 新行置顶，00 追加表尾）。改完必跑 `_work/verify_versions_v101.py`（FAIL/WARN 0）。
- 流水线：`_work/_docxlib.py` 解包→改 `word/document.xml`→重打包。
- ★坑：①子表 `ROW.finditer` 偏移**相对** ⇒ 加表基址转绝对 ②禁用 `find_para/replace_in_para` ⇒ 单 `<w:t>` run 内 `replace_text_once` ③`esc` 转义 ④正则须 `<w:t(?:\s[^>]*)?>` ⑤克隆 ≥5 列表重算 `gridCol` ⑥附录追加位置 = max(最后顶层 tbl 结束, 最后非表格 p 结束) ⑦模块 docx 变更记录新行插表头之后。

## 2 构建 / 测试 / 设备 / 打包
- 先 `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`（本机无全局 JAVA）再 `.\gradlew`。`minSdk=24`/`targetSdk=37`；portrait；无 androidTest ⇒ Room 迁移真机覆盖安装验。
- ★KDoc 内嵌套 `/**` 触发 `Unclosed comment`（症状：别处一堆 Unresolved reference）⇒ 只看 `^e: ` 前几行。★Kotlin 协程里 `while (isActive)` 需 `import kotlinx.coroutines.isActive`。★**supabase-kt 3.0+ 强依赖 Ktor 3** ⇒ 接 Supabase 一律**裸 HTTP/WS 走 Ktor 2.3.12**。★单测 JUnit4；`assertTrue(message, cond)` 参数顺序反了编译不过。
- adb：`am force-stop` 再取 db；只用 `install -r`；`shell screencap`+`pull`（`exec-out >` 毁二进制）。
- 打包：对外试用 = `assembleBeta`（~15MB）。要点：真 keystore + `isDebuggable=false`；入口开关 `BuildConfig.FULL_ENTRIES`；★`app/src/beta/res/xml/network_security_config.xml` 放行明文（缺则联机必挂且无提示）；邀请码 = **渠道标签非门禁**（`BetaAccess` 到期 2026-10-19）；遥测 `data/telemetry/` Ktor2 裸 REST，后端 = `App_.../local.properties`（空串 ⇒ 零请求）；⚠️ 配置编译进包 ⇒ 先配后端再发 beta。
- 设备：P1 新主力 / P2 旧低端 / P3 M12 机位；M12 gate = `API≥29 && RAM≥4GB && hasCamera`。

## 3 联机(M5) / 跨网络在线 / V2 大厅
- **LAN**：`domain/room/RoomMatch`+`RoomMatchRules`、`data/room/RoomRepository`、`net/host/`（房主 Ktor 权威）、`net/client/`（WS+`RoomMirror`）、`net/protocol/RoomCodec`。只 X01；端口 **8080**；`PROTOCOL_VERSION=1`；换源唯一入口 = `RoomRepositoryProvider`。
- **在线**：云端事件流 `dartvio_room_events`(room_id, seq, actor_id, type, payload, `unique(room_id,seq)`) + `dartvio_rooms` 索引 + `dartvio_room_state` 大厅快照（都只插不更）；Ktor2 WS 手搓 Phoenix + PostgREST 写；本地 `RoomEventReplay` **重放事件**得权威状态。代码：`net/online/`、`OnlineRoomRepository` + `OnlineRoomLink`（**必须单例**）、`ui/lobby/OnlineLinkSetup`。LAN 并存不删。
- ★★**Supabase Realtime 协议坑（2026-09-23 实锤）**：现行推送 = `event="postgres_changes"`、行在 `payload.data.record`、类型 `payload.data.type`；**老格式 `event="insert"`+`payload.record` 已不再推送**。按老格式解析 ⇒ join ok、推送照到、**解析时全部静默丢弃**（真机表现：房主端永远「等待玩家加入」，客人拉全量却正常）。`RoomEventStream`/`RoomStateStream` 已兼容两种格式。诊断法：Python `websockets` 直连 `wss://<ref>.supabase.co/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0`，join `realtime:public:<表>` + POST 测试行看帧（首次 join 后 WAL 管道有冷启动，首条可能 >20s，之后 0.1s 级）。
- ★防御：`OnlineRoomRepository.ensureStream` 泵内并行 **3s 增量轮询**（`fetchEvents(roomId, afterSeq)` ⇒ `&seq=gt.N`），Realtime 只当加速器。
- ★坑（在线）：PostgREST GET 偶发卡分钟级 ⇒ 拉/写**超时+重试**（`TIMEOUT_MS=15s`、3 次；seq 唯一 ⇒ 重复插入变 Conflict 幂等）；禁把异常吞成 null（记 `lastFailure`）；端到端测试房间号必须**随机**。`dartvio_room_state` **未进** publication（用户截图证实 publication 只有 events 表）⇒ 大厅实时推送死、靠 30s 轮询；要实时须 `alter publication supabase_realtime add table public.dartvio_room_state;`。
- ★T2 断网不置 `FAILED`；T3 文案 `failureCopy()`；T4 `DiagnosticsLog` 环形 200 条；T5 断线 `online=false` 不移除成员；T6 `submit` 锁内**幂等→版本→裁定→写回→记账**，冲突 `40903`；T7/T8 撤回与判负**只能服务端裁定**；T9 `rematch` 不重读房间、version+turnSeq 归零、只房主；T10 联机进历史（标 source）不进 M9，matchId=`lan-<roomId>-<startedAt>`。
- **V2 大厅**：实时比分 = 追加式 `dartvio_room_state`（大厅取最新）；统计记「进房来源」；重名消歧 = 昵称+4 位短码（排除 I/O/0/1/L/5/8）；昵称 ≤12 字符+敏感词过滤；身份 `OnlineIdentity`（`dv_...`）；R3 流水由 `RoomEventReplay` 派生。服务端 SQL 见 PRD §8（RLS 只 select/insert）；★快照定期清（>1 天）。

## 4 领域规则（冻结）
- ★`MatchConfig` = 规则唯一来源 `outMode/inMode/bullMode/maxRounds`；`bullMode` 仅 X01 生效。轮数上限单字段 `maxRounds`；协议 `max_rounds`+旧键 `round_limit` 并行。
- `X01RulesOptions`：`BullMode`/`InMode`/`OutMode`；Cricket TACTICS 9 档 27 marks；Overkill 领先 ≥200 得分作废；并列 ⇒ 席序靠前者胜。
- `X01LegState.players` 与 `RoomMatch.members` **同序**（下标是唯一可靠对应）；跨局局数记 `RoomMatch.legsWon`（每局重置的 `legsWon` 联机不可用）。

## 5 UI 版式（冻结）
- 底栏钉屏底 = `Scaffold(bottomBar)`+`navigationBarsPadding()`；圆角：大卡 14 / 条 12 / chip 10 / 小件 8；主题无 `BorderDark`，描边用 `Divider`。
- ★`DartKeypad` 行高 `weight(1f)` 必须调用方给界高（进 `verticalScroll` 整板塌 0 高**不报错**）；★禁显式 `import ...layout.weight`；横 chip 防挤压（`maxLines=1`+`softWrap=false` / FlowRow）。
- ★一屏不滚动范式（极速挑战/入口/建房）：无 `verticalScroll`，CTA 钉底；报告页/大厅可滚动但确认按钮钉底。★主题级关 overscroll stretch。
- ★对抗练习对局页：输入 `VersusBoardInput`（点哪儿记哪儿，判定与 M12/X01 同源）；旧 `VersusKeypad`/`VersusMiniBoard` 已删。顶部玩家卡（`IntrinsicSize.Min`）+ 一行 `RoundBar`。★`Primary` 等主题色不能在 DrawScope 读，从 `drawingColors()` 传参。
- ★`drawBoardViewportIn` 有 `markerRadiusMm`（默认 4.5；精准工坊放大窗传 2.25，用户口径「圆点直径 50%」= 减半，待真机确认）。`ImpactPracticeScreen` 预览框锁 `aspectRatio(1f)`。

## 6 其它模块（指针）
- **M12 视觉计分**（docx V1.04）：半自动；红线 **禁 Ultralytics YOLO 系（AGPL）**；标定点 = `BOUNDARY_DIRS` 4 分割线（θ 9/99/189/279°，R=170）；服务 127.0.0.1:8765 = PC Python（App 内没有）。★App 侧现状（09-22）：`domain/vision/` 契约齐（`DartRecognizer` 可插拔、置信 0.90、候选必须用户确认）；**CameraX 未引入**；`RecognitionFrame` KDoc「帧不上传不落盘」⇒ 云端识别方案须先修订该表述。
- **云端视觉自动计分**（评估已交付待拍板）：`_待清理/PRD_手机摄像头自动计分_可行性评估_V1.0.md`。云端**只输出针尖像素坐标**，判分交给本地 `Homography`+`dartAt`。失败线：分区准确率 ≥95% 进工程、<90% 否决。★红线：**Doubao AK/SK 是付费凭证 ⇒ 绝不进包**，必须后端中转 + 限流。已拍板（09-22）：先 PoC / Edge Function 中转 / 每镖一拍 ⇒ **多镖差分**（本地按 mm<15 剔旧镖，不让模型判新旧）/ ★置信度口径待修（现公式判分全对也仅 35% 过 0.90）。
- **产品形态**：Android 主机 → 微信小程序获客 → iOS 只 Client；硬件三级解锁。
- **精准工坊**（`domain/impact/`）：几何唯一来源 `BoardGeometry`（禁字面量副本）；`outBand!=0` 不进 σ/R95/KDE；环径 99/107/162/170；n<12 不出结论；术语 `session`/`round`（禁 `group`）。
- **对抗练习**：路由 `versus_list → versus_setup → versus → versus_report`；V1 只亮前 3 模式；★`appendRound` 后必须 `loadMatch` 回读；数据拆表 `versus_*` 不进 M9。
- **结镖训练**：★`throwElapsedMs` = 唯一成绩；★个人最佳**插入前**查 `bestUnhintedThrowMs`；未投出不补 MISS；切后台先落 `ABORTED`；B 级不进正式历史。

## 7 账号系统（暂缓）
- 个人主体：微信登录/短信验证码均不可行；唯一真账号 = Supabase 邮箱密码（国内不习惯）。用户拍板暂缓，先发 beta；触发条件 = 换机丢数据反馈 / 好友对战或排行榜 / 正式上架。
