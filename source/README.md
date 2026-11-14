# WXEntry 免费版源码 / 2.26 / v289

## 官方下载与验包

只从本仓库的 Releases 或官方频道置顶下载 WXEntry。官方 APK 签名证书的 SHA-256 指纹为：

```text
87b7f91d54f8d67a367630cacfc9476b6e4d83bea7ced24cd2f7bd884b3f9d11
```

用 Android SDK Build Tools 的 `apksigner verify --print-certs WXEntry.apk` 查看签名证书。文件名、包名和版本号可以相同，请核对完整证书指纹以及该 Release 提供的 APK 文件 SHA-256。第三方重签包不属于官方发布。当前官方 2.26 APK 可从 [WXEntry 更新服务器](https://walideng.fun/wx/wxentry.apk) 下载。文件 SHA-256 为 `813fa15960a82247bfbef392544dcd2b9925997b5eca8ebf72f305c00fa69f29`。


## 构建免费内核

使用 JDK 17，以及包含 android.jar、xposed-api-82.jar、d8.jar 和 jadx-all.jar 的依赖目录。

```text
python build/build_kernels.py --free-only --java-bin <JDK-bin> --libs <依赖目录>
python build/build_loader.py --java-bin <JDK-bin> --libs <依赖目录>
```

公开源码用于构建 v289 免费内核。2.26 正式签名 APK 内置 v288，可以在模块内检查更新取得 v289。
