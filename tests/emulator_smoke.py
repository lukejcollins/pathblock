"""Run the rule, Chrome and upgrade checks on a disposable emulator."""
import http.server
import re
import shlex
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

APP = "arpa.homelab.pathblock"
CHROME = "com.android.chrome"
PORT = 18766
BASE = f"http://127.0.0.1:{PORT}"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=40).strip()


def shell(*args):
    return adb("shell", shlex.join(args))


def wait(predicate, label, seconds=35):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = predicate()
        if value is not None and value is not False:
            return value
        time.sleep(0.4)
    raise AssertionError(label)


def nodes():
    shell("rm", "-f", "/sdcard/pathblock-ci.xml")
    output = shell("uiautomator", "dump", "/sdcard/pathblock-ci.xml")
    if "dumped to" not in output:
        return []
    return list(ET.fromstring(shell("cat", "/sdcard/pathblock-ci.xml")).iter("node"))


def find(label, attribute="text"):
    return next((node for node in nodes() if node.get(attribute) == label), None)


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def shield():
    return any("Pathblock shield" in line and "Window{" in line
               for line in shell("dumpsys", "window", "windows").splitlines())


def launch():
    shell("am", "start", "-W", "-n", APP + "/.MainActivity")
    wait(lambda: not shield(), "Shield remained visible outside Chrome")


def open_chrome(path):
    result = shell("am", "start", "-W", "-a", "android.intent.action.VIEW",
                   "-d", BASE + path, "-p", CHROME)
    assert "Status: ok" in result, result


def allowed_page_visible():
    for node in nodes():
        if node.get("package") != CHROME:
            continue
        if node.get("text") == "No thanks":
            tap(node)
            return False
        if node.get("text") == "Pathblock fixture":
            return True
    return False


class Page(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html")
        self.end_headers()
        self.wfile.write(b"<html><title>Pathblock fixture</title><h1>Pathblock fixture</h1></html>")


def main():
    with http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Page) as server:
        threading.Thread(target=server.serve_forever, daemon=True).start()
        adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")
        assert CHROME in shell("pm", "list", "packages", CHROME), "Chrome is missing from the emulator image"
        shell("am", "set-debug-app", "--persistent", CHROME)
        shell("sh", "-c", "echo 'chrome --disable-fre --no-default-browser-check' > /data/local/tmp/chrome-command-line")
        shell("am", "force-stop", CHROME)
        shell("settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
        shell("input", "keyevent", "KEYCODE_WAKEUP")
        shell("wm", "dismiss-keyguard")
        adb("install", "/tmp/pathblock-previous.apk")
        launch()
        tap(wait(lambda: find("android.widget.EditText", "class"), "URL input did not appear"))
        shell("input", "text", BASE + "/hey/")
        tap(wait(lambda: find("Add rule", "content-desc"), "Add button missing"))
        saved_rule = f"127.0.0.1:{PORT}/hey"
        wait(lambda: find(saved_rule), "Rule did not save to predecessor")
        version_code = lambda: int(re.search(r"versionCode=(\d+)", shell("dumpsys", "package", APP))[1])
        previous_code = version_code()
        adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
        launch()
        wait(lambda: find(saved_rule), "Saved rule was lost during update")
        assert version_code() > previous_code, "The replacement APK must have a higher version code"
        print("PASS: in-place upgrade retains rules", flush=True)

        adb("install", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
        result = shell("am", "instrument", "-w", "-r", APP + ".test/" + APP + ".RuleChecks")
        assert "PASS: 13 rule checks" in result and "INSTRUMENTATION_CODE: -1" in result, result
        print(result, flush=True)

        launch()
        shell("settings", "delete", "secure", "enabled_accessibility_services")
        shell("settings", "put", "secure", "enabled_accessibility_services", APP + "/.ChromeGuard")
        shell("settings", "put", "secure", "accessibility_enabled", "1")
        wait(lambda: "Bound services:{Service[label=Pathblock for Chrome" in shell("dumpsys", "accessibility"),
             "Chrome guard did not bind")
        open_chrome("/HEY/child")
        wait(shield, "Chrome did not show the shield for a case-insensitive subpath")
        launch()
        open_chrome("/heyday")
        wait(allowed_page_visible, "Chrome did not show the allowed sibling page", seconds=60)
        assert not shield(), "Chrome blocked a similarly named sibling"
        print("PASS: Chrome blocks the subpath and allows a similarly named sibling", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print([(n.get("package"), n.get("class"), n.get("text"), n.get("content-desc")) for n in nodes()], flush=True)
        raise
