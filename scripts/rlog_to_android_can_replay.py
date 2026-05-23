#!/usr/bin/env python3
"""
Convert an openpilot rlog into an Android CAN replay asset.

Android reads the generated binary file and publishes each record through:
  ph.publishBuffer("can", msgCanData.serialize(true))

Record format, 16 bytes each:
  uint32 can_id_le
  uint8  bus
  uint8  dlc
  uint16 delay_ms_le
  uint8  data[8]

Usage:
  python3 scripts/rlog_to_android_can_replay.py ./rlog
  python3 scripts/rlog_to_android_can_replay.py ./rlog --bus 0 --max-messages 1500
"""

import argparse
import bz2
import os
import struct
import sys


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.dirname(SCRIPT_DIR)
RECORD_STRUCT = struct.Struct("<IBBH8s")

for candidate in (REPO_DIR, os.path.join(REPO_DIR, "openpilot")):
  if os.path.isdir(candidate) and candidate not in sys.path:
    sys.path.append(candidate)

try:
  import zstandard as zstd
except ModuleNotFoundError:
  zstd = None

capnp_log = None


def get_capnp_log():
  global capnp_log
  if capnp_log is None:
    try:
      from cereal import log as loaded_capnp_log
    except ModuleNotFoundError as e:
      print("Could not import cereal/capnp. Install openpilot Python dependencies, including pycapnp.",
            file=sys.stderr)
      raise e
    capnp_log = loaded_capnp_log
  return capnp_log


def read_log_bytes(rlog_path):
  with open(rlog_path, "rb") as f:
    dat = f.read()

  if rlog_path.endswith(".bz2") or dat.startswith(b"BZh"):
    return bz2.decompress(dat)

  if rlog_path.endswith(".zst") or dat.startswith(b"\x28\xB5\x2F\xFD"):
    if zstd is None:
      raise RuntimeError("zstandard is required to read .zst logs")
    return zstd.ZstdDecompressor().decompress(dat)

  return dat


def iter_log_events(rlog_path):
  yield from get_capnp_log().Event.read_multiple_bytes(read_log_bytes(rlog_path))


def extract_can_frames(rlog_path, bus_filter, max_messages, delay_ms):
  frames = []
  for msg in iter_log_events(rlog_path):
    if msg.which() != "can":
      continue

    for c in msg.can:
      if bus_filter is not None and c.src != bus_filter:
        continue

      data = bytes(c.dat)[:8]
      dlc = len(data)
      frames.append((int(c.address), int(c.src) & 0xFF, dlc, delay_ms, data + b"\x00" * (8 - dlc)))

      if max_messages and len(frames) >= max_messages:
        return frames

  return frames


def write_replay(frames, out_path):
  os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
  with open(out_path, "wb") as f:
    for can_id, bus, dlc, delay_ms, data in frames:
      f.write(RECORD_STRUCT.pack(can_id & 0xFFFFFFFF, bus, dlc, delay_ms, data))


def main():
  p = argparse.ArgumentParser()
  p.add_argument("rlog", help="path to rlog, rlog.bz2, or rlog.zst")
  p.add_argument("--out", default=os.path.join(REPO_DIR, "selfdrive", "assets", "can_replay.bin"),
                 help="output Android asset path")
  p.add_argument("--bus", type=int, default=None, help="only include this CAN bus/src")
  p.add_argument("--max-messages", type=int, default=1500,
                 help="cap the number of messages; set to 0 to disable")
  p.add_argument("--delay-ms", type=int, default=1,
                 help="delay before each replayed CAN message")
  args = p.parse_args()

  if args.delay_ms < 0 or args.delay_ms > 0xFFFF:
    p.error("--delay-ms must be between 0 and 65535")

  cap = args.max_messages if args.max_messages > 0 else None
  frames = extract_can_frames(args.rlog, args.bus, cap, args.delay_ms)
  if not frames:
    print("No CAN frames found. Check the rlog path and --bus filter.", file=sys.stderr)
    sys.exit(1)

  write_replay(frames, args.out)
  bytes_used = len(frames) * RECORD_STRUCT.size
  print(f"Wrote {len(frames)} frames to {args.out}")
  print(f"Replay file size: {bytes_used} bytes ({bytes_used / 1024:.1f} KB)")


if __name__ == "__main__":
  main()
