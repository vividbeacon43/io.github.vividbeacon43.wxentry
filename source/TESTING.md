# v246 验证

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
- PASS 108 red packet state/ownership/template checks
- PASS 49 native red packet callback/submission checks
- PASS 102 red packet settings/persistence/reply-flow checks
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
- PASS 168 framework DEX ordering/search/body/immutability checks
- PASS 81 final DEX enum reflection contracts
- PASS 6 signed loader upgrade/rollback checks
- PASS 8 APK manifest protocol and delivery checks (release and rollback)

本次以自动化行为测试及原微信 APK 接口检查验证朋友圈长按、原生完整素材下载、仅打开发表页、双红包页面支持及既有媒体规则。具体手机验证项目见 validation.json，不把静态检查计作完整手机验收。

手机验证摘要见 validation.json。现场截图、二维码标识和密钥不分发。本次在授权测试机验证朋友圈菜单、转发开关与广告过滤；具体项目见手机验证记录。未发表测试朋友圈，测试草稿已丢弃，未覆盖所有系统组合。
