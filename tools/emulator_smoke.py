"""Exercise a fresh, disposable Android emulator against the real KITTY gateway.

Uses UI-tree bounds for every tap. No calls/messages or model downloads.
The actual language model, microphone and other apps require separate testing.
"""
import argparse
import json
import io
import tarfile
import zipfile
from urllib.request import urlopen
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import threading
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))
from kitty import Brain, Server, Handler, initialize

PACKAGE = "com.kitty.ai"


class Device:
    def __init__(self, serial, output):
        self.serial, self.output, self.step = serial, output, 0
        output.mkdir(parents=True, exist_ok=True)

    def adb(self, *args, binary=False):
        r = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True,
                           timeout=40, check=True)
        return r.stdout if binary else r.stdout.decode(errors="replace")

    def tree(self, name):
        self.step += 1
        for attempt in range(6):
            raw = self.adb("exec-out", "uiautomator", "dump", "/dev/tty")
            start, end = raw.find("<?xml"), raw.rfind("</hierarchy>")
            if start >= 0 and end >= 0:
                break
            time.sleep(1)
        else:
            raise AssertionError("UI tree unavailable after retries: " + raw[:200])
        xml = raw[start:end + len("</hierarchy>")]
        (self.output / f"{self.step:02d}-{name}.xml").write_text(xml)
        return ET.fromstring(xml)

    @staticmethod
    def bounds(node):
        points = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
        if len(points) != 4 or points[2] <= points[0] or points[3] <= points[1]:
            raise AssertionError("Target has no visible bounds")
        return points

    def tap_node(self, node):
        x1, y1, x2, y2 = self.bounds(node)
        self.adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))

    def find(self, label):
        for attempt in range(2):
            root = self.tree("find")
            nodes = [n for n in root.iter("node")
                     if n.get("text", "").casefold() == label.casefold()]
            if len(nodes) == 1:
                return nodes[0]
            scrolls = [n for n in root.iter("node") if n.get("scrollable") == "true"]
            if attempt == 0 and scrolls:
                x1,y1,x2,y2 = self.bounds(scrolls[-1])
                x, top, bottom = (x1+x2)//2, y1+(y2-y1)//4, y2-(y2-y1)//4
                self.adb("shell", "input", "swipe", str(x), str(bottom), str(x), str(top), "300")
                continue
            raise AssertionError(f"Expected one visible target {label!r}, found {len(nodes)}")

    def tap(self, label):
        self.tap_node(self.find(label))

    def expect(self, fragment, timeout=18):
        deadline = time.monotonic()+timeout
        while time.monotonic() < deadline:
            root = self.tree("expect")
            if any(fragment in n.get("text", "") for n in root.iter("node")):
                print("PASS:", fragment, flush=True)
                return root
            time.sleep(.25)
        raise AssertionError("Missing UI text: " + fragment)

    def expect_in_history(self, fragment):
        """Inspect archived turns even when the chat has scrolled past them."""
        for attempt in range(6):
            root = self.tree("history")
            if any(fragment in n.get("text", "") for n in root.iter("node")):
                print("PASS: archived " + fragment, flush=True)
                return
            scrolls = [n for n in root.iter("node") if n.get("scrollable") == "true"]
            if scrolls:
                x1, y1, x2, y2 = self.bounds(scrolls[-1])
                x = (x1 + x2) // 2
                self.adb("shell", "input", "swipe", str(x), str(y1 + (y2-y1)//4),
                         str(x), str(y2 - (y2-y1)//4), "350")
            time.sleep(.4)
        raise AssertionError("Missing archived UI text: " + fragment)

    def screenshot(self, name):
        (self.output / (name+".png")).write_bytes(self.adb("exec-out", "screencap", "-p", binary=True))

    def type_text(self, value, verify=True):
        # Test strings are deliberately restricted; adb shell performs a second
        # parsing step even though the host subprocess does not use a shell.
        if not re.fullmatch(r"[A-Za-z0-9 _-]+", value):
            raise ValueError("Unsupported emulator test text")
        for attempt in range(3):
            if attempt:
                # API 35 supports key combinations. Select the current field and
                # clear it before retrying a rare overloaded-emulator key reorder.
                self.adb("shell", "input", "keycombination", "113", "29")
                self.adb("shell", "input", "keyevent", "67")
            self.adb("shell", "input", "text", value.replace(" ", "%s"))
            if not verify:
                return
            time.sleep(.35)
            root = self.tree("typed")
            focused = [n for n in root.iter("node")
                       if n.get("class") == "android.widget.EditText" and n.get("focused") == "true"]
            if len(focused) == 1 and focused[0].get("text", "") == value:
                return
        raise AssertionError("ADB did not enter the expected text")

    def wait_service(self, name, running, timeout=12):
        deadline = time.monotonic()+timeout
        while time.monotonic() < deadline:
            active = name in self.adb("shell", "dumpsys", "activity", "services", PACKAGE)
            if active == running:
                return
            time.sleep(.25)
        raise AssertionError(f"Service {name} running={active}, expected {running}")

    def send(self, text):
        root = self.tree("compose")
        fields = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"]
        if len(fields) != 1:
            raise AssertionError("Expected the chat input")
        self.tap_node(fields[0])
        self.type_text(text)
        self.adb("shell", "input", "keyevent", "4")  # close software keyboard
        self.tap("Send")


def speech_service_check(d,home):
    """Real Vosk model/native initialization and service lifecycle, without speech accuracy claims."""
    model_zip=home/'voice.zip'
    with urlopen('https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip',timeout=60) as response:
        model_zip.write_bytes(response.read(60_000_000))
    archive_path=home/'voice.tar'
    with zipfile.ZipFile(model_zip) as model,tarfile.open(archive_path,'w') as archive:
        for entry in model.infolist():
            parts=Path(entry.filename).parts
            if len(parts)<2 or entry.is_dir():continue
            if '..' in parts or entry.filename.startswith('/'):raise ValueError('Unexpected model ZIP path')
            data=model.read(entry)
            info=tarfile.TarInfo('files/vosk-model/'+str(Path(*parts[1:])));info.size=len(data);info.mode=0o600
            archive.addfile(info,io.BytesIO(data))
    with archive_path.open('rb') as archive:
        subprocess.run(['adb','-s',d.serial,'exec-in','run-as',PACKAGE,'tar','-xf','-'],stdin=archive,check=True,timeout=90)
    d.adb('shell','pm','grant',PACKAGE,'android.permission.RECORD_AUDIO')
    d.tap('Hey Kitty: off');d.expect('Listening · say Hey Kitty',timeout=45)
    d.screenshot('07-real-vosk-listening')
    d.tap('Hey Kitty: on');d.expect('Hey Kitty: off');d.wait_service('VoiceService',False)
    # Stopping while a model is loading must close the native resources.
    d.tap('Hey Kitty: off');d.expect('Hey Kitty: on',timeout=5)
    d.tap('Hey Kitty: on');d.expect('Hey Kitty: off');d.wait_service('VoiceService',False)
    d.tap('Hey Kitty: off');d.expect('Listening · say Hey Kitty',timeout=45)
    d.tap('Tap to talk');d.expect('Listening · say your command')
    d.tap('Hey Kitty: on');d.expect('Hey Kitty: off');d.wait_service('VoiceService',False)
    print('PASS: real Vosk model load, mic start/stop, rapid stop/restart, bound tap-to-talk arm',flush=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True)
    p.add_argument("--apk", type=Path, default=ROOT/"android/app/build/outputs/apk/debug/app-debug.apk")
    p.add_argument("--output", type=Path, default=ROOT/"android/app/build/reports/emulator")
    args = p.parse_args()
    if not args.serial.startswith("emulator-"):
        p.error("This smoke test is only for a disposable emulator, not your personal phone.")
    devices = subprocess.check_output(["adb", "devices"], text=True)
    if f"{args.serial}\tdevice" not in devices:
        p.error("Selected emulator is not online")
    d = Device(args.serial, args.output)
    server = None
    with tempfile.TemporaryDirectory(prefix="kitty-emulator-") as tmp:
        home = Path(tmp)
        initialize(home)
        config = json.loads((home/"config.json").read_text())
        # This token is only for an isolated test server and disappears after QA.
        config["token"] = "kitty-emulator-test-token"
        config["model_timeout"] = 2
        (home/"config.json").write_text(json.dumps(config))
        Handler.log_message=lambda self,fmt,*values: print("Test gateway:",fmt % values,flush=True)
        server = Server(("127.0.0.1", 0), Brain(home))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            d.adb("install", "-r", str(args.apk))
            d.adb("reverse", "tcp:8765", f"tcp:{server.server_port}")
            d.adb("logcat", "-c")
            probe=d.adb("shell", "(printf 'GET /health HTTP/1.0\\r\\n\\r\\n'; sleep 1) | toybox nc -w 3 127.0.0.1 8765")
            assert '"status": "ok"' in probe, "USB loopback cannot reach the running test gateway"
            print("PASS: USB gateway health endpoint",flush=True)
            d.adb("shell", "input", "keyevent", "224")
            d.adb("shell", "wm", "dismiss-keyguard")
            for setting in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
                d.adb("shell", "settings", "put", "global", setting, "0")
            activity = d.adb("shell", "cmd", "package", "resolve-activity", "--brief", PACKAGE).strip().splitlines()[-1]
            if "/" not in activity:
                raise AssertionError("Launcher activity did not resolve")
            d.adb("shell", "am", "start", "-W", "-n", activity)
            d.expect("At your service, Sir.")
            d.expect("Your mind,")
            d.screenshot("01-welcome")
            d.send("battery")
            d.expect("Sir, your phone is at")
            d.screenshot("02-local-command")
            d.send("play the first song")
            d.expect("enable KITTY's Accessibility service")
            d.tap("Hey Kitty: off")
            d.expect("import a small Vosk English model")
            d.tap("Settings")
            d.expect("Laptop server URL")
            d.screenshot("03-settings")
            root = d.tree("pairing")
            fields = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"]
            if len(fields) != 3:
                raise AssertionError("Expected URL, token and country fields")
            d.tap_node(fields[1])
            d.type_text(config["token"],verify=False)
            d.adb("shell", "input", "keyevent", "4")
            d.tap("Speak replies")
            d.tap("Save")
            d.send("remember that emulator pairing works")
            d.expect("remember")
            # Check actual persisted data too; seeing the user's own message
            # alone must never count as a successful server round trip.
            deadline = time.monotonic()+12
            while time.monotonic() < deadline and not server.brain.store.memories():
                time.sleep(.2)
            assert any(m["text"] == "emulator pairing works" for m in server.brain.store.memories()), "Phone did not save a gateway memory"
            d.expect("Sir,")
            # Pre-pair commands belong only to the phone. Verify they were not
            # uploaded to a server paired later, then check a post-pair event.
            with server.brain.store.db() as db:
                assert db.execute("SELECT COUNT(*) FROM turns WHERE input='battery'").fetchone()[0]==0, "Pre-pair archive crossed a pairing boundary"
            d.send("battery")
            d.expect("Sir, your phone is at")
            deadline=time.monotonic()+8
            while time.monotonic()<deadline:
                with server.brain.store.db() as db:
                    count=db.execute("SELECT COUNT(*) FROM turns WHERE input='battery'").fetchone()[0]
                if count:break
                time.sleep(.2)
            assert count==1, "Local conversation did not sync to archive"
            d.adb("shell", "am", "force-stop", PACKAGE)
            d.adb("shell", "am", "start", "-W", "-n", activity, "--es", "command", "battery", "--es", "action_nonce", "invalid-nonce")
            fresh = d.expect("At your service, Sir.")
            d.expect("Sir, your phone is at")
            d.expect_in_history("emulator pairing works")
            time.sleep(1)
            with server.brain.store.db() as db:
                assert db.execute("SELECT COUNT(*) FROM turns WHERE input='battery'").fetchone()[0]==count, "Exported activity executed an untrusted action extra"
            d.send("show memories")
            d.expect("1. emulator pairing works")
            d.screenshot("04-paired-memory")
            d.send("say hello")
            d.expect("Start the llama.cpp model server")
            d.screenshot("05-model-offline")
            original=server.brain._respond
            release=threading.Event()
            def fixture(text,session,on_event=None,control=None):
                if text!='streaming test':return original(text,session,on_event,control)
                on_event('token',{'text':'Sir, streaming is visible before completion. '})
                try:
                    for _ in range(300):
                        control.check()
                        if release.wait(.1):break
                    control.check()
                    return {'mode':'model','reply':'Sir, streaming is visible before completion. Finished.','actions':[]}
                except Exception:
                    return {'mode':'cancelled','reply':'Sir, stopped the test reply.','actions':[]}
            server.brain._respond=fixture
            d.send('streaming test')
            d.expect('streaming is visible before completion')
            with server.brain.store.db() as db:
                assert db.execute("SELECT COUNT(*) FROM turns WHERE input='streaming test'").fetchone()[0]==0, 'Reply had already finished'
            d.screenshot('06-streaming')
            d.tap('Stop');d.expect('[Stopped]')
            deadline=time.monotonic()+8
            while time.monotonic()<deadline and server.brain.pending:time.sleep(.1)
            assert not server.brain.pending,'Stop did not cancel gateway generation'
            release.set()
            server.shutdown()
            server.server_close()
            server = None
            d.send("hello again")
            d.expect("couldn't reach my laptop brain")
            d.send("battery")
            d.expect("Sir, your phone is at")
            d.send("introduce yourself");d.expect("created by Virat")
            d.screenshot("06-gateway-offline")
            speech_service_check(d,home)
            crash = d.adb("logcat", "-b", "crash", "-d")
            if "com.kitty.ai" in crash:
                raise AssertionError("KITTY crash found in logcat")
            (args.output/"result.txt").write_text("PASS: launch, local command, missing voice model, settings, pairing, Keystore token after process restart, persisted gateway memory, rejected external action extras, model offline, gateway offline, local command after disconnect, persisted phone history, archived local actions, live partial text and cancellation; real Vosk model initialization and microphone lifecycle; no KITTY crash.\nNo physical speech-accuracy, acoustic TTS, real language-model inference or WhatsApp verification.\n")
            print("KITTY emulator smoke checks passed.", flush=True)
        finally:
            try:
                d.screenshot("final-screen")
                (args.output/"logcat.txt").write_text(d.adb("logcat", "-d"))
                (args.output/"crash.txt").write_text(d.adb("logcat", "-b", "crash", "-d"))
                d.adb("reverse", "--remove", "tcp:8765")
            finally:
                if server:
                    server.shutdown()
                    server.server_close()


if __name__ == "__main__":
    main()
