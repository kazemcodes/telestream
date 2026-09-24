import os
import sys
import subprocess
import gradio as gr

def start_bot():
    jar_candidates = [
        "telestream-all.jar",
        "build/libs/telestream-all.jar"
    ]
    jar_path = next((j for j in jar_candidates if os.path.exists(j)), None)

    if not jar_path:
        print("⚙️ telestream-all.jar not found, attempting to build with Gradle...")
        subprocess.run(["chmod", "+x", "./gradlew"])
        build_res = subprocess.run(["./gradlew", "fatJar", "--no-daemon"])
        if build_res.returncode == 0:
            jar_path = "build/libs/telestream-all.jar"
        else:
            print("❌ Failed to build JAR with Gradle")

    if jar_path and os.path.exists(jar_path):
        print(f"🚀 Launching TeleStream JAR: {jar_path}")
        # Run Java process in the background
        subprocess.Popen([
            "java",
            "-XX:+UseG1GC",
            "-XX:MaxRAMPercentage=75.0",
            "-Xms256m",
            "-jar",
            jar_path
        ])
    else:
        print("❌ Cannot find or build executable JAR.")

# Start bot process on space startup
start_bot()

# Gradio Web Interface for Hugging Face Space
with gr.Blocks(title="TeleStream Bot") as demo:
    gr.Markdown("""
    # 🎬 TeleStream Telegram Bot
    ### 🟢 وضعیت: فعال و در حال اجرا (Pure JVM)
    ربات تلگرام با موفقیت اجرا شد و در حال پاسخگویی به کاربران است.
    
    * به ربات تلگرام خود بروید و دستور `/start` را ارسال کنید.
    * وب‌سرور داخلی و استریم‌ها در پس‌زمینه فعال هستند.
    """)

if __name__ == "__main__":
    demo.launch(server_port=7860)
