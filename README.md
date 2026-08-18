<div align="center">
  <img src="https://auth.xn--cdwq63c.online/assets/dream-portal-mark.png" alt="Dream" width="88" height="88" />

  # Dream Launcher for PC

  Windows 上的 Dream 邀请启动器

  [官网与下载](https://auth.xn--cdwq63c.online/) · [反馈问题](https://github.com/diziqin/dream-launcher-pc/issues) · [上游 HMCL](https://github.com/HMCL-dev/HMCL)
</div>

---

Dream Launcher for PC 面向加入 Dream Server 的玩家。它会打开经验证的 Dream 邀请链接，准备该服务器要求的游戏版本、加载器和客户端文件，并将服务器地址写入受管游戏实例。

它不是 Dream Server 服主端；创建、运行、分享服务器请使用 [Dream Server](https://auth.xn--cdwq63c.online/#downloads)。Minecraft 账号登录仍由启动器自身的账号能力完成。

## 玩家使用

1. 从 [Dream 官网下载页](https://auth.xn--cdwq63c.online/#downloads) 安装 Windows 版启动器。
2. 从服主获得官方邀请页链接，或在启动器中打开 `dream-launcher://join/...` 邀请。
3. 查看服务器、游戏版本与环境说明，确认后开始同步。
4. 同步完成后，从 Dream 受管实例启动游戏，在多人游戏列表中直接进入服务器。

邀请页没有客户端整合包时，启动器仍会准备对应的原版游戏版本和加载器；不会无故下载未知文件。

## Dream 邀请能力

- 验证 Dream manifest v2 的来源、HTTPS、Ed25519 签名、文件大小与 SHA-256。
- 支持 Fabric、Forge、NeoForge、Quilt 等加载器，以及原版环境同步。
- 记录受管邀请与 revision；服主重新发布后，玩家可检查并同步新环境。
- 支持 `dream-launcher://join/...` Windows 协议入口。
- 对无客户端环境的服务器明确显示“无需额外整合包”，不会把服务端专用插件带到客户端。

> 请只通过 Dream 官方邀请页和可信服主分享的链接导入环境。未知来源的链接、签名错误或文件校验失败会被拒绝。

## 下载与校验

正式安装包、SHA-256 校验值和当前兼容说明统一在 [Dream 官网下载页](https://auth.xn--cdwq63c.online/#downloads) 发布。安装完成后可直接点击邀请页唤起本启动器。

## 本地构建

本仓库保留完整的构建源码，适合开发、审查和贡献 Dream Launcher for PC 的客户端改动。

```powershell
git clone https://github.com/diziqin/dream-launcher-pc.git
cd dream-launcher-pc
.\gradlew.bat :HMCL:makeExecutables
```

构建完成后，Windows 可执行文件和 JAR 位于 `HMCL/build/libs/`。开发环境应使用与项目 Gradle 配置兼容的 JDK；首次构建会下载依赖。

运行测试与静态检查：

```powershell
.\gradlew.bat :HMCL:test :HMCL:checkstyle
```

## 反馈范围

- 启动器界面、邀请导入、环境同步、协议唤起：请在本仓库提交 Issue。
- Dream Server 实例、账号、云端邀请、下载服务：请通过 [官网支持入口](https://auth.xn--cdwq63c.online/) 联系 Dream 团队，并不要在 Issue 中提交账号密码、令牌或完整日志中的敏感信息。

## 致谢与许可证

Dream Launcher for PC 基于 [Hello Minecraft! Launcher (HMCL)](https://github.com/HMCL-dev/HMCL) 进行适配和扩展，保留其版权声明与许可证要求。HMCL 是本项目的重要上游；Dream 的邀请协议、受管实例和云端服务并不代表获得 HMCL 官方背书或与其存在合作关系。

本仓库中的代码依照 [GPL-3.0](LICENSE) 发布。分发修改版本时，请同时遵守 GPLv3 与 HMCL 的附加条款，并明确标识其与上游 HMCL 的区别。
