# WXEntry 2.30 / v351

公开源码包含 WXEntry 加载器及 APK 内置的免费版内核。三版均支持聊天长按复读和新群免打扰。

## 构建

准备 JDK 17、Android SDK、Xposed API 82、D8/R8 及包含 dexlib2 的 jadx-all.jar。将 android.jar、xposed-api-82.jar、d8.jar、jadx-all.jar 放入依赖目录。依次执行：

```sh
python build/build_kernels.py --free-only --libs <依赖目录>
python build/build_loader.py --libs <依赖目录>
```

两个脚本均支持 `--java-bin` 指定 JDK bin。使用官方 2.29 签名 APK 作为资源基线，运行 `python build/repack_apk.py --help` 查看未签名 APK 打包参数。构建与签名互相独立。
