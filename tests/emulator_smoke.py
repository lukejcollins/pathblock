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


def check_update_controls():
    shell("pm", "grant", APP, "android.permission.POST_NOTIFICATIONS")
    launch()
    tap(wait(lambda: find("Settings", "content-desc"), "Settings button missing"))
    label = "Notify me about updates"
    toggle = wait(lambda: find(label), "Combined update toggle missing")
    assert toggle.get("checked") == "true", "Existing automatic-update default was lost"
    tap(toggle)

    def toggle_state(checked):
        node = find(label)
        return node is not None and node.get("checked") == checked

    wait(lambda: toggle_state("false"), "Update notification toggle did not turn off")

    def manual_check_ready():
        node = find("Check for updates")
        return node if node is not None and node.get("enabled") == "true" else None

    tap(wait(manual_check_ready, "Manual check was unavailable with notifications off"))
    wait(manual_check_ready, "Manual check did not finish with notifications off")
    tap(wait(lambda: find(label), "Combined update toggle disappeared"))
    wait(lambda: toggle_state("true"), "Update notification toggle did not turn on")
    shell("input", "keyevent", "KEYCODE_BACK")
    print("PASS: update notifications can be toggled independently of manual checks", flush=True)


def check_guard_setup():
    shell("settings", "delete", "secure", "enabled_accessibility_services")
    shell("settings", "put", "secure", "accessibility_enabled", "0")
    launch()
    tap(wait(lambda: find("Enable Chrome guard", "content-desc"), "Setup entry point missing"))
    tap(wait(lambda: find("Agree and continue"), "Consent action missing"))
    app_info = wait(lambda: find("Open Pathblock App info"), "App info action missing")
    assert app_info.get("enabled") == "false", "Step two was enabled before visiting Accessibility"
    tap(wait(lambda: find("Open Accessibility"), "Accessibility action missing"))
    wait(lambda: find("com.android.settings", "package"), "Did not open Android Accessibility")
    shell("input", "keyevent", "KEYCODE_BACK")

    app_info = wait(lambda: find("Open Pathblock App info"), "Setup did not resume at step two")
    assert app_info.get("enabled") == "true", "Step two was not unlocked"
    tap(app_info)
    wait(lambda: find("App info"), "Did not open Pathblock’s App info")
    shell("input", "keyevent", "KEYCODE_BACK")

    final_step = wait(lambda: find("Return to Accessibility"), "Step three action missing")
    assert final_step.get("enabled") == "true", "Step three was not unlocked"
    reopen_accessibility = wait(lambda: find("Reopen Accessibility"), "Step one retry missing")
    assert reopen_accessibility.get("enabled") == "true", "Completed step one was disabled"
    tap(reopen_accessibility)
    wait(lambda: find("com.android.settings", "package"), "Step one retry did not open Accessibility")
    shell("input", "keyevent", "KEYCODE_BACK")
    final_step = wait(lambda: find("Return to Accessibility"), "Step one retry lost step three")
    assert final_step.get("enabled") == "true", "Reopening step one reset progress"
    tap(final_step)
    wait(lambda: find("com.android.settings", "package"), "Did not return to Android Accessibility")
    shell("input", "keyevent", "KEYCODE_BACK")
    tap(wait(lambda: find("Reopen Pathblock App info"), "App info retry missing after step three"))
    wait(lambda: find("App info"), "App info retry did not reopen Android Settings")
    shell("input", "keyevent", "KEYCODE_BACK")
    tap(wait(lambda: find("Return to Accessibility"), "Step three was unavailable after retry"))
    wait(lambda: find("com.android.settings", "package"), "Did not reopen Android Accessibility")
    shell("settings", "put", "secure", "enabled_accessibility_services", APP + "/.ChromeGuard")
    shell("settings", "put", "secure", "accessibility_enabled", "1")
    shell("input", "keyevent", "KEYCODE_BACK")
    wait(lambda: find("Guard on"), "Setup did not detect that Android enabled Pathblock")
    print("PASS: guided setup opens both Android settings screens and detects guard activation", flush=True)


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
        shell("pm", "grant", CHROME, "android.permission.POST_NOTIFICATIONS")
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

        for address in ("zebra.example/news", "alpha.example/news"):
            tap(wait(lambda: find("android.widget.EditText", "class"), "URL input did not appear"))
            shell("input", "text", address)
            tap(wait(lambda: find("Add rule", "content-desc"), "Add button missing"))
            wait(lambda: find(address), "New rule did not appear")
        expected = [saved_rule, "alpha.example/news", "zebra.example/news"]
        assert [node.get("text") for node in nodes() if node.get("text") in expected] == expected
        print("PASS: rules display in alphabetical order", flush=True)

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
        open_chrome("/welcome")
        wait(allowed_page_visible, "Chrome did not finish first-run setup", seconds=60)
        open_chrome("/hey/before-consent")
        wait(allowed_page_visible, "Chrome should remain usable before consent", seconds=60)
        assert not shield(), "Chrome was monitored before the user accepted the disclosure"
        launch()
        tap(wait(lambda: find("Enable Chrome guard", "content-desc"), "Guard control was missing"))
        tap(wait(lambda: find("Cancel"), "Disclosure could not be cancelled"))
        launch()
        tap(wait(lambda: find("Enable Chrome guard", "content-desc"), "Guard was enabled after cancellation"))
        tap(wait(lambda: find("Agree and continue"), "Consent action was missing"))
        wait(lambda: find("Guard on"), "Consent did not activate the guard")
        print("PASS: Chrome monitoring requires explicit consent", flush=True)
        open_chrome("/HEY/child")
        wait(shield, "Chrome did not show the shield for a case-insensitive subpath")
        launch()
        open_chrome("/heyday")
        wait(allowed_page_visible, "Chrome did not show the allowed sibling page", seconds=60)
        assert not shield(), "Chrome blocked a similarly named sibling"
        print("PASS: Chrome blocks the subpath and allows a similarly named sibling", flush=True)
        launch()
        tap(wait(lambda: find("Chrome guard on. Manage Chrome guard", "content-desc"), "Guard settings were missing"))
        tap(wait(lambda: find("Turn off monitoring"), "Opt-out action was missing"))
        wait(lambda: find("Enable Chrome guard", "content-desc"), "Opt-out did not update guard status")
        open_chrome("/hey/after-opt-out")
        wait(allowed_page_visible, "Chrome should remain usable after opt-out", seconds=60)
        assert not shield(), "Chrome was monitored after consent was withdrawn"
        print("PASS: monitoring stops after consent is withdrawn", flush=True)
        check_guard_setup()
        check_update_controls()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print([(n.get("package"), n.get("class"), n.get("text"), n.get("content-desc")) for n in nodes()], flush=True)
        raise
