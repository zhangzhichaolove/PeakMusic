#!/usr/bin/env python3
"""Loopback-only JSON fixture kept alive across the emulator's Debug -> Release upgrade."""
import http.server
import json
import pathlib
import socketserver
import sys
import struct

data_size = 60 * 8000 * 2
wave = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", 36 + data_size, b"WAVE", b"fmt ", 16, 1, 1, 8000, 16000, 2, 16, b"data", data_size) + bytes(data_size)

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/release.lrc':
            body = b'[00:00.00]Release source lyric\n'
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path == '/audio.wav':
            start, end = 0, len(wave) - 1
            partial = self.headers.get('Range', '').startswith('bytes=')
            if partial:
                first, last = self.headers['Range'][6:].split('-', 1)
                start = int(first)
                if last:
                    end = min(end, int(last))
            if start < 0 or start > end:
                self.send_error(416)
                return
            self.send_response(206 if partial else 200)
            self.send_header('Content-Type', 'audio/wav')
            self.send_header('Accept-Ranges', 'bytes')
            if partial:
                self.send_header('Content-Range', f'bytes {start}-{end}/{len(wave)}')
            self.send_header('Content-Length', str(end - start + 1))
            self.end_headers()
            self.wfile.write(wave[start:end + 1])
            return
        if not self.path.startswith('/music/getMusicList'):
            self.send_error(404)
            return
        body = json.dumps({'success': True, 'result': {'records': [
            {'id': 1, 'name': 'Release API fixture', 'singer': 'Local fixture', 'mp3': '/not-played.mp3'}
        ]}}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

class LoopbackServer(http.server.HTTPServer):
    def server_bind(self):
        # Avoid a host reverse-DNS lookup during test setup.
        socketserver.TCPServer.server_bind(self)
        self.server_name = 'localhost'
        self.server_port = self.server_address[1]

server = LoopbackServer(('127.0.0.1', 0), Handler)
pathlib.Path(sys.argv[1]).write_text(str(server.server_port))
server.serve_forever()
