from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import subprocess
import urllib.parse
import os

YTDLP = r"C:\Users\A\AppData\Roaming\Python\Python314\Scripts\yt-dlp.exe"
HOST = "0.0.0.0"
PORT = 8765

class Handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)
        url = params.get("url", [None])[0]

        if parsed.path == "/extract" and url:
            self.extract_video(url)
        elif parsed.path == "/download" and url:
            self.download_video(url)
        elif parsed.path == "/alive":
            self.send_json({"ok": True})
        else:
            self.send_json({"error": "use /extract?url=... or /download?url=..."}, 404)

    def extract_video(self, url):
        try:
            cmd = [YTDLP, "--no-playlist", "-g", "--no-check-certificate", url]
            out = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True, timeout=30)
            video_url = out.strip().split("\n")[0]
            if video_url.startswith("http"):
                self.send_json({"url": video_url, "title": ""})
            else:
                self.send_json({"error": "no video found"}, 404)
        except subprocess.TimeoutExpired:
            self.send_json({"error": "timed out"}, 504)
        except subprocess.CalledProcessError as e:
            self.send_json({"error": e.output}, 400)
        except Exception as e:
            self.send_json({"error": str(e)}, 500)

    def download_video(self, url):
        try:
            out_dir = r"C:\Users\A\Downloads\ytdlp_temp"
            os.makedirs(out_dir, exist_ok=True)
            cmd = [YTDLP, "--no-playlist", "-f", "best[ext=mp4]/best",
                   "-o", f"{out_dir}/%(id)s.%(ext)s", "--no-check-certificate", url]
            out = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True, timeout=120)
            for line in out.split("\n"):
                if "[download] Destination:" in line:
                    fpath = line.split("Destination:", 1)[1].strip()
                    if os.path.exists(fpath):
                        fname = os.path.basename(fpath)
                        fsize = os.path.getsize(fpath)
                        self.send_json({"filename": fname, "path": fpath, "size": fsize,
                                        "download_url": f"http://10.0.2.2:{PORT}/file/{fname}"})
                        return
            self.send_json({"error": "download completed but file not found"}, 500)
        except subprocess.TimeoutExpired:
            self.send_json({"error": "download timed out"}, 504)
        except subprocess.CalledProcessError as e:
            self.send_json({"error": e.output}, 400)
        except Exception as e:
            self.send_json({"error": str(e)}, 500)

    def send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, f, *args):
        pass

if __name__ == "__main__":
    server = HTTPServer((HOST, PORT), Handler)
    print(f"yt-dlp server running on http://{HOST}:{PORT}")
    print(f"Extract: http://localhost:{PORT}/extract?url=INSTAGRAM_URL")
    print(f"Download: http://localhost:{PORT}/download?url=INSTAGRAM_URL")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
