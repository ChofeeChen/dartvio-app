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
 *   3. ICP 备案号（见设置页"关于"）
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

    private static let sections: [Section] = [
        Section(
            title: "一、我们收集哪些信息",
            body: """
            1. 本机数据：对局设置、历史成绩与训练记录。仅保存在你的设备本地，不上传，卸载 App 即随之删除。
            2. Apple 账户标识：你选择「通过 Apple 登录」时，我们保存 Apple 返回的稳定标识符与昵称，用于识别你的账号与同步数据。标识符保存在设备钥匙串中，我们不保存你的 Apple 密码，也不索取邮箱与手机号。
            3. 联机对局数据：你主动进入联机房间时，昵称、房间内的操作与成绩会发送到我们的服务器，供对局双方同步。不联机则不产生这类数据。
            4. 匿名使用统计：默认关闭。你主动开启后，才会在不含直接身份标识的前提下上报功能使用情况。
            """
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
            title: "四、存储地点与期限",
            body: """
            联机对局数据与（如你开启的）统计数据存储在中华人民共和国境内的自建服务器，不向境外提供。
            本机数据始终留在你的设备上。数据保留至你删除账号或停止使用相关功能为止；
            你删除账号后，我们会清除相关个人信息（法律、法规规定需留存的最小日志除外）。
            """
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
            body: """
            你有权查阅、复制、更正、补充、删除你的个人信息，有权撤回已作出的同意，有权注销账号。
            路径：App 内「设置 → 账号 → 删除账号」可注销并清除本机账号信息与联机身份；
            「设置 → 隐私 → 匿名使用统计」可随时撤回统计授权。你也可以联系我们行使上述权利，
            我们会在收到请求后十五个工作日内响应。注销前请知悉：账号注销后数据无法恢复。
            """
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
