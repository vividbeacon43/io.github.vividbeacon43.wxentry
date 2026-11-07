"""Isolated production-handler tests; synthetic accounts and freshly generated RSA key only."""
import unittest,tempfile,pathlib,importlib.util,json,sys,concurrent.futures,threading
from unittest.mock import patch
sys.path.insert(0,r'C:\Users\Q\Documents\Codex\2026-11-03\new-chat\work\tools\ssh-python')
from cryptography.hazmat.primitives.asymmetric import rsa,padding
from cryptography.hazmat.primitives import serialization,hashes
SOURCE=pathlib.Path(__file__).resolve().parents[1]/'server/wx-api.py'
class Activation(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory(prefix='wxentry-license-test-');self.addCleanup(self.temp.cleanup)
  spec=importlib.util.spec_from_file_location('isolated_wx_api',SOURCE);self.m=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.m)
  original=self.m.BASE;self.base=pathlib.Path(self.temp.name)
  for k,v in list(vars(self.m).items()):
   if isinstance(v,str) and (v==original or v.startswith(original+str(pathlib.os.sep))):setattr(self.m,k,str(self.base)+v[len(original):])
  pathlib.Path(self.m.PUB).write_bytes(self.key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
  self.m.sign=lambda data:self.key.sign(data,padding.PKCS1v15(),hashes.SHA256())
  self.m.write_codes([]);self.write(self.m.BIND,{});self.write(self.m.CODE_TIER,{})
  self.write(self.m.PACKS,{'free':['basic'],'pro':['legacy-pro'],'plus':['advanced'],'vip':['vip']})
  self.write(self.m.AUTO_FREE_CFG,{'on':True,'daily':0,'ip_daily':0,'wh_daily':0,'tier':'vip'})
  self.h=object.__new__(self.m.Handler);self.h.client_address=('127.0.0.1',1000);self.h.headers={};self.h._json=lambda obj,status=200:(obj,status)
 def write(self,path,value):pathlib.Path(path).write_text(json.dumps(value),encoding='utf-8')
 def who(self,wxid):return self.h._whoami(json.dumps({'wxid':wxid}).encode())
 def activate(self,wxid,code):return self.h._activate(json.dumps({'wxid':wxid,'code':code}).encode())
 def code(self,tier):
  with self.m._pol_lock:
   code=self.m.admin_gen(1)[0];tiers=self.m._load_map(self.m.CODE_TIER);tiers[code]=tier;self.m._save_map(self.m.CODE_TIER,tiers);return code
 def test_new_account_restores_after_reinstall_and_policy_off(self):
  first,status=self.who('wxid_new');self.assertEqual(status,200);self.assertTrue(first['ok']);self.assertEqual(first['tier'],'free');code=first['code']
  self.assertEqual(self.m.token_ok(first['token'])['code'],code);self.assertEqual(self.m.load_bind()[code]['wxid'],'wxid_new')
  self.assertEqual(self.m._load_map(self.m.CODE_TIER)[code],'free')
  self.write(self.m.AUTO_FREE_CFG,{'on':False})
  again,status=self.who('wxid_new');self.assertEqual(status,200);self.assertEqual(again['code'],code);self.assertEqual(len(self.m.load_codes()),1)
  self.assertEqual(self.m.auto_free_state()['total'],1)
  other,status=self.who('wxid_other');self.assertFalse(other['ok']);self.assertEqual(len(self.m.load_codes()),1)
 def test_simultaneous_same_account_has_one_code(self):
  barrier=threading.Barrier(20)
  def request(_):barrier.wait();return self.m.auto_free_grant('wxid_parallel','127.0.0.1','')
  with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:results=list(pool.map(request,range(20)))
  self.assertEqual(len({c for c,rec in results}),1);self.assertEqual(len(self.m.load_codes()),1);self.assertEqual(len(self.m.load_bind()),1);self.assertEqual(self.m.auto_free_state()['total'],1)
 def test_parallel_admin_generation_and_new_accounts_preserve_codes(self):
  def request(i):
   if i%2:return self.m.auto_free_grant('wxid_parallel_'+str(i),'127.0.0.1','')[0]
   return self.code('plus')
  with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:codes=list(pool.map(request,range(24)))
  self.assertEqual(len(set(codes)),24);self.assertEqual(set(codes),self.m.load_codes());self.assertEqual(len(self.m.load_bind()),12)
 def test_paid_activation_then_recovery_uses_new_paid_code(self):
  first,_=self.who('wxid_upgrade');paid=self.code('plus');reply,status=self.activate('wxid_upgrade',paid)
  self.assertEqual(status,200);self.assertEqual(reply['code'],paid);self.assertEqual(reply['tier'],'plus')
  recovered,_=self.who('wxid_upgrade');self.assertEqual(recovered['code'],paid);self.assertEqual(self.m.token_ok(recovered['token'])['code'],paid)
  self.assertEqual(self.m.load_bind()[first['code']]['wxid'],'wxid_upgrade');self.assertIn('legacy-pro',recovered['feat']);self.assertIn('advanced',recovered['feat'])
 def test_latest_activation_even_same_second_and_reused_owned_code(self):
  a,b=self.code('plus'),self.code('vip')
  with patch.object(self.m.time,'time',return_value=2000000000):
   self.activate('wxid_sequence',a);self.activate('wxid_sequence',b);self.assertEqual(self.who('wxid_sequence')[0]['code'],b)
   self.activate('wxid_sequence',a);reply,_=self.who('wxid_sequence');self.assertEqual(reply['code'],a)
  self.assertEqual(reply['tier'],'vip');self.assertGreater(self.m.load_bind()[a]['active_seq'],self.m.load_bind()[b]['active_seq'])
 def test_legacy_paid_code_beats_later_free_record(self):
  free,paid=self.code('free'),self.code('plus');self.m.save_bind({paid:{'wxid':'wxid_legacy','ts':10},free:{'wxid':'wxid_legacy','ts':1000}})
  reply,_=self.who('wxid_legacy');self.assertEqual(reply['code'],paid);self.assertEqual(reply['tier'],'plus');self.assertEqual(len(self.m.load_codes()),2)
 def test_legacy_professional_joins_advanced_and_has_three_channels(self):
  code=self.code('pro');self.m.save_bind({code:{'wxid':'wxid_professional','ts':10}});reply,_=self.who('wxid_professional')
  self.assertEqual(reply['tier'],'plus');self.assertEqual(reply['kernel_edition'],'plus');self.assertEqual(reply['tier_name'],'高级版')
  self.assertEqual(self.m.KERNEL_CHANNELS,('free','plus','vip'));self.assertNotIn('value="pro"',self.m.ADMIN_HTML)
 def test_other_account_cannot_claim_bound_paid_code(self):
  code=self.code('vip');self.activate('wxid_owner',code);before=self.m.load_bind();reply,_=self.activate('wxid_thief',code)
  self.assertFalse(reply['ok']);self.assertEqual(before,self.m.load_bind())
 def test_disabled_user_and_invalid_identity_never_allocate(self):
  self.write(self.m.DISABLED,{'wxid_disabled':{'reason':'test'}});reply,_=self.who('wxid_disabled');self.assertTrue(reply['disabled'])
  for identity in ['', '../bad','a','wxid bad']:self.assertFalse(self.who(identity)[0]['ok'])
  self.assertEqual(len(self.m.load_codes()),0)
 def test_corrupt_binding_store_never_allocates_new_code(self):
  pathlib.Path(self.m.BIND).write_text('{broken',encoding='utf-8');reply,status=self.who('wxid_corrupt')
  self.assertEqual(status,503);self.assertFalse(reply['ok']);self.assertEqual(len(self.m.load_codes()),0)
 def test_free_code_input_keeps_existing_paid_primary(self):
  paid,unused=self.code('vip'),self.code('free');self.activate('wxid_paid',paid);reply,_=self.activate('wxid_paid',unused)
  self.assertEqual(reply['code'],paid);self.assertNotIn(unused,self.m.load_bind());self.assertEqual(self.m.token_ok(reply['token'])['code'],paid)
 def test_unlock_paid_marks_latest_and_returns_signed_license(self):
  free,_=self.who('wxid_unlock');paid=self.code('plus');reply,status=self.h._unlock(json.dumps({'token':free['token'],'code':paid}).encode())
  self.assertEqual(status,200);self.assertEqual(reply['code'],paid);self.assertEqual(self.m.token_ok(reply['token'])['wxid'],'wxid_unlock');self.assertEqual(self.who('wxid_unlock')[0]['code'],paid)
 def test_primary_sequence_survives_cache_loss_and_revocation(self):
  a,b=self.code('plus'),self.code('vip');self.activate('wxid_reload',a);self.activate('wxid_reload',b);self.m._tok={};self.m._codes_cache=(None,set())
  self.assertEqual(self.who('wxid_reload')[0]['code'],b);self.m.write_codes([a]);self.assertEqual(self.who('wxid_reload')[0]['code'],a)
 def test_free_recovery_and_auto_allocation_race_keeps_single_binding(self):
  code=self.code('free');barrier=threading.Barrier(2)
  def request(auto):
   barrier.wait()
   if auto:return self.m.auto_free_grant('wxid_race','127.0.0.1','')[0]
   return self.m.claim_license('wxid_race',code,False)[1]
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(request,[True,False]))
  self.assertEqual(len(set(results)),1);self.assertEqual(len([r for r in self.m.load_bind().values() if r['wxid']=='wxid_race']),1)
 def test_admin_only_three_editions_and_pack_migration(self):
  self.m.admin_authed=lambda handler:True
  reply,_=self.h._admin_api(json.dumps({'a':'gen','n':1,'tier':'pro'}).encode());self.assertFalse(reply['ok'])
  reply,_=self.h._admin_api(json.dumps({'a':'autofree','tier':'vip'}).encode());self.assertFalse(reply['ok'])
  reply,_=self.h._admin_api(b'{"a":"packs"}');self.assertEqual(reply['tiers'],['free','plus','vip']);self.assertNotIn('pro',reply['packs']);self.assertIn('legacy-pro',reply['packs']['plus'])
  self.h._admin_api(json.dumps({'a':'packs','set':True,'packs':reply['packs']}).encode());paid=self.code('plus');self.activate('wxid_pack_migrate',paid);self.assertIn('legacy-pro',self.who('wxid_pack_migrate')[0]['feat'])
if __name__=='__main__':unittest.main(verbosity=2)
