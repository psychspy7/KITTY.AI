"""Cloud auth boundaries with Firebase verifier fixtures, no live credentials."""
import json
import http.client
import threading
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import Mock, patch
from server.accounts import Accounts, ADMIN_EMAIL, CHARACTER, CREATOR
from server.kitty import Brain, Server, initialize, GenerationControl
from server.access import scope_body
from server.hosted import validate_environment
import uuid


class FirebaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.home=Path(self.tmp.name)
        self.env=patch.dict(os.environ,{'FIREBASE_PROJECT_ID':'kitty-test','KITTY_PUBLIC_URL':'https://kitty.example.test'},clear=True);self.env.start()
        self.sdk=patch.object(Accounts,'init_firebase',return_value=object());self.factory=self.sdk.start()
        initialize(self.home);self.brain=Brain(self.home);self.a=self.brain.accounts
    def tearDown(self):
        self.a.close();self.sdk.stop();self.env.stop();self.tmp.cleanup()
    def claims(self,uid='user-a',email='user@example.test',provider='google.com',verified=True):
        return {'sub':uid,'email':email,'email_verified':verified,'firebase':{'sign_in_provider':provider,'identities':{'google.com':['google-'+uid]}}}
    def login(self,claims):
        with patch.object(self.a,'verify_firebase',return_value=claims):return self.a.authenticate('firebase-sdk-id-token')
    def test_sdk_checks_revocation_and_uses_fixed_project_app(self):
        self.factory.assert_called_once_with('kitty-test')
        verify=Mock(return_value=self.claims())
        with patch.dict('sys.modules',{'firebase_admin':types.SimpleNamespace(auth=types.SimpleNamespace(verify_id_token=verify))}):
            self.assertEqual(self.a.verify_firebase('signed-token')['sub'],'user-a')
        verify.assert_called_once_with('signed-token',app=self.a.firebase_app,check_revoked=True)
    def test_wrong_provider_and_unverified_email_rejected(self):
        for claims in [self.claims(provider='password'),self.claims(verified=False),self.claims(uid='')]:
            verify=Mock(return_value=claims)
            with patch.dict('sys.modules',{'firebase_admin':types.SimpleNamespace(auth=types.SimpleNamespace(verify_id_token=verify))}):
                with self.assertRaises(ValueError):self.a.verify_firebase('token')
    def test_invalid_expired_revoked_or_wrong_project_tokens_cannot_authenticate(self):
        for reason in ('bad signature','expired','revoked','wrong audience'):
            with patch.object(self.a,'verify_firebase',side_effect=ValueError(reason)):
                self.assertIsNone(self.a.authenticate('bad-token'))
        self.assertIsNone(self.a.authenticate(''))
        self.assertIsNone(self.a.authenticate('x'*12001))
    def test_legacy_tokens_cannot_bypass_firebase(self):
        with self.assertRaises(ValueError):self.a.session_for({'sub':'old','email':ADMIN_EMAIL})
        with patch.object(self.a,'verify_firebase',side_effect=ValueError):
            self.assertIsNone(self.a.authenticate(self.brain.config['token']))
    def test_only_verified_owner_gets_admin_and_uid_is_pinned(self):
        admin=self.login(self.claims('virat',ADMIN_EMAIL));user=self.login(self.claims())
        self.assertTrue(self.a.admin(admin));self.assertFalse(self.a.admin(user))
        self.assertEqual(self.login(self.claims('impostor',ADMIN_EMAIL)),None)
        self.assertEqual(self.a.vault.get()['admin_google_sub'],'google-virat')
        with self.assertRaises(ValueError):self.a.configure(user,{'character':'changed','creator':'impostor','groq_key':'secret'})
        self.assertEqual(self.a.settings()['creator'],CREATOR)
    def test_core_identity_admin_control_and_personal_memory_isolation(self):
        admin=self.login(self.claims('virat',ADMIN_EMAIL));user=self.login(self.claims());other=self.login(self.claims('user-b','b@example.test'))
        self.a.configure(admin,{'character':CHARACTER,'creator':CREATOR})
        self.a.remember(user[5:],'You were made by someone else')
        self.assertEqual(self.a.memories(other[5:]),[])
        body=scope_body(user,{'session':'chat','request_id':str(uuid.uuid4()),'text':'who made you'})
        result=self.brain.chat(body)
        self.assertIn('made by Virat with the help of Kitty Corp',result['reply'])
        for forbidden in ('Groq','Qwen','phone commands'):self.assertNotIn(forbidden,result['reply'])
        self.a.configure(admin,{'creator':'KITTY Studio'})
        self.assertIn('made by KITTY Studio',self.a.respond('who made you',body['session'],None,GenerationControl(),None)['reply'])
    def test_sessions_stable_across_new_firebase_id_tokens(self):
        first=self.login(self.claims());second=self.login(self.claims())
        self.assertEqual(first,second)
        with self.a.db() as c:self.assertEqual(c.execute('SELECT count(*) FROM sessions').fetchone()[0],0)
    def test_keys_never_returned_in_settings(self):
        admin=self.login(self.claims('virat',ADMIN_EMAIL));self.a.configure(admin,{'groq_key':'KEY-PRIVATE','gemini_key':'SPEECH-PRIVATE'})
        self.assertNotIn('PRIVATE',json.dumps(self.a.settings()))
        self.assertNotIn(b'KEY-PRIVATE',(self.home/'admin.sealed').read_bytes())
    def test_http_admin_routes_reject_role_spoofing_and_legacy_login(self):
        server=Server(('127.0.0.1',0),self.brain)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        def verify(token):
            if token=='admin':return self.claims('virat',ADMIN_EMAIL)
            if token=='user':return self.claims()
            raise ValueError('Invalid Firebase token')
        def request(path,token,body=None):
            c=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=5)
            headers={'Authorization':'Bearer '+token}
            if body is not None:headers['Content-Type']='application/json'
            c.request('POST' if body is not None else 'GET',path,None if body is None else json.dumps(body),headers)
            r=c.getresponse();result=r.status,json.loads(r.read());c.close();return result
        try:
            with patch.object(self.a,'verify_firebase',side_effect=verify):
                self.assertEqual(request('/v1/admin/settings','user',{'role':'admin','email':ADMIN_EMAIL,'groq_key':'stolen'})[0],403)
                status,settings=request('/v1/admin/settings','admin',{'groq_key':'server-only'})
                self.assertEqual(status,200);self.assertTrue(settings['groq_configured']);self.assertNotIn('server-only',json.dumps(settings))
                self.assertEqual(request('/v1/me','invalid')[0],401)
                self.assertEqual(request('/v1/me','user')[1]['role'],'user')
                self.assertEqual(request('/v1/auth/google','user',{})[0],410)
        finally:server.shutdown();server.server_close();thread.join()

    def test_hosted_entry_requires_config_and_rejects_emulator_tokens(self):
        validate_environment()
        with patch.dict(os.environ,{'FIREBASE_PROJECT_ID':''}):
            with self.assertRaises(ValueError):validate_environment()
        with patch.dict(os.environ,{'FIREBASE_AUTH_EMULATOR_HOST':'localhost:9099'}):
            with self.assertRaises(ValueError):validate_environment()


if __name__=='__main__':unittest.main()
