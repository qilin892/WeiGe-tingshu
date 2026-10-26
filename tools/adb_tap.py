#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真机点按小工具：按界面文字找控件并点它，避免手算坐标。

用法：
  python tools/adb_tap.py dump                     # 打印当前界面上所有文字和坐标
  python tools/adb_tap.py tap 发现                  # 点文字为「发现」的控件
  python tools/adb_tap.py tap 评书 --index 1        # 同名控件点第二个
  python tools/adb_tap.py shot out.png             # 截图
  python tools/adb_tap.py wait 评书                 # 等到某个文字出现（最多 20s）

依赖：adb 在 PATH，或改下面的 ADB 常量。
"""
import re
import subprocess
import sys
import time

ADB = r"D:\android-sdk\platform-tools\adb.exe"
PKG = "com.weige.tingshu"


def adb(*args, binary=False):
    cmd = [ADB, *args]
    r = subprocess.run(cmd, capture_output=True)
    if r.returncode != 0 and not binary:
        sys.stderr.write(r.stderr.decode("utf-8", "replace"))
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def nodes():
    """dump 一层 UI，返回 [(text, x, y)]，按从上到下、从左到右排好"""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        text = m.group(1).strip()
        if not text:
            continue
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
        out.append((text, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return

    op = sys.argv[1]

    if op == "dump":
        for t, x, y in nodes():
            print(f"{x:5d},{y:5d}  {t}")
        return

    if op == "shot":
        path = sys.argv[2] if len(sys.argv) > 2 else "shot.png"
        with open(path, "wb") as f:
            f.write(adb("exec-out", "screencap", "-p", binary=True))
        print("saved", path)
        return

    if op in ("tap", "wait"):
        target = sys.argv[2]
        index = 1
        if "--index" in sys.argv:
            index = int(sys.argv[sys.argv.index("--index") + 1])

        deadline = time.time() + (20 if op == "wait" else 5)
        while time.time() < deadline:
            hits = [n for n in nodes() if target in n[0]]
            if len(hits) >= index:
                _, x, y = hits[index - 1]
                if op == "tap":
                    adb("shell", "input", "tap", str(x), str(y))
                    print(f"tap {target} @ {x},{y}")
                else:
                    print(f"found {target} @ {x},{y}")
                return
            time.sleep(0.8)
        print(f"not found: {target}")
        sys.exit(1)

    print("unknown op:", op)
    print(__doc__)


if __name__ == "__main__":
    main()
