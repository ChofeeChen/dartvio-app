**DartVio**

**数据库 Schema 文档**

版本 V1.05 \| 2026-09-19 \| 本地 Room 版本链汇总与 MIGRATION_10_11（DB v10 → v11）
历史修订摘要（旧版本与日期仅供追溯）：
★V1.01：本地 Room 库当前版本订正为 DB v9；新增 §7 对抗练习两张表与三条索引（as-built 未验证）｜版本 V1.01 \| 2026-09-12 \| T3（命名归零，原 v1.0）
MySQL 8.0 / PostgreSQL 14+ 兼容
含完整建表SQL语句
供后端开发使用

# 0 本期平台最终重大决策

D-PLATFORM-001 \| CONFIRMED \| 2026-09-15 \| 决策来源：用户正式授权。DartVio 本期仅开发 Android，采用 Kotlin + Jetpack Compose；iOS 不纳入本期范围。本决定为最终重大决策，不再标记为 OPEN。

当前平台基线：minSdk 24，compileSdk/targetSdk 37；使用 Android SDK 与 Gradle 构建环境、相关 Kotlin/JVM 单元测试及 Android 设备/模拟器验收。历史代码量、测试结果与数据库版本不代表当前工程状态。

适用边界：Swift、UIKit、SwiftUI、SF Symbols、iPhone/iPad 及其专用 API、资源和验收项均为旧版遗留或未来范围，不作为本期实施与验收条件。保留其业务流程、视觉语义和交互含义；未明确的 Android 映射及其他 OPEN 不在本次裁决。旧 pt 尺寸不直接作为 Android 验收值，Android 布局使用 dp、字号使用 sp，具体数值冲突仍待原责任模块裁决。

文档依据：用户最新明确决定 > T6 正式决定 > 对应功能模块最高版本 > 产品总览 > 技术附件和历史实现记录。正式登记见《T6_产品路线与决策记录_V1.04.docx》§0；当前文档版本与文件引用见《00_产品总览与架构_V1.08.docx》§6。历史修订中的旧版本号只用于追溯，不代表当前文件入口。

# 变更记录

| 版本 | 日期 | 变更类型 | 变更内容 | 影响范围 | 变更原因 |
| --- | --- | --- | --- | --- | --- |
| V1.05 | 2026-09-19 | as-built 回写 | ★V1.05（本地 Room 版本链汇总与 MIGRATION_10_11）：①新增 §10 —— 版本链 v9 / v10 / v11 汇总表（变化摘要 + 关键红线 + 引入时间）；②match_records v11 四列逐列定义（source / roomId / winnerName / forfeited）与「默认值 = 老行应读成的值，故不订正 UPDATE」的理由；③MIGRATION_10_11 写法红线；④战绩 / 历史两个读取入口 observeMatches() 与 observeStatsMatches() 的分工。 | T3（连带 M5 / M9） | M5 联机 T10 已落地：联机对局须出现在历史列表，但不能进入统计分母，客观性取决于「同一张表 + 唯一判据」这个实现选择，必须在 Schema 层固化 |
| V1.04 | 2026-09-18 | 新增+定稿 | 新增checkout_rush_attempts与MIGRATION_9_10目标态；保持正式对局数据物理隔离 | T3（联动M11/M9/T5） | 用户确认第一版建立练习专用Room明细表 |
| V1.03 | 2026-09-17 | 开发过程回写 | Room v6 至 v8 与 max_rounds 口径补全 | 相关模块、接口、数据模型与验收 | 依据 Temper File 定稿回写提示词与当前 PRD 对比 |
| V1.02 | 2026-09-15 | 重大决策登记及平台对齐 | D-PLATFORM-001：本期仅 Android，Kotlin + Jetpack Compose；iOS 不纳入本期。新增§0平台适用声明，标注旧版 Apple 写法，更新当前文件版本与交叉引用。原业务规则及其他 OPEN 不变。 | 本文件平台定义、相关示例/验收及文档包引用 | 用户正式授权；由 V1.01 升级至 V1.02。 |
| V1.01 | 2026-09-14 | 订正+新增 | 新增 §7 本地数据库（Android Room）— DB v9：①版本链订正为 v6 → v7 → v8 → v9；②新增 `versus_match_records`（17 列）与 `versus_round_records`（13 列）两张表的完整字段表；③新增三条索引（`index_versus_match_records_modeKey_endedAt` / `index_versus_round_records_matchId` / `index_versus_round_records_playerName`）；④新增 MIGRATION_8_9 纯增量说明（既有三表一列未动、迁移 SQL 不写 DEFAULT、索引名与实体 @Entity(indices) 声明逐字一致、禁 fallbackToDestructiveMigration）。⚠️ as-built 未验证：`DartVioDatabase.kt` 已声明 version = 9 与 MIGRATION_8_9，但 `assembleDebug` / `testDebugUnitTest` / `read_lints` 三项全缺，`app/schemas/9.json` 未导出。 | T3（连带 00 / M9 / M11） | 双人对抗练习模块立项：按先拆表后落库的方式落库，红线由表结构保证 |
| V1.00 | 2026-09-12 | 命名归零 | ★版本号归零（依《00 产品总览与架构》§7.1：文件名版本 = 文档内版本标识，格式 V1.0X）：文件名与文内版本标识统一为 V1.00 —— 原 vX.Y 编号（含正文/历史记录中的 vX.Y 引用）均为历史编号，此后一律按 V1.0X 递增（V1.01 → V1.02 …）。本文件原文件名与文内声明均为 v1.0，而变更记录已到 v1.1，本次一并归零。本轮仅做版本命名规范化，正文内容未变；as-built 内容回写按《00 产品总览与架构》§9 待办 T7 逐份推进。 | T3 数据库 Schema（文件名 v1.0 → V1.00） | 版本命名规范化：本文件长期沿用旧格式 vX.Y，且文件名 / 文内声明 / 变更记录三处版本号互不一致；《00 产品总览与架构》§7.1要求统一为 V1.0X 并把本轮修订作为重定基线 V1.00。 |
| v1.0 | 2026-09-05 | 新增 | 初始版本，14张表+ER关系+索引+完整建表SQL | 全部数据表 | T3任务，基于12份模块PRD数据模型整理 |
| v1.1 | 2026-09-10 | 补全+对齐 | ①★rooms/matches：total_rounds更名为max_rounds（回合上限）；新增target_score(301/501/701/901/1101)、match_mode(casual/multi)、legs_to_win(1-10)、diddle_enabled、overtime_rule；②★match_players：ai_difficulty枚举对齐M4（beginner/intermediate/advanced/pro），新增cricket_state JSON快照列；③★turns：新增state_before JSON（Cricket撤销快照）；④★users：新增cricket_ppr、mpr、cricket_matches；⑤★新增practice_sessions表（练习记录，对应M11）；⑥表清单14→15张；⑦初始化成就补充Cricket相关；⑧§6对应关系补M11 | 全部数据表 | 产品决策：Cricket提升P0+目标分301/501/701+玩法局数模型统一+练习模式P0+AI难度唯一来源 |

# 1. 概述

## 1.1 设计原则

- • 第三范式（3NF）：减少数据冗余，字段原子性
- • 软删除：重要数据使用 deleted_at 软删除，不物理删除
- • 时间戳：所有表含 created_at / updated_at，UTC时间
- • 主键统一：使用 BIGINT UNSIGNED 自增主键（id），业务ID用唯一索引（user_id/match_id等）
- • JSON字段：灵活配置类数据使用 JSON 类型（MySQL 8.0+ / PostgreSQL原生支持）
- • 字符集：utf8mb4 / UTF-8，支持emoji和特殊字符
- • 引擎：InnoDB（MySQL），支持事务和外键
## 1.2 表清单

★V1.01 阅读指引：§1–§6 为服务端 MySQL 逻辑模型；端上 Android Room 本地库的当前事实（DB v9，含对抗练习 `versus_match_records` / `versus_round_records`）见 §7。两边同名不同库，勿混读。

| 序号 | 表名 | 中文名 | 记录数预估 | 核心用途 |
| --- | --- | --- | --- | --- |
| 1 | users | 用户表 | 百万级 | 用户账户/资料/等级/设置 |
| 2 | user_settings | 用户设置表 | 百万级 | 用户个性化设置（JSON） |
| 3 | devices | 设备表 | 十万级 | 硬件设备绑定/状态 |
| 4 | rooms | 房间表 | 万级（活跃） | 在线对战房间 |
| 5 | room_players | 房间玩家表 | 十万级 | 房间内玩家/准备状态 |
| 6 | matches | 比赛表 | 百万级 | 比赛元信息/结果 |
| 7 | match_players | 比赛玩家表 | 百万级 | 比赛中玩家数据/比分 |
| 8 | legs | 局表 | 千万级 | 每局数据 |
| 9 | turns | 回合表 | 亿级 | 每回合3镖数据 |
| 10 | darts | 镖表 | 亿级 | 每镖详细数据 |
| 11 | friends | 好友关系表 | 百万级 | 双向好友关系 |
| 12 | friend_requests | 好友申请表 | 十万级 | 好友申请记录 |
| 13 | achievements | 成就定义表 | 百级 | 18项成就定义 |
| 14 | user_achievements | 用户成就表 | 百万级 | 用户成就解锁记录 |
| 15 | practice_sessions | 练习记录表 | 百万级 | ★v1.1：练习模式记录与最佳成绩（对应M11） |

## 1.3 ER关系总览

users 1───N user_settings
 │
 ├─1──N devices (绑定者)
 ├─1──N rooms (房主)
 ├─1──N room_players
 ├─1──N matches (胜者)
 ├─1──N match_players
 ├─1──N legs (胜者)
 ├─1──N turns (玩家)
 ├─1──N darts (玩家)
 ├─1──N friends (用户A/用户B)
 ├─1──N friend_requests (发送者/接收者)
 └─1──N user_achievements

rooms 1───N room_players
 │
 └─1───1 matches (比赛由房间创建)

matches 1───N match_players
 │
 ├─1───N legs
 │ └─1───N turns
 │ └─1───N darts
 └─1───N spectators (观战记录，可选)

achievements 1───N user_achievements

# 2. 表结构定义

### users — 用户表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| user_id | VARCHAR(32) | UNIQUE NOT NULL | - | 业务用户ID（DV_XXXX，可读） |
| phone | VARCHAR(20) | UNIQUE | NULL | 手机号（加密存储，AES-256） |
| apple_id | VARCHAR(128) | UNIQUE | NULL | Apple ID（加密存储） |
| nickname | VARCHAR(32) | NOT NULL | - | 昵称（2-16字符） |
| avatar_url | VARCHAR(512) |  |  | 头像URL |
| gender | ENUM("male","female","unknown") | NOT NULL | "unknown" | 性别 |
| level | INT UNSIGNED | NOT NULL | 1 | 等级（1-100） |
| level_title | VARCHAR(32) | NOT NULL | "飞镖新手" | 等级称号 |
| experience | INT UNSIGNED | NOT NULL | 0 | 经验值 |
| ppr | DECIMAL(5,1) | NOT NULL | 0.0 | 平均PPR（X01，缓存，定时计算） |
| ★cricket_ppr | DECIMAL(5,1) |  | 0.0 | ★v1.1：Cricket平均得分效率（每镖平均得分） |
| ★mpr | DECIMAL(5,2) |  | 0.00 | ★v1.1：平均MPR（每回合平均Mark数） |
| ★cricket_matches | INT UNSIGNED | NOT NULL | 0 | ★v1.1：Cricket总场次（缓存） |
| win_rate | DECIMAL(5,4) | NOT NULL | 0.0000 | 胜率（缓存） |
| total_matches | INT UNSIGNED | NOT NULL | 0 | 总比赛场数（缓存） |
| status | ENUM("active","disabled","banned") | NOT NULL | "active" | 账号状态 |
| last_login_at | DATETIME |  | NULL | 最后登录时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted_at | DATETIME |  | NULL | 软删除时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| idx_users_user_id | user_id | UNIQUE | 业务ID查询 |
| idx_users_phone | phone | UNIQUE | 手机号登录 |
| idx_users_apple_id | apple_id | UNIQUE | Apple登录 |
| idx_users_nickname | nickname | NORMAL | 昵称搜索 |
| idx_users_level | level | NORMAL | 等级排行榜 |
| idx_users_status | status, deleted_at | NORMAL | 有效用户筛选 |

- 建表SQL：
CREATE TABLE users (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 user_id VARCHAR(32) NOT NULL,
 phone VARCHAR(20) DEFAULT NULL,
 apple_id VARCHAR(128) DEFAULT NULL,
 nickname VARCHAR(32) NOT NULL,
 avatar_url VARCHAR(512) NOT NULL DEFAULT '',
 gender ENUM('male','female','unknown') NOT NULL DEFAULT 'unknown',
 level INT UNSIGNED NOT NULL DEFAULT 1,
 level_title VARCHAR(32) NOT NULL DEFAULT '飞镖新手',
 experience INT UNSIGNED NOT NULL DEFAULT 0,
 ppr DECIMAL(5,1) NOT NULL DEFAULT 0.0,
 win_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
 total_matches INT UNSIGNED NOT NULL DEFAULT 0,
 status ENUM('active','disabled','banned') NOT NULL DEFAULT 'active',
 last_login_at DATETIME DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted_at DATETIME DEFAULT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_users_user_id (user_id),
 UNIQUE KEY uk_users_phone (phone),
 UNIQUE KEY uk_users_apple_id (apple_id),
 KEY idx_users_nickname (nickname),
 KEY idx_users_level (level),
 KEY idx_users_status (status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

### user_settings — 用户设置表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| user_id | VARCHAR(32) | UNIQUE NOT NULL | - | 关联用户 |
| quick_buttons_enabled | TINYINT(1) | NOT NULL | 1 | 是否显示快捷按钮 |
| quick_button_values | JSON | NOT NULL | [60,100,140,180,26,41,85] | 7个快捷按钮值 |
| voice_input_enabled | TINYINT(1) | NOT NULL | 1 | 语音输入开关 |
| voice_language | VARCHAR(10) | NOT NULL | "zh-CN" | 语音识别语言 |
| push_notifications | TINYINT(1) | NOT NULL | 1 | 推送通知 |
| sound_effects | TINYINT(1) | NOT NULL | 1 | 音效 |
| haptics | TINYINT(1) | NOT NULL | 1 | 触觉反馈 |
| dark_mode | ENUM("system","light","dark") | NOT NULL | "system" | 深色模式 |
| auto_score_confirm | TINYINT(1) | NOT NULL | 0 | 硬件自动计分自动确认 |
| last_match_config | JSON |  | NULL | 上次比赛设置（快速开始） |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_settings_user_id | user_id | UNIQUE | 用户唯一设置 |

- 建表SQL：
CREATE TABLE user_settings (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 user_id VARCHAR(32) NOT NULL,
 quick_buttons_enabled TINYINT(1) NOT NULL DEFAULT 1,
 quick_button_values JSON NOT NULL,
 voice_input_enabled TINYINT(1) NOT NULL DEFAULT 1,
 voice_language VARCHAR(10) NOT NULL DEFAULT 'zh-CN',
 push_notifications TINYINT(1) NOT NULL DEFAULT 1,
 sound_effects TINYINT(1) NOT NULL DEFAULT 1,
 haptics TINYINT(1) NOT NULL DEFAULT 1,
 dark_mode ENUM('system','light','dark') NOT NULL DEFAULT 'system',
 auto_score_confirm TINYINT(1) NOT NULL DEFAULT 0,
 last_match_config JSON DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_settings_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

### devices — 设备表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| device_id | VARCHAR(32) | UNIQUE NOT NULL | - | 设备业务ID（DV-PRO-XXXX） |
| user_id | VARCHAR(32) | NOT NULL | - | 绑定用户 |
| device_name | VARCHAR(64) | NOT NULL | "DartVio设备" | 设备名称（用户可改） |
| device_type | ENUM("pro","lite") | NOT NULL | - | 高端/低端 |
| model | VARCHAR(32) | NOT NULL | - | 设备型号（DV-PRO-01） |
| mac_address | VARCHAR(17) | UNIQUE NOT NULL | - | MAC地址 |
| firmware_version | VARCHAR(16) | NOT NULL | - | 固件版本 |
| bind_token | VARCHAR(64) |  |  | 绑定Token（高端，加密） |
| pin_code | VARCHAR(8) |  |  | PIN码（低端，加密） |
| wifi_ssid | VARCHAR(64) |  |  | 连接的WiFi SSID |
| ip_address | VARCHAR(45) |  |  | 局域网IP |
| rtsp_url | VARCHAR(256) |  | NULL | RTSP地址（低端） |
| status | ENUM("online","offline","connecting","updating") | NOT NULL | "offline" | 连接状态 |
| wifi_signal | INT |  | NULL | 信号强度dBm |
| camera_status | JSON |  | NULL | 各摄像头状态 |
| recognition_accuracy | DECIMAL(5,4) |  | NULL | 识别准确率（高端） |
| last_connected_at | DATETIME |  | NULL | 最后连接时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 绑定时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted_at | DATETIME |  | NULL | 解绑时间（软删除） |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_devices_device_id | device_id | UNIQUE | 设备ID查询 |
| uk_devices_mac | mac_address | UNIQUE | MAC唯一 |
| idx_devices_user_id | user_id, deleted_at | NORMAL | 用户设备列表 |
| idx_devices_status | status | NORMAL | 在线设备统计 |

- 建表SQL：
CREATE TABLE devices (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 device_id VARCHAR(32) NOT NULL,
 user_id VARCHAR(32) NOT NULL,
 device_name VARCHAR(64) NOT NULL DEFAULT 'DartVio设备',
 device_type ENUM('pro','lite') NOT NULL,
 model VARCHAR(32) NOT NULL,
 mac_address VARCHAR(17) NOT NULL,
 firmware_version VARCHAR(16) NOT NULL,
 bind_token VARCHAR(64) NOT NULL DEFAULT '',
 pin_code VARCHAR(8) NOT NULL DEFAULT '',
 wifi_ssid VARCHAR(64) NOT NULL DEFAULT '',
 ip_address VARCHAR(45) NOT NULL DEFAULT '',
 rtsp_url VARCHAR(256) DEFAULT NULL,
 status ENUM('online','offline','connecting','updating') NOT NULL DEFAULT 'offline',
 wifi_signal INT DEFAULT NULL,
 camera_status JSON DEFAULT NULL,
 recognition_accuracy DECIMAL(5,4) DEFAULT NULL,
 last_connected_at DATETIME DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted_at DATETIME DEFAULT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_devices_device_id (device_id),
 UNIQUE KEY uk_devices_mac (mac_address),
 KEY idx_devices_user_id (user_id, deleted_at),
 KEY idx_devices_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

### rooms — 房间表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| room_id | VARCHAR(32) | UNIQUE NOT NULL | - | 房间业务ID（RM_XXXX） |
| host_user_id | VARCHAR(32) | NOT NULL | - | 房主用户ID |
| name | VARCHAR(64) | NOT NULL | - | 房间名称 |
| game_type | ENUM("x01","cricket") | NOT NULL | - | 游戏类型 |
| ★target_score | INT UNSIGNED |  | 501 | ★v1.1：301/501/701/901/1101（game_type=cricket时忽略） |
| ★match_mode | ENUM("casual","multi") | NOT NULL | "casual" | ★v1.1：休闲单局/多局 |
| ★legs_to_win | TINYINT UNSIGNED |  | 3 | ★v1.1：1-10，multi模式下赢下多少局获胜 |
| ★diddle_enabled | TINYINT(1) | NOT NULL | 0 | ★v1.1：是否争红心定先手 |
| ★max_rounds | INT UNSIGNED | NOT NULL | 0 | ★v1.1：轮数上限（0/15/20/50/80，0=无上限），X01与Cricket共用 |
| in_rule | ENUM("straight","double","master") | NOT NULL | "straight" | 开局规则 |
| out_rule | ENUM("straight","double","master") | NOT NULL | "double" | 结镖规则 |
| max_players | TINYINT UNSIGNED | NOT NULL | 2 | 最大玩家数（P0=2） |
| is_private | TINYINT(1) | NOT NULL | 0 | 是否私有 |
| password_hash | VARCHAR(64) |  | NULL | 房间密码哈希（私有房） |
| status | ENUM("waiting","in_progress","finished","destroyed") | NOT NULL | "waiting" | 房间状态 |
| current_match_id | VARCHAR(32) |  | NULL | 当前进行的比赛ID |
| spectator_count | INT UNSIGNED | NOT NULL | 0 | 观战人数（缓存） |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| finished_at | DATETIME |  | NULL | 结束时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_rooms_room_id | room_id | UNIQUE | 房间ID查询 |
| idx_rooms_status | status, is_private | NORMAL | 大厅列表筛选 |
| idx_rooms_host | host_user_id | NORMAL | 房主房间查询 |
| idx_rooms_game_type | game_type, status | NORMAL | 按游戏类型筛选 |
| idx_rooms_created | created_at | NORMAL | 按时间排序 |

- 建表SQL：
CREATE TABLE rooms (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 room_id VARCHAR(32) NOT NULL,
 host_user_id VARCHAR(32) NOT NULL,
 name VARCHAR(64) NOT NULL,
 game_type ENUM('x01','cricket') NOT NULL,
 target_score INT UNSIGNED DEFAULT 501,
 match_mode ENUM('casual','multi') NOT NULL DEFAULT 'casual',
 legs_to_win TINYINT UNSIGNED DEFAULT 3,
 diddle_enabled TINYINT(1) NOT NULL DEFAULT 0,
 max_rounds INT UNSIGNED NOT NULL DEFAULT 20,
 in_rule ENUM('straight','double','master') NOT NULL DEFAULT 'straight',
 out_rule ENUM('straight','double','master') NOT NULL DEFAULT 'double',
 max_players TINYINT UNSIGNED NOT NULL DEFAULT 2,
 is_private TINYINT(1) NOT NULL DEFAULT 0,
 password_hash VARCHAR(64) DEFAULT NULL,
 status ENUM('waiting','in_progress','finished','destroyed') NOT NULL DEFAULT 'waiting',
 current_match_id VARCHAR(32) DEFAULT NULL,
 spectator_count INT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 finished_at DATETIME DEFAULT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_rooms_room_id (room_id),
 KEY idx_rooms_status (status, is_private),
 KEY idx_rooms_host (host_user_id),
 KEY idx_rooms_game_type (game_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间表';

### room_players — 房间玩家表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| room_id | VARCHAR(32) | NOT NULL | - | 房间ID |
| user_id | VARCHAR(32) | NOT NULL | - | 玩家用户ID |
| is_host | TINYINT(1) | NOT NULL | 0 | 是否房主 |
| is_ready | TINYINT(1) | NOT NULL | 0 | 是否准备 |
| player_index | TINYINT UNSIGNED | NOT NULL | 0 | 玩家序号（0/1） |
| joined_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 加入时间 |
| left_at | DATETIME |  | NULL | 离开时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_room_player | room_id, user_id | UNIQUE | 房间内玩家唯一 |
| idx_room_players_room | room_id | NORMAL | 房间玩家列表 |
| idx_room_players_user | user_id | NORMAL | 用户所在房间 |

- 建表SQL：
CREATE TABLE room_players (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 room_id VARCHAR(32) NOT NULL,
 user_id VARCHAR(32) NOT NULL,
 is_host TINYINT(1) NOT NULL DEFAULT 0,
 is_ready TINYINT(1) NOT NULL DEFAULT 0,
 player_index TINYINT UNSIGNED NOT NULL DEFAULT 0,
 joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 left_at DATETIME DEFAULT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_room_player (room_id, user_id),
 KEY idx_room_players_room (room_id),
 KEY idx_room_players_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间玩家表';

### matches — 比赛表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| match_id | VARCHAR(32) | UNIQUE NOT NULL | - | 比赛业务ID（MT_XXXX） |
| room_id | VARCHAR(32) |  | NULL | 来源房间ID（本地比赛为NULL） |
| match_type | ENUM("online","local","practice","dartbot") | NOT NULL | - | 比赛类型 |
| game_type | ENUM("x01","cricket") | NOT NULL | - | 游戏类型 |
| ★target_score | INT UNSIGNED |  | 501 | ★v1.1：X01目标分301/501/701/901/1101 |
| ★match_mode | ENUM("casual","multi") | NOT NULL | "casual" | ★v1.1：casual休闲单局/multi多局 |
| ★legs_to_win | TINYINT UNSIGNED |  | 3 | ★v1.1：1-10局胜（multi） |
| ★overtime_rule | ENUM("none","sudden_death","extra_round") | NOT NULL | "none" | ★v1.1：超轮判定策略 |
| ★max_rounds | INT UNSIGNED | NOT NULL | 0 | ★v1.1：轮数上限（0=无上限），X01与Cricket共用 |
| in_rule | ENUM("straight","double","master") | NOT NULL | "straight" | 开局规则 |
| out_rule | ENUM("straight","double","master") | NOT NULL | "double" | 结镖规则 |
| status | ENUM("pending","in_progress","finished","forfeited","cancelled") | NOT NULL | "pending" | 比赛状态 |
| match_version | INT UNSIGNED | NOT NULL | 0 | 当前状态版本号（乐观锁） |
| current_leg | INT UNSIGNED | NOT NULL | 1 | 当前局号 |
| current_player_index | TINYINT UNSIGNED |  | NULL | 当前玩家序号 |
| winner_user_id | VARCHAR(32) |  | NULL | 胜者用户ID |
| final_score | VARCHAR(16) |  | NULL | 最终比分（如"3:1"） |
| first_player_index | TINYINT UNSIGNED |  | NULL | 先手玩家序号（争红心结果） |
| started_at | DATETIME |  | NULL | 开始时间 |
| finished_at | DATETIME |  | NULL | 结束时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_matches_match_id | match_id | UNIQUE | 比赛ID查询 |
| idx_matches_room | room_id | NORMAL | 房间关联比赛 |
| idx_matches_status | status | NORMAL | 比赛状态筛选 |
| idx_matches_winner | winner_user_id | NORMAL | 胜者查询 |
| idx_matches_created | created_at | NORMAL | 按时间排序 |
| idx_matches_type | match_type, game_type, status | NORMAL | 复合筛选 |

- 建表SQL：
CREATE TABLE matches (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 match_id VARCHAR(32) NOT NULL,
 room_id VARCHAR(32) DEFAULT NULL,
 match_type ENUM('online','local','practice','dartbot') NOT NULL,
 game_type ENUM('x01','cricket') NOT NULL,
 target_score INT UNSIGNED DEFAULT 501,
 match_mode ENUM('casual','multi') NOT NULL DEFAULT 'casual',
 legs_to_win TINYINT UNSIGNED DEFAULT 3,
 diddle_enabled TINYINT(1) NOT NULL DEFAULT 0,
 max_rounds INT UNSIGNED NOT NULL DEFAULT 20,
 in_rule ENUM('straight','double','master') NOT NULL DEFAULT 'straight',
 out_rule ENUM('straight','double','master') NOT NULL DEFAULT 'double',
 status ENUM('pending','in_progress','finished','forfeited','cancelled') NOT NULL DEFAULT 'pending',
 match_version INT UNSIGNED NOT NULL DEFAULT 0,
 current_leg INT UNSIGNED NOT NULL DEFAULT 1,
 current_player_index TINYINT UNSIGNED DEFAULT NULL,
 winner_user_id VARCHAR(32) DEFAULT NULL,
 final_score VARCHAR(16) DEFAULT NULL,
 first_player_index TINYINT UNSIGNED DEFAULT NULL,
 started_at DATETIME DEFAULT NULL,
 finished_at DATETIME DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_matches_match_id (match_id),
 KEY idx_matches_room (room_id),
 KEY idx_matches_status (status),
 KEY idx_matches_winner (winner_user_id),
 KEY idx_matches_type (match_type, game_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛表';

### match_players — 比赛玩家表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| match_id | VARCHAR(32) | NOT NULL | - | 比赛ID |
| user_id | VARCHAR(32) | NOT NULL | - | 玩家用户ID（DartBot为AI_XXX） |
| player_index | TINYINT UNSIGNED | NOT NULL | 0 | 玩家序号 |
| nickname | VARCHAR(32) | NOT NULL | - | 比赛时昵称快照 |
| avatar_url | VARCHAR(512) |  |  | 头像快照 |
| level | INT UNSIGNED | NOT NULL | 1 | 等级快照 |
| legs_won | INT UNSIGNED | NOT NULL | 0 | 获胜局数 |
| ppr | DECIMAL(5,1) | NOT NULL | 0.0 | 本场PPR（X01） |
| is_ai | TINYINT(1) | NOT NULL | 0 | 是否AI玩家 |
| ai_difficulty | ENUM("beginner","intermediate","advanced","pro") |  | NULL | ★v1.1：AI难度（入门/进阶/高手/专业，唯一来源M4），DartBot/AI对局用 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| ★cricket_state | JSON |  | NULL | ★v1.1：Cricket运行时快照{closed,counts,score}（用于断线重连/mid-turn恢复） |
| ★cricket_mpr | DECIMAL(5,2) |  | NULL | ★v1.1：本场Cricket MPR（每回合平均Mark数） |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_match_player | match_id, user_id | UNIQUE | 比赛内玩家唯一 |
| idx_match_players_match | match_id | NORMAL | 比赛玩家列表 |
| idx_match_players_user | user_id | NORMAL | 用户比赛历史 |

- 建表SQL：
CREATE TABLE match_players (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 match_id VARCHAR(32) NOT NULL,
 user_id VARCHAR(32) NOT NULL,
 player_index TINYINT UNSIGNED NOT NULL DEFAULT 0,
 nickname VARCHAR(32) NOT NULL,
 avatar_url VARCHAR(512) NOT NULL DEFAULT '',
 level INT UNSIGNED NOT NULL DEFAULT 1,
 legs_won INT UNSIGNED NOT NULL DEFAULT 0,
 ppr DECIMAL(5,1) NOT NULL DEFAULT 0.0,
 is_ai TINYINT(1) NOT NULL DEFAULT 0,
 ai_difficulty ENUM('easy','normal','hard','pro') DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_match_player (match_id, user_id),
 KEY idx_match_players_match (match_id),
 KEY idx_match_players_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛玩家表';

### legs — 局表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| match_id | VARCHAR(32) | NOT NULL | - | 比赛ID |
| leg_number | INT UNSIGNED | NOT NULL | - | 局号（从1开始） |
| status | ENUM("in_progress","finished","sudden_death") | NOT NULL | "in_progress" | 局状态 |
| winner_user_id | VARCHAR(32) |  | NULL | 胜者 |
| total_turns | INT UNSIGNED | NOT NULL | 0 | 总回合数 |
| started_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 开始时间 |
| finished_at | DATETIME |  | NULL | 结束时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_leg_match_num | match_id, leg_number | UNIQUE | 比赛内局号唯一 |
| idx_legs_match | match_id | NORMAL | 比赛局列表 |
| idx_legs_winner | winner_user_id | NORMAL | 胜者统计 |

- 建表SQL：
CREATE TABLE legs (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 match_id VARCHAR(32) NOT NULL,
 leg_number INT UNSIGNED NOT NULL,
 status ENUM('in_progress','finished','sudden_death') NOT NULL DEFAULT 'in_progress',
 winner_user_id VARCHAR(32) DEFAULT NULL,
 total_turns INT UNSIGNED NOT NULL DEFAULT 0,
 started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at DATETIME DEFAULT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_leg_match_num (match_id, leg_number),
 KEY idx_legs_match (match_id),
 KEY idx_legs_winner (winner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='局表';

### turns — 回合表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| match_id | VARCHAR(32) | NOT NULL | - | 比赛ID |
| leg_number | INT UNSIGNED | NOT NULL | - | 局号 |
| turn_number | INT UNSIGNED | NOT NULL | - | 回合号 |
| user_id | VARCHAR(32) | NOT NULL | - | 玩家ID |
| player_index | TINYINT UNSIGNED | NOT NULL | - | 玩家序号 |
| turn_score | INT | NOT NULL | 0 | 回合总分 |
| is_bust | TINYINT(1) | NOT NULL | 0 | 是否Bust |
| is_checkout | TINYINT(1) | NOT NULL | 0 | 是否Checkout |
| checkout_label | VARCHAR(8) |  | NULL | 结镖双倍值（如D20） |
| remaining_before | INT |  | NULL | 回合前剩余分（X01） |
| remaining_after | INT |  | NULL | 回合后剩余分（X01） |
| cricket_marks | INT |  | NULL | Cricket回合Marks |
| cricket_score_gained | INT |  | NULL | Cricket回合得分 |
| is_white_horse | TINYINT(1) |  | 0 | 是否White Horse（Cricket） |
| input_source | ENUM("manual","voice","hardware_vision") | NOT NULL | "manual" | 输入来源 |
| client_timestamp | BIGINT UNSIGNED |  | NULL | 客户端时间戳（幂等） |
| undo_status | ENUM("active","undone","locked") | NOT NULL | "active" | 撤销状态 |
| undo_expire_at | DATETIME |  | NULL | 撤销窗口过期时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| ★state_before | JSON |  | NULL | ★v1.1：本回合提交前的cricketState快照（Cricket撤销整体恢复用） |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_turn_match_leg_num | match_id, leg_number, turn_number, user_id | UNIQUE | 比赛内回合唯一 |
| idx_turns_match | match_id, leg_number | NORMAL | 比赛回合列表 |
| idx_turns_user | user_id | NORMAL | 玩家回合统计 |
| idx_turns_checkout | is_checkout | NORMAL | Checkout统计 |
| idx_turns_client_ts | user_id, client_timestamp | NORMAL | 幂等去重 |

- 建表SQL：
CREATE TABLE turns (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 match_id VARCHAR(32) NOT NULL,
 leg_number INT UNSIGNED NOT NULL,
 turn_number INT UNSIGNED NOT NULL,
 user_id VARCHAR(32) NOT NULL,
 player_index TINYINT UNSIGNED NOT NULL,
 turn_score INT NOT NULL DEFAULT 0,
 is_bust TINYINT(1) NOT NULL DEFAULT 0,
 is_checkout TINYINT(1) NOT NULL DEFAULT 0,
 checkout_label VARCHAR(8) DEFAULT NULL,
 remaining_before INT DEFAULT NULL,
 remaining_after INT DEFAULT NULL,
 cricket_marks INT DEFAULT NULL,
 cricket_score_gained INT DEFAULT NULL,
 is_white_horse TINYINT(1) NOT NULL DEFAULT 0,
 input_source ENUM('manual','voice','hardware_vision') NOT NULL DEFAULT 'manual',
 client_timestamp BIGINT UNSIGNED DEFAULT NULL,
 undo_status ENUM('active','undone','locked') NOT NULL DEFAULT 'active',
 undo_expire_at DATETIME DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_turn_match_leg_num (match_id, leg_number, turn_number, user_id),
 KEY idx_turns_match (match_id, leg_number),
 KEY idx_turns_user (user_id),
 KEY idx_turns_client_ts (user_id, client_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回合表';

### darts — 镖表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| turn_id | BIGINT UNSIGNED | NOT NULL | - | 关联回合ID |
| match_id | VARCHAR(32) | NOT NULL | - | 比赛ID（冗余，加速查询） |
| dart_index | TINYINT UNSIGNED | NOT NULL | - | 第幾镖（1/2/3） |
| label | VARCHAR(8) | NOT NULL | - | 分区标签（T20/D16/S5/BULL/MISS） |
| number | TINYINT UNSIGNED | NOT NULL | 0 | 分区数字（1-20，Bull=25，MISS=0） |
| multiplier | TINYINT UNSIGNED | NOT NULL | 0 | 倍数（1=S,2=D,3=T，MISS=0） |
| score | SMALLINT UNSIGNED | NOT NULL | 0 | 该镖得分 |
| position_x | DECIMAL(8,4) |  | NULL | 落点X坐标（硬件识别，mm） |
| position_y | DECIMAL(8,4) |  | NULL | 落点Y坐标（硬件识别，mm） |
| confidence | DECIMAL(5,4) |  | NULL | 识别置信度（硬件识别） |
| camera_id | VARCHAR(16) |  | NULL | 识别摄像头ID（高端） |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_dart_turn_index | turn_id, dart_index | UNIQUE | 回合内镖序号唯一 |
| idx_darts_match | match_id | NORMAL | 比赛镖数据查询 |
| idx_darts_label | label | NORMAL | 按分区统计（如T20命中率） |
| idx_darts_score | score | NORMAL | 按分数统计 |

- 建表SQL：
CREATE TABLE darts (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 turn_id BIGINT UNSIGNED NOT NULL,
 match_id VARCHAR(32) NOT NULL,
 dart_index TINYINT UNSIGNED NOT NULL,
 label VARCHAR(8) NOT NULL,
 number TINYINT UNSIGNED NOT NULL DEFAULT 0,
 multiplier TINYINT UNSIGNED NOT NULL DEFAULT 0,
 score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
 position_x DECIMAL(8,4) DEFAULT NULL,
 position_y DECIMAL(8,4) DEFAULT NULL,
 confidence DECIMAL(5,4) DEFAULT NULL,
 camera_id VARCHAR(16) DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_dart_turn_index (turn_id, dart_index),
 KEY idx_darts_match (match_id),
 KEY idx_darts_label (label),
 KEY idx_darts_score (score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镖表';

### friends — 好友关系表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| user_id_a | VARCHAR(32) | NOT NULL | - | 用户A（较小ID） |
| user_id_b | VARCHAR(32) | NOT NULL | - | 用户B（较大ID） |
| status | ENUM("active","blocked","deleted") | NOT NULL | "active" | 关系状态 |
| friend_since | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 成为好友时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_friends_pair | user_id_a, user_id_b | UNIQUE | 好友对唯一（A<B） |
| idx_friends_a | user_id_a, status | NORMAL | 用户A的好友 |
| idx_friends_b | user_id_b, status | NORMAL | 用户B的好友 |

- 建表SQL：
CREATE TABLE friends (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 user_id_a VARCHAR(32) NOT NULL,
 user_id_b VARCHAR(32) NOT NULL,
 status ENUM('active','blocked','deleted') NOT NULL DEFAULT 'active',
 friend_since DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_friends_pair (user_id_a, user_id_b),
 KEY idx_friends_a (user_id_a, status),
 KEY idx_friends_b (user_id_b, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
-- 注：插入时保证 user_id_a < user_id_b（字典序），查询时用OR

### friend_requests — 好友申请表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| request_id | VARCHAR(32) | UNIQUE NOT NULL | - | 申请业务ID（FR_XXXX） |
| from_user_id | VARCHAR(32) | NOT NULL | - | 发送者 |
| to_user_id | VARCHAR(32) | NOT NULL | - | 接收者 |
| message | VARCHAR(100) |  |  | 申请留言 |
| status | ENUM("pending","accepted","rejected","cancelled") | NOT NULL | "pending" | 状态 |
| handled_at | DATETIME |  | NULL | 处理时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_requests_request_id | request_id | UNIQUE | 申请ID查询 |
| uk_requests_pair | from_user_id, to_user_id, status | UNIQUE | 同一对用户只能有一个pending申请 |
| idx_requests_to | to_user_id, status | NORMAL | 收到的申请列表 |
| idx_requests_from | from_user_id | NORMAL | 发出的申请 |

- 建表SQL：
CREATE TABLE friend_requests (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 request_id VARCHAR(32) NOT NULL,
 from_user_id VARCHAR(32) NOT NULL,
 to_user_id VARCHAR(32) NOT NULL,
 message VARCHAR(100) NOT NULL DEFAULT '',
 status ENUM('pending','accepted','rejected','cancelled') NOT NULL DEFAULT 'pending',
 handled_at DATETIME DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_requests_request_id (request_id),
 UNIQUE KEY uk_requests_pair (from_user_id, to_user_id, status),
 KEY idx_requests_to (to_user_id, status),
 KEY idx_requests_from (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';

### achievements — 成就定义表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| achievement_id | VARCHAR(32) | UNIQUE NOT NULL | - | 成就业务ID（ACH_XXX） |
| name | VARCHAR(64) | NOT NULL | - | 成就名称 |
| description | VARCHAR(256) | NOT NULL | - | 成就描述 |
| icon | VARCHAR(32) | NOT NULL | - | 图标语义标识（旧版 SF Symbol 名仅作历史映射参考，不要求 Android 使用该库） |
| category | ENUM("match","score","social","practice","special") | NOT NULL | "match" | 分类 |
| target_type | ENUM("count","boolean","progress") | NOT NULL | "boolean" | 目标类型 |
| target_value | INT |  | NULL | 目标值（progress类型） |
| points | INT UNSIGNED | NOT NULL | 10 | 成就点数 |
| sort_order | INT UNSIGNED | NOT NULL | 0 | 显示排序 |
| is_active | TINYINT(1) | NOT NULL | 1 | 是否启用 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_achievements_id | achievement_id | UNIQUE | 成就ID查询 |
| idx_achievements_category | category, is_active | NORMAL | 按分类筛选 |

- 建表SQL：
CREATE TABLE achievements (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 achievement_id VARCHAR(32) NOT NULL,
 name VARCHAR(64) NOT NULL,
 description VARCHAR(256) NOT NULL,
 icon VARCHAR(32) NOT NULL,
 category ENUM('match','score','social','practice','special') NOT NULL DEFAULT 'match',
 target_type ENUM('count','boolean','progress') NOT NULL DEFAULT 'boolean',
 target_value INT DEFAULT NULL,
 points INT UNSIGNED NOT NULL DEFAULT 10,
 sort_order INT UNSIGNED NOT NULL DEFAULT 0,
 is_active TINYINT(1) NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_achievements_id (achievement_id),
 KEY idx_achievements_category (category, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就定义表';

### user_achievements — 用户成就表

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PRIMARY KEY AUTO_INCREMENT | - | 自增主键 |
| user_id | VARCHAR(32) | NOT NULL | - | 用户ID |
| achievement_id | VARCHAR(32) | NOT NULL | - | 成就ID |
| is_unlocked | TINYINT(1) | NOT NULL | 0 | 是否解锁 |
| progress_current | INT | NOT NULL | 0 | 当前进度 |
| unlocked_at | DATETIME |  | NULL | 解锁时间 |
| created_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

- 索引：
| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| uk_user_achievement | user_id, achievement_id | UNIQUE | 用户成就唯一 |
| idx_ua_user | user_id, is_unlocked | NORMAL | 用户成就列表 |
| idx_ua_achievement | achievement_id | NORMAL | 成就统计 |

- 建表SQL：
CREATE TABLE user_achievements (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 user_id VARCHAR(32) NOT NULL,
 achievement_id VARCHAR(32) NOT NULL,
 is_unlocked TINYINT(1) NOT NULL DEFAULT 0,
 progress_current INT NOT NULL DEFAULT 0,
 unlocked_at DATETIME DEFAULT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_user_achievement (user_id, achievement_id),
 KEY idx_ua_user (user_id, is_unlocked),
 KEY idx_ua_achievement (achievement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就表';

practice_sessions — 练习记录表（★v1.1新增，对应M11）

建表SQL：

CREATE TABLE practice_sessions (

id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

session_id VARCHAR(32) NOT NULL,

user_id VARCHAR(32) NOT NULL,

game_type ENUM('x01','cricket') NOT NULL,
 target_score INT UNSIGNED DEFAULT 501,
 match_mode ENUM('casual','multi') NOT NULL DEFAULT 'casual',
 legs_to_win TINYINT UNSIGNED DEFAULT 3,
 diddle_enabled TINYINT(1) NOT NULL DEFAULT 0 DEFAULT 'x01',

practice_mode ENUM('count_up','countdown','checkout','random_checkout','cricket_free','cricket_close','cricket_triple') NOT NULL,

total_score INT UNSIGNED NOT NULL DEFAULT 0,

total_darts INT UNSIGNED NOT NULL DEFAULT 0,

rounds INT UNSIGNED NOT NULL DEFAULT 0,

best_round INT UNSIGNED NOT NULL DEFAULT 0,

cricket_closed_turns INT UNSIGNED DEFAULT NULL,

cricket_marks INT UNSIGNED DEFAULT NULL,

cricket_score INT UNSIGNED DEFAULT NULL,

is_personal_best TINYINT(1) NOT NULL DEFAULT 0,

started_at DATETIME DEFAULT NULL,

ended_at DATETIME DEFAULT NULL,

created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

PRIMARY KEY (id),

UNIQUE KEY uk_practice_session_id (session_id),

KEY idx_practice_user (user_id, practice_mode, created_at DESC),

KEY idx_practice_best (user_id, game_type, practice_mode, is_personal_best)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='练习记录表';

# 3. 数据量与分区策略

| 表 | 预估数据量 | 增长速度 | 分区/归档策略 |
| --- | --- | --- | --- |
| users | 百万级 | 稳定 | 不分区，按status软删除 |
| user_settings | 百万级 | 1:1用户 | 不分区 |
| devices | 十万级 | 稳定 | 不分区，按deleted_at软删除 |
| rooms | 万级（活跃） | 快（创建/销毁频繁） | 不分区，finished/destroyed状态30天后归档 |
| room_players | 十万级 | 快 | 不分区，随房间归档 |
| matches | 百万级 | 中 | 按created_at月份分区，12个月热数据，历史归档 |
| match_players | 百万级 | 中 | 随matches分区 |
| legs | 千万级 | 快 | 按match_id哈希分表（如legs_00到legs_15），或随matches分区 |
| turns | 亿级 | 快 | 按match_id哈希分表（turns_00到turns_31），冷数据归档 |
| darts | 亿级 | 快 | 按match_id哈希分表（darts_00到darts_31），冷数据归档 |
| friends | 百万级 | 稳定 | 不分区 |
| friend_requests | 十万级 | 中 | 不分区，已处理申请90天后归档 |
| achievements | 百级 | 不变 | 不分区（配置表） |
| user_achievements | 百万级 | 中 | 不分区 |

# 4. 缓存策略（Redis）

| 缓存Key | 数据结构 | TTL | 说明 |
| --- | --- | --- | --- |
| user:{user_id} | Hash | 1小时 | 用户基本信息缓存 |
| user:stats:{user_id} | Hash | 10分钟 | 用户统计缓存（PPR/胜率/总场次） |
| room:{room_id} | Hash | 房间存活期间 | 房间实时状态（内存态，不依赖DB） |
| match:{match_id}:state | Hash | 比赛存活+1小时 | 比赛实时状态（matchVersion/当前玩家/比分） |
| match:{match_id}:undo | String | 5秒 | 撤销窗口标记（过期自动锁定） |
| device:{device_id}:status | Hash | 5分钟 | 设备在线状态缓存 |
| presence:{user_id} | String | 30秒 | 在线状态（心跳续期） |
| friend:list:{user_id} | Set | 5分钟 | 好友ID列表缓存 |
| rate_limit:{user_id}:{api} | String | 1分钟 | 限流计数器 |
| verify_code:{phone} | String | 5分钟 | 手机验证码 |
| idempotency:{user_id}:{key} | String | 24小时 | 幂等键（防止重复提交） |

# 5. 初始化数据

成就定义表初始化24项成就（与 M9 统计成就模块一致）：

| achievement_id | 名称 | 描述 | 分类 | 目标 |
| --- | --- | --- | --- | --- |
| ACH_FIRST_WIN | 首胜 | 赢得第一场比赛 | match | boolean |
| ACH_FIRST_MATCH | 初出茅庐 | 完成第一场比赛 | match | boolean |
| ACH_10_MATCHES | 小有经验 | 完成10场比赛 | match | 10 |
| ACH_50_MATCHES | 身经百战 | 完成50场比赛 | match | 50 |
| ACH_100_MATCHES | 百战飞镖 | 完成100场比赛 | match | 100 |
| ACH_180 | 满分 | 单回合打出180分 | score | boolean |
| ACH_TON_PLUS | 百回合 | 单回合打出100+分 | score | boolean |
| ACH_CHECKOUT_170 | 极限结镖 | 170分结镖 | score | boolean |
| ACH_WHITE_HORSE | 白马 | Cricket一回合关闭3个分区 | score | boolean |
| ACH_10_WINS | 十连胜 | 连续赢得10场比赛 | match | 10 |
| ACH_FIRST_FRIEND | 初结镖友 | 添加第一个好友 | social | boolean |
| ACH_10_FRIENDS | 镖友遍天下 | 添加10个好友 | social | 10 |
| ACH_FIRST_DARTBOT | AI初体验 | 完成第一场DartBot比赛 | practice | boolean |
| ACH_DARTBOT_PRO | 战胜专业AI | DartBot专业难度获胜 | practice | boolean |
| ACH_COUNT_UP_800 | Count Up达人 | Count Up模式达到800分 | practice | 800 |
| ACH_RANDOM_CHECKOUT_10 | 随机结镖10连 | Random Checkout连续完成10次 | practice | 10 |
| ACH_PERFECTION | 完美主义 | 单局0失误（无Bust） | special | boolean |
| ACH_EARLY_BIRD | 早起镖手 | 早上6-8点完成比赛 | special | boolean |
| ACH_CRICKET_FIRST_WIN | 板球首胜 | 赢得第一场Cricket对局 | match | boolean |
| ACH_CRICKET_WHITE_HORSE | 白马王子 | Cricket单回合关闭3个分区 | score | boolean |
| ACH_CRICKET_PERFECT | 七关全开 | Cricket单局关闭全部7个分区 | score | boolean |
| ACH_CRICKET_MPR3 | 神投手 | Cricket单场MPR达到3.0 | score | boolean |
| ACH_CRICKET_20_WINS | 板球大师 | 赢得20场Cricket对局 | match | 20 |
| ACH_ADAPTIVE_BEAT_PRO | 遇强则强 | 在同级自适应下战胜高于自身实力的AI | practice | boolean |

# 6. 与模块PRD的对应关系

| 数据表 | 对应模块PRD | 数据模型章节 |
| --- | --- | --- |
| users / user_settings | 01_M1_基础设施 | 4.1 User / 4.2 AppSettings |
| devices | 08_M8_硬件接入 | 4.1 Device |
| rooms / room_players | 06_M6_比赛大厅 | 房间数据模型 |
| matches / match_players | 04_M4_对局核心 | 比赛数据模型 |
| legs / turns / darts | 04_M4_对局核心 + 02_M2_规则引擎 | 回合/单镖数据模型 |
| friends / friend_requests | 10_M10_社交 | 好友数据模型 |
| achievements / user_achievements | 09_M9_统计成就 | 成就数据模型 |
| ★practice_sessions | 11_M11_练习模式 | 练习记录数据模型 |

# 8. Room v6 至 v8 详细契约

本节补齐 §7 中被概览化的逐镖表和迁移事实；v9 对抗练习两表保持不变。所有迁移均为纯增量，禁止 fallbackToDestructiveMigration。

## 8.1 dart_hits 版本链

| 版本 | 变化 | 索引 |
| --- | --- | --- |
| v6 | 新增 dart_hits：id、matchId、profileId、legNumber、xMm、yMm、number、multiplier、source、hitAt | 无 |
| v7 | 新增 sessionId、intentNumber、intentMultiplier、dartIndexInRound、windowSpanMm、outBand、outLevel | index_dart_hits_profileId_hitAt |
| v8 | 新增 prescriptionMetric、prescriptionTarget、interventionNote、pressureMode | 新增 index_dart_hits_sessionId；幂等补 profileId_hitAt |
| v9 | dart_hits 不变；新增 versus_match_records 与 versus_round_records | 见 §7 |

- v8 的 dart_hits 共 21 列。DartHitEntity 必须同时声明两个 @Index，保证 v6→v8、v7→v8 和 v8 全新安装结构一致。
- v7/v8 已发布迁移不得回改；后续变化必须新增迁移。pressureMode 当前恒为 0，保留作 P1 预留。
- IMPACT_DRILL 数据不进入比赛记录、成就或排行榜。
## 8.2 App 本地表与服务端表对应

| 本地表 | 服务端目标态 | 同步边界 |
| --- | --- | --- |
| match_records / match_players | matches / match_players | 当前本地权威；服务端目标态未实现 |
| dart_hits | 暂无强制一一映射 | 本地训练与点位诊断；不进入 M9 竞争统计 |
| versus_match_records / versus_round_records | 暂无 P0 同步要求 | 仅双方训练统计 |

# 9. 极速结镖练习本地数据

**状态：**目标态 DB v10。当前 as-built 仍为 DB v9；功能实现时通过 MIGRATION_9_10 纯增量新增，不改写既有比赛与训练数据。

## 9.1 checkout_rush_attempts

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | TEXT | PK | attempt唯一ID |
| session_id | TEXT | NOT NULL | 10题挑战或自由练习会话ID |
| created_at | INTEGER | NOT NULL | UTC毫秒时间戳 |
| target_score | INTEGER | NOT NULL | 待结镖分数 |
| out_mode | TEXT | NOT NULL | MVP固定DOUBLE_OUT |
| bull_mode | TEXT | NOT NULL | MVP标准Bull |
| difficulty | TEXT | NOT NULL | BEGINNER/INTERMEDIATE/CHALLENGE/MIXED |
| darts_json | TEXT | NOT NULL | 按实际顺序序列化List<Dart>；未投镖不补MISS |
| input_mode | TEXT | NOT NULL | dart_by_dart/board_tap |
| throw_elapsed_ms | INTEGER | NOT NULL | 主训练用时 |
| input_elapsed_ms | INTEGER | NOT NULL | 录入用时，仅交互分析 |
| route_hint_used | INTEGER | NOT NULL | 0/1；一旦查看提示即为1 |
| result | TEXT | NOT NULL | CHECKOUT/BUST/NOT_FINISHED/SKIPPED/ABORTED |
| remaining_after | INTEGER | NOT NULL | 裁定后的剩余分 |
| bust_reason | TEXT | NULL | OVER_SCORE/LEAVE_ONE/INVALID_OUT等 |
| retry_of_attempt_id | TEXT | NULL | 重试来源attempt |

## 9.2 索引与迁移

- 索引：session_id；created_at；difficulty + route_hint_used + result。
- MIGRATION_9_10只创建checkout_rush_attempts及索引；不得修改match_records、match_players、versus_match_records、versus_round_records或dart_hits。
- 全新安装直接创建DB v10；升级测试必须证明v9既有数据逐字节语义保留，且新表可正常写入和查询。
- 本表纯本地、B级，不进入服务端正式比赛表；未来云同步必须另立决策。
# **7. 本地数据库（Android Room）— DB v9（★V1.01 新增）**

⚠️ 本节为 as-built 未验证：`data/local/DartVioDatabase.kt` 已声明 `version = 9` 与 `MIGRATION_8_9`，但 `assembleDebug` / `testDebugUnitTest` / `read_lints` 三项验证全缺，`app/schemas/9.json` 尚未导出。§1–§6 为服务端 MySQL 逻辑模型；本节给出端上 Room 库的当前事实。

## **7.1 版本链与当前版本**

当前版本 v9。升级链：v6（`dart_hits` 诞生）→ v7（+7 列：落点诊断意图 / 窗口档位）→ v8（+4 列：量化目标 / 干预标签 / 压力预留）→ v9（新增 `versus_match_records` + `versus_round_records`，纯增量）。既有三张表（`match_records` / `match_players` / `dart_hits`）在 v9 中一列未动，老数据读出的每个值逐位不变，胜率 / 成就 / 排行榜不受影响。

## **7.2 表全集（5 张）**

| 表名 | 引入版本 | 列数 | 统计归属 | 说明 |
| --- | --- | --- | --- | --- |
| match_records | v1–v5 | — | 计入对局记录 / 成就 / 排行榜 | 对局主表（X01 / Cricket） |
| match_players | v1–v5 | — | 同上 | 对局席位明细 |
| dart_hits | v6（v7 +7 / v8 +4） | 21 | 仅落点诊断（精准工坊） | 逐镖落点；不进 M9 统计与成就 |
| versus_match_records | v9 | 17 | 仅双方训练统计 | 双人对抗练习场次；见 §7.3 |
| versus_round_records | v9 | 13 | 仅双方训练统计 | 双人对抗练习逐轮；见 §7.4 |

★对抗练习的数据红线由拆表保证：既有代码读不到这两张表，因此「不计入对局记录 / 成就 / 排行榜」不需要任何查询谓词，也不给 `match_records` 加判别列。

## **7.3 versus_match_records — 对抗练习场次表**

主键 `matchId` 为 UUID 字符串（不复用自增 id：同一毫秒连开两场不会撞主键）。下方「实体缺省」列仅表示实体构造时的取值（保证未赋值时非空），迁移 SQL 不写 DEFAULT 子句，理由见 §7.6。

| 字段名 | 类型 | 约束 | 实体缺省 | 说明 |
| --- | --- | --- | --- | --- |
| matchId | TEXT | PK, NOT NULL | — | 场次 id（UUID） |
| modeKey | TEXT | NOT NULL | — | 模式唯一标识（`VersusModes` 的 modeKey） |
| modeLabel | TEXT | NOT NULL | — | 模式中文名（落库以免枚举改名后历史战报失真） |
| matchSource | TEXT | NOT NULL | VERSUS_PRACTICE | 仅用于导出时自证来源；不是判别列（红线由拆表保证） |
| configJson | TEXT | NOT NULL | — | 开赛配置快照（`BattleConfig.toStorageString()`），战报可复现当时规则与让分 |
| playerNamesCsv | TEXT | NOT NULL | — | 双方名字，`\|` 分隔（名字录入口禁用该分隔符） |
| playerCount | INTEGER | NOT NULL | 2 | 参战人数（双人对抗恒为 2） |
| winnerIndex | INTEGER | NOT NULL | -1 | 胜者席位；-1 = 中止或未结束 |
| winnerName | TEXT | NOT NULL | — | 胜者名字；无胜者时为空串 |
| endReason | TEXT | NOT NULL | — | `BattleEndReason` 枚举名（NORMAL / SHANGHAI / RESIGN / ABORT）；空串 = 进行中或未回填 |
| handicapSummary | TEXT | NOT NULL | — | 让分摘要；无人让分时为空串 |
| roundCount | INTEGER | NOT NULL | 0 | 已结算回合数（含加赛轮） |
| totalDarts | INTEGER | NOT NULL | 0 | 本场总镖数 = 双方所有轮次镖数之和 |
| wentToPlayoff | INTEGER | NOT NULL | 0 | 是否经过平分加赛（0/1；Room 布尔落 INTEGER） |
| startedAt | INTEGER | NOT NULL | — | 开赛时刻（epoch ms） |
| endedAt | INTEGER | NOT NULL | 0 | 结束时刻；0 = 未结束 |
| durationMs | INTEGER | NOT NULL | 0 | 时长（endedAt − startedAt，下限 0） |

## **7.4 versus_round_records — 对抗练习逐轮表**

每次换手落一条。逐镖明细以 `dartsCsv` 一串存下（不单列展开成第三张表）：统计只需 `dartCount` / `roundScore` 两列，`dartsCsv` 只服务战报与导出。

| 字段名 | 类型 | 约束 | 实体缺省 | 说明 |
| --- | --- | --- | --- | --- |
| id | INTEGER | PK 自增 | 0 | 主键 |
| matchId | TEXT | NOT NULL | — | 所属场次 id |
| modeKey | TEXT | NOT NULL | — | 模式 key（冗余，供「某人 × 某模式」聚合时不必回表 join） |
| playerIndex | INTEGER | NOT NULL | — | 出手席位（0 / 1） |
| playerName | TEXT | NOT NULL | — | 出手者名字（冗余，聚合时不必回表 join） |
| roundNo | INTEGER | NOT NULL | — | 第几轮（1 起；加赛轮的 isPlayoff = 1） |
| isPlayoff | INTEGER | NOT NULL | — | 是否加赛轮（0/1） |
| dartsCsv | TEXT | NOT NULL | — | 本轮逐镖，`\|` 分隔的 `Dart.label()` 记号（如 T20\|S5\|MISS） |
| dartCount | INTEGER | NOT NULL | — | 本轮镖数（单独成列，统计时不必解析 CSV） |
| roundScore | INTEGER | NOT NULL | — | 本轮得分（含义随模式：Bull 之争是命中数，环游类是推进格数） |
| runningScore | INTEGER | NOT NULL | — | 本轮结束后的累计分 / 累计进度（战报曲线数据源） |
| targetSnapshot | TEXT | NOT NULL | — | 本轮目标文字快照（如「打 18 分区」） |
| recordedAt | INTEGER | NOT NULL | — | 落库时刻（epoch ms） |

## **7.5 索引（★V1.01 新增 3 条）**

| 索引名 | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| index_versus_match_records_modeKey_endedAt | (modeKey, endedAt) | 复合 | 按模式查历史战报 |
| index_versus_round_records_matchId | (matchId) | 普通 | 战报读取本场全部轮次 |
| index_versus_round_records_playerName | (playerName) | 普通 | 「某人 × 某模式」训练统计聚合（与 modeKey 同表冗余，故聚合不必回表） |

★三条索引必须在实体上用 `@Entity(indices = [@Index(...)])` 声明，只在迁移 SQL 里写 CREATE INDEX 不算数（Room 迁移校验会报索引不一致）。索引名与 Room 生成的默认名 `index_<表名>_<列名...>` 逐字一致，两侧都不得改名。

## **7.6 MIGRATION_8_9（纯增量）**

- 只做两件事：CREATE TABLE 两张新表 + CREATE INDEX 三条索引；既有三表一列未动、一行未改。
- ★迁移 SQL 不写 DEFAULT 子句：实体未标 `@ColumnInfo(defaultValue)` 时，Room 生成的 CREATE TABLE 里没有 DEFAULT，迁移后会逐列比对默认值，多写即抛 "Migration didn't properly handle ..."；新表无存量行，本就不需要默认值。
- ★索引名必须与 `@Entity(indices=[@Index(...)])` 的声明逐字一致；v6→v8 / v7→v8 / v9 全新安装三条路径产出的索引集合必须完全相同。
- ★红线：禁 `fallbackToDestructiveMigration`；迁移一旦发布不可回改（v9 两表结构即冻结）；既有断言不删。
- 写入时机：开赛插一行场次（endedAt = 0、endReason = 空串），每次换手往逐轮表追加一条，结束 / 认输 / 中途退出时回填该场（REPLACE）——「中途退出不丢数据」不依赖任何 onDestroy 回调。
- 统计口径：对抗练习不计入线上对局记录 / 成就 / 排行榜，只按 `playerName` × `modeKey` 计入双方训练统计（见 `09_M9_统计成就_V1.06.docx` §7）。
# **10. 本地 Room 版本链汇总与 MIGRATION_10_11（★V1.05 新增）**

本章汇总 App 本地 Room 数据库的版本演进，并给出 v10 → v11 的落地写法。所有迁移共用的三条红线：禁 fallbackToDestructiveMigration；迁移一旦发布不可回改；既有断言不删不改。

# **10.1 版本链一览**

| 版本 | 变化摘要 | 关键红线 | 引入时间 |
| --- | --- | --- | --- |
| v9 | 对抗练习 versus_match_records / versus_round_records 两张表与三条索引 | 只做 CREATE TABLE + CREATE INDEX，既有三表一列未动；迁移 SQL 不写 DEFAULT 子句 | 2026-09-V1.01 |
| v10 | 极速结镖 checkout_rush_attempts（练习专用 Room 明细表） | 登记统计归属为 B 级训练数据；练习数据不进正式历史 | 2026-09-18（V1.04） |
| v11 | match_records 纯增量四列 source / roomId / winnerName / forfeited | ★默认值就是老行应被读成的值，因此不需要订正 UPDATE；红线收在 MatchStatsFilter.countsForStats，DAO 谓词只作性能前置 | 2026-09-19（V1.05） |

# **10.2 match_records 新增四列（v11）**

四列全部是纯增量：既有三表一列未动、一行未改。★每列的默认值都等于「老行应被读成的值」（source = LOCAL、roomId = 空串、winnerName = 空串、forfeited = 0），因此迁移不需要任何订正 UPDATE，老用户的统计分母也不会变化。

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| source | TEXT | NOT NULL | 'LOCAL' | 对局来源：LOCAL 本地 / LAN 联机（房间 betr-hosting）。★未知取值必须宽容回落到 LOCAL，不得静默剔除老行 |
| roomId | TEXT | NOT NULL | '' (空串) | 联机房间号；本地行恒为空串（不参与任何 join，仅用于展示与反问来源） |
| winnerName | TEXT | NOT NULL | '' (空串) | 胜者显示名。★必须独立成列：房主端只有线上身份，拿不到客人档案 ID，不能靠 users 表反查 |
| forfeited | INTEGER | NOT NULL | 0 | 是否判负终结（T8 弃权）。0 = 正常打完，1 = 对手掉线判负 |

# **10.3 MIGRATION_10_11 写法**

• 迁移体只做 ALTER TABLE match_records ADD COLUMN source TEXT NOT NULL DEFAULT "LOCAL" 等四条，逐列比对：实体侧标注 @ColumnInfo(defaultValue) 与迁移 SQL 的 DEFAULT 必须一致，多写或漏写都会在迁移后的校验中抛出 Have 差异。

• ★列顺序写在 SQL 里，不要依赖 ALTER 之后的表顺序做任何推断：读取一律走列名。

• ★不新增针对这四列的索引：source 只是战绩查询的前置谓词，历史列表按时间倒序排即可；贸然加索引会把成本转嫁到每一次落库写入。

• ★三条安装路径（v10 → v11 迁移、全新安装、中间版本跳升）产出的列集合与默认值必须完全相同；索引名字面量与 @Entity(indices=[@Index(...)]) 声明逐字一致。

# **10.4 历史口径与战绩口径分两个读取入口**

• observeMatches()：历史口径，返回含联机在内的全部对局；消费方是统计页的历史列表。

• observeStatsMatches()：战绩口径，排除 source = LAN 的行；消费方是成就、排行榜与统计计算，统计页用 combine 同时取两份。

• ★红线唯一判据是 MatchStatsFilter.countsForStats(record)；DAO 里的 WHERE source <> "LAN" 只是性能前置，Repository 与成就取数必须按同一判据再兜一次 —— 「忘了写谓词」的后果只是慢一点，而不是数据算错。

• ★第二道闸：联机行的 isFormal = false，即使有人绕开了 Filter 也不会被当作正式战绩。

• 统计口径的产品侧表达（标签、文案与「联机不计入战绩」提示）见 09《统计成就》§12。
