# v250 验证

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
- PASS 168 framework DEX ordering/search/body/immutability checks
- PASS 81 final DEX enum reflection contracts
- PASS 6 signed loader upgrade/rollback checks
- PASS 8 APK manifest protocol and delivery checks

本版测试实际原生入口适配类、完整消息与皮肤模型传递、会话和消息参数、晚加载上下文恢复、延时交接及无可点击按钮时的超时诊断。原微信APK检查确认钱包入口、与聊天点击相同的新旧页面选择条件及Application路由的NEW_TASK处理。

手机测试范围见validation.json；未把原生接口静态检查或桩测试表述为实际免费红包到账验收。测试机此前锁屏，本次只记录真实可完成的安装、最终字节和启动兼容检查。没有为本次验收收取红包、转账或向联系人发送消息。用户13:43上传报告仅用于定位，不随源码分发。
