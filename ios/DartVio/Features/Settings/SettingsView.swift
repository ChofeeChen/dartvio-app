import AuthenticationServices
import SwiftUI

/**
 * 设置页（原来首页那张「设置」卡指向的是 `PlaceholderView`）。
 *
 * ## 顺序为什么是「账号 → 隐私 → 关于」
 *
 * 账号是唯一会**改变数据归属**的操作（登录把数据从本机搬到账号下、删除账号会清掉数据），
 * 所以它要在最先被看到的位置，而不是埋在通用偏好里；隐私紧随其后，因为登录与删除都直接
 * 牵涉个人信息处理；通用偏好（外观/单位/通知）目前还没有内容，所以这一版不摆空卡。
 *
 * ## 为什么不用 `List`
 *
 * 与首页一致：`List` 屏幕外的行不渲染，XCUITest 查不到，而且默认分隔线与深色主题要反复调。
 * 这里用 `ScrollView` + 卡片，行少也不受影响。
 */
struct SettingsView: View {

    @State private var account = AccountStore.shared
    @State private var showDeleteConfirm = false
    /// 匿名使用统计：**默认关**。开启前必须有明确的用途与接收方，否则就是"先收集再想怎么用"。
    @AppStorage("settings.telemetryEnabled") private var telemetryEnabled = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                accountCard
                privacyCard
                aboutCard
            }
            .padding()
        }
        .background(Palette.background)
        .navigationTitle("设置")
        .onAppear { account.refreshCredentialState() }
        .alert("删除账号", isPresented: $showDeleteConfirm) {
            Button("删除账号", role: .destructive) { account.deleteAccount() }
            Button("取消", role: .cancel) { }
        } message: {
            Text("将清除本机的账号信息、昵称与联机身份，且无法恢复。对局记录只存在本机，也会一并清除。")
        }
        .accessibilityIdentifier("settingsRoot")
    }

    // MARK: - 账号

    private var accountCard: some View {
        card {
            sectionTitle("账号", symbol: "person.crop.circle")

            switch account.state {
            case .guest:
                Text("游客模式 · 数据仅存本机")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Palette.textPrimary)
                    .accessibilityIdentifier("accountStateLabel")

                Text("不登录也能使用全部功能。登录只用于跨设备同步与保留昵称、战绩。")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)

                // 官方控件：样式与最小尺寸由系统保证，不自己画一个"苹果登录"按钮
                //（4.8 要求 Sign in with Apple 必须使用官方按钮样式）。
                SignInWithAppleButton(.signIn) { request in
                    account.configure(request)
                } onCompletion: { result in
                    account.handle(result)
                }
                .frame(height: 46)
                .signInWithAppleButtonStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("signInWithApple")

                Text("我们只索取昵称，不索取邮箱、手机号；不登录不会限制任何功能。")
                    .font(.caption2)
                    .foregroundStyle(Palette.textMuted)

            case .signedIn(_, let displayName):
                HStack(spacing: 10) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.title2)
                        .foregroundStyle(Palette.primary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(displayName)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(Palette.textPrimary)
                        Text("已通过 Apple 登录")
                            .font(.caption2)
                            .foregroundStyle(Palette.textMuted)
                    }
                }
                .accessibilityIdentifier("accountStateLabel")

                Button {
                    account.signOut()
                } label: {
                    Text("退出登录")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(Palette.textSecondary)
                .accessibilityIdentifier("signOut")

                // Apple 5.1.1(v)：支持创建账号就必须在 App 内提供删除账号，不能只给退出登录。
                Button(role: .destructive) {
                    showDeleteConfirm = true
                } label: {
                    Text("删除账号")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("deleteAccount")
            }

            if let message = account.errorMessage {
                Text(message)
                    .font(.caption2)
                    .foregroundStyle(Palette.error)
                    .accessibilityIdentifier("accountError")
            }
        }
    }

    // MARK: - 隐私

    private var privacyCard: some View {
        card {
            sectionTitle("隐私", symbol: "hand.raised")

            Toggle(isOn: $telemetryEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("匿名使用统计")
                        .font(.subheadline)
                        .foregroundStyle(Palette.textPrimary)
                    Text("默认关闭。开启后仅向境内自建服务上报匿名使用数据（不含广告标识符，不用于追踪）。")
                        .font(.caption2)
                        .foregroundStyle(Palette.textMuted)
                }
            }
            .tint(Palette.primary)
            .accessibilityIdentifier("telemetryToggle")

            Divider().background(Palette.divider)

            NavigationLink {
                PrivacyPolicyView()
            } label: {
                rowLabel("隐私政策", symbol: "doc.text")
            }
            .accessibilityIdentifier("privacyPolicy")

            Text("对局数据只在你主动进入联机房间时上传；纯单机玩法不上传任何数据。")
                .font(.caption2)
                .foregroundStyle(Palette.textMuted)
        }
    }

    // MARK: - 关于

    private var aboutCard: some View {
        card {
            sectionTitle("关于", symbol: "info.circle")

            HStack {
                Text("版本")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textSecondary)
                Spacer()
                Text(appVersion)
                    .font(.subheadline)
                    .foregroundStyle(Palette.textPrimary)
            }

            HStack {
                Text("服务提供地")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textSecondary)
                Spacer()
                Text("中国境内（自建）")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textPrimary)
            }

            HStack {
                Text("ICP 备案号")
                    .font(.subheadline)
                    .foregroundStyle(Palette.textSecondary)
                Spacer()
                // ⚠️ 域名 dartvio.win 指向境内服务器，备案号待确认：中国区上架需要在
                // App Store Connect 填备案号，否则会被卡审核。
                Text("待填写")
                    .font(.subheadline)
                    .foregroundStyle(Palette.warning)
            }
            .accessibilityIdentifier("icpRow")
        }
    }

    // MARK: - 共用

    private func sectionTitle(_ text: String, symbol: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.caption)
                .foregroundStyle(Palette.primary)
            Text(text)
                .font(.headline)
                .foregroundStyle(Palette.textPrimary)
        }
    }

    private func rowLabel(_ text: String, symbol: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.caption)
                .foregroundStyle(Palette.textSecondary)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Palette.textPrimary)
        }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            content()
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

    private var appVersion: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }
}
