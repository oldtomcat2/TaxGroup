# 古楚轩船 (TaxGroup)

税务选题管理与联动合作 Android 应用

## 版本信息

- **当前版本**: v1.0.0-beta1
- **版本号**: 100
- **更新日期**: 2026-08-03

## 功能特性

### 核心功能
- ✅ 用户登录/注册系统
- ✅ 选题报送、查询、编辑、审核全流程
- ✅ 联动合作（可参与选题/查询维护）
- ✅ 版本更新检查与自动下载

### 技术栈
- Kotlin + Android SDK 36
- Turso 远程数据库（HTTP API）
- Material Design 3 组件
- 古风 UI 设计（金/红/宣纸配色）

## 数据库结构

| 表名 | 说明 |
|------|------|
| `commission_summary` | 选题主表 |
| `joined_topical` | 联动参与表（联合主键） |
| `topical_type` | 选题类型表（level=1重要选题/level=2选题方向） |
| `Department` | 部门表 |
| `User` | 用户表 |
| `Menu_1` | 菜单表 |

## 开发环境

- Android Studio
- compileSdk: 36
- minSdk: 24
- targetSdk: 36

## 更新日志

### v1.0.0-beta1 (2026-08-03)
- 初始 Beta 版本发布
- 完成基础功能开发

## 许可证

Copyright © 2026 oldtomcat2
