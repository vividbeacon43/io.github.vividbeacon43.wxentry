# v254 验证

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
- PASS 41 native voice menu/file/task/result contracts
- PASS 44 native red packet request/result/text contracts
- PASS 50 native transfer and manual red packet UI contracts
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

## 测试机实测

微信8.0.80（3220）、Android14、高级版，最终APK2.13及v254签名内核均逐字节校验一致。

- 浅色和深色模式显示微信原生心形、逗号分隔的可换行好友姓名，点击本地姓名进入正确资料页。
- 全选、保存、展开完整名单、资料页跳转已操作。早期候选全选40项时暴露系统服务联系人，修正后可选池为30位真实联系人；最终包全选显示30/30。
- 随机4人保存后显示4位，安装最终包并重启后重新打开仍预选4位。随机输入0时显示1到30的范围错误；取消随机选择后全选30位保持不变。
- 最终包深色好友选择器、随机人数和清理确认框的操作文字对比度正常。
- 同一条朋友圈有2条原生真实点赞时，本地追加1位合并展示，原有真实姓名链接仍能打开正确资料页；清除本条本地互动后恢复原有2条真实点赞。
- 最后通过模块菜单清空全部测试互动，关闭伪集赞、保持伪评论关闭。私有文件精确为空记录格式；原有转发、去广告开关仍开启。
- 深色模式为本次临时测试，系统恢复原浅色设置。按用户明确要求保留充电亮屏（全局值7），不恢复原0值。

本次未发送真实点赞、评论、朋友圈或好友消息。未重复进行v252已完成的真实图片/表情评论和伪评论编辑全流程；本次对应路径通过自动检查。5000人全选、随机和持久化为自动检查，手机实际好友池为30人。未重新测试真实转账/红包到账或朋友圈发表页准备。
