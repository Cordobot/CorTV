"""
Checks every iptv-org live channel and writes the ones that load and have sound.

The app only shows the channels listed in common/src/main/assets/verified_streams.txt, so run this
again from time to time (results depend on the network/country of the computer that runs it):

    python -m pip install imageio-ffmpeg
    python tools/verify_channels.py common/src/main/assets/verified_streams.txt

1. Quick pass: the stream URL must answer HTTP 200 (with the User-Agent/Referer it needs).
2. ffmpeg pass: reads a few seconds of the stream; it must have a video track and an audio track
   that is not silent (any language).
"""
import concurrent.futures as cf
import json
import re
import subprocess
import sys
import time
import urllib.request

import imageio_ffmpeg

API = "https://iptv-org.github.io/api/"
DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
SILENCE_DB = -60.0  # mean volume below this is treated as no sound
SECONDS = 6


def fetch_json(name):
    with urllib.request.urlopen(API + name, timeout=60) as r:
        return json.load(r)


def candidate_streams():
    """Streams the app would show: no NSFW, closed or blocklisted channels (same rules as IptvOrgApi.kt)."""
    streams = fetch_json("streams.json")
    channels = {c["id"]: c for c in fetch_json("channels.json")}
    blocked = {b["channel"] for b in fetch_json("blocklist.json")}
    result = []
    for s in streams:
        ch = channels.get(s["channel"]) if s["channel"] else None
        if s["channel"] in blocked or (ch and (ch["is_nsfw"] or ch["closed"])):
            continue
        result.append(s)
    return result


def http_ok(s):
    req = urllib.request.Request(s["url"], headers={"User-Agent": s["user_agent"] or DEFAULT_UA})
    if s["referrer"]:
        req.add_header("Referer", s["referrer"])
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            r.read(2048)
            return r.status == 200
    except Exception:
        return False


def has_video_and_sound(s):
    cmd = [FFMPEG, "-hide_banner", "-nostdin", "-loglevel", "info",
           "-user_agent", s["user_agent"] or DEFAULT_UA, "-rw_timeout", "15000000"]
    if s["referrer"]:
        cmd += ["-headers", f"Referer: {s['referrer']}\r\n"]
    cmd += ["-i", s["url"], "-t", str(SECONDS), "-map", "0:a:0", "-af", "volumedetect", "-f", "null", "-"]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, errors="replace", timeout=45).stderr
    except subprocess.TimeoutExpired:
        return False, "timeout"
    if not re.search(r"Stream #\d+:\d+.*: Video:", out):
        return False, "sin video"
    if not re.search(r"Stream #\d+:\d+.*: Audio:", out):
        return False, "sin audio"
    m = re.search(r"mean_volume: (-?[\d.]+) dB", out)
    if not m:
        return False, "no se pudo leer el audio"
    if float(m.group(1)) < SILENCE_DB:
        return False, f"silencio ({m.group(1)} dB)"
    return True, f"{m.group(1)} dB"


def run_pool(fn, items, workers, label):
    results = {}
    start = time.time()
    with cf.ThreadPoolExecutor(workers) as pool:
        futures = {pool.submit(fn, s): s["url"] for s in items}
        for i, f in enumerate(cf.as_completed(futures), 1):
            results[futures[f]] = f.result()
            if i % 250 == 0 or i == len(items):
                print(f"[{label}] {i}/{len(items)}  {time.time() - start:.0f}s", flush=True)
    return results


def main():
    out_path = sys.argv[1]
    report_path = sys.argv[2] if len(sys.argv) > 2 else None
    streams = candidate_streams()
    print(f"{len(streams)} streams para probar", flush=True)

    alive = run_pool(http_ok, streams, 64, "http")
    reachable = [s for s in streams if alive[s["url"]]]
    print(f"{len(reachable)} responden", flush=True)

    checked = run_pool(has_video_and_sound, reachable, 32, "audio")
    good = sorted(url for url, (ok, _) in checked.items() if ok)
    print(f"{len(good)} con video y sonido", flush=True)

    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# Generado por tools/verify_channels.py el {time.strftime('%Y-%m-%d')}: {len(good)} canales con video y sonido\n")
        f.write("\n".join(good) + "\n")
    if report_path:
        report = {s["url"]: ("http", "no responde") if not alive[s["url"]] else checked[s["url"]] for s in streams}
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump({u: {"ok": r[0] is True, "detail": r[1]} for u, r in report.items()}, f, ensure_ascii=False, indent=0)


if __name__ == "__main__":
    main()
