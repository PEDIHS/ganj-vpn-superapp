import importlib.util
import struct
import unittest
import zlib
from pathlib import Path

spec = importlib.util.spec_from_file_location("guard", Path(__file__).with_name("check-apk-png.py"))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))


def valid_png():
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00\xff"))
            + chunk(b"IEND", b""))


class PngGuardTest(unittest.TestCase):
    def test_valid(self):
        guard.validate_png(valid_png())

    def test_truncated_idat(self):
        with self.assertRaisesRegex(ValueError, "truncated"):
            guard.validate_png(valid_png()[:45])

    def test_missing_end(self):
        with self.assertRaisesRegex(ValueError, "IEND"):
            guard.validate_png(valid_png()[:-12])

    def test_bad_crc(self):
        data = bytearray(valid_png())
        data[29] ^= 1
        with self.assertRaisesRegex(ValueError, "CRC"):
            guard.validate_png(data)


if __name__ == "__main__":
    unittest.main()
