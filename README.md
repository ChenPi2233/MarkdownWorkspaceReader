# MarkdownWorkspaceReader

个人/内部使用的 Android Markdown 工作区阅读器。

这个项目不是通用笔记软件，也不是完整 Git 客户端。它只面向一个 GitHub 文档仓库，用来按文件树阅读 Markdown，并在本地保存和文档版本绑定的笔记。

## 当前定位

- 单仓库 Markdown 工作区阅读器
- GitHub 只作为内容源
- 文档身份来自 Markdown frontmatter
- 本地 note 绑定到 `project_code + doc_id + version`
- 不写回 GitHub，不做 commit/push/pull

## 当前功能

- 配置一个 GitHub 仓库：`owner/repo`、`branch`、`token`、可选起始目录
- 按文件夹树浏览仓库里的 Markdown 文档
- 阅读常见 Markdown 内容
- 解析 frontmatter 中的文档身份字段
- 缓存文件树和已打开文档
- 打开 App 时恢复上次阅读文档和阅读位置
- 收藏常用文档
- 每个文档版本一份本地文档 note
- 基于文本选区的本地笔记
- 右侧文档工作台列出当前文档笔记并可跳转

## Frontmatter 协议

V1 依赖文档开头的 YAML frontmatter。关键字段：

```yaml
---
project_code: ANLAN-VN
doc_id: NAR-001
title: 叙事总纲
version: 0.1.2
last_updated: 2026-04-18
---
```

其中：

- `project_code + doc_id` 是文档身份
- `project_code + doc_id + version` 是文档版本态
- note 只绑定当前文档版本态
- `version` 精确匹配，不做 semver 推断
- 路径只是物理位置，不作为 note 主身份

缺少合法 frontmatter 的 Markdown 仍可阅读，但不支持版本作用域 note。

## 构建

需要 Android Studio 或本机 Android SDK 环境。

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已授权 USB 调试的手机：

```powershell
.\tools\install-debug.ps1
```

也可以手动安装：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 测试

```powershell
.\gradlew.bat testDebugUnitTest :app:assembleDebug
```

手机测试步骤见：

```text
PHONE_TEST_CHECKLIST.md
```

## 当前限制

- 只支持一个仓库
- 不做 GitHub 写回
- 不做云端笔记同步
- 不做 Markdown 编辑
- 不做复杂账号系统
- 右侧工作台目前只通过按钮打开，暂时关闭滑动手势
- 早期测试版使用 debug APK，不作为正式分发包

## 第三方资源

Tabler Icons 说明见：

```text
THIRD_PARTY_NOTICES.md
```
