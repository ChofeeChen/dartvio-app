import Foundation

/**
 * 数据所在区域：**开发期境外 / 正式版境内**，由配置切换，不写死在文案里。
 *
 * ## 为什么要做成配置
 *
 * 现在联机在新加坡（境外），正式版要迁回境内走合规审核。两条路的义务完全不同：
 *
 * | | 境内 | 境外 |
 * | --- | --- | --- |
 * | 出境单独同意（PIPL 39） | 不需要 | **需要**，且同意前不得传输 |
 * | ICP 备案 | **需要**，App Store Connect 要填备案号 | 不适用 |
 * | 隐私政策存储一节 | 写境内 | 写接收方、所在地、目的、信息种类 |
 *
 * 写死任何一边，另一边就是错的合规表述——那种错误不是"文案不好看"，是政策与实际不符。
 * 所以区域只有一个来源：Info.plist 的 `DataRegion`（`mainland` / `overseas`）。
 *
 * ## 怎么切
 *
 * 工程 `GENERATE_INFOPLIST_FILE = YES`，所以加两行 build setting 即可，不用手改 plist：
 * - `INFOPLIST_KEY_DataRegion = mainland`
 * - `INFOPLIST_KEY_IcpFiling = <备案号>`（境内必填，境外忽略）
 *
 * ⚠️ 缺 key 时按 `overseas` 处理 —— **默认取更严格的一边**，漏配不会变成"少告知"。
 */
enum DataRegion: Equatable {

    case mainland
    case overseas(place: String)

    static var current: DataRegion {
        let raw = (Bundle.main.object(forInfoDictionaryKey: "DataRegion") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if raw == "mainland" { return .mainland }
        let place = (Bundle.main.object(forInfoDictionaryKey: "DataRegionPlace") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return .overseas(place: place?.isEmpty == false ? place! : "新加坡")
    }

    var isOverseas: Bool {
        if case .overseas = self { return true }
        return false
    }

    var displayName: String {
        switch self {
        case .mainland: return "中国境内"
        case .overseas(let place): return "\(place)（境外）"
        }
    }

    /// 境外接收方所在地（隐私政策第四节要写具体国家或地区，不能只写"境外"）。
    var placeName: String {
        switch self {
        case .mainland: return "中国境内"
        case .overseas(let place): return place
        }
    }

    /// 境内必须挂备案号；这条也是 App Store Connect 中国区上架的必填项。
    /// 未配置时返回 nil —— 界面据此显示"待填写"，不编一个假的备案号出来。
    static var icpFiling: String? {
        guard current.isOverseas == false else { return nil }
        let value = (Bundle.main.object(forInfoDictionaryKey: "IcpFiling") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return value?.isEmpty == false ? value : nil
    }
}
