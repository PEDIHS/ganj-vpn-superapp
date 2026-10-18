#!/usr/bin/env python3
"""Capture and summarize actual Android graphics frames without OCR/screenshots.

No threshold is silently invented. Reports API, CPU ABI, jank (>16.67ms),
p50/p90/p95/p99, and PSS; keep raw gfxinfo for regression comparison.
Emulator metrics describe the emulator, NOT physical device performance.
"""
import argparse
import json
import math
import pathlib
import re
import subprocess
import time

PACKAGE = "com.ganj.vpn"

def run(*args, timeout=18):
    return subprocess.run(["adb", *args], text=True, capture_output=True,
                          timeout=timeout, check=True).stdout

def percentile(values, p):
    if not values: return None
    ordered = sorted(values)
    index = max(0, math.ceil(p * len(ordered)) - 1)
    return round(ordered[index], 2)

def frame_durations(raw):
    durations = []
    collecting = False
    header = None
    first = last = None
    for line in raw.splitlines():
        line=line.strip()
        if line.startswith("---PROFILEDATA---"):
            collecting = not collecting
            header = None
            continue
        if not collecting or not line: continue
        if line.startswith("Flags,"):
            header = [column.strip() for column in line.split(",")]
            try: first=header.index("IntendedVsync");last=header.index("FrameCompleted")
            except ValueError: header=None
            continue
        if not header: continue
        values=line.split(",")
        if len(values)<len(header): continue
        try:
            if int(values[0]) != 0: continue
            start=int(values[first]);end=int(values[last])
            ms=(end-start)/1_000_000
            if math.isfinite(ms) and 0 < ms < 3000: durations.append(ms)
        except (ValueError, IndexError): continue
    return durations

def summarize_frames(values):
    return {
        "frames":len(values),
        "p50_ms":percentile(values,.50),
        "p90_ms":percentile(values,.90),
        "p95_ms":percentile(values,.95),
        "p99_ms":percentile(values,.99),
        "over_16_67ms":sum(x>16.67 for x in values),
        "over_33_33ms":sum(x>33.33 for x in values),
        "over_16_67_pct":round(100*sum(x>16.67 for x in values)/len(values),2) if values else None,
    }

def total_pss(raw):
    m = re.search(r"TOTAL\s+PSS:\s*([0-9,]+)",raw)
    if m:return int(m.group(1).replace(",",""))
    m=re.search(r"^\s*TOTAL\s+([0-9,]+)\s+",raw,re.M)
    return int(m.group(1).replace(",","")) if m else None

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--output-dir",default="startup-evidence")
    parser.add_argument("--swipes",type=int,default=5)
    args=parser.parse_args()
    out=pathlib.Path(args.output_dir);out.mkdir(parents=True,exist_ok=True)
    run("shell","am","start","-W","-n",PACKAGE+"/.MainActivity",timeout=30)
    run("shell","dumpsys","gfxinfo",PACKAGE,"reset")
    size=run("shell","wm","size")
    dimensions=re.search(r"(\d+)x(\d+)",size)
    width,height=(int(dimensions.group(i)) for i in (1,2)) if dimensions else (1080,1920)
    x=width//2
    for n in range(args.swipes):
        start=int(height*(.74 if n%2==0 else .32))
        end=int(height*(.32 if n%2==0 else .74))
        run("shell","input","swipe",str(x),str(start),str(x),str(end),"350")
    time.sleep(1)
    gfx=run("shell","dumpsys","gfxinfo",PACKAGE,"framestats")
    memory=run("shell","dumpsys","meminfo",PACKAGE)
    (out/"frame-stats.txt").write_text(gfx)
    (out/"process-meminfo.txt").write_text(memory)
    frames=frame_durations(gfx)
    summary={
        "source":"Android adb dumpsys gfxinfo framestats",
        "benchmark_scope":"Emulator smoke-run baseline, not physical-device certification",
        "api_level":run("shell","getprop","ro.build.version.sdk").strip(),
        "abi":run("shell","getprop","ro.product.cpu.abi").strip(),
        "swipes":args.swipes,
        "graphics":summarize_frames(frames),
        "pss_kb":total_pss(memory),
    }
    (out/"performance.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(summary,ensure_ascii=False))

if __name__=="__main__": main()
