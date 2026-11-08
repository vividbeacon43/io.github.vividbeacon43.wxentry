# v252 验证

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
- PASS 48 local Moments codec, immutable edits, identity and bounded retention checks
- PASS 43 local Moments account isolation, paid grants, atomic failure, corrupt recovery and size cleanup checks
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
- PASS 20 native local Moments post binding, view pooling, avatar and profile contracts
- PASS 168 framework DEX ordering/search/body/immutability checks
- PASS 81 final DEX enum reflection contracts
- PASS 6 signed loader upgrade/rollback checks
- PASS 8 APK manifest protocol and delivery checks

手机：原微信8.0.80（3220）、Android14，现有已验证高级版账号；最终安装APK2.11和动态内核v252，核对设备实际APK及内核缓存字节与签名构建报告一致。

实际完成：

- 两个新开关默认关闭、右侧小滑块显示正常，独立启停并重启保留。
- 长按朋友圈文案出现伪集赞、伪评论与既有一键转发，原生文本浮动菜单和列表菜单均可操作。
- 多选两名好友生成本地点赞，真实头像加载，点击头像打开对应好友资料。
- 通过菜单保存临时评论预设；批量选择两位好友并逐人选择预设、保存两条评论。
- 评论头像及姓名点击打开相应好友原生资料页；长按姓名仅编辑，不再同时误开资料。
- 修改保存单条评论、删除另一条评论，点赞和其余评论保持；重启后剩余记录仍保留。
- 在已有原生文字、表情、图片评论的朋友圈添加本地点赞和评论，原生评论保留；单条清理后只有本地生成的部分消失。
- 滚动到其他朋友圈未出现测试记录串位；重新进入仍定位原目标。关闭伪集赞后仅保留伪评论显示。
- 使用“清空本地互动记录”清理全部测试记录，预设编辑框删除临时预设，两个开关恢复关闭；既有转发和去广告开关保持开启。最终文件和菜单状态由私有验收脚本复核。

未提交真实点赞、评论、朋友圈或联系人消息，未操作真实收款，也未切换或解绑测试账号；免费/VIP权限、失效授权、账号隔离及异常文件等边界用行为/DEX测试验证，不声称在手机上切换了真实账号或付费等级。未实际验收所有微信版本、全部点赞组合、深色模式、大字体以及最大数量展开。头像和原生资料路由随当前微信版本适配。测试日志、联系人和截图不随源码打包。
