# -*- coding: utf-8 -*-
"""
Generates the 30-second promotional video for SpaceSlimmer (1920x1080 @ 30fps)
according to implementation_plan.md Section 2.4:
0:00 - 0:05: Scene 1: Problem Hook (Storage Full popup -> SpaceSlimmer solution)
0:05 - 0:11: Scene 2: Visual Storage Treemap (Interactive colored block scan)
0:11 - 0:18: Scene 3: 80% Compression (Summer_Trip_4K.mp4 1.24GB -> 285MB)
0:18 - 0:24: Scene 4: Split-screen Compare slider & 30-Day Safe Vault undo
0:24 - 0:30: Scene 5: Outro CTA (SpaceSlimmer Logo, 100% Offline, Get it on Google Play)
"""
import os
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 1080
FPS = 30
DURATION = 30  # seconds
TOTAL_FRAMES = FPS * DURATION  # 900 frames

def get_font(size, bold=False):
    font_paths = [
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
    ]
    for p in font_paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()

def draw_gradient(draw, w, h, top_color, bottom_color):
    for y in range(0, h, 2):
        f = y / float(h)
        r = int(top_color[0] + (bottom_color[0] - top_color[0]) * f)
        g = int(top_color[1] + (bottom_color[1] - top_color[1]) * f)
        b = int(top_color[2] + (bottom_color[2] - top_color[2]) * f)
        draw.line([(0, y), (w, y)], fill=(r, g, b), width=2)

def render_frame(frame_idx):
    sec = frame_idx / float(FPS)
    img = Image.new("RGB", (W, H), (15, 23, 42))
    draw = ImageDraw.Draw(img)
    
    # Common background
    draw_gradient(draw, W, H, (15, 23, 42), (10, 15, 28))
    
    # ---------------- SCENE 1: 0.0s - 5.0s (Hook) ----------------
    if sec < 5.0:
        t = sec / 5.0
        # Title
        f_title = get_font(52, bold=True)
        f_sub = get_font(28, bold=False)
        draw.text((W // 2 - 380, 120), "Running Out of Phone Storage?", fill=(255, 255, 255), font=f_title)
        draw.text((W // 2 - 340, 190), "Don't delete your precious memories. Shrink them.", fill=(56, 189, 248), font=f_sub)
        
        # Center Alert Card
        card_w, card_h = 720, 420
        cx = W // 2 - card_w // 2
        cy = 300
        draw.rounded_rectangle([cx, cy, cx + card_w, cy + card_h], radius=24, fill=(30, 41, 59), outline=(244, 63, 94), width=3)
        
        draw.text((cx + 60, cy + 50), "⚠️  Storage Almost Full", fill=(244, 114, 182), font=get_font(36, bold=True))
        draw.text((cx + 60, cy + 110), "98% of 128 GB Used (Only 1.2 GB Remaining)", fill=(226, 232, 240), font=get_font(24))
        
        # Progress bar
        draw.rounded_rectangle([cx + 60, cy + 160, cx + card_w - 60, cy + 190], radius=10, fill=(51, 65, 85))
        draw.rounded_rectangle([cx + 60, cy + 160, cx + int((card_w - 120) * 0.98), cy + 190], radius=10, fill=(244, 63, 94))
        
        # SpaceSlimmer Solution badge
        draw.rounded_rectangle([cx + 60, cy + 240, cx + card_w - 60, cy + 340], radius=18, fill=(19, 36, 56), outline=(2, 132, 199), width=2)
        draw.text((cx + 90, cy + 265), "🚀 SpaceSlimmer to the Rescue", fill=(52, 211, 153), font=get_font(28, bold=True))
        draw.text((cx + 90, cy + 305), "Reclaim up to 80% space • 100% on-device & private", fill=(148, 163, 184), font=get_font(20))
        
    # ---------------- SCENE 2: 5.0s - 11.0s (Treemap) ----------------
    elif sec < 11.0:
        t = (sec - 5.0) / 6.0
        draw.text((W // 2 - 400, 80), "Step 1: Visual Storage Treemap", fill=(255, 255, 255), font=get_font(48, bold=True))
        draw.text((W // 2 - 320, 145), "See exactly where your gigabytes disappeared in 1 tap", fill=(56, 189, 248), font=get_font(26))
        
        # Big Treemap Mockup
        tx, ty, tw, th = 320, 220, 1280, 680
        draw.rounded_rectangle([tx, ty, tx + tw, ty + th], radius=20, fill=(15, 23, 42), outline=(59, 130, 246), width=2)
        
        # Block 1: DCIM / Camera (with animated pulse highlight)
        b1_w = int(tw * 0.60)
        b1_h = int(th * 0.65)
        pulse = int(math.sin(t * 12) * 20)
        draw.rounded_rectangle([tx + 8, ty + 8, tx + b1_w - 8, ty + b1_h - 8], radius=16, fill=(2, 132, 199), outline=(56, 189, 248), width=3)
        draw.text((tx + 40, ty + 40), "DCIM / Camera Roll", fill=(255, 255, 255), font=get_font(36, bold=True))
        draw.text((tx + 40, ty + 90), "64.4 GB (Huge 4K Videos)", fill=(224, 242, 254), font=get_font(28))
        draw.rounded_rectangle([tx + 40, ty + b1_h - 100, tx + 420, ty + b1_h - 40], radius=12, fill=(15, 23, 42))
        draw.text((tx + 60, ty + b1_h - 80), "🔍 Tap to Compress ➔", fill=(52, 211, 153), font=get_font(24, bold=True))
        
        # Block 2: Movies / Clips
        draw.rounded_rectangle([tx + b1_w + 8, ty + 8, tx + tw - 8, ty + int(th * 0.45) - 8], radius=16, fill=(79, 70, 229))
        draw.text((tx + b1_w + 30, ty + 30), "Movies: 18.2 GB", fill=(255, 255, 255), font=get_font(28, bold=True))
        
        # Block 3: Downloads
        draw.rounded_rectangle([tx + b1_w + 8, ty + int(th * 0.45) + 8, tx + tw - 8, ty + int(th * 0.75) - 8], radius=16, fill=(217, 119, 6))
        draw.text((tx + b1_w + 30, ty + int(th * 0.45) + 30), "Downloads: 9.6 GB", fill=(255, 255, 255), font=get_font(28, bold=True))
        
        # Block 4: Free Space
        draw.rounded_rectangle([tx + 8, ty + b1_h + 8, tx + tw - 8, ty + th - 8], radius=16, fill=(51, 65, 85))
        draw.text((tx + 40, ty + b1_h + 30), "Free Space: 38.6 GB Available", fill=(52, 211, 153), font=get_font(26, bold=True))

    # ---------------- SCENE 3: 11.0s - 18.0s (Compression) ----------------
    elif sec < 18.0:
        t = (sec - 11.0) / 7.0
        draw.text((W // 2 - 420, 80), "Step 2: Smart On-Device Compression", fill=(255, 255, 255), font=get_font(48, bold=True))
        draw.text((W // 2 - 360, 145), "Hardware GPU Acceleration • Up to 80% Space Savings", fill=(52, 211, 153), font=get_font(26, bold=True))
        
        # Hero Compression Card
        card_w, card_h = 1000, 560
        cx = W // 2 - card_w // 2
        cy = 240
        draw.rounded_rectangle([cx, cy, cx + card_w, cy + card_h], radius=24, fill=(30, 41, 59), outline=(2, 132, 199), width=2)
        
        # Video title
        draw.text((cx + 60, cy + 50), "🎬 Summer_Trip_4K.mp4 (4K 60fps)", fill=(255, 255, 255), font=get_font(32, bold=True))
        
        # Comparison sizes
        draw.rounded_rectangle([cx + 60, cy + 120, cx + 420, cy + 220], radius=16, fill=(38, 24, 34), outline=(244, 63, 94), width=2)
        draw.text((cx + 90, cy + 140), "BEFORE COMPRESSION", fill=(244, 114, 182), font=get_font(20, bold=True))
        draw.text((cx + 90, cy + 170), "1.24 GB", fill=(255, 255, 255), font=get_font(36, bold=True))
        
        draw.text((cx + 470, cy + 155), "➔", fill=(56, 189, 248), font=get_font(40, bold=True))
        
        draw.rounded_rectangle([cx + 560, cy + 120, cx + 920, cy + 220], radius=16, fill=(16, 38, 44), outline=(16, 185, 129), width=2)
        draw.text((cx + 590, cy + 140), "AFTER SPACESLIMMER", fill=(52, 211, 153), font=get_font(20, bold=True))
        draw.text((cx + 590, cy + 170), "285 MB", fill=(255, 255, 255), font=get_font(36, bold=True))
        
        # Animated compression progress bar
        prog = min(1.0, t * 1.3)
        draw.text((cx + 60, cy + 280), f"Hardware HEVC Encoding... {int(prog * 100)}%", fill=(226, 232, 240), font=get_font(24, bold=True))
        draw.rounded_rectangle([cx + 60, cy + 325, cx + card_w - 60, cy + 365], radius=12, fill=(51, 65, 85))
        draw.rounded_rectangle([cx + 60, cy + 325, cx + 60 + int((card_w - 120) * prog), cy + 365], radius=12, fill=(16, 185, 129))
        
        # Big Savings Banner
        draw.rounded_rectangle([cx + 60, cy + 410, cx + card_w - 60, cy + 500], radius=18, fill=(6, 78, 59))
        draw.text((cx + 120, cy + 435), "⚡ Reclaimed 955 MB (-77%) • Crystal-Clear HD Retained", fill=(209, 250, 229), font=get_font(28, bold=True))

    # ---------------- SCENE 4: 18.0s - 24.0s (Compare & Vault) ----------------
    elif sec < 24.0:
        t = (sec - 18.0) / 6.0
        draw.text((W // 2 - 440, 80), "Step 3: Side-by-Side Compare & Safe Vault", fill=(255, 255, 255), font=get_font(48, bold=True))
        draw.text((W // 2 - 340, 145), "Inspect sharpness in real-time • Undo anytime within 30 days", fill=(56, 189, 248), font=get_font(26))
        
        # Left card: Split Slider (w: 580)
        lx = 240
        draw.rounded_rectangle([lx, 230, lx + 680, 880], radius=20, fill=(0, 0, 0), outline=(59, 130, 246), width=2)
        # Background gradient inside slider
        draw_gradient(draw, lx + 678, 878, (20, 40, 90), (10, 80, 100))
        # Animated divider slider
        slider_x = lx + 340 + int(math.sin(t * 8) * 120)
        draw.line([(slider_x, 232), (slider_x, 878)], fill=(56, 189, 248), width=4)
        draw.ellipse([slider_x - 30, 520, slider_x + 30, 580], fill=(2, 132, 199), outline=(255, 255, 255), width=2)
        draw.text((lx + 40, 260), "Original: 1.24 GB", fill=(255, 255, 255), font=get_font(22, bold=True))
        draw.text((lx + 440, 260), "Slim: 285 MB", fill=(52, 211, 153), font=get_font(22, bold=True))
        draw.text((lx + 160, 820), "🔍 Pixel-Sharp Clarity Retained", fill=(255, 255, 255), font=get_font(22, bold=True))
        
        # Right card: Safe Recovery Vault (w: 680)
        rx = lx + 740
        draw.rounded_rectangle([rx, 230, rx + 680, 880], radius=20, fill=(30, 41, 59), outline=(16, 185, 129), width=2)
        draw.text((rx + 40, 260), "🛡️ 30-Day Safe Recovery Vault", fill=(52, 211, 153), font=get_font(30, bold=True))
        draw.text((rx + 40, 310), "Original files safely backed up in sandbox.", fill=(226, 232, 240), font=get_font(22))
        
        # 2 vault items
        draw.rounded_rectangle([rx + 40, 370, rx + 640, 510], radius=16, fill=(15, 23, 42))
        draw.text((rx + 60, 395), "Summer_Trip_4K.mp4 (1.24 GB)", fill=(255, 255, 255), font=get_font(22, bold=True))
        draw.text((rx + 60, 430), "Protected in sandbox • Auto-expires in 29 days", fill=(148, 163, 184), font=get_font(18))
        draw.rounded_rectangle([rx + 420, 440, rx + 620, 490], radius=10, fill=(2, 132, 199))
        draw.text((rx + 440, 452), "1-Tap Undo", fill=(255, 255, 255), font=get_font(18, bold=True))
        
        draw.rounded_rectangle([rx + 40, 540, rx + 640, 680], radius=16, fill=(15, 23, 42))
        draw.text((rx + 60, 565), "Family_Drone_Sunset.mp4 (860 MB)", fill=(255, 255, 255), font=get_font(22, bold=True))
        draw.text((rx + 60, 600), "Protected in sandbox • Auto-expires in 29 days", fill=(148, 163, 184), font=get_font(18))
        draw.rounded_rectangle([rx + 420, 610, rx + 620, 660], radius=10, fill=(2, 132, 199))
        draw.text((rx + 440, 622), "1-Tap Undo", fill=(255, 255, 255), font=get_font(18, bold=True))
        
        draw.text((rx + 40, 740), "✓ Zero risk of losing memories.", fill=(52, 211, 153), font=get_font(24, bold=True))
        draw.text((rx + 40, 780), "✓ Reversible anytime. Total peace of mind.", fill=(52, 211, 153), font=get_font(24, bold=True))

    # ---------------- SCENE 5: 24.0s - 30.0s (Outro CTA) ----------------
    else:
        # App Icon & Logo
        icon_path = "store/icon-512.png"
        if os.path.exists(icon_path):
            ic = Image.open(icon_path).resize((180, 180), Image.Resampling.LANCZOS)
            img.paste(ic, (W // 2 - 90, 180))
            
        draw.text((W // 2 - 280, 400), "SpaceSlimmer", fill=(255, 255, 255), font=get_font(64, bold=True))
        draw.text((W // 2 - 340, 490), "Keep Your Memories. Drop the File Size.", fill=(56, 189, 248), font=get_font(34, bold=True))
        
        # 3 Trust Pills
        pills = [
            "🛡️ 100% On-Device",
            "🔒 Zero Cloud Uploads",
            "⚡ GPU Accelerated"
        ]
        pw = 320
        for i, p in enumerate(pills):
            px = W // 2 - (3 * pw + 40) // 2 + i * (pw + 20)
            draw.rounded_rectangle([px, 580, px + pw, 650], radius=16, fill=(30, 41, 59), outline=(2, 132, 199), width=2)
            draw.text((px + 35, 600), p, fill=(255, 255, 255), font=get_font(22, bold=True))
            
        # Download on Google Play Button
        btn_w, btn_h = 560, 100
        bx = W // 2 - btn_w // 2
        by = 720
        draw.rounded_rectangle([bx, by, bx + btn_w, by + btn_h], radius=24, fill=(2, 132, 199))
        draw.text((bx + 60, by + 28), "GET IT ON GOOGLE PLAY", fill=(255, 255, 255), font=get_font(32, bold=True))
        
        draw.text((W // 2 - 240, 860), "Free • No Registration Required • Instant Use", fill=(148, 163, 184), font=get_font(22))
        
    return img

def main():
    os.makedirs("store/promo_frames", exist_ok=True)
    os.makedirs("store", exist_ok=True)
    os.makedirs("release/store_assets", exist_ok=True)
    
    print(f"Generating {TOTAL_FRAMES} frames (30s @ {FPS}fps)...")
    # For speed and smooth encoding, generate keyframes every 1 frame or interpolate
    for i in range(TOTAL_FRAMES):
        frame = render_frame(i)
        frame_path = f"store/promo_frames/frame_{i:04d}.png"
        frame.save(frame_path)
        if i % 150 == 0:
            print(f"  Rendered frame {i}/{TOTAL_FRAMES} ({i/FPS:.1f}s)")
            
    print("All frames rendered. Compiling to H.264 MP4 with ffmpeg...")
    out_mp4 = "store/promo_video.mp4"
    rel_mp4 = "release/store_assets/promo_video.mp4"
    cmd = [
        "ffmpeg", "-y",
        "-r", str(FPS),
        "-i", "store/promo_frames/frame_%04d.png",
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        "-crf", "20",
        "-preset", "fast",
        out_mp4
    ]
    subprocess.run(cmd, check=True)
    
    # Copy to release/store_assets
    import shutil
    shutil.copy2(out_mp4, rel_mp4)
    print(f"Promotional Video successfully compiled: {out_mp4} ({os.path.getsize(out_mp4)} bytes)")
    
    # Cleanup frames
    print("Cleaning up intermediate frames...")
    shutil.rmtree("store/promo_frames", ignore_errors=True)
    print("Done generating 30s Promo Video!")

if __name__ == "__main__":
    main()
