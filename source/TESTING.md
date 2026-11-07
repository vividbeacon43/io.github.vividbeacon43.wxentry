# v230 验证

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
- True
- PASS 89 premium entitlement and exclusive mode checks
- PASS 96 red packet state/ownership/template checks
- PASS 35 native red packet callback/submission checks
- PASS 78 red packet settings/persistence/reply-flow checks
- PASS 37 receipt list/seconds/migration checks
- PASS 42 transfer parser/native/receipt-flow checks
- PASS 41 native voice menu/file/task/result contracts
- PASS 39 native red packet request/result/text contracts
- PASS 44 native transfer and manual red packet UI contracts
- PASS 50 native recall allocator and inline row/text/identity contracts
- PASS 168 framework DEX ordering/search/body/immutability checks
- PASS 81 final DEX enum reflection contracts
- PASS 6 signed loader upgrade/rollback checks

最终压缩类库以隔离类加载器执行，再将同一类库转为发布 DEX，逐一检查枚举反射方法。

未执行手机上的真实消息收发、收款或完整系统组合验收。
