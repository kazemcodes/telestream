import os
import sys

# Ensure UTF-8 output encoding across all platforms
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

import subprocess
import httpx
from fastapi import Request, Response
from fastapi.responses import HTMLResponse, RedirectResponse
import gradio as gr

# Support Hugging Face ZeroGPU if assigned
try:
    import spaces
    @spaces.GPU
    def gpu_handler():
        return "ZeroGPU Ready"
except Exception:
    def gpu_handler():
        return "CPU Ready"

bot_process = None
KTOR_INTERNAL_URL = "http://127.0.0.1:8080"

def start_bot():
    global bot_process
    jar_candidates = [
        "telestream-all.jar",
        "build/libs/telestream-all.jar"
    ]
    jar_path = next((j for j in jar_candidates if os.path.exists(j)), None)

    if not jar_path:
        print("⚙️ telestream-all.jar not found, attempting to build with Gradle...")
        subprocess.run(["chmod", "+x", "./gradlew"])
        build_res = subprocess.run(["./gradlew", "fatJar", "--no-daemon", "-x", "test"])
        if build_res.returncode == 0:
            jar_path = "build/libs/telestream-all.jar"
        else:
            print("❌ Failed to build JAR with Gradle")

    if jar_path and os.path.exists(jar_path):
        print(f"🚀 Launching TeleStream JAR: {jar_path}")
        env = os.environ.copy()
        # TeleStream internal Ktor server on 8080 so Gradio on 7860 has zero port conflict
        env["PORT"] = "8080"

        # Ensure cache directories exist
        os.makedirs("data/plugins_cache", exist_ok=True)

        bot_process = subprocess.Popen(
            [
                "java",
                "-XX:+UseG1GC",
                "-XX:MaxRAMPercentage=75.0",
                "-Xms256m",
                "-jar",
                jar_path
            ],
            env=env
        )
        print(f"✅ TeleStream process started with PID {bot_process.pid}")
    else:
        print("❌ Cannot find or build executable JAR.")

def get_status():
    global bot_process
    if bot_process and bot_process.poll() is None:
        return f"🟢 TeleStream is ACTIVE (PID: {bot_process.pid})"
    return "🔴 TeleStream process is stopped"

# Start bot process on space startup
start_bot()

# 1. Gradio Web Interface
with gr.Blocks(title="TeleStream Bot & WebApp") as demo:
    gr.Markdown("""
    # 🎬 TeleStream: Native CloudStream Telegram Bot & Mini App
    ### 🟢 وضعیت ربات: فعال و آنلاین (Pure JVM Kotlin)
    """)

    with gr.Tabs():
        with gr.TabItem("🎬 پیش‌نمایش مینی‌اپ تحت وب (Web Player)"):
            gr.Markdown("""
            مینی‌اپ تلگرام مستقیماً در زیر در دسترس است. می‌توانید فیلم‌ها و سریال‌ها را جستجو کرده و استریم کنید:
            """)
            gr.HTML("""
            <div style="width: 100%; height: 780px; border-radius: 16px; overflow: hidden; border: 1px solid rgba(255,255,255,0.1); box-shadow: 0 8px 32px rgba(0,0,0,0.6);">
                <iframe src="/webapp" style="width: 100%; height: 100%; border: none;"></iframe>
            </div>
            <div style="margin-top: 10px; text-align: center;">
                <a href="/webapp" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%); color: white; padding: 10px 20px; border-radius: 20px; text-decoration: none; font-weight: bold; font-family: sans-serif;">
                    🔗 باز کردن مینی‌اپ در صفحه تمام‌صفحه (Full Screen)
                </a>
            </div>
            """)

        with gr.TabItem("🤖 راهنمای اتصال در تلگرام (Telegram Setup)"):
            gr.Markdown("""
            ### 📱 روش‌های باز کردن مینی‌اپ در تلگرام:
            
            1. **دکمه منوی کنار کادر پیام (Menu Button):**
               در چت ربات، در گوشه پایین سمت چپ کادر تایپ پیام، دکمه **🎬 TeleStream** قرار دارد که با یک کلیک مینی‌اپ را به صورت کشویی (Modal Sheet) باز می‌کند.
            
            2. **دستور `/app` یا `/start`:**
               در ربات دستور `/app` یا `/start` را ارسال کنید تا کلید شیشه‌ای **🌐 باز کردن در مینی‌اپ** برای شما ارسال شود.
            
            3. **تنظیم دکمه منو در @BotFather (اختیاری و دائمی):**
               * به ربات `@BotFather` در تلگرام بروید.
               * دستور `/setmenubutton` را ارسال کنید.
               * ربات خود را انتخاب کنید.
               * آدرس مینی‌اپ را وارد کنید:
                 `https://kazemcodes-telestream-bot.hf.space/webapp`
               * عنوان دکمه را وارد کنید: `🎬 TeleStream`
            
            4. **ساخت لینک اختصاصی مینی‌اپ (Direct Mini App Link):**
               * در `@BotFather` دستور `/newapp` را ارسال کنید.
               * ربات خود را انتخاب و نام و توضیحات و تصویر را وارد کنید.
               * آدرس وب‌اپ را قرار دهید:
                 `https://kazemcodes-telestream-bot.hf.space/webapp`
               * یک نام کوتاه (مثل `play` یا `stream`) انتخاب کنید تا لینک `t.me/YourBot/play` برای اشتراک‌گذاری در کانال‌ها و گروه‌ها ایجاد شود.
            """)

        with gr.TabItem("⚙️ وضعیت و سلامت سرور (Server Status)"):
            status_box = gr.Textbox(value=get_status, label="Service Status", every=10)
            gr.Markdown("""
            * **رم اختصاص یافته:** تا ۱۶ گیگابایت حافظه رم بر روی Hugging Face Spaces
            * **موتور اجرایی:** Pure JVM Kotlin (JDK 21 LTS) + Ktor CIO + Gradio FastAPI
            * **پروتکل‌ها:** HLS (m3u8), DoH (DNS over HTTPS), ASM Bytecode Shims
            """)

    # Required element to satisfy Hugging Face ZeroGPU scanner
    dummy_btn = gr.Button("GPU Check", visible=False)
    dummy_output = gr.Textbox(visible=False)
    dummy_btn.click(gpu_handler, outputs=dummy_output)

# 2. Attach FastAPI Proxy Routes directly to demo.app (Gradio's underlying FastAPI instance)
@demo.app.get("/webapp", response_class=HTMLResponse)
async def serve_webapp():
    async with httpx.AsyncClient() as client:
        try:
            res = await client.get(f"{KTOR_INTERNAL_URL}/webapp", timeout=12.0)
            return HTMLResponse(content=res.text, status_code=res.status_code)
        except Exception as e:
            return HTMLResponse(
                content=f"""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta http-equiv="refresh" content="3">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>TeleStream Starting...</title>
                    <style>
                        body {{ background: #07090e; color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }}
                        .spinner {{ width: 44px; height: 44px; border: 4px solid rgba(99,102,241,0.2); border-top-color: #6366f1; border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 20px; }}
                        @keyframes spin {{ to {{ transform: rotate(360deg); }} }}
                    </style>
                </head>
                <body>
                    <div class="spinner"></div>
                    <h2>🎬 TeleStream در حال راه‌اندازی...</h2>
                    <p style="color: #94a3b8; font-size: 0.9rem;">ربات در حال آماده‌سازی و بارگذاری افزونه‌ها است. صفحه خودکار بارگذاری خواهد شد...</p>
                </body>
                </html>
                """,
                status_code=503
            )

@demo.app.api_route("/api/{path:path}", methods=["GET", "POST", "OPTIONS"])
async def proxy_api(request: Request, path: str):
    async with httpx.AsyncClient() as client:
        try:
            url = f"{KTOR_INTERNAL_URL}/api/{path}"
            if request.url.query:
                url += f"?{request.url.query}"
            body = await request.body()
            res = await client.request(
                method=request.method,
                url=url,
                content=body,
                headers={k: v for k, v in request.headers.items() if k.lower() not in ["host", "content-length"]},
                timeout=45.0
            )
            return Response(
                content=res.content,
                status_code=res.status_code,
                headers=dict(res.headers),
                media_type=res.headers.get("content-type")
            )
        except Exception as e:
            return Response(
                content=f'{{"error":"Internal gateway error: {e}"}}',
                status_code=502,
                media_type="application/json"
            )

@demo.app.get("/r/{token}")
@demo.app.get("/webapp/r/{token}")
async def proxy_redirect(token: str):
    async with httpx.AsyncClient() as client:
        try:
            res = await client.get(f"{KTOR_INTERNAL_URL}/r/{token}", follow_redirects=False, timeout=10.0)
            target = res.headers.get("Location")
            if target:
                return RedirectResponse(target)
            return Response(content=res.content, status_code=res.status_code)
        except Exception as e:
            return Response(content=f"Redirect error: {e}", status_code=500)

@demo.app.get("/health")
async def health_check():
    async with httpx.AsyncClient() as client:
        try:
            res = await client.get(f"{KTOR_INTERNAL_URL}/health", timeout=4.0)
            return Response(content=res.content, status_code=res.status_code, media_type="application/json")
        except Exception:
            return {"status": "starting", "gradio": "healthy"}

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 7860))
    demo.launch(server_name="0.0.0.0", server_port=port)
