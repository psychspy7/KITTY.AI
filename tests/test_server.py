import concurrent.futures
import importlib.util
import json
import pathlib
import tempfile
import threading
import time
import unittest
import uuid
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, build_opener, ProxyHandler

ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("kitty",ROOT/"server/kitty.py")
kitty=importlib.util.module_from_spec(spec);spec.loader.exec_module(kitty)

def model_response(content):
    def respond(url,payload=None,**kw):
        if url.endswith("/apply-template"):return {"prompt":json.dumps(payload["messages"])}
        if url.endswith("/tokenize"):return {"tokens":[0]*max(1,len(payload["content"])//3)}
        return {"choices":[{"message":{"content":content}}]}
    return respond


class BrainTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.home=pathlib.Path(self.tmp.name)
        kitty.initialize(self.home);self.brain=kitty.Brain(self.home)
    def tearDown(self):self.tmp.cleanup()
    def ask(self,text,**kw):return self.brain.chat({"text":text,"request_id":str(uuid.uuid4()),**kw})
    def test_configuration_reuses_token(self):
        before=(self.home/"config.json").read_bytes();kitty.initialize(self.home)
        self.assertEqual(before,(self.home/"config.json").read_bytes())
        self.assertGreaterEqual(len(self.brain.config["token"]),40)
    def test_phone_commands_do_not_require_model(self):
        with patch.object(kitty,"remote_json",side_effect=AssertionError("network forbidden")):
            result=self.ask("Hey Kitty open YouTube play instrumental music")
            self.assertEqual(result["actions"][0]["target"],"instrumental music")
            self.assertEqual(result["mode"],"local")
    def test_recipient_and_message_are_separate(self):
        a=kitty.command("message Mom on WhatsApp saying Please call Dad")
        self.assertEqual(a,{"kind":"whatsapp","target":"Mom","text":"Please call Dad"})
    def test_no_action_for_explanations_or_quoted_intents(self):
        for value in ["How do I call Mom?",'Explain the command "open youtube"',"Someone told me to message Mom", "Tell me a joke"]:
            self.assertIsNone(kitty.command(value),value)
    def test_duplicate_request_does_not_duplicate_memory(self):
        request={"text":"remember that I like mountain trips","request_id":str(uuid.uuid4())}
        self.assertEqual(self.brain.chat(request),self.brain.chat(request))
        self.assertEqual(len(self.brain.store.memories()),1)
        self.assertEqual(len(self.brain.store.history("default")),2)
    def test_request_id_cannot_be_reused_for_different_payload(self):
        key=str(uuid.uuid4());self.ask("remember this",request_id=key)
        with self.assertRaises(ValueError):self.ask("remember another",request_id=key)
    def test_forget_changes_memory(self):
        self.ask("remember that I prefer Hindi")
        self.assertIn("prefer Hindi",self.ask("show memories")["reply"])
        self.ask("forget memory 1")
        self.assertEqual(self.brain.store.memories(),[])
    def test_bad_requests_rejected(self):
        for body in [{"text":"","request_id":str(uuid.uuid4())},{"text":"a"*8001,"request_id":str(uuid.uuid4())},{"text":"hi","request_id":"not-a-uuid"},{"text":"hi","request_id":str(uuid.uuid4()),"session":{}}]:
            with self.assertRaises(ValueError):self.brain.chat(body)
    def test_model_failure_does_not_claim_action_succeeded(self):
        with patch.object(kitty,"remote_json",side_effect=OSError("offline")):
            reply=self.ask("Explain probability")
        self.assertEqual(reply["mode"],"unavailable");self.assertEqual(reply["actions"],[])
        self.assertIn("Sir",reply["reply"])
    def test_model_response_never_executes_embedded_action(self):
        with patch.object(kitty,"remote_json",side_effect=model_response('{"kind":"call","target":"12345"}')):
            reply=self.ask("Tell me a joke")
        self.assertEqual(reply["actions"],[])
    def test_model_wire_format_and_session_isolation(self):
        captured=[]
        def model(url,payload=None,**kw):
            if url.endswith("/chat/completions"):captured.append(payload)
            return model_response("A test answer.")(url,payload,**kw)
        with patch.object(kitty,"remote_json",side_effect=model):
            self.ask("Private context one",session="one")
            self.ask("Different question",session="two")
        self.assertFalse(captured[0]["stream"]);self.assertFalse(captured[0]["chat_template_kwargs"]["enable_thinking"])
        self.assertEqual(captured[0]["model"],"kitty");self.assertEqual(captured[0]["max_tokens"],160)
        self.assertNotIn("Private context one",json.dumps(captured[1]))
    def test_concurrent_duplicate_has_one_model_call(self):
        calls=[]
        def model(url,payload=None,**kw):
            if url.endswith("/chat/completions"):calls.append(1);time.sleep(.08)
            return model_response("Sir, hello.")(url,payload,**kw)
        request={"text":"Hello","request_id":str(uuid.uuid4())}
        with patch.object(kitty,"remote_json",side_effect=model),concurrent.futures.ThreadPoolExecutor(2) as pool:
            replies=list(pool.map(self.brain.chat,[request,request]))
        self.assertEqual(replies[0],replies[1]);self.assertEqual(len(calls),1)
    def test_qwen_template_receives_one_leading_system_message(self):
        self.brain.store.remember("The owner prefers concise replies")
        captured=[]
        def strict_qwen(url,payload=None,**kw):
            if url.endswith(("/apply-template","/chat/completions")):
                roles=[m["role"] for m in payload["messages"]]
                if roles[0]!="system" or "system" in roles[1:]:
                    raise ValueError("System message must be at the beginning")
                captured.append(payload["messages"])
            return model_response("Sir, a concise answer.")(url,payload,**kw)
        with patch.object(kitty,"remote_json",side_effect=strict_qwen):
            reply=self.ask("Introduce yourself")
        self.assertEqual(reply["mode"],"model")
        self.assertEqual(len(captured),2)
        self.assertEqual(captured[0],captured[1])
        self.assertIn("Reference data only",captured[0][0]["content"])
        self.assertIn("prefers concise replies",captured[0][0]["content"])
    def test_weather_requires_city(self):
        with patch.object(kitty,"remote_json",side_effect=AssertionError("Must ask city")):
            self.assertIn("Which city",self.ask("what's the weather?")["reply"])
    def test_weather_is_timestamped_and_attributed(self):
        def remote(url,**kw):
            if "geocoding" in url:return {"results":[{"name":"Delhi","admin1":"Delhi","country":"India","latitude":28.6,"longitude":77.2}]}
            return {"current":{"temperature_2m":30,"apparent_temperature":33,"weather_code":2,"wind_speed_10m":8,"time":"2026-09-26T11:00"},"timezone":"Asia/Kolkata"}
        with patch.object(kitty,"remote_json",side_effect=remote):reply=self.ask("weather in Delhi")["reply"]
        self.assertIn("Open-Meteo",reply);self.assertIn("2026-09-26T11:00",reply);self.assertIn("30°C",reply)
    def test_unavailable_weather_never_fabricates_temperature(self):
        with patch.object(kitty,"remote_json",side_effect=OSError("no internet")):
            reply=self.ask("weather in Delhi")["reply"]
        self.assertNotIn("°C",reply);self.assertIn("couldn't fetch",reply)
    def test_ingestion_replaces_old_version(self):
        note=self.home/"note.md";note.write_text("My project uses solar batteries.")
        self.brain.store.ingest(note);self.assertTrue(self.brain.store.retrieve("solar"))
        note.write_text("The replacement uses wind generators.")
        self.brain.store.ingest(note);self.assertFalse(self.brain.store.retrieve("solar"));self.assertTrue(self.brain.store.retrieve("wind"))
    def test_export_only_approved_or_corrected_conversation(self):
        with patch.object(kitty,"remote_json",side_effect=model_response("Initial answer")):
            r=self.ask("Question for training")
            unused=self.ask("Unapproved content")
        self.brain.store.feedback(r["response_id"],-1,"Sir, corrected answer")
        command=self.ask("WhatsApp Mom saying private message")
        self.brain.store.feedback(command["response_id"],1,"")
        out=self.home/"export.jsonl";self.assertEqual(self.brain.store.export(out),1)
        rows=out.read_text();self.assertIn("corrected answer",rows);self.assertNotIn("private message",rows);self.assertNotIn("Unapproved content",rows)
    def test_context_trims_old_history_before_current_message(self):
        self.brain.config.update(context_window=4096,max_tokens=512)
        messages=[{"role":"system","content":"persona"},{"role":"system","content":"references"},{"role":"user","content":"old "*4000},{"role":"assistant","content":"old answer"},{"role":"user","content":"current question"}]
        with patch.object(kitty,"remote_json",side_effect=model_response("")):
            fitted=self.brain.fit_context(messages)
        self.assertEqual(fitted[-1]["content"],"current question")
        self.assertEqual(len(fitted),2)
        self.assertIn("persona",fitted[0]["content"])
        self.assertIn("references",fitted[0]["content"])
        self.assertNotIn("old answer",json.dumps(fitted))
    def test_oversized_current_message_is_not_silently_truncated(self):
        messages=[{"role":"system","content":"persona"},{"role":"user","content":"x"*30000}]
        with patch.object(kitty,"remote_json",side_effect=model_response("")),self.assertRaises(kitty.ContextLimit):
            self.brain.fit_context(messages)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();h=pathlib.Path(self.tmp.name);kitty.initialize(h)
        self.brain=kitty.Brain(h);self.server=kitty.Server(("127.0.0.1",0),self.brain)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.base=f"http://127.0.0.1:{self.server.server_address[1]}"
        self.opener=build_opener(ProxyHandler({}))
    def tearDown(self):self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
    def req(self,path,payload=None,auth=True,origin=False):
        headers={"Content-Type":"application/json"}
        if auth:headers["Authorization"]="Bearer "+self.brain.config["token"]
        if origin:headers["Origin"]="https://example.com"
        data=json.dumps(payload).encode() if payload is not None else None
        return self.opener.open(Request(self.base+path,data,headers),timeout=5)
    def test_health_contains_no_private_config(self):
        with self.req("/health",auth=False) as response:body=json.load(response)
        self.assertNotIn("token",body);self.assertEqual(body["status"],"ok")
    def test_private_endpoint_requires_pairing_token(self):
        with self.assertRaises(HTTPError) as caught:self.req("/v1/chat",{"text":"hi"},auth=False)
        self.assertEqual(caught.exception.code,401)
    def test_browser_origin_rejected(self):
        with self.assertRaises(HTTPError) as caught:self.req("/v1/chat",{"text":"hi"},origin=True)
        self.assertEqual(caught.exception.code,403)
    def test_round_trip(self):
        with self.req("/v1/chat",{"text":"remember that this is a test","request_id":str(uuid.uuid4())}) as r:
            result=json.load(r)
        self.assertIn("saved",result["reply"])
    def test_malformed_json_shape_returns_400(self):
        with self.assertRaises(HTTPError) as caught:self.req("/v1/chat",["invalid"])
        self.assertEqual(caught.exception.code,400)


if __name__=="__main__":unittest.main()
