import http.client
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import threading
import unittest
import uuid
from unittest.mock import patch

from server.access import Access, load_env, scope_body, scope_id
from server.cloud_sync import CloudSync, SCHEMA, enqueue
from server.kitty import Brain, Server, initialize
from server.web_search import research


class AccessTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.home=Path(self.temp.name);initialize(self.home)
        self.brain=Brain(self.home)
        self.guest,self.token=self.brain.access.invite("Presentation",1)
        self.server=Server(("127.0.0.1",0),self.brain)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def request(self,path,body,token=None):
        c=http.client.HTTPConnection("127.0.0.1",self.server.server_port,timeout=5)
        c.request("POST",path,json.dumps(body),{"Content-Type":"application/json","Authorization":"Bearer "+(token or self.token)})
        r=c.getresponse();value=(r.status,json.loads(r.read()));c.close();return value
    def test_guest_token_hashed_and_revocable(self):
        self.assertNotIn(self.token,(self.home/"guests.json").read_text())
        self.assertEqual(self.brain.access.authenticate(self.token,"owner"),self.guest["id"])
        self.brain.access.revoke(self.guest["id"])
        status,_=self.request("/v1/chat",{})
        self.assertEqual(status,401)
    def test_guests_cannot_read_owner_memories_or_modify_them(self):
        self.brain.store.remember("PRIVATE-OWNER-MEMORY")
        for command in ("show memories","remember another","forget memory 1"):
            status,result=self.request("/v1/chat",{"request_id":str(uuid.uuid4()),"text":command})
            self.assertEqual(status,200);self.assertEqual(result["mode"],"guest")
            self.assertNotIn("PRIVATE-OWNER-MEMORY",result["reply"])
        self.assertEqual(len(self.brain.store.memories()),1)
    def test_guests_cannot_replay_or_rate_owner_feedback(self):
        key=str(uuid.uuid4())
        self.brain.chat({"text":"introduce yourself","request_id":key})
        status,_=self.request("/v1/feedback",{"response_id":key,"rating":1})
        self.assertEqual(status,400)
        status,result=self.request("/v1/chat",{"request_id":key,"text":"show memories"})
        self.assertEqual(result["mode"],"guest");self.assertEqual(result["response_id"],key)
        self.assertIsNotNone(self.brain.store.cached(scope_id(self.guest["id"],key)))
    def test_guest_model_prompt_excludes_private_documents_and_persona(self):
        self.brain.store.remember("OWNER-SECRET")
        (self.home/"personality.txt").write_text("CUSTOM-PRIVATE-PERSONA")
        captured=[]
        def model(url,payload,**kw):
            if url.endswith("apply-template"):return {"prompt":"short"}
            if url.endswith("tokenize"):return {"tokens":[1]}
            captured.append(payload);return {"choices":[{"message":{"content":"Hello"}}]}
        with patch("server.kitty.remote_json",side_effect=model):
            self.request("/v1/chat",{"request_id":str(uuid.uuid4()),"text":"Tell me a story","session":"default"})
        self.assertEqual(len(captured),1)
        self.assertNotIn("OWNER-SECRET",json.dumps(captured));self.assertNotIn("CUSTOM-PRIVATE",json.dumps(captured))
    def test_guest_events_and_feedback_use_same_namespace(self):
        key=str(uuid.uuid4());event={"id":key,"session":"a","input":"battery","reply":"90 percent"}
        status,result=self.request("/v1/events",{"events":[event]})
        self.assertEqual((status,result["accepted"]),(200,[key]))
        status,_=self.request("/v1/feedback",{"response_id":key,"rating":1})
        self.assertEqual(status,200);self.assertIsNone(self.brain.store.cached(key))
    def test_rate_limit(self):
        import time
        self.server.rates[self.guest["id"]]=(time.monotonic(),30)
        self.assertEqual(self.request("/v1/chat",{})[0],429)


class OutboxTests(unittest.TestCase):
    def test_retry_and_replay_do_not_lose_writes_or_duplicate_rows(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/"local.db"
            with sqlite3.connect(path) as db:
                db.execute("CREATE TABLE _cloud_outbox(id INTEGER PRIMARY KEY,sql TEXT,args TEXT)")
                enqueue(db,"INSERT OR REPLACE INTO memories VALUES(?,?,?)",(42,"saved",1))
            remote=sqlite3.connect(":memory:");remote.executescript(SCHEMA)
            class Replica:
                execute=remote.execute
                commit=remote.commit
                fail=True
                def push(self):
                    if self.fail:raise OSError("offline")
            replica=Replica();sync=CloudSync(path,"https://example.invalid","test")
            with self.assertRaises(OSError):sync.flush(replica)
            self.assertEqual(sync.status()["pending"],1)
            replica.fail=False
            self.assertEqual(sync.flush(replica),1)
            self.assertEqual(sync.status()["pending"],0)
            self.assertEqual(remote.execute("SELECT COUNT(*) FROM memories").fetchone()[0],1)
            remote.close()
    def test_local_writes_are_committed_with_outbox_without_network(self):
        from server.kitty import Store
        with tempfile.TemporaryDirectory() as d:
            store=Store(Path(d)/"local.db");store.turso=True
            item=store.remember("Cloud later")
            store.forget(item)
            with store.db() as db:self.assertEqual(db.execute("SELECT COUNT(*) FROM _cloud_outbox").fetchone()[0],2)
            self.assertEqual(store.memories(),[])


class WebTests(unittest.TestCase):
    def test_missing_key_is_not_a_hallucinated_web_answer(self):
        with patch.dict(os.environ,{"BRAVE_SEARCH_API_KEY":""}):
            self.assertEqual(research("news")["mode"],"web_unavailable")
    def test_search_failure_does_not_leak_key(self):
        with patch.dict(os.environ,{"BRAVE_SEARCH_API_KEY":"SECRET-KEY"}),patch("server.web_search.urlopen",side_effect=OSError("SECRET-KEY")):
            self.assertNotIn("SECRET-KEY",json.dumps(research("news")))
    def test_search_cleans_html_and_filters_unsafe_links(self):
        from io import BytesIO
        payload={"web":{"results":[{"title":"A &amp; B","description":"<b>Example</b>","url":"https://example.org/article"},{"url":"javascript:bad()"}]}}
        with patch.dict(os.environ,{"BRAVE_SEARCH_API_KEY":"test"}),patch("server.web_search.urlopen",return_value=BytesIO(json.dumps(payload).encode())):
            result=research("question")
        self.assertEqual(result["mode"],"web");self.assertEqual(len(result["sources"]),1)
        self.assertIn("A & B",result["reply"]);self.assertNotIn("<b>",result["reply"])
    def test_env_file_is_data_not_executable(self):
        with tempfile.TemporaryDirectory() as d:
            file=Path(d)/"secrets.env";file.write_text("NOT_ALLOWED=1")
            with self.assertRaises(ValueError):load_env(file)


if __name__=="__main__":unittest.main()
