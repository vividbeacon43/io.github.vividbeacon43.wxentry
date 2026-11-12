# WXEntry 2.19 / 内核 v281 候选版

以附件中的 v279 源码为基线。免费版、高级版和 VIP 使用粉白设置菜单。列表内容超出窗口时，底部显示滑动方向，顶部可直接检查更新。「更新公告」位于「打赏」下方。 三版「实验功能」中的「模拟相机扫码」用右侧滑块。菜单的滑块和进入子页的箭头共用同一列，开关可点击或拖动。

## 填写更新公告

在服务端程序同目录维护 UTF-8 文件 `announcements.json`。每次发布内核时，在 `releases` 中增加一条，版本号填内核版本数字（字符串，不带 v），在 `added` 和 `fixed` 数组中逐项填写你自己的文字。没有新增或修复的分组可留空数组，至少一组需要有内容。日期可省略。菜单自动按内核版本从新到旧显示，修改公告无需重打 APK。

下面是填写格式示例。随源码提供的 `server/announcements.json` 初始为空。

```json
{
  "releases": [
    {
      "version": "281",
      "date": "2026-11-12",
      "added": ["填写本次新增功能"],
      "fixed": ["填写本次修复内容"]
    }
  ]
}
```

替换线上公告文件时，先在同目录写入新文件，校验 JSON，再原子替换正式文件。菜单从 `GET /wx/kernel/announcements` 获取公告。

## 构建和发布

1. 使用 Python 3、JDK 17 和包含 `android.jar`、`xposed-api-82.jar`、`d8.jar`、`jadx-all.jar` 的依赖目录运行 `python build/build_kernels.py --java-bin <JDK-bin> --libs <依赖目录>`。要生成可上线的签名内核，在维护环境中加 `--signing-key <内核私钥>`。
2. 运行 `python build/build_loader.py --java-bin <JDK-bin> --libs <依赖目录>`。再运行 `python build/repack_apk.py --base-apk <原签名的WXEntry-2.17.apk> --java <java.exe> --build-tools <Android构建工具目录> --output <WXEntry-2.19-unsigned.apk>`。用原 APK 证书签名候选 APK，然后按现有的签名格式 `v|name|sha256` 生成 `apk.json`。版本应是 2.19（119），118 保留给历史回退版本。
3. 先部署服务端接口并发布已签名 APK 与 `apk.json`，确认两者匹配，再发布三条内核通道及手写公告。新内核清单生成 `min_apk_v: 119`，服务端对旧 APK 的新内核请求返回 426。旧版 2.17 用户可以先覆盖安装 2.19，再彻底退出并重新打开微信。新版加载器会先检查 APK，再按当前授权拉取相应内核。后续仅发布兼容的内核时，可以继续使用对应的 APK 版本门槛。
