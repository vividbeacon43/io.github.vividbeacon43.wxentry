# v238 验证

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
- True
- PASS 89 premium entitlement and exclusive mode checks
- PASS 108 red packet state/ownership/template checks
- PASS 49 native red packet callback/submission checks
- PASS 89 red packet settings/persistence/reply-flow checks
- PASS 37 receipt list/seconds/migration checks
- PASS 42 transfer parser/native/receipt-flow checks
- PASS 41 native voice menu/file/task/result contracts
- PASS 44 native red packet request/result/text contracts
- PASS 44 native transfer and manual red packet UI contracts
- PASS 50 native recall allocator and inline row/text/identity contracts
- PASS 23 native tablet/login/QR contracts
- PASS 168 framework DEX ordering/search/body/immutability checks
- PASS 81 final DEX enum reflection contracts
- PASS 6 signed loader upgrade/rollback checks

最终签名 APK 已在未激活分身上验证旧开关无法启用平板模式，原微信进程保持不变。高级/VIP 以签名授权测试，本版没有执行付费手机二维码或扫码确认登录验收。

手机验证摘要见 validation.json。现场截图、二维码标识和密钥不分发。未执行真实消息收发、收款及完整系统组合验收。
