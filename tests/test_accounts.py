"""Hosted-account boundaries and provider contracts; no live keys or Google accounts."""
import base64
import http.client
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
import uuid
import wave
from unittest.mock import patch
from server.accounts import ADMIN_EMAIL, Accounts
from server.access import scope_body
from server.kitty import Brain, Server, GenerationControl, initialize
from server.providers import ProviderError, gemini_speech, groq_stream

class AccountTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.home=Path(self.temp.name)
        self.env=patch.dict(os.environ,{'GOOGLE_WEB_CLIENT_ID':'test.apps.googleusercontent.com','KITTY_PUBLIC_URL':'https://kitty.example.test'},clear=True);self.env.start()
        initialize(self.home);self.brain=Brain(self.home);self.a=self.brain.accounts
        self.admin=self.a.session_for({'sub':'admin-google-sub','email':ADMIN_EMAIL,'name':'Virat'})
        self.user=self.a.session_for({'sub':'first-user','email':'first@example.test'})
        self.other=self.a.session_for({'sub':'second-user','email':'second@example.test'})
        self.server=Server(('127.0.0.1',0),self.brain);self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.env.stop();self.temp.cleanup()
    def request(self,path,body=None,account=None,token=None):
        c=http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=5)
        headers={'Authorization':'Bearer '+(token or (account or self.user)['token'])}
        if body is not None:headers['Content-Type']='application/json'
        c.request('GET' if body is None else 'POST',path,None if body is None else json.dumps(body),headers)
        r=c.getresponse();result=r.status,json.loads(r.read());c.close();return result
    def test_verified_nonce_consumed_and_cannot_replay(self):
        challenge=self.a.challenge()
        claims={'sub':'verified','email':'verified@example.test','nonce':challenge['nonce']}
        with patch.object(self.a,'verify_google',return_value=claims):
            logged=self.a.login({'challenge':challenge['challenge'],'id_token':'google-token'})
            self.assertEqual(self.a.authenticate(logged['token']),'user_'+logged['account']['id'])
            with self.assertRaises(ValueError):self.a.login({'challenge':challenge['challenge'],'id_token':'google-token'})
        self.assertNotIn(logged['token'],(self.home/'accounts.sqlite3').read_bytes().decode(errors='ignore'))
    def test_nonce_mismatch_cannot_login(self):
        challenge=self.a.challenge()
        with patch.object(self.a,'verify_google',return_value={'sub':'bad','nonce':'wrong'}):
            with self.assertRaisesRegex(ValueError,'nonce'):self.a.login({'challenge':challenge['challenge'],'id_token':'token'})
    def test_role_spoof_and_owner_pairing_cannot_reach_admin(self):
        self.assertEqual(self.request('/v1/admin/settings',{'role':'admin','email':ADMIN_EMAIL,'groq_key':'fake'})[0],403)
        self.assertEqual(self.request('/v1/admin/settings',token=self.brain.config['token'])[0],401)
        self.assertFalse(self.a.settings()['groq_configured'])
    def test_admin_keys_encrypted_and_masked(self):
        status,result=self.request('/v1/admin/settings',{'groq_key':'private-test-key','gemini_key':'private-speech-key'},self.admin)
        self.assertEqual(status,200);self.assertTrue(result['groq_configured']);self.assertNotIn('private-test',json.dumps(result))
        self.assertNotIn(b'private-test-key',(self.home/'admin.sealed').read_bytes())
        self.assertEqual(self.request('/v1/status')[1]['provider'],'groq')
    def test_admin_subject_is_pinned(self):
        with self.assertRaises(ValueError):self.a.session_for({'sub':'different-sub','email':ADMIN_EMAIL})
    def test_account_id_stable_across_token_rotation(self):
        again=self.a.session_for({'sub':'first-user','email':'first@example.test'})
        self.assertEqual(again['account']['id'],self.user['account']['id']);self.assertNotEqual(again['token'],self.user['token'])
    def test_account_scoped_memory_idempotent_and_transactional(self):
        row={'id':'1234abcd','text':'I like tea','deleted':False}
        for _ in range(2):self.assertEqual(self.request('/v1/memories',{'changes':[row]})[0],200)
        self.assertEqual(len(self.request('/v1/memories')[1]['memories']),1)
        self.assertEqual(self.request('/v1/memories',account=self.other)[1]['memories'],[])
        self.request('/v1/memories',{'changes':[{**row,'deleted':True}]},self.other)
        self.assertEqual(len(self.request('/v1/memories')[1]['memories']),1)
        self.assertEqual(self.request('/v1/memories',{'changes':[{**row,'id':'deadbeef'},{'id':'invalid'}]})[0],400)
        self.assertEqual(len(self.request('/v1/memories')[1]['memories']),1)
    def test_invalid_consent_and_default_private(self):
        self.assertFalse(self.request('/v1/me')[1]['backup'])
        self.assertEqual(self.request('/v1/consent',{'backup':False,'training':True})[0],400)
        self.assertEqual(self.request('/v1/consent',{'backup':'true','training':False})[0],400)
    def test_drive_revocation_queues_deletion_without_consent_race(self):
        with patch.object(self.a,'drive_write',return_value=''):
            self.request('/v1/consent',{'backup':True,'training':True})
            _,me=self.request('/v1/consent',{'backup':False,'training':False})
            self.assertFalse(me['training']);self.assertIn(me['drive']['state'],('pending','deleted'))
    def test_notice_admin_only_and_shared_without_keys(self):
        self.assertEqual(self.request('/v1/admin/notice',{'title':'hi','body':'update'})[0],403)
        self.assertEqual(self.request('/v1/admin/notice',{'title':'New version','body':'Check Settings'},self.admin)[0],200)
        self.assertEqual(self.request('/v1/notices',account=self.other)[1]['notices'][0]['title'],'New version')
    def test_logout_invalidates_session(self):
        self.assertEqual(self.request('/v1/logout',{})[0],200);self.assertEqual(self.request('/v1/me')[0],401)
    def test_model_context_uses_only_current_account(self):
        self.a.remember(self.user['account']['id'],'FIRST-SECRET');self.a.remember(self.other['account']['id'],'SECOND-SECRET');self.a.vault.update({'groq_key':'test-key'})
        scoped=scope_body('user_'+self.user['account']['id'],{'request_id':str(uuid.uuid4()),'session':'same','text':'Say hello'})
        captured=[]
        def stream(url,payload,control,timeout,headers):
            captured.append(payload);yield {'choices':[{'delta':{'content':'Hello, Sir.'}}]}
        with patch('server.kitty.remote_stream',side_effect=stream):result=self.brain.chat(scoped)
        self.assertEqual(result['mode'],'model');self.assertIn('FIRST-SECRET',json.dumps(captured));self.assertNotIn('SECOND-SECRET',json.dumps(captured))
    def test_identity_does_not_require_provider_call(self):
        _,reply=self.request('/v1/chat',{'text':'who created you','request_id':str(uuid.uuid4()),'session':'same'})
        self.assertIn('made by Virat with the help of Kitty Corp',reply['reply']);self.assertEqual(reply['mode'],'identity')
    def test_drive_requires_oauth_secret_not_api_key(self):
        self.assertEqual(self.request('/v1/admin/drive',{},self.admin)[0],400)
    def test_training_export_only_reviewed_and_consented(self):
        for account in (self.user,self.other):
            scoped=scope_body('user_'+account['account']['id'],{'request_id':str(uuid.uuid4()),'session':'same','text':'Question'})
            self.brain.store.save(scoped['request_id'],scoped['session'],'Question',{'reply':'APPROVED-'+account['account']['id'],'mode':'model'})
            self.brain.store.feedback(scoped['request_id'],1,'')
        self.a.consent(self.user['account']['id'],{'backup':True,'training':True})
        exported=json.dumps(self.a.export_training());self.assertIn(self.user['account']['id'],exported);self.assertNotIn(self.other['account']['id'],exported)

class ProviderTests(unittest.TestCase):
    def test_groq_sse_usage_and_streaming(self):
        events=[]
        def fixture(url,payload,control,timeout,headers):
            self.assertEqual(url,'https://api.groq.com/openai/v1/chat/completions');self.assertTrue(payload['stream']);self.assertEqual(headers['Authorization'],'Bearer secret')
            yield {'choices':[{'delta':{'content':'Hello'}}]};yield {'choices':[],'usage':{'completion_tokens':1}}
        answer,usage=groq_stream('secret','test-model',[],GenerationControl(),lambda k,d:events.append(d),fixture)
        self.assertEqual(answer,'Hello');self.assertEqual(usage['completion_tokens'],1);self.assertEqual(events,[{'text':'Hello'}])
    def test_gemini_rest_audio_and_payload(self):
        out=io.BytesIO()
        with wave.open(out,'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(24000);w.writeframes(b'\0\0'*240)
        raw=out.getvalue()
        def fixture(url,payload,headers,**kwargs):
            self.assertFalse(payload['store']);self.assertEqual(payload['generation_config']['speech_config'],[{'voice':'Kore'}]);self.assertEqual(headers,{'x-goog-api-key':'secret'})
            return {'steps':[{'type':'model_output','content':[{'type':'audio','mime_type':'audio/wav','data':base64.b64encode(raw).decode()}]}]}
        with patch('server.providers.json_api',side_effect=fixture):self.assertEqual(gemini_speech('secret','test-tts','Kore','Hello'),raw)
    def test_speech_input_and_malformed_output_bounded(self):
        with self.assertRaises(ValueError):gemini_speech('secret','tts','Kore','x'*901)
        with patch('server.providers.json_api',return_value={'steps':[]}):
            with self.assertRaises(ProviderError):gemini_speech('secret','tts','Kore','Hello')
