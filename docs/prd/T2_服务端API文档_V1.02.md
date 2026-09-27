**DartVio**

**服务端 API 文档**

版本 V1.02 \| 2026-09-17 \| max_rounds 无上限口径订正
历史修订摘要（旧版本与日期仅供追溯）：
版本 V1.00 \| 2026-09-12 \| T2（命名归零，原 v1.0）
RESTful API + WebSocket 事件协议
供后端开发与客户端对接使用

# 0 本期平台最终重大决策

D-PLATFORM-001 \| CONFIRMED \| 2026-09-15 \| 决策来源：用户正式授权。DartVio 本期仅开发 Android，采用 Kotlin + Jetpack Compose；iOS 不纳入本期范围。本决定为最终重大决策，不再标记为 OPEN。

当前平台基线：minSdk 24，compileSdk/targetSdk 37；使用 Android SDK 与 Gradle 构建环境、相关 Kotlin/JVM 单元测试及 Android 设备/模拟器验收。历史代码量、测试结果与数据库版本不代表当前工程状态。

适用边界：Swift、UIKit、SwiftUI、SF Symbols、iPhone/iPad 及其专用 API、资源和验收项均为旧版遗留或未来范围，不作为本期实施与验收条件。保留其业务流程、视觉语义和交互含义；未明确的 Android 映射及其他 OPEN 不在本次裁决。旧 pt 尺寸不直接作为 Android 验收值，Android 布局使用 dp、字号使用 sp，具体数值冲突仍待原责任模块裁决。

文档依据：用户最新明确决定 > T6 正式决定 > 对应功能模块最高版本 > 产品总览 > 技术附件和历史实现记录。正式登记见《T6_产品路线与决策记录_V1.01.docx》§0；当前文档版本与文件引用见《00_产品总览与架构_V1.05.docx》§6。历史修订中的旧版本号只用于追溯，不代表当前文件入口。

# 变更记录

| 版本 | 日期 | 变更类型 | 变更内容 | 影响范围 | 变更原因 |
| --- | --- | --- | --- | --- | --- |
| V1.02 | 2026-09-17 | 开发过程回写 | max_rounds 无上限口径订正 | 相关模块、接口、数据模型与验收 | 依据 Temper File 定稿回写提示词与当前 PRD 对比 |
| V1.01 | 2026-09-15 | 重大决策登记及平台对齐 | D-PLATFORM-001：本期仅 Android，Kotlin + Jetpack Compose；iOS 不纳入本期。新增§0平台适用声明，标注旧版 Apple 写法，更新当前文件版本与交叉引用。原业务规则及其他 OPEN 不变。 | 本文件平台定义、相关示例/验收及文档包引用 | 用户正式授权；由 V1.00 升级至 V1.01。 |
| V1.00 | 2026-09-12 | 命名归零 | ★版本号归零（依《00 产品总览与架构》§7.1：文件名版本 = 文档内版本标识，格式 V1.0X）：文件名与文内版本标识统一为 V1.00 —— 原 vX.Y 编号（含正文/历史记录中的 vX.Y 引用）均为历史编号，此后一律按 V1.0X 递增（V1.01 → V1.02 …）。本文件原文件名与文内声明均为 v1.0，而变更记录已到 v1.1，本次一并归零。本轮仅做版本命名规范化，正文内容未变；as-built 内容回写按《00 产品总览与架构》§9 待办 T7 逐份推进。 | T2 服务端 API 文档（文件名 v1.0 → V1.00） | 版本命名规范化：本文件长期沿用旧格式 vX.Y，且文件名 / 文内声明 / 变更记录三处版本号互不一致；《00 产品总览与架构》§7.1要求统一为 V1.0X 并把本轮修订作为重定基线 V1.00。 |
| v1.0 | 2026-09-05 | 新增 | 初始版本，8类RESTful API + WebSocket 26种事件 + 错误码 + 安全规范 | 全部服务端接口 | T2任务，基于12份模块PRD整理 |
| v1.1 | 2026-09-10 | 补全+对齐 | ①★创建房间/房间详情API参数对齐M6 v1.1：total_rounds更名为max_rounds（回合上限，仅X01），新增target_score(301/501/701/901/1101)、match_mode(casual/multi)、legs_to_win(1-10)、diddle_enabled；game_type枚举x01/cricket；②★比赛状态/详情响应补cricketState（含closed/counts/score）与matchMode/legsToWin字段，maxRounds→maxRounds；③★提交回合API补充Cricket分支说明（调用M2 validateCricketTurn，无Bust，返回cricketState）；④★统计API补充Cricket指标（markRate/closeRate/bullRate/scoreEfficiency）；⑤★新增§13.5练习API（练习记录保存/查询/最佳成绩）；⑥§14对应关系补M11练习模式；⑦错误码新增Cricket相关（非目标分区/非法镖） | 全部服务端接口 | 产品决策：Cricket提升P0+目标分301/501/701+玩法局数模型统一+练习模式P0 |

# 1. 概述

## 1.1 API设计原则

- • RESTful风格：资源命名用名词复数，HTTP方法表达动作（GET/POST/PUT/DELETE）
- • 版本管理：URL路径包含版本号 /api/v1/，不兼容变更升级版本号
- • 统一响应：所有接口返回统一JSON格式（code/message/data）
- • 鉴权：JWT Token，Header中携带 Authorization: Bearer <token>
- • 幂等：写操作支持幂等键（Idempotency-Key），防止重复提交
- • 分页：列表接口统一使用 page/page_size 参数，返回 total/page/page_size/items
- • 时间格式：ISO 8601（2026-09-05T10:24:00Z），UTC时间
- • 接口保持平台无关；本期 App 客户端仅 Android。iOS 为未来范围，不属于本期交付或验收；硬件协议含义保持不变。
## 1.2 基础信息

| 项目 | 值 |
| --- | --- |
| 基础URL（生产） | https://api.dartvio.com |
| 基础URL（测试） | https://api-staging.dartvio.com |
| API版本 | v1 |
| 完整前缀 | https://api.dartvio.com/api/v1 |
| WebSocket URL | wss://api.dartvio.com/ws/v1 |
| 内容类型 | application/json; charset=utf-8 |
| 字符编码 | UTF-8 |
| 时间格式 | ISO 8601 UTC |
| 限流 | 60次/分钟（普通用户），10次/秒（突发） |

# 2. 通用规范

## 2.1 统一响应格式

// 成功响应
{
 "code": 0,
 "message": "success",
 "data": { ... }
}

// 失败响应
{
 "code": 40001,
 "message": "参数错误：手机号格式不正确",
 "data": null
}

// 分页响应
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 128,
 "page": 1,
 "page_size": 20,
 "items": [ ... ]
 }
}

## 2.2 鉴权

// 请求头
Authorization: Bearer <access_token>

// Token结构（JWT）
Header: { "alg": "HS256", "typ": "JWT" }
Payload: {
 "userId": "DV_8X3K2A",
 "exp": 1693987200, // 过期时间（2小时）
 "iat": 1693980000, // 签发时间
 "type": "access" // access / refresh
}

// Token刷新
// access_token过期（2小时）→ 用 refresh_token（30天）换取新token
// refresh_token过期 → 重新登录

## 2.3 分页参数

| 参数 | 类型 | 默认值 | 范围 | 说明 |
| --- | --- | --- | --- | --- |
| page | int | 1 | 1-1000 | 页码，从1开始 |
| page_size | int | 20 | 1-100 | 每页数量 |

## 2.4 错误码总表

| 错误码 | HTTP状态 | 类别 | 说明 |
| --- | --- | --- | --- |
| 0 | 200 | - | 成功 |
| 40001 | 400 | 参数 | 参数错误（详细原因见message） |
| 40002 | 400 | 参数 | 缺少必填参数 |
| 40003 | 400 | 参数 | 参数格式错误 |
| 40101 | 401 | 鉴权 | 未登录或Token无效 |
| 40102 | 401 | 鉴权 | Token已过期 |
| 40103 | 401 | 鉴权 | Refresh Token已过期，请重新登录 |
| 40301 | 403 | 权限 | 无权限访问该资源 |
| 40302 | 403 | 权限 | 账号被禁用 |
| 40401 | 404 | 资源 | 用户不存在 |
| 40402 | 404 | 资源 | 房间不存在或已解散 |
| 40403 | 404 | 资源 | 比赛不存在 |
| 40404 | 404 | 资源 | 设备不存在 |
| 40901 | 409 | 冲突 | 版本冲突（matchVersion不匹配） |
| 40902 | 409 | 冲突 | 房间已满 |
| 40903 | 409 | 冲突 | 已经是好友 |
| 40904 | 409 | 冲突 | 好友申请已发送 |
| 42901 | 429 | 限流 | 请求过于频繁 |
| 50001 | 500 | 服务端 | 服务器内部错误 |
| 50301 | 503 | 服务端 | 服务维护中 |
| 40001 | 400 | 请求错误 | ★v1.1：Cricket模式提交了非15-20/Bull的镖 |
| 40002 | 400 | 请求错误 | ★v1.1：Cricket分区计数超过3或非法Mark |
| 40003 | 400 | 请求错误 | ★v1.1：legs_to_win超出1-10范围 |
| 40004 | 400 | 请求错误 | ★v1.1：对局未满足获胜条件（未关闭全部分区或分数不足） |

# 3. 认证 API

#### POST /api/v1/auth/send-code

发送手机验证码

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | string | 是 | 手机号（11位，1开头） |
| type | string | 否 | login(默认)/register |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "expire_in": 300, // 验证码有效期（秒）
 "retry_after": 60 // 重新发送间隔（秒）
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 手机号格式错误 |
| 42901 | 发送过于频繁（60秒内） |

#### POST /api/v1/auth/login-phone

手机号验证码登录/注册

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | string | 是 | 手机号 |
| code | string | 是 | 6位验证码 |
| device_info | object | 否 | 设备信息（型号/系统版本/App版本） |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "access_token": "eyJhbGciOiJIUzI1NiIs...",
 "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
 "expires_in": 7200,
 "user": {
 "userId": "DV_8X3K2A",
 "nickname": "飞镖选手1234",
 "avatarUrl": "https://cdn.dartvio.com/avatar/xxx.jpg",
 "gender": "unknown",
 "level": 1,
 "levelTitle": "飞镖新手",
 "isNewUser": true // 是否新注册用户
 }
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 验证码错误或已过期 |
| 40302 | 账号被禁用 |

#### POST /api/v1/auth/login-apple

Apple ID 登录

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| identity_token | string | 是 | Apple返回的identityToken |
| device_info | object | 否 | 设备信息 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "access_token": "...",
 "refresh_token": "...",
 "expires_in": 7200,
 "user": { ... }
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | identityToken无效或已过期 |

#### POST /api/v1/auth/refresh

刷新Access Token

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| refresh_token | string | 是 | 刷新令牌 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "access_token": "eyJhbGciOiJIUzI1NiIs...",
 "expires_in": 7200
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40103 | Refresh Token已过期，请重新登录 |

#### POST /api/v1/auth/logout

退出登录

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| refresh_token | string | 是 | 当前refresh_token（服务端作废） |

- 响应数据：
{ "code": 0, "message": "success", "data": null }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40101 | 未登录 |

# 4. 用户 API

#### GET /api/v1/users/me

获取当前登录用户信息

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无） |  |  | Header鉴权 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "userId": "DV_8X3K2A",
 "nickname": "飞镖选手1234",
 "avatarUrl": "https://...",
 "gender": "male",
 "level": 5,
 "levelTitle": "飞镖狂人",
 "ppr": 32.5,
 "winRate": 0.643,
 "totalMatches": 42,
 "createdAt": "2026-08-20T10:00:00Z",
 "lastLoginAt": "2026-09-05T08:00:00Z",
 "settings": {
 "quickButtonsEnabled": true,
 "quickButtonValues": [60,100,140,180,26,41,85],
 "voiceInputEnabled": true,
 "darkMode": "system",
 "autoScoreConfirm": false
 }
 }
}

#### PUT /api/v1/users/me

更新当前用户资料

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| nickname | string | 否 | 昵称（2-16字符） |
| avatarUrl | string | 否 | 头像URL（先上传获取URL） |
| gender | string | 否 | male/female/unknown |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": { "userId": "DV_8X3K2A", "nickname": "新昵称", ... }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 昵称格式错误（2-16字符） |

#### PUT /api/v1/users/me/settings

更新用户设置

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| settings | object | 是 | 设置对象（完整替换，需传全部字段） |

- 响应数据：
{ "code": 0, "message": "success", "data": { "settings": { ... } } }

#### GET /api/v1/users/{userId}

获取指定用户公开资料

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | path | 是 | 用户ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "userId": "DV_XXXX",
 "nickname": "玩家B",
 "avatarUrl": "https://...",
 "gender": "male",
 "level": 8,
 "levelTitle": "飞镖大师",
 "ppr": 45.2,
 "winRate": 0.712,
 "totalMatches": 156,
 "isFriend": false,
 "friendRequestSent": false
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40401 | 用户不存在 |

#### GET /api/v1/users/search

搜索用户

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 是 | 搜索关键词（昵称/用户ID） |
| page | int | 否 | 页码 |
| page_size | int | 否 | 每页数量 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 5,
 "page": 1,
 "page_size": 20,
 "items": [
 { "userId": "DV_XXXX", "nickname": "飞镖选手", "avatarUrl": "...", "level": 3, "ppr": 28.5 }
 ]
 }
}

#### POST /api/v1/upload/avatar

上传头像（获取临时URL）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | 图片文件（multipart/form-data，≤2MB，JPG/PNG） |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "avatarUrl": "https://cdn.dartvio.com/avatar/temp_xxx.jpg",
 "expireIn": 3600
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 文件格式不支持或超过大小限制 |

# 5. 房间 API

#### GET /api/v1/rooms

获取房间列表

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| game_type | string | 否 | x01/cricket（筛选） |
| is_private | bool | 否 | true/false（默认false，只看公开房） |
| page | int | 否 | 页码 |
| page_size | int | 否 | 每页数量 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 12,
 "page": 1,
 "page_size": 20,
 "items": [
 {
 "roomId": "RM_ABC123",
 "name": "飞镖选手1234的房间",
 "gameType": "x01",
 "maxRounds": 20,
 "inRule": "straight",
 "outRule": "double",
 "maxPlayers": 2,
 "currentPlayers": 1,
 "isPrivate": false,
 "status": "waiting",
 "host": { "userId": "DV_8X3K2A", "nickname": "飞镖选手1234", "avatarUrl": "...", "level": 5 },
 "spectatorCount": 0,
 "createdAt": "2026-09-05T10:00:00Z"
 }
 ]
 }
}

#### POST /api/v1/rooms

创建房间

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 否 | 房间名称（默认"XX的房间"） |
| game_type | string | 是 | x01/cricket |
| ★target_score | int | 否 | ★v1.1：301/501/701(P0)，901/1101(P1)；game_type=x01时必填 |
| ★match_mode | string | 否 | ★v1.1：casual(休闲单局)/multi(多局)，默认casual |
| ★legs_to_win | int | 否 | ★v1.1：1-10，match_mode=multi时必填 |
| ★diddle_enabled | bool | 否 | ★v1.1：是否争红心定先手，默认false |
| ★max_rounds | int | 否 | ★v1.1：回合上限15/20/50/80（仅X01；Cricket不传） |
| in_rule | string | 是 | straight/double/master（Cricket不传） |
| out_rule | string | 是 | straight/double/master（Cricket不传） |
| max_players | int | 是 | 2（P0固定2，P1支持3-4） |
| is_private | bool | 否 | true/false（默认false） |
| password | string | 否 | 房间密码（is_private=true时必填，4-6位） |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "roomId": "RM_ABC123",
 "name": "飞镖选手1234的房间",
 "gameType": "x01",
 "maxRounds": 20, // 回合上限（仅X01）
 "targetScore": 501, // X01目标分：301/501/701/901/1101
 "matchMode": "multi", // casual休闲单局/multi多局
 "legsToWin": 3, // multi模式下赢下多少局获胜
 "cricketState": null, // ★gameType=cricket时非空，见下例
 "inRule": "straight",
 "outRule": "double",
 "maxPlayers": 2,
 "isPrivate": false,
 "status": "waiting",
 "host": { ... },
 "players": [ { "userId": "DV_8X3K2A", "nickname": "...", "isReady": false, "isHost": true } ],
 "spectators": [],
 "createdAt": "2026-09-05T10:00:00Z"
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 房间设置参数错误 |
| 40902 | 已有进行中的房间（每人同时只能创建一个） |

#### POST /api/v1/rooms/{roomId}/join

加入房间

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roomId | path | 是 | 房间ID |
| password | string | 否 | 房间密码（私有房必填） |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "roomId": "RM_ABC123",
 "status": "waiting",
 "players": [
 { "userId": "DV_8X3K2A", "nickname": "...", "isReady": false, "isHost": true },
 { "userId": "DV_PLAYERB", "nickname": "玩家B", "isReady": false, "isHost": false }
 ]
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40402 | 房间不存在或已解散 |
| 40902 | 房间已满 |
| 40301 | 密码错误 |

#### POST /api/v1/rooms/{roomId}/leave

离开房间

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roomId | path | 是 | 房间ID |

- 响应数据：
{ "code": 0, "message": "success", "data": null }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40402 | 房间不存在 |

#### POST /api/v1/rooms/{roomId}/ready

切换准备状态

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roomId | path | 是 | 房间ID |
| is_ready | bool | 是 | true/false |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": { "roomId": "RM_ABC123", "players": [ { "userId": "...", "isReady": true } ] }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40402 | 房间不存在 |
| 40301 | 不是房间成员 |

#### POST /api/v1/rooms/{roomId}/start

房主开始比赛（所有人已准备）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roomId | path | 是 | 房间ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchId": "MT_XYZ789",
 "roomId": "RM_ABC123",
 "status": "in_progress",
 "firstPlayerIndex": 0,
 "matchVersion": 0
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40402 | 房间不存在 |
| 40301 | 不是房主 |
| 40001 | 有玩家未准备 |

#### DELETE /api/v1/rooms/{roomId}

房主解散房间

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roomId | path | 是 | 房间ID |

- 响应数据：
{ "code": 0, "message": "success", "data": null }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40402 | 房间不存在 |
| 40301 | 不是房主 |

#### GET /api/v1/rooms/{roomId}

获取房间详情

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roomId | path | 是 | 房间ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "roomId": "RM_ABC123",
 "name": "...",
 "gameType": "x01",
 "maxRounds": 20,
 "inRule": "straight",
 "outRule": "double",
 "maxPlayers": 2,
 "isPrivate": false,
 "status": "waiting",
 "host": { ... },
 "players": [ ... ],
 "spectators": [ ... ],
 "createdAt": "..."
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40402 | 房间不存在 |

# 6. 比赛 API

#### GET /api/v1/matches/{matchId}

获取比赛当前状态（全量）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |
| last_known_version | int | 否 | 客户端已知版本，用于增量同步 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchId": "MT_XYZ789",
 "roomId": "RM_ABC123",
 "gameType": "x01",
 "maxRounds": 20,
 "inRule": "straight",
 "outRule": "double",
 "status": "in_progress",
 "matchVersion": 5,
 "currentLeg": 1,
 "currentPlayerIndex": 1,
 "currentTurn": 3,
 "players": [
 {
 "userId": "DV_8X3K2A",
 "nickname": "飞镖选手1234",
 "avatarUrl": "...",
 "remaining": 321,
 "legsWon": 0,
 "turns": [
 { "turnNumber": 1, "darts": [{"label":"T20","score":60},...], "turnScore": 85, "isBust": false },
 { "turnNumber": 2, "darts": [...], "turnScore": 95, "isBust": false }
 ]
 },
 { "userId": "DV_PLAYERB", "remaining": 401, "legsWon": 0, "turns": [...] }
 ],
 "legs": [
 { "legNumber": 1, "status": "in_progress", "winnerId": null }
 ],
 "undoWindow": {
 "isOpen": false,
 "playerId": null,
 "expireAt": null
 },
 "startedAt": "2026-09-05T10:05:00Z",
 "updatedAt": "2026-09-05T10:12:00Z"
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40403 | 比赛不存在 |
| 40301 | 不是比赛参与者 |

#### POST /api/v1/matches/{matchId}/turns

#### 【★v1.1 Cricket分支】gameType=cricket时：①服务端调用M2.validateCricketTurn校验；②无Bust概念；③响应增加cricketState字段；④请求darts中每镖需为15-20或Bull，非法分区返回40001。

提交回合（在线对战）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |
| darts | array | 是 | 3镖数组，每镖含label/number/multiplier/score |
| client_timestamp | long | 是 | 客户端时间戳（毫秒，幂等用） |
| match_version | int | 是 | 客户端当前matchVersion（乐观锁） |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchVersion": 6,
 "turnNumber": 3,
 "playerId": "DV_8X3K2A",
 "darts": [ {"label":"T20","score":60}, {"label":"S20","score":20}, {"label":"S5","score":5} ],
 "turnScore": 85,
 "isBust": false,
 "isCheckout": false,
 "remainingAfter": 321,
 "undoWindow": {
 "isOpen": true,
 "playerId": "DV_8X3K2A",
 "expireAt": "2026-09-05T10:12:05Z"
 }
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 镖数据格式错误 |
| 40901 | 版本冲突（matchVersion不匹配） |
| 40301 | 不是当前玩家回合 |
| 40001 | Bust判定失败（规则校验不通过） |

#### POST /api/v1/matches/{matchId}/turns/{turnNumber}/undo

撤销回合（5秒窗口内）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |
| turnNumber | path | 是 | 回合号 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchVersion": 7,
 "turnNumber": 3,
 "playerId": "DV_8X3K2A",
 "remainingRestored": 406,
 "undoWindow": { "isOpen": false, "playerId": null, "expireAt": null }
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 撤销窗口已关闭（超时或对手已操作） |
| 40301 | 不是该回合提交者 |

#### POST /api/v1/matches/{matchId}/player-activated

通知服务端玩家开始操作（触发对手撤销窗口关闭）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |
| action | string | 是 | score_input/quick_button/voice/tap |

- 响应数据：
{ "code": 0, "message": "success", "data": { "matchVersion": 7, "undoLocked": true } }

#### GET /api/v1/users/me/matches

获取我的比赛历史

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| game_type | string | 否 | x01/cricket |
| result | string | 否 | win/lose |
| page | int | 否 | 页码 |
| page_size | int | 否 | 每页数量 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 42,
 "page": 1,
 "page_size": 20,
 "items": [
 {
 "matchId": "MT_XYZ789",
 "gameType": "x01",
 "maxRounds": 20,
 "result": "win",
 "score": "3:1",
 "myPPR": 32.5,
 "opponentPPR": 28.1,
 "opponent": { "userId": "DV_PLAYERB", "nickname": "玩家B", "avatarUrl": "..." },
 "finishedAt": "2026-09-05T10:30:00Z"
 }
 ]
 }
}

#### GET /api/v1/matches/{matchId}/detail

获取比赛详情（含每回合数据）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchId": "MT_XYZ789",
 "gameType": "x01",
 "maxRounds": 20,
 "inRule": "straight",
 "outRule": "double",
 "result": "win",
 "finalScore": "3:1",
 "players": [
 {
 "userId": "DV_8X3K2A",
 "nickname": "飞镖选手1234",
 "ppr": 32.5,
 "legsWon": 3,
 "legs": [
 {
 "legNumber": 1,
 "result": "win",
 "turns": [ { "turnNumber":1, "darts":[...], "turnScore":85 }, ... ],
 "checkout": "D20",
 "dartsUsed": 18
 }
 ]
 }
 ],
 "startedAt": "...",
 "finishedAt": "..."
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40403 | 比赛不存在 |
| 40301 | 不是比赛参与者 |

#### POST /api/v1/matches/{matchId}/forfeit

弃权比赛

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchId": "MT_XYZ789",
 "status": "finished",
 "result": "lose",
 "winnerId": "DV_PLAYERB",
 "matchVersion": 10
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40403 | 比赛不存在 |
| 40001 | 比赛已结束 |

# 7. 好友 API

#### GET /api/v1/friends

获取好友列表

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | int | 否 | 页码 |
| page_size | int | 否 | 每页数量 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 8,
 "page": 1,
 "page_size": 20,
 "items": [
 {
 "userId": "DV_FRIEND1",
 "nickname": "好友A",
 "avatarUrl": "...",
 "level": 6,
 "ppr": 38.2,
 "presence": "online",
 "lastActiveAt": "2026-09-05T10:00:00Z"
 }
 ]
 }
}

#### POST /api/v1/friends/requests

发送好友申请

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| user_id | string | 是 | 目标用户ID |
| message | string | 否 | 申请留言（≤50字） |

- 响应数据：
{ "code": 0, "message": "success", "data": { "requestId": "FR_001", "status": "pending" } }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40401 | 用户不存在 |
| 40903 | 已经是好友 |
| 40904 | 好友申请已发送（待处理） |

#### GET /api/v1/friends/requests

获取好友申请列表（收到的）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | string | 否 | pending/accepted/rejected（默认pending） |
| page | int | 否 | 页码 |
| page_size | int | 否 | 每页数量 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 3,
 "page": 1,
 "page_size": 20,
 "items": [
 {
 "requestId": "FR_001",
 "fromUser": { "userId": "DV_STRANGER", "nickname": "陌生人", "avatarUrl": "...", "level": 3 },
 "message": "你好，加个好友一起打镖",
 "status": "pending",
 "createdAt": "2026-09-05T09:00:00Z"
 }
 ]
 }
}

#### POST /api/v1/friends/requests/{requestId}/accept

接受好友申请

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | path | 是 | 申请ID |

- 响应数据：
{ "code": 0, "message": "success", "data": { "friendId": "DV_STRANGER", "nickname": "陌生人" } }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40401 | 申请不存在 |
| 40001 | 申请已处理 |

#### POST /api/v1/friends/requests/{requestId}/reject

拒绝好友申请

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | path | 是 | 申请ID |

- 响应数据：
{ "code": 0, "message": "success", "data": null }

#### DELETE /api/v1/friends/{userId}

删除好友

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | path | 是 | 好友用户ID |

- 响应数据：
{ "code": 0, "message": "success", "data": null }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40401 | 用户不存在或不是好友 |

#### GET /api/v1/friends/{userId}/profile

获取好友详细资料（含对战记录）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | path | 是 | 好友用户ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "userId": "DV_FRIEND1",
 "nickname": "好友A",
 "avatarUrl": "...",
 "gender": "male",
 "level": 6,
 "levelTitle": "飞镖狂人",
 "ppr": 38.2,
 "winRate": 0.68,
 "totalMatches": 89,
 "presence": "online",
 "friendSince": "2026-08-25T10:00:00Z",
 "matchesWithMe": {
 "total": 5,
 "myWins": 2,
 "friendWins": 3
 }
 }
}

# 8. 统计 API

#### GET /api/v1/users/{userId}/stats

获取用户统计数据

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | path | 是 | 用户ID |
| period | string | 否 | 7d/30d/all（默认all） |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "userId": "DV_8X3K2A",
 "period": "all",
 "totalMatches": 42,
 "wins": 27,
 "losses": 15,
 "winRate": 0.643,
 "avgPPR": 32.5,
 "bestPPR": 58.3,
 "avgTurn": 24.8,
 "bestTurn": 180,
 "checkoutRate": 0.35,
 "oneEightyCount": 3,
 "tonPlusCount": 12,
 "x01Matches": 35,
 "cricketMatches": 7,
 "cricket": { // ★v1.1 Cricket专属指标
 "markRate": 0.62,
 "closeRate": 0.88,
 "bullRate": 0.35,
 "scoreEfficiency": 1.42,
 "avgCloseTurns": 14.5,
 "winRate": 0.57
 },
 "avgDartsPerLeg": 18.5,
 "trend": [
 { "date": "2026-09-01", "ppr": 28.5, "matches": 2 },
 { "date": "2026-09-02", "ppr": 31.2, "matches": 3 }
 ]
 }
}

#### GET /api/v1/users/{userId}/achievements

获取用户成就列表

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | path | 是 | 用户ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 18,
 "unlocked": 12,
 "items": [
 {
 "achievementId": "ACH_FIRST_WIN",
 "name": "首胜",
 "description": "赢得第一场比赛",
 "icon": "trophy",
 "isUnlocked": true,
 "unlockedAt": "2026-08-21T10:00:00Z",
 "progress": null
 },
 {
 "achievementId": "ACH_180",
 "name": "满分",
 "description": "单回合打出180分",
 "icon": "flame",
 "isUnlocked": true,
 "unlockedAt": "2026-09-01T15:00:00Z",
 "progress": null
 },
 {
 "achievementId": "ACH_100_MATCHES",
 "name": "百战飞镖",
 "description": "完成100场比赛",
 "icon": "number.100",
 "isUnlocked": false,
 "unlockedAt": null,
 "progress": { "current": 42, "target": 100, "percent": 42 }
 }
 ]
 }
}

# 9. 设备 API

#### GET /api/v1/devices

获取已绑定设备列表

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无） |  |  | Header鉴权 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "items": [
 {
 "deviceId": "DV-PRO-A3B2",
 "deviceName": "DartVio Pro 计分设备",
 "deviceType": "pro",
 "model": "DV-PRO-01",
 "firmwareVersion": "v1.2.3",
 "status": "online",
 "wifiSsid": "Home-5G",
 "ipAddress": "192.168.1.100",
 "wifiSignal": -55,
 "cameraStatus": { "top": "normal", "left": "normal", "right": "normal" },
 "lastConnectedAt": "2026-09-05T10:00:00Z",
 "createdAt": "2026-09-01T14:30:00Z"
 }
 ]
 }
}

#### POST /api/v1/devices/bind

绑定设备（配网完成后）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| device_id | string | 是 | 设备ID（MAC哈希） |
| device_type | string | 是 | pro/lite |
| model | string | 是 | 设备型号 |
| bind_token | string | 否 | 绑定Token（高端设备） |
| pin_code | string | 否 | 设备PIN码（低端设备） |
| wifi_ssid | string | 是 | 设备连接的WiFi SSID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "deviceId": "DV-PRO-A3B2",
 "deviceName": "DartVio设备",
 "deviceType": "pro",
 "status": "online",
 "createdAt": "2026-09-05T10:00:00Z"
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40001 | 设备ID或PIN码错误 |
| 40901 | 设备已被其他用户绑定 |

#### GET /api/v1/devices/{deviceId}

获取设备详情

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | path | 是 | 设备ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "deviceId": "DV-PRO-A3B2",
 "deviceName": "DartVio Pro 计分设备",
 "deviceType": "pro",
 "model": "DV-PRO-01",
 "firmwareVersion": "v1.2.3",
 "macAddress": "XX:XX:XX:XX:A3:B2",
 "status": "online",
 "wifiSsid": "Home-5G",
 "ipAddress": "192.168.1.100",
 "wifiSignal": -55,
 "latencyMs": 12,
 "cameraStatus": { "top": "normal", "left": "normal", "right": "normal" },
 "recognitionAccuracy": 0.952,
 "rtspUrl": null,
 "lastConnectedAt": "2026-09-05T10:00:00Z",
 "createdAt": "2026-09-01T14:30:00Z"
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40404 | 设备不存在 |
| 40301 | 不是设备绑定者 |

#### PUT /api/v1/devices/{deviceId}

更新设备信息（重命名）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | path | 是 | 设备ID |
| device_name | string | 否 | 设备名称（2-20字符） |

- 响应数据：
{ "code": 0, "message": "success", "data": { "deviceId": "DV-PRO-A3B2", "deviceName": "新名称" } }

#### DELETE /api/v1/devices/{deviceId}

解绑设备

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceId | path | 是 | 设备ID |

- 响应数据：
{ "code": 0, "message": "success", "data": null }

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40404 | 设备不存在 |
| 40301 | 不是设备绑定者 |

# 10. 观战 API

#### GET /api/v1/matches/live

获取正在进行的比赛列表（可观战）

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | int | 否 | 页码 |
| page_size | int | 否 | 每页数量 |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "total": 5,
 "page": 1,
 "page_size": 20,
 "items": [
 {
 "matchId": "MT_LIVE001",
 "gameType": "x01",
 "players": [
 { "userId": "DV_A", "nickname": "玩家A", "avatarUrl": "...", "remaining": 200, "ppr": 35.2 },
 { "userId": "DV_B", "nickname": "玩家B", "avatarUrl": "...", "remaining": 150, "ppr": 42.1 }
 ],
 "currentLeg": 2,
 "spectatorCount": 12,
 "startedAt": "2026-09-05T10:00:00Z"
 }
 ]
 }
}

#### POST /api/v1/matches/{matchId}/spectate

加入观战

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |

- 响应数据：
{
 "code": 0,
 "message": "success",
 "data": {
 "matchId": "MT_LIVE001",
 "spectatorCount": 13,
 "matchState": { ... } // 全量比赛状态（P0仅比分，不含视频）
 }
}

- 错误码：
| 错误码 | 说明 |
| --- | --- |
| 40403 | 比赛不存在或已结束 |
| 40301 | 比赛不允许观战（房主设置） |

#### POST /api/v1/matches/{matchId}/spectate/leave

离开观战

- 请求参数：
| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| matchId | path | 是 | 比赛ID |

- 响应数据：
{ "code": 0, "message": "success", "data": { "spectatorCount": 12 } }

# 11. WebSocket 事件协议

## 11.1 连接与认证

// 连接
URL: wss://api.dartvio.com/ws/v1?token=<access_token>
协议: WebSocket (RFC 6455)
心跳: 客户端每30秒发送 {"type":"ping"}, 服务端10秒内回复 {"type":"pong"}
重连: 指数退避 (1s/2s/4s/8s/16s/30s)，最多10次

// 连接成功
服务端推送:
{
 "type": "connected",
 "data": {
 "connectionId": "WS_ABC123",
 "serverTime": "2026-09-05T10:00:00Z",
 "heartbeatInterval": 30
 }
}

// 消息格式（JSON-RPC 2.0 请求）
{
 "jsonrpc": "2.0",
 "method": "submit_turn",
 "params": { ... },
 "id": 1
}

// 响应
{
 "jsonrpc": "2.0",
 "result": { ... },
 "id": 1
}

// 错误响应
{
 "jsonrpc": "2.0",
 "error": { "code": 40901, "message": "版本冲突" },
 "id": 1
}

// 服务端推送事件（无id）
{
 "type": "turn_submitted",
 "data": { ... }
}

## 11.2 客户端→服务端 方法

| 方法 | 参数 | 说明 | 响应 |
| --- | --- | --- | --- |
| ping | {} | 心跳 | pong |
| submit_turn | matchId, darts, clientTimestamp, matchVersion | 提交回合 | 提交结果+matchVersion |
| undo_turn | matchId, turnNumber | 撤销回合 | 撤销结果+matchVersion |
| player_activated | matchId, action | 玩家开始操作（触发锁定） | matchVersion |
| join_room | roomId | 加入房间（WebSocket通道） | 房间状态 |
| leave_room | roomId | 离开房间 | - |
| join_match | matchId | 加入比赛事件通道 | 比赛全量状态 |
| leave_match | matchId | 离开比赛通道 | - |
| join_spectate | matchId | 加入观战 | 比赛状态（比分） |
| leave_spectate | matchId | 离开观战 | - |
| send_video_frame | matchId, frameData(binary), timestamp | 发送视频帧（低端硬件） | -（二进制帧，非JSON） |
| sync_match | matchId, lastKnownVersion | 同步全量比赛状态 | 比赛状态+错过事件 |
| update_presence | presence(online/in_match/offline) | 更新在线状态 | - |
| forfeit | matchId | 弃权 | 比赛结果 |

## 11.3 服务端→客户端 事件

| 事件 | 数据字段 | 触发条件 | 接收方 |
| --- | --- | --- | --- |
| connected | connectionId, serverTime, heartbeatInterval | WebSocket连接成功 | 当前连接 |
| pong | timestamp | 响应客户端ping | 当前连接 |
| error | code, message | 连接级错误 | 当前连接 |
| turn_submitted | matchId, legNumber, turnNumber, playerId, darts, turnScore, isBust, matchVersion | 回合提交成功 | 比赛所有玩家 |
| turn_undone | matchId, turnNumber, playerId, remainingRestored, matchVersion | 回合被撤销 | 比赛所有玩家 |
| turn_locked | matchId, turnNumber, reason(player_activated/timeout), matchVersion | 撤销窗口关闭 | 回合提交者 |
| bust_occurred | matchId, turnNumber, playerId, matchVersion | Bust发生 | 比赛所有玩家 |
| checkout_occurred | matchId, legNumber, playerId, checkoutScore, matchVersion | Checkout发生 | 比赛所有玩家 |
| leg_finished | matchId, legNumber, winnerId, legStats, matchVersion | 一局结束 | 比赛所有玩家 |
| match_finished | matchId, winnerId, matchStats, matchVersion | 比赛结束 | 比赛所有玩家+观战者 |
| sudden_death_started | matchId, turnNumber, matchVersion | 进入超轮 | 比赛所有玩家 |
| player_switched | matchId, currentPlayerIndex, matchVersion | 切换当前玩家 | 比赛所有玩家 |
| player_joined | roomId, player | 玩家加入房间 | 房间内所有人 |
| player_left | roomId, playerId, reason | 玩家离开房间 | 房间内所有人 |
| player_ready | roomId, playerId, isReady | 准备状态变化 | 房间内所有人 |
| match_started | roomId, matchId, firstPlayerIndex | 比赛开始 | 房间内所有人 |
| room_destroyed | roomId, reason | 房间销毁 | 房间内所有人 |
| spectator_joined | roomId, spectator | 观战者加入 | 房间内+比赛玩家 |
| spectator_left | roomId, spectatorId | 观战者离开 | 房间内+比赛玩家 |
| opponent_offline | matchId, playerId, reconnectDeadline | 对手离线 | 当前玩家 |
| opponent_reconnected | matchId, playerId | 对手重连 | 当前玩家 |
| presence_update | userId, presence | 好友在线状态变化 | 该用户的好友 |
| friend_request | requestId, fromUser, message | 收到好友申请 | 目标用户 |
| diddle_result | matchId, results, firstPlayerIndex | 争红心结果 | 比赛所有玩家 |

## 11.4 在线撤回锁定时序

客户端A 服务端 客户端B
 │ │ │
 │── submit_turn ──────────────>│ │
 │ │── 裁定（M2规则引擎） │
 │ │── matchVersion+1 │
 │<── result(undoWindow=5s) ────│ │
 │── broadcast turn_submitted ──>│──────────────────────────────>│
 │ (显示5秒撤销按钮) │ │ (显示"A提交了85分")
 │ │ │
 │ [5秒内] B开始操作 │ │
 │ │<── player_activated ──────────│
 │ │── 关闭撤销窗口 │
 │ │── matchVersion+1 │
 │<── turn_locked(reason=player_activated) ──│ │
 │ (撤销按钮消失) │ │
 │ │ │
 │ [如果B在5秒内无操作] │ │
 │ │── 5秒超时 → 关闭撤销窗口 │
 │<── turn_locked(reason=timeout) │ │
 │ │ │
 │ [如果A在5秒内点撤销] │ │
 │── undo_turn ─────────────────>│ │
 │ │── 回退分数, matchVersion+1 │
 │<── result(remainingRestored) ─│ │
 │── broadcast turn_undone ─────>│──────────────────────────────>│
 │ (输入区恢复) │ │ (Toast"对手已撤销")

## 11.5 视频流转发协议（低端硬件）

// 视频帧使用WebSocket二进制帧传输（非JSON）
// 帧头格式（18字节）+ H.264 NAL数据

字节 长度 字段 说明
0-1 2 magic 0xD8 0x00（DartVio视频帧魔数）
2-3 2 version 0x0001（协议版本）
4-5 2 frameType 0x01=普通帧, 0x02=关键帧, 0x03=控制帧
6-9 4 timestamp uint32，毫秒级时间戳
10-13 4 frameSeq uint32，帧序号（单调递增）
14-17 4 dataLength uint32，后续NAL数据长度（字节）
18+ N nalData H.264 NAL单元数据

// 服务端转发逻辑：
// 1. 收到玩家A的视频帧
// 2. 验证A是当前投镖者（player_switched事件维护）
// 3. 转发给：玩家B（对手）+ 所有观战者（P2）
// 4. 不转发给A自己

// 当前投镖者切换：
// - 服务端广播 player_switched(currentPlayerIndex=B)
// - A停止发送视频帧
// - B开始发送视频帧
// - A收到B的视频帧 → 显示B的靶盘画面

# 12. 安全规范

| 项目 | 规范 |
| --- | --- |
| 传输加密 | 所有API和WebSocket使用TLS 1.2+（HTTPS/WSS），禁止明文传输 |
| 鉴权 | JWT Token，access_token 2小时过期，refresh_token 30天过期 |
| 密码存储 | bcrypt哈希（cost=12），不存储明文密码 |
| 敏感数据 | 手机号/AppleID加密存储，API响应中脱敏（138****1234） |
| 输入校验 | 服务端对所有输入做长度/格式/范围校验，不信任客户端 |
| SQL注入 | 使用参数化查询/ORM，禁止拼接SQL |
| XSS | 所有用户输入在存储和输出时转义，API返回JSON不直接渲染HTML |
| CSRF | JWT Header认证天然防CSRF，不使用Cookie认证 |
| 限流 | 60次/分钟（普通接口），10次/秒（突发），登录接口5次/分钟 |
| 幂等 | 写操作支持Idempotency-Key Header，重复请求返回相同结果 |
| 版本冲突 | matchVersion乐观锁，冲突返回40901，客户端同步后重试 |
| 房间隐私 | 私有房间不出现在列表中，加入需要密码 |
| 视频流 | 低端硬件视频流端到端加密（DTLS-SRTP），服务端只转发不存储 |
| 日志 | 不记录Token/密码/手机号全文，敏感字段脱敏 |
| 设备绑定 | 设备绑定Token一次性使用，绑定后设备只能被绑定者使用 |

# 13. 与模块PRD的对应关系

# ★13.5 练习模式 API（v1.1新增，对应M11）

# POST /api/v1/practice/sessions

# 创建并结束一次练习会话（客户端本地完成后上报）。

# 请求体：

# {

# "userId": "uuid",

# "gameType": "x01", // x01 / cricket

# "practiceMode": "count_up", // count_up/countdown/checkout/random_checkout/cricket_free/cricket_close/cricket_triple

# "startedAt": "2026-09-10T10:00:00Z",

# "endedAt": "2026-09-10T10:25:00Z",

# "darts": [ {"segment": "T20", "score": 60, "timestamp": 1234567890}, {"segment": "MISS", "score": 0, "timestamp": 1234567891} ],

# "result": { "totalScore": 600, "totalDarts": 60, "rounds": 20, "bestRound": 140, "cricketClosedTurns": null, "cricketMarks": null, "cricketScore": null }

# }

# 响应 201：

# { "sessionId": "uuid", "isNewBest": true, "bestRecord": {"totalScore": 600, "achievedAt": "..."} }

# GET /api/v1/practice/best?userId=uuid&practiceMode=count_up&gameType=x01

# 查询某练习模式的历史最佳。响应 200：

# { "practiceMode": "count_up", "best": {"totalScore": 620, "achievedAt": "..."} }

# GET /api/v1/practice/sessions?userId=uuid&gameType=cricket&limit=20&cursor=xxx

# 分页查询练习记录（同步至M9练习记录页）。

# ★说明：练习数据为B级，仅个人成长用途；不进入天梯ELO与排名；practiceMode枚举与M11 §6.6保持一致。

| API分类 | 对应模块PRD | 章节 |
| --- | --- | --- |
| 认证API | 01_M1_基础设施 | 5. API接口（loginWithApple/loginWithPhone） |
| 用户API | 01_M1_基础设施 | 4.2 User数据模型 / 5. API接口 |
| 房间API | 06_M6_比赛大厅 | 5. API接口 / 房间生命周期状态机 |
| 比赛API | 04_M4_对局核心 + 05_M5_实时同步 | 比赛状态机 / 回合提交 / 撤回锁定 |
| 好友API | 10_M10_社交 | 5. API接口 |
| 统计API | 09_M9_统计成就 | 5. API接口 / 18项成就 |
| 设备API | 08_M8_硬件接入 | 5. API接口 / 设备数据模型 |
| 观战API | 06_M6_比赛大厅 | 观战P0功能 |
| WebSocket事件 | 05_M5_实时同步 | 2.2 事件类型总表（26种） |
| 规则裁定 | 02_M2_规则引擎 | 服务端使用与客户端相同的规则逻辑 |
| ★练习API | 11_M11_练习模式 | 练习记录 / 最佳成绩 |

# 14. X01 与 Cricket 轮数上限契约订正

max_rounds 的候选集为 0 / 15 / 20 / 50 / 80，默认 0；0 表示无上限。该字段由 X01 与 Cricket 共用，不得标成“仅 X01”。X01 达到上限时剩余分最低者胜；Cricket 达到上限时按对应变体规则判定，Tactics 可使用非 0 值。服务端未知值应拒绝为非法配置，不得静默改成 20。
