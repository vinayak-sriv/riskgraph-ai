import time
import urllib.request

deadline = time.monotonic() + 60
for port in (8081, 8082, 8080):
    while True:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=2) as response:
                if response.status == 200:
                    break
        except OSError:
            pass
        if time.monotonic() >= deadline:
            raise SystemExit(f"Service {port} did not become healthy")
        time.sleep(0.5)
