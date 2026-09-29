import SwiftUI

/**
 * 隐私政策：Agreement 页，从「设置 → 隐私 → 隐私政策」进入，也用于 App Store 审核时核对。
 *
 * 文本按《个人信息保护法》第 17 条（告知义务）与《移动互联网应用程序信息服务管理规定》组织：
 * 处理者是谁 / 收集什么 / 不收集什么 / 用在哪 / 存在哪 / 与谁共享 / 你有什么权利 / 怎么联系我们。
 *
 * ⚠️ 三处需要业务确认后才能定稿，已就地标出，不要凭空填：
 *   1. 联系方式邮箱（现为占位 privacy@dartvio.win）
 *   2. 服务端数据保留期限（等后端定，暂写"你删除或停止使用后清除"）
 *   3. 境外接收方的**运营主体名称与联系方式**（现为占位，见 `DataTransferConsent.recipient`）
 *
 * ⚠️ 服务器在新加坡（境外），所以联机=出境，第四节按 PIPL 第 39 条写成单独同意告知。
 * 正式版若把服务器迁回境内，三处要一起改：`DataTransferConsent`、隐私政策第四节、设置页"服务提供地"。
 */
struct PrivacyPolicyView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                ForEach(Self.sections) { section in
                    sectionView(section)
                }
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("隐私政策")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("隐私政策")
                .font(.title2.weight(.bold))
                .foregroundStyle(Palette.textPrimary)
            Text("生效日期：2026-09-29 · 适用于 DartVio iOS（com.dartvio.app）")
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
            Text("我们只收集让飞镖记分与联机对局能跑起来所必需的信息，不收集与功能无关的内容，不用于广告追踪。")
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)
        }
    }

    private func sectionView(_ section: Section) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(section.title)
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
            Text(section.body)
                .font(.subheadline)
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Palette.divider, lineWidth: 1)
        )
    }

    private struct Section: Identifiable {
        let id = UUID()
        let title: String
        let body: String
    }

    // MARK: - 随区域变化的段落

    /** 区域来自 `DataRegion`：境外要写"出境"与单独同意，境内不写（写了就是告知错误）。 */
    private static var collectBody: String {
        let base = """
        1. 本机数据：对局设置、历史成绩与训练记录。仅保存在你的设备本地，不上传，卸载 App 即随之删除。
        2. Apple 账户标识：你选择「通过 Apple 登录」时，我们保存 Apple 返回的稳定标识符与昵称，用于识别你的账号与同步数据。标识符保存在设备钥匙串中，我们不保存你的 Apple 密码，也不索取邮箱与手机号。
        3. 联机对局数据：你主动进入联机房间时，昵称、房间内的操作与成绩会发送到我们的服务器，供对局双方同步。不联机则不产生这类数据。
        4. 匿名使用统计：默认关闭。你主动开启后，才会在不含直接身份标识的前提下上报功能使用情况。
        """
        guard DataRegion.current.isOverseas else { return base }
        return base + "\n（当前该服务器位于\(DataTransferConsent.region)，第 3 项属于个人信息出境，我们会事先取得你的单独同意，详见第四节。）"
    }

    private static var storageTitle: String {
        DataRegion.current.isOverseas ? "四、存储地点、出境与期限" : "四、存储地点与期限"
    }

    private static var storageBody: String {
        let retention = """
        数据保留至你删除账号或停止使用相关功能为止；账号注销后我们会清除相关个人信息
        （法律、法规规定需留存的最小日志除外）。
        """
        guard DataRegion.current.isOverseas else {
            return """
            本机数据始终留在你的设备上。联机对局数据与（如你开启的）统计数据存储在
            中华人民共和国境内的自建服务器，不向境外提供。
            \(retention)
            """
        }
        return """
        本机数据始终留在你的设备上。联机对局数据与（如你开启的）统计数据存储在我们自建的服务器，
        该服务器目前位于\(DataTransferConsent.region)，属于向中华人民共和国境外提供个人信息。

        境外接收方：\(DataTransferConsent.recipient)；所在国家或地区：\(DataTransferConsent.region)；
        处理目的：\(DataTransferConsent.purposes)；处理方式：经加密通道传输并存储；
        个人信息种类：\(DataTransferConsent.categories)。

        依据《个人信息保护法》第三十九条，我们在向境外提供前会向你告知上述事项，并单独取得你的同意；
        你可以随时撤回该同意（设置 → 隐私 → 向境外提供个人信息），撤回后立即停止传输。
        你也可以向我们索取境外接收方信息的副本。
        \(retention)
        """
    }

    private static var rightsBody: String {
        let base = """
        你有权查阅、复制、更正、补充、删除你的个人信息，有权撤回已作出的同意，有权注销账号。
        路径：App 内「设置 → 账号 → 删除账号」可注销并清除本机账号信息与联机身份；
        「设置 → 隐私 → 匿名使用统计」可随时撤回统计授权。
        """
        let crossBorder = "「设置 → 隐私 → 向境外提供个人信息」可撤回出境传输的单独同意（撤回后联机功能停止传输）。"
        let tail = """
        你也可以联系我们行使上述权利，我们会在收到请求后十五个工作日内响应。
        注销前请知悉：账号注销后数据无法恢复。
        """
        return DataRegion.current.isOverseas ? base + crossBorder + tail : base + tail
    }

    private static let sections: [Section] = [
        Section(
            title: "一、我们收集哪些信息",
            body: collectBody
        ),
        Section(
            title: "二、我们不收集什么",
            body: """
            不收集通讯录、照片、精确位置、通话与短信；不使用广告标识符（IDFA）与任何广告、归因 SDK，
            不对你的使用行为做画像用于广告；不收集与飞镖记分无关的个人信息；不强制收集手机号，
            不要求实名认证（本 App 不属于网络游戏、网络直播等依法需实名的类别）。
            """
        ),
        Section(
            title: "三、使用目的",
            body: """
            仅为实现下列功能所必需：记录与展示你的成绩、在联机对局中同步房间状态、在你开启统计后改进产品。
            不会超出上述目的使用；如超出目的范围处理你的个人信息，会事先告知并征得你的同意。
            """
        ),
        Section(
            title: storageTitle,
            body: storageBody
        ),
        Section(
            title: "五、共享、转让与公开",
            body: """
            不向任何第三方出售或共享你的个人信息。仅在你主动使用联机功能时，房间内的昵称与操作
            会提供给同一房间的其他参与者，这是联机对局得以进行的前提。除法律法规要求或司法机关
            依法定程序要求外，不对外提供。
            """
        ),
        Section(
            title: "六、你的权利",
            body: rightsBody
        ),
        Section(
            title: "七、未成年人保护",
            body: """
            未满十四周岁未成年人的个人信息属于敏感个人信息。若你是未满十四周岁的未成年人，
            请在监护人陪同下阅读本政策并在取得监护人同意后使用；我们不会在明知的情况下，
            在未取得监护人同意时处理其个人信息。
            """
        ),
        Section(
            title: "八、政策更新",
            body: """
            本政策更新时，会在 App 内提示；涉及收集目的、方式、范围变更的，会再次征得你的同意。
            """
        ),
        Section(
            title: "九、联系我们",
            body: """
            个人信息保护相关咨询、投诉与权利行使：privacy@dartvio.win（占位，需确认后生效）。
            """
        )
    ]
}
