# TaxGroup 新版本发布指南

## 快速流程

### 1. 修改版本号

文件：`app/build.gradle.kts`

```kotlin
defaultConfig {
    versionCode = 101          // 每次发布+1（必须递增）
    versionName = "1.0.1"      // 版本名称（随意）
}
```

---

### 2. 更新 version.json

文件：`version.json`

```json
{
  "versionCode": 101,
  "versionName": "1.0.1",
  "apkUrl": "https://github.com/oldtomcat2/TaxGroup/releases/download/v1.0.1/app-debug.apk",
  "changelog": "版本更新内容：\n- 修复了xxx问题\n- 新增了xxx功能",
  "forceUpdate": false
}
```

**注意**：
- `versionCode` 必须比旧版本大，否则用户收不到更新
- `apkUrl` 中的 `v1.0.1` 要与 GitHub Tag 一致

---

### 3. 编译 APK

Android Studio：
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

APK 输出路径：
```
app/build/outputs/apk/debug/app-debug.apk
```

---

### 4. 提交代码到 GitHub

```powershell
cd "E:\AndroidApp\SDK\MyProject\TaxGroup"
git add .
git commit -m "Release v1.0.1: 更新内容简述"
git push origin main
```

---

### 5. 创建 GitHub Release

1. 打开 https://github.com/oldtomcat2/TaxGroup/releases
2. 点击 **"Draft a new release"**
3. 填写信息：
   - **Tag**: `v1.0.1`（与 version.json 中的 apkUrl 对应）
   - **Title**: `TaxGroup v1.0.1`
   - **Description**: 版本更新说明
4. 上传 APK 文件（把 `app-debug.apk` 拖到上传区域）
5. 点击 **"Publish release"**

---

### 6. 验证更新

在手机上打开 App → 首页菜单 → "版本更新" → 应提示有新版本

---

## 版本号规则

| 版本类型 | versionCode | versionName | 示例 |
|---------|-------------|-------------|------|
| 测试版 | 100, 101... | 1.0.0-beta1 | 当前 |
| 正式版 | 200, 201... | 1.0.0, 1.1.0 | 后续 |
| 大版本 | 300+ | 2.0.0 | 重大更新 |

---

## 常见问题

**Q: 用户收不到更新？**
- 检查 `version.json` 中的 `versionCode` 是否比用户当前版本大
- 检查 `apkUrl` 链接是否能正常下载

**Q: 安装失败？**
- 确保 APK 使用相同的签名（debug.keystore）
- 正式版需要配置 release 签名

**Q: version.json 不生效？**
- GitHub Raw 有缓存，等 5-10 分钟再试
- 或访问 `https://raw.githubusercontent.com/oldtomcat2/TaxGroup/main/version.json` 确认内容
