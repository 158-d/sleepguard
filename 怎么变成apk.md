# 方案 B：把工程变成 apk，装到手机上

## 先说清楚现状

你现在拿到的是一个**完整的安卓工程**（Java，零第三方依赖，`minSdk 28`，即 Android 9 以上）。
但我没法在这里直接给你 apk —— 这台电脑上没有 JDK、没有 Android SDK，也连不上外网，
编译不了。所以下面给你两条把源码变成 apk 的路，选一条就行。

- 路线一：GitHub 云端打包（**推荐**，不用装任何开发工具，全程网页操作）
- 路线二：Android Studio 本地打包（你电脑上已经装了开发环境的话）

---

## 路线一：GitHub 云端打包（约 10 分钟）

仓库已经建好（你已经到这一步了）。剩下就是把文件放上去，两种方式选一个。

### 方式 1：用 Git 命令推上去（推荐，你电脑上已经装了 Git）

开始菜单搜 **Git Bash** 打开，一条条粘贴执行：

```bash
cd "D:/文档/deepseek-harness/default-workspace/抖音睡前自动息屏/douyin-sleep-guard"
git init
git config user.name "158-d"
git config user.email "158-d@users.noreply.github.com"
git add -A
git commit -m "init"
git branch -M main
git remote add origin https://github.com/158-d/sleepguard.git
git push -u origin main
```

最后一条会弹出 GitHub 登录窗口（浏览器里点授权即可；第一次可能提示安装
Git Credential Manager，点同意）。推完刷新仓库页面，能看到 `app/`、`.github/`、
`settings.gradle` 就成功了。

### 方式 2：网页拖拽上传（不想碰命令行就用这个）

1. 在当前这个空仓库页面，点 **Quick setup** 卡片下方那行小字里的
   **"uploading an existing file"** 链接
2. 打开资源管理器，进入：
   `D:\文档\deepseek-harness\default-workspace\抖音睡前自动息屏\douyin-sleep-guard`
3. **Ctrl+A 全选里面的内容**（`settings.gradle`、`build.gradle`、`gradle.properties`、
   `.gitignore`、`app`、`.github`），直接拖进浏览器上传区
4. 底部点 **Commit changes**

> 拖整个 `douyin-sleep-guard` 文件夹进去也可以 —— 打包脚本会自动找到工程在哪一层。
> 但文件别拆开放到别的地方。

### 然后（两种方式都一样）

1. 点 **Actions** 标签（若提示启用，选 enable）
2. 左侧 **build-apk** → **Run workflow** → 绿色按钮
   （如果是 push 上去的，一般已经自动开始跑了，直接看进度就行）
3. 等 3～5 分钟出现绿 ✅ → 点进这次运行 → 拉到页面最下面 **Artifacts** →
   下载 **sleepguard-apk.zip**
4. 解压得到 `app-debug.apk`，传到手机（微信文件传输助手 / 数据线 / 网盘都行）
5. 手机上点开安装，允许"未知来源"

> 如果编译失败（红色 ❌）：点进日志，找到第一条带 `error:` 的行，把那一行发我，我改代码。
> 几个已知的坑先列在这里，能自己处理：
>
> | 日志里的关键字 | 原因 | 处理 |
> |---|---|---|
> | `Unsupported class file major version` | JDK 版本不对 | workflow 已固定 JDK 17，别改 |
> | `Could not find method namespace()` | 用了老版 AGP | 别把 `8.1.4` 改成 7.x |
> | `resource xml/accessibility_service_config` 相关错误 | 某个事件名这套 android 版本不认 | 把该文件里 `android:accessibilityEventTypes=...` 整行换成 `android:accessibilityEventTypes="typeAllMask"`（多收一点事件，逻辑不受影响） |
> | `SDK location not found`（只在本地 Android Studio 出现） | 缺 `local.properties` | 让 Android Studio 自动生成，或新建一行 `sdk.dir=你的SDK路径` |

---

## 路线二：Android Studio 本地打包

1. 装 **Android Studio**（官网下载，约 3 GB）
2. `File → Open`，选 `douyin-sleep-guard` 这个目录（不是它的父目录）
3. 第一次打开会提示 Gradle Sync，需要联网下载 Android Gradle Plugin（约几百 MB，国内可能慢）
4. 同步完成后：`Build → Build Bundle(s)/APK(s) → Build APK(s)`
5. 右下角弹窗点 **locate**，就是
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 装到手机之后（这几步别跳过）

1. 打开 App，点 **① 打开无障碍权限设置**
2. 在无障碍列表里（一般在「已下载的服务」或「已安装的应用」下面）找到
   **「睡着自动息屏」** → 打开 → 弹窗选 **允许**
3. **安卓 13 / 14 必看**：侧载安装的 App 默认被禁止开启无障碍，列表里会写
   *"此设置因安全限制而不可用"*。解决办法：
   设置 → 应用 → 睡着自动息屏 → **右上角 ⋮ → 允许受限设置** → 再回去开无障碍。
4. 回到 App，顶部应显示 **「守护服务：运行中 ✅」**
5. 点 **② 立即测试一次锁屏**，屏幕应该立刻熄灭 —— 这一步不通，后面都不会生效
6. 点 **③** 关掉省电限制：
   - 小米/红米：设置 → 应用设置 → 睡着自动息屏 → 省电策略 → **无限制**；同时打开「自启动」
   - 华为/荣耀：设置 → 应用 → 应用启动管理 → 睡着自动息屏 → 改为**手动管理**，三项全开
   - OPPO/一加/realme：电池 → 应用耗电管理 → 允许**后台运行**
   - vivo/iQOO：电池 → 后台高耗电 → 允许
7. 去抖音关掉 **「后台播放 / 锁屏后继续播放」**（我 → ☰ → 设置 里找）。
   不关的话，屏幕黑了声音还会响一整夜，等于白装。

---

## 参数怎么调

| 设置 | 默认 | 说明 |
|---|---|---|
| 多久没操作算睡着 | 12 分钟 | 刷抖音时手基本十几秒就动一下（滑、点赞、看评论）。12 分钟完全没动，基本就是睡着了 |
| 只在夜间生效 | 00:00 → 08:00 | 白天刷的时候不会误伤。经常 23 点多就开始躺下刷的话，把起点改成 23:00 |
| 锁定前轻震提醒 | 开 | 到阈值的 80%（约 9.6 分钟）震一下，还醒着动动手指就重置 |
| 监视哪些 App | 抖音 | 还可以勾快手、B 站、微信视频号等 |

**如果"我还醒着它就锁屏了"**：说明你这台手机在刷的时候没把触摸/滑动事件报给无障碍服务。
把阈值调到 20～30 分钟，或者把「只在夜间生效」的范围收窄。

---

## 耗电到底多少

三个事实，按重要性排：

1. **不持锁、不设闹钟、不联网**。代码里没有 `WakeLock`、没有 `AlarmManager`、没有前台服务、没有任何网络请求。
   息屏之后系统进入休眠，那个 10 秒的定时检查会被系统直接冻结，**不会把手机唤醒**——
   待机时额外耗电约等于 0，只是进程常驻占 ~10–20 MB 内存。
2. **亮屏刷抖音时**：每 10 秒做一次极轻量检查（问系统"前台是哪个 App"+ 一个时间戳比较），
   粗估 **0.1%～0.3%/小时**。对比抖音自己播放视频的 8%～15%/小时，可以忽略。
3. **它省下来的电才是大头**：真正费电的是"你睡着后抖音继续播放 6～8 小时"——
   屏幕常亮 + 硬解 + 移动网络，一夜通常吃掉 **30%～60%** 电量，还带着静态画面烧屏的风险。
   这个 App 的价值就是 12 分钟后把这一切停掉。

自己验证的办法：设置 → 电池 → 电池使用情况，看「睡着自动息屏」在不在列表末尾（正常应该 < 1%）。
如果它排到很前面，那说明有异常，把截图发我。

> 反过来说，如果你开了很激进的「省电模式 / 禁止后台活动」，系统会把这个检查一起冻结，
> 那就更省电 —— 但也可能一整晚都不锁屏。所以记得按上面第 6 步给它开白名单。

---

## 它的判断逻辑（代码在哪）

- `app/src/main/java/com/diy/sleepguard/GuardService.java` —— 全部逻辑
  - 每 10 秒看一次前台 App（`getRootInActiveWindow()`）
  - 收到点击、长按、滑动、触摸开始、切换 App 等无障碍事件 → 计时器清零
  - 前台是抖音 + 计时超过阈值 → `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` 直接锁屏
  - 息屏状态下不计时，避免影响你正常使用
- `Prefs.java` —— 配置、夜间时间窗、统计
- `MainActivity.java` —— 设置界面

**隐私**：不联网、不截屏、不读聊天内容，只判断"哪个 App 在前台"和"有没有发生点击/滑动"。
全部代码就这几个文件，可以自己看。

---

## 已知限制（诚实说明）

1. **依赖无障碍服务**：部分系统/清理软件会把它关掉，失效后需要重新进设置打开一次。
   所以建议把方案 A 的「定时开关机」当兜底。
2. **没在你手机上真机跑过**：逻辑是标准写法，但我这里没有安卓设备，无法替你验证。
   请在第一次使用时点 ② 确认锁屏权限通过，并观察一晚。
3. **分屏 / 小窗刷抖音**不保证有效（前台判断会漂移）。
4. 部分 ROM 出于省电会限制每 10 秒的后台检查，可能延迟几十秒才锁屏 —— 影响不大。
