"""Permission-light cloud onboarding and lifecycle checks on a disposable emulator.
Live Firebase accounts and provider playback require the owner's configured release.
"""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
PACKAGE="com.kitty.ai"


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


def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--apk',type=Path,default=ROOT/'android/app/build/outputs/apk/debug/app-debug.apk');p.add_argument('--output',type=Path,default=ROOT/'android/app/build/reports/emulator');args=p.parse_args()
    d=Device(args.serial,args.output)
    d.adb('install','-r',str(args.apk));d.adb('logcat','-c');d.adb('logcat','-b','crash','-c')
    try:
        d.adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
        d.expect('Continue with Google');d.expect('Service setup pending');d.screenshot('welcome')
        root=d.tree('clean-login');labels=' '.join(n.get('text','') for n in root.iter('node'))
        for forbidden in ('Pair with','laptop','Hey Kitty','API key','Screen control','Connect KITTY'):
            if forbidden in labels:raise AssertionError('Legacy setup leaked into onboarding: '+forbidden)
        d.tap('Continue with Google');d.expect('Virat needs to connect Firebase');d.screenshot('setup-pending');d.tap('OK')
        # A launched activity cannot process old internal action extras.
        d.adb('shell','am','start','-n',PACKAGE+'/.MainActivity','--es','kitty_action','call','--es','number','12345')
        d.expect('Continue with Google')
        d.adb('shell','input','keyevent','3');d.adb('shell','am','force-stop',PACKAGE)
        d.adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity');d.expect('Continue with Google');d.screenshot('restart')
        d.adb('shell','settings','put','system','font_scale','1.3')
        d.adb('shell','am','force-stop',PACKAGE);d.adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity');d.expect('Continue with Google');d.screenshot('large-font')
        d.adb('shell','settings','put','system','font_scale','1.0')
        permissions=d.adb('shell','dumpsys','package',PACKAGE)
        (args.output/'package.txt').write_text(permissions)
        for sensitive in ('android.permission.RECORD_AUDIO','android.permission.READ_CONTACTS','android.permission.CALL_PHONE','android.permission.POST_NOTIFICATIONS','android.permission.REQUEST_INSTALL_PACKAGES','android.permission.FOREGROUND_SERVICE'):
            if sensitive in permissions:raise AssertionError('Sensitive permission is still requested: '+sensitive)
        services=d.adb('shell','dumpsys','activity','services',PACKAGE);(args.output/'services.txt').write_text(services)
        if 'VoiceService' in services or 'KittyAccessibilityService' in services:raise AssertionError('Legacy service running')
        crash=d.adb('logcat','-b','crash','-d');(args.output/'crash.txt').write_text(crash)
        if PACKAGE in crash:raise AssertionError('KITTY crashed')
        (args.output/'result.txt').write_text('PASS: cloud-only onboarding, honest missing-config state, no pairing or API setup controls, no sensitive runtime permissions, old action extras ignored, process restart, large-font startup, no voice/control services, no KITTY crash.\nNOT TESTED: live Google/Firebase login, authenticated chat UI, real provider inference/playback, deployed HTTPS host, scanner certification.\n')
        test_apk=ROOT/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        d.adb('install','-r',str(test_apk))
        instrument=d.adb('shell','am','instrument','-w','-r','com.kitty.ai.test/androidx.test.runner.AndroidJUnitRunner')
        (args.output/'instrumentation.txt').write_text(instrument)
        if 'OK (2 tests)' not in instrument:raise AssertionError('Native layout/storage instrumentation failed: '+instrument[-2000:])
        for name in ('home-fixture','conversation-fixture','composer-fixture'):
            raw=d.adb('exec-out','run-as',PACKAGE,'cat','files/qa-layout/'+name+'.png',binary=True)
            (args.output/(name+'.png')).write_bytes(raw)
        crash=d.adb('logcat','-b','crash','-d')
        if PACKAGE in crash:raise AssertionError('KITTY crashed during native layout fixtures')
        with (args.output/'result.txt').open('a') as out:out.write('PASS: production home/conversation/composer renderer with test-only visual fixtures; composer width and scoped SQLite migration checks. Fixtures do not validate Google login or live API replies.\n')
        print('KITTY cloud onboarding and native layout smoke checks passed.',flush=True)
    finally:
        (args.output/'logcat.txt').write_text(d.adb('logcat','-d'))
        (args.output/'crash.txt').write_text(d.adb('logcat','-b','crash','-d'))


if __name__=='__main__':main()
