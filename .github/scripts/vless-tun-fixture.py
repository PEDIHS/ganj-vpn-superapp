"""Loopback-only VLESS/HTTP fixture for Android TUN integration; never deployed."""
import socketserver
import struct
import uuid


class Fixture(socketserver.BaseRequestHandler):
    def exact(self, count):
        result = b""
        while len(result) < count:
            part = self.request.recv(count - len(result))
            if not part:
                raise ConnectionError("incomplete test frame")
            result += part
        return result

    def handle(self):
        self.request.settimeout(10)
        try:
            assert self.exact(1) == b"\x00"
            assert self.exact(16) == uuid.UUID("40000000-0000-4000-8000-000000000001").bytes
            self.exact(self.exact(1)[0])
            assert self.exact(1) == b"\x01"  # TCP
            assert struct.unpack(">H", self.exact(2))[0] == 18080
            assert self.exact(1) == b"\x01"  # IPv4
            assert self.exact(4) == bytes([198, 18, 0, 1])
            request = b""
            while b"\r\n\r\n" not in request:
                request += self.exact(1)
                assert len(request) <= 4096
            assert request.startswith(b"GET /ganj-tun-check ")
            body = b"ganj-tun-vless-roundtrip-ok"
            self.request.sendall(b"\x00\x00HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: "
                                 + str(len(body)).encode() + b"\r\n\r\n" + body)
            print("TUN VLESS roundtrip served", flush=True)
        except (AssertionError, ConnectionError, OSError):
            print("Rejected invalid or timed-out test request", flush=True)


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


with Server(("127.0.0.1", 18081), Fixture) as server:
    print("fixture ready", flush=True)
    server.serve_forever()
