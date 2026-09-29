#!/bin/bash
# 开关 Sign in with Apple（SIWA）。
#
# 用法（在 ios/ 下执行）：
#   Scripts/toggle_sign_in_with_apple.sh on
#   Scripts/toggle_sign_in_with_apple.sh off
#
# ## 为什么要有这个开关
#
# SIWA 需要 `com.apple.developer.applesignin` 权限，而**个人（免费）开发者团队不支持该能力**：
# 带权限构建真机会直接失败
#   "Personal development teams do not support the Sign In with Apple capability"
# 也就是说：付费账号就位前，要么不能真机跑，要么不能保留 SIWA —— 这个脚本用来二选一，
# 不必每次手改工程文件。
#
# ## 它改两处（必须一起改，只改一处会不一致）
#
# 1. `CODE_SIGN_ENTITLEMENTS`（决定描述文件要不要含该权限 → 决定真机能不能编过）
# 2. `SWIFT_ACTIVE_COMPILATION_CONDITIONS` 里的 `ENABLE_SIGN_IN_WITH_APPLE`
#    （决定设置页渲染官方按钮，还是渲染"当前构建未启用"的说明）
#
# 当前默认 **off**：保证任何账号都能真机编译，账号能力就位后 `on` 即可。

set -euo pipefail

MODE="${1:-}"
PBX="$(dirname "$0")/../DartVio.xcodeproj/project.pbxproj"

if [[ "$MODE" != "on" && "$MODE" != "off" ]]; then
  echo "用法: $(basename "$0") on|off" >&2
  exit 2
fi

python3 - "$MODE" "$PBX" <<'PY'
import re, sys

mode, path = sys.argv[1], sys.argv[2]
s = open(path).read()

ENTITLEMENT = "\t\t\t\tCODE_SIGN_ENTITLEMENTS = DartVio/DartVio.entitlements;\n"
FLAG_LINE = "\t\t\t\tSWIFT_ACTIVE_COMPILATION_CONDITIONS = ENABLE_SIGN_IN_WITH_APPLE;\n"
# 只匹配 App target（UITests 的 bundle id 带 .UITests 后缀，不会被命中）
APP_ID_LINE = "\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = com.dartvio.app;\n"

if mode == "off":
    s = s.replace(ENTITLEMENT, "")
    s = s.replace(FLAG_LINE, "")
    s = s.replace(" ENABLE_SIGN_IN_WITH_APPLE", "")
    s = s.replace("ENABLE_SIGN_IN_WITH_APPLE ", "")
    # 去掉被清空的编译条件行，避免留下 `SWIFT_ACTIVE_COMPILATION_CONDITIONS = ;`
    s = re.sub(r"\t+SWIFT_ACTIVE_COMPILATION_CONDITIONS = ;\n", "", s)
else:
    s = s.replace(APP_ID_LINE, ENTITLEMENT + FLAG_LINE + APP_ID_LINE)

open(path, "w").write(s)
has = "CODE_SIGN_ENTITLEMENTS" in s
print("SIWA %s（entitlement=%s，编译开关=%s）" % (
    mode, has, "ENABLE_SIGN_IN_WITH_APPLE" in s))
PY
