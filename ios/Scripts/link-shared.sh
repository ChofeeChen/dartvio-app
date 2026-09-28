#!/bin/bash
#
# 把 Gradle 产出的 shared.framework 交给 Xcode。
#
# 为什么要这个脚本：Kotlin 对真机与模拟器产出的是**两个** framework
# （android/shared/build/bin/iosArm64、iosSimulatorArm64），而 Xcode 的
# FRAMEWORK_SEARCH_PATHS 只认一个目录。所以按当前 SDK_NAME 选一份拷贝到
# ios/Build/Frameworks/ 下，Xcode 始终链接同一路径。
#
# 产物落在 ios/Build/ 是刻意的：仓库 .gitignore 的 `*/build/` 已覆盖它，
# 于是 WCB 那边 `git add ios/` 不会把 28MB 的 framework 提交进仓库。
#
# 需要 Xcode 侧设置 ENABLE_USER_SCRIPT_SANDBOXING = NO（Xcode 15+ 默认开启沙盒，
# 会拦掉脚本对工程目录的写入；已在 project.pbxproj 里关掉）。
#
# 注意两点（踩过）：
# 1. 变量名一律用 ${} 定界，且 echo 只用 ASCII —— Xcode 的构建环境是 C locale，
#    bash 3.2 会把紧跟变量名的多字节字符当成变量名的一部分，报 "unbound variable"。
# 2. 这里只做拷贝，不在 Xcode 里跑 Gradle：Gradle 构建由 CI / 命令行负责，
#    产物缺失时下面的分支才会兜底构建一次。

set -euo pipefail

FW_NAME="shared"

case "${SDK_NAME:-}" in
  *simulator*)
    KOTLIN_TARGET="iosSimulatorArm64"
    GRADLE_TASK="linkDebugFrameworkIosSimulatorArm64"
    ;;
  *)
    KOTLIN_TARGET="iosArm64"
    GRADLE_TASK="linkDebugFrameworkIosArm64"
    ;;
esac

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_DIR="${PROJECT_DIR}/../android"
SRC="${ANDROID_DIR}/shared/build/bin/${KOTLIN_TARGET}/debugFramework/${FW_NAME}.framework"
DEST_DIR="${PROJECT_DIR}/Build/Frameworks"
DEST="${DEST_DIR}/${FW_NAME}.framework"

if [[ ! -d "${SRC}" ]]; then
  echo ":: ${FW_NAME}.framework missing, building with Gradle (${KOTLIN_TARGET}) ..."
  export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
  (cd "${ANDROID_DIR}" && ./gradlew ":shared:${GRADLE_TASK}")
fi

mkdir -p "${DEST_DIR}"
rm -rf "${DEST}"
cp -R "${SRC}" "${DEST}"

echo ":: ${FW_NAME}.framework ready (${KOTLIN_TARGET}) -> ${DEST}"
