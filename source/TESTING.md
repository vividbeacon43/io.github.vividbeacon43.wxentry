# v256 验证

- PASS 29 voice callback behavior checks
- PASS 45 voice forwarding behavior checks
- PASS 37 modern voice file/submission checks
- PASS 16 voice marker layout/recycling checks
- PASS 74 batch timing/progress/cancellation checks
- PASS 19 conversation eligibility/filter checks
- PASS 23 right-side switch/binding/theme checks
- PASS 42 inline recall metadata/following-message regression checks
- PASS 56 inline recall binding/recycling/persistence display checks
- PASS 20 plus popup presence, reuse, and transactional recovery checks
- PASS 30 early tablet mode, atomic persistence, isolation and disable checks
- PASS 9 explicit restart scheduling/fallback/termination checks
- PASS 15 signed paid tablet entitlement, downgrade and tampering checks
- PASS 35 media cache expiry, retention, restore, identity and independent original policy checks
- PASS 31 native media interception, original mode, queued list/owner and failure behavior checks
- PASS 49 local Moments codec, immutable edits, identity and bounded retention checks
- PASS 43 local Moments account isolation, paid grants, atomic failure, corrupt recovery and size cleanup checks
- PASS 67 all-friend and random-count selection, uniqueness, range and non-mutating checks
- PASS 35 Moments switch/account/entitlement and native ad-page filtering checks
- PASS 59 SNS draft eligibility, native editor extras, cancellation, account and owned-file behavior checks
- True
- PASS 89 premium entitlement and exclusive mode checks
- PASS 110 red packet state/ownership/template checks
- PASS 49 native red packet callback/submission checks
- PASS 20 red packet late-boot context and native launch delegation checks
- PASS 22 native red packet source, envelope and entry delegation checks
- PASS 105 red packet settings/persistence/reply-flow checks
- PASS 44 receipt list/seconds/migration checks
- PASS 51 transfer parser/native/receipt-flow checks
- PASS 51 VIP keyword parsing, exclusive rules, isolation, deferred receipt and confirmation checks
- PASS 41 native voice menu/file/task/result contracts
- PASS 44 native red packet request/result/text contracts
- PASS 52 native transfer and manual red packet UI contracts
- PASS 50 native recall allocator and inline row/text/identity contracts
- PASS 23 native tablet/login/QR contracts
- PASS 57 native video/file revoke, download, path and original image contracts
- PASS 91 native SNS long-press, source, downloads and composer contracts
- PASS 6 native Moments merged-page and advertising marker contracts
- PASS 37 native complete red packet entry, page selector and application route contracts
- PASS 35 native local Moments post binding, view pooling, avatar and profile contracts
- PASS 168 framework DEX ordering/search/body/immutability checks
- PASS 81 final DEX enum reflection contracts
- PASS 6 signed loader upgrade/rollback checks
- PASS 8 APK manifest protocol and delivery checks

## 测试机与真实红包

Android14、微信8.0.80（3220）、服务端正式绑定的VIP账号。用户发送小额红包，模块在原有前台领取模式执行；未由测试代理发送真实消息、点赞或付款。

- 普通红包含test：命中跳过＋模糊匹配，日志确认跳过，未拉起领取页。
- 皮肤红包含test：同规则跳过。
- 皮肤红包“恭喜发财”：未命中，自动打开LuckyMoneyNewReceiveUI并拆包，原生结果和详情页确认0.01元已存入零钱。
- 切换为仅领取命中＋精确匹配后，皮肤红包“这是test红包”被跳过；祝福语完整等于test的皮肤红包自动领取，原生结果和详情页再次确认0.01元到账。

实际菜单验证包括两策略互斥、开启时空词表不能保存、多词显示、仅领取＋精确保存后重开、红包／转账分开设置。测试用词预置后通过真实菜单保存策略，未将手机ADB输入法的乱序输入当作成功输入。转账备注过滤通过自动行为、解析和原生字段契约检查，本次未另做真实转账到账测试，也未重复静默模式真实资金测试。

## 打赏与最终包

真实红包实测后，按用户新增要求仅修改三种内核中的打赏代码。逐文件哈希验证红包、转账及设置源文件完全相同；三份Kernel去除打赏区段后的源代码哈希也相同。最终包重新通过全部1711项检查、168项加载器兼容、81项枚举、6项签名回退、8项APK协议，以及2.05／2.07／2.09／2.11／2.13旧加载器检查；最终安装字节与构建报告再次核对。

正常点击打赏已在外部浏览器打开https://www.zidianidc.com/shop/Z2EWXNI6，并核对浏览器完整地址。临时停用浏览器后点击打赏，提示“未找到可用浏览器，打赏链接已复制”；在未保存的模块输入框粘贴验证完整URL，随后取消。浏览器原启用状态在finally中恢复并再次逐项校验。

最终已清除临时关键词测试设置，红包／转账原配置精确恢复；测试机VIP升级和用户要求的充电亮屏值7保持不变。所有截图、真实授权、设备标识、原始日志和私有配置均不包含在源码包内。
