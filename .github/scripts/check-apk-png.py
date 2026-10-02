#!/usr/bin/env python3
"""Reject malformed PNG payloads even when the containing APK ZIP is valid."""
import struct
import sys
import zipfile
import zlib


def validate_png(data):
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("invalid PNG signature")
    offset = 8
    compressed = bytearray()
    header = False
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("truncated PNG chunk")
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:end - 4]
        checksum = struct.unpack_from(">I", data, end - 4)[0]
        if zlib.crc32(kind + payload) != checksum:
            raise ValueError("PNG CRC mismatch")
        if offset == 8:
            header = kind == b"IHDR" and length == 13
            if not header:
                raise ValueError("missing PNG header")
        if kind == b"IDAT":
            compressed.extend(payload)
        if kind == b"IEND":
            if length != 0 or end != len(data) or not compressed:
                raise ValueError("invalid PNG end/data")
            decoder = zlib.decompressobj()
            decoder.decompress(compressed, 64 * 1024 * 1024)
            if not decoder.eof or decoder.unused_data:
                raise ValueError("incomplete or oversized PNG image stream")
            return
        offset = end
    raise ValueError("missing PNG IEND")


if __name__ == "__main__":
    for artifact in sys.argv[1:]:
        count = 0
        with zipfile.ZipFile(artifact) as archive:
            for name in archive.namelist():
                if name.lower().endswith(".png"):
                    try:
                        validate_png(archive.read(name))
                    except (ValueError, zlib.error) as exc:
                        raise SystemExit(f"{artifact}: {name}: {exc}") from exc
                    count += 1
        print(f"PNG payload guard passed: {count} images")
