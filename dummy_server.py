#!/usr/bin/env python3
"""
PoseTracker Mock Server
-----------------------
Listens for a TCP connection from the PoseTracker Android app.
Accepts incoming pose data frames and silently discards them.

Usage:
    python3 pose_server.py [port]

Default port: 9000
"""

import socket
import sys
import threading
from datetime import datetime


def log(msg: str):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


def handle_client(conn: socket.socket, addr):
    log(f"Connected: {addr[0]}:{addr[1]}")
    frame_count = 0
    try:
        buffer = b""
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            buffer += chunk
            # Frames are newline-delimited JSON
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                if line.strip():
                    frame_count += 1
                    if frame_count % 30 == 0:  # log every ~1 second at 30fps
                        log(f"Received {frame_count} frames from {addr[0]}:{addr[1]}")
    except ConnectionResetError:
        pass
    except Exception as e:
        log(f"Error: {e}")
    finally:
        conn.close()
        log(f"Disconnected: {addr[0]}:{addr[1]} — {frame_count} total frames received")


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9000

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server.bind(("0.0.0.0", port))
    except OSError as e:
        print(f"Error: Could not bind to port {port} — {e}")
        sys.exit(1)

    server.listen(5)
    log(f"Listening on port {port}  (Ctrl+C to stop)")
    log(f"Connect your Android app to your computer's IP address, port {port}")

    try:
        while True:
            conn, addr = server.accept()
            thread = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
            thread.start()
    except KeyboardInterrupt:
        log("Shutting down.")
    finally:
        server.close()


if __name__ == "__main__":
    main()
