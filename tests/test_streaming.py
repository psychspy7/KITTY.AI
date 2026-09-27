"""Real HTTP streaming contract, cancellation and archive behaviour."""
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
import uuid
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'server'))
from kitty import Brain, Server, initialize

class StreamingTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();home=Path(self.tmp.name);initialize(home)
        self.brain=Brain(home);self.release=threading.Event();self.first=threading.Event()
        owner=self
        class Model(BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def do_POST(self):
                body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                if self.path=='/apply-template':out={'prompt':'short'}
                elif self.path=='/tokenize':out={'tokens':[1,2,3]}
                else:
                    owner.payload=body;self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
                    try:
                        self.wfile.write(b'data: {"choices":[{"delta":{"content":"Sir, hello. "}}]}\n\n');self.wfile.flush();owner.first.set()
                        owner.release.wait(5)
                        self.wfile.write(b'data: {"choices":[{"delta":{"content":"Done."},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n');self.wfile.flush()
                    except OSError:pass
                    return
                raw=json.dumps(out).encode();self.send_response(200);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
        self.model=ThreadingHTTPServer(('127.0.0.1',0),Model)
        self.brain.config['model_base_url']=f'http://127.0.0.1:{self.model.server_port}/v1'
        self.gateway=Server(('127.0.0.1',0),self.brain)
        for server in (self.model,self.gateway):threading.Thread(target=server.serve_forever,daemon=True).start()
    def tearDown(self):
        self.release.set()
        for s in (self.gateway,self.model):s.shutdown();s.server_close()
        self.tmp.cleanup()
    def start(self,text='Tell me something'):
        key=str(uuid.uuid4());c=http.client.HTTPConnection('127.0.0.1',self.gateway.server_port,timeout=5)
        c.request('POST','/v1/chat/stream',json.dumps({'text':text,'request_id':key,'session':'test'}),{'Content-Type':'application/json','Authorization':'Bearer '+self.brain.config['token']})
        r=c.getresponse();self.assertEqual(r.status,200);return key,c,r
    def event(self,r,wanted):
        while True:
            line=r.readline()
            if not line:raise AssertionError('SSE ended before '+wanted)
            if line.strip()==('event: '+wanted).encode():return json.loads(r.readline()[5:])
    def test_first_token_arrives_before_model_finishes(self):
        key,c,r=self.start()
        try:
            first=self.event(r,'token');self.assertEqual(first['text'],'Sir, hello. ');self.assertFalse(self.release.is_set());self.assertTrue(self.payload['stream'])
            self.release.set();done=self.event(r,'done');self.assertEqual(done['reply'],'Sir, hello. Done.');self.assertEqual(done['response_id'],key)
            self.assertIn('first_token_ms',done);self.assertEqual(json.loads(self.brain.store.cached(key)['result'])['mode'],'model')
        finally:c.close()
    def test_cancel_unblocks_model_without_waiting_for_next_token(self):
        key,c,r=self.start();self.event(r,'token')
        began=time.monotonic();self.assertTrue(self.brain.cancel(key))
        while time.monotonic()-began<2 and not self.brain.store.cached(key):time.sleep(.02)
        try:
            self.assertIsNotNone(self.brain.store.cached(key));self.assertLess(time.monotonic()-began,2)
            self.assertEqual(json.loads(self.brain.store.cached(key)['result'])['mode'],'cancelled')
            self.assertTrue(self.brain.model_gate.acquire(blocking=False));self.brain.model_gate.release()
        finally:c.close()
    def test_identity_never_calls_model(self):
        with patch('kitty.remote_json',side_effect=AssertionError('model should not run')):
            key,c,r=self.start('Introduce yourself')
            try:done=self.event(r,'done');self.assertEqual(done['mode'],'identity');self.assertIn('Virat',done['reply'])
            finally:c.close()
    def test_archive_is_kept_and_events_are_idempotent(self):
        key=str(uuid.uuid4());event={'id':key,'session':'test','input':'battery','reply':'Sir, 90 percent','source':'voice'}
        self.brain.store.import_events([event]);self.brain.store.import_events([event])
        with self.brain.store.db() as db:db.execute('UPDATE turns SET created=0')
        self.brain.store.purge(0)
        self.assertIsNotNone(self.brain.store.cached(key));self.assertEqual(self.brain.store.history('test'),[])
        with self.brain.store.db() as db:self.assertEqual(db.execute('SELECT COUNT(*) FROM turns').fetchone()[0],1)
    def test_disconnect_cancels_generation(self):
        key,c,r=self.start();self.event(r,'token');r.close();c.close()
        deadline=time.monotonic()+4
        while time.monotonic()<deadline and not self.brain.store.cached(key):time.sleep(.04)
        self.assertIsNotNone(self.brain.store.cached(key))
        self.assertEqual(json.loads(self.brain.store.cached(key)['result'])['mode'],'cancelled')

if __name__=='__main__':unittest.main()
