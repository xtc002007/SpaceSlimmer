# -*- coding: utf-8 -*-
"""
SpaceSlimmer Clean UI Screen Generator.
Generates 8 pixel-perfect, hyper-clean app UI source screens in 860x1600:
1. Media Jobs (Compression Results): Summer_Trip_4K.mp4, 1.24 GB -> 285 MB (-77%)
2. Split-Screen Video Compare: Before 1.24 GB vs After 285 MB with curtain slider
3. Safe Recovery Vault: 30-day sandbox protection, 1-tap restore undo
4. Visual Storage Treemap: Colorful squarified treemap blocks (DCIM, Videos, Downloads, Free)
5. Video Purpose & Presets: Balanced preset, WhatsApp 16MB, Discord 25MB, Gmail 50MB
6. Batch Photo & Audio: 48 camera photos, 8 voice notes (-80% space saved)
7. Duplicate & Big Files: Identical file detection, 1-tap clean wasted copies
8. 100% Offline & Privacy: Zero internet permissions, local GPU acceleration, zero tracking

Supports both English and Chinese locales.
Zero personal/children's photos, zero carrier status bars, zero text truncation.
"""
import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WIDTH = 860
HEIGHT = 1600

def get_font(size, bold=False, is_chinese=False):
    font_paths = []
    if is_chinese:
        font_paths = [
            "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
        ]
    else:
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

def draw_status_bar(draw, w, is_chinese=False):
    # Pure Android status bar: 09:41, Wi-Fi, Battery 100%
    f_time = get_font(22, bold=True, is_chinese=is_chinese)
    draw.text((40, 20), "09:41", fill=(226, 232, 240), font=f_time)
    
    # Wi-Fi icon at right
    wx = w - 120
    draw.arc([wx, 26, wx + 28, 54], start=210, end=330, fill=(226, 232, 240), width=3)
    draw.arc([wx + 5, 32, wx + 23, 50], start=210, end=330, fill=(226, 232, 240), width=3)
    draw.ellipse([wx + 11, 44, wx + 17, 50], fill=(226, 232, 240))
    
    # Battery icon at w - 70
    bx = w - 72
    draw.rounded_rectangle([bx, 28, bx + 36, 46], radius=4, outline=(226, 232, 240), width=2)
    draw.rectangle([bx + 36, 33, bx + 39, 41], fill=(226, 232, 240))
    draw.rounded_rectangle([bx + 4, 32, bx + 32, 42], radius=2, fill=(16, 185, 129))

def draw_top_nav(draw, title, subtitle=None, is_chinese=False):
    # App top bar with back arrow and title
    f_title = get_font(32, bold=True, is_chinese=is_chinese)
    f_sub = get_font(18, bold=False, is_chinese=is_chinese)
    
    # Back chevron
    draw.line([(42, 95), (54, 83)], fill=(255, 255, 255), width=3)
    draw.line([(42, 95), (54, 107)], fill=(255, 255, 255), width=3)
    
    draw.text((76, 78), title, fill=(255, 255, 255), font=f_title)
    if subtitle:
        draw.text((76, 118), subtitle, fill=(148, 163, 184), font=f_sub)

def create_base_canvas():
    img = Image.new("RGB", (WIDTH, HEIGHT), (15, 23, 42))
    draw = ImageDraw.Draw(img)
    # Background subtle dark gradient
    for y in range(HEIGHT):
        f = y / float(HEIGHT)
        r = int(15 + (10 - 15) * f)
        g = int(23 + (15 - 23) * f)
        b = int(42 + (30 - 42) * f)
        draw.line([(0, y), (WIDTH, y)], fill=(r, g, b))
    return img, draw

# ================= SCREEN 1: COMPRESSION RESULTS =================
def render_screen_01(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "压缩队列" if is_chinese else "Compression Queue"
    sub = "已完成 3 个项目 • 释放 3.28 GB" if is_chinese else "3 Completed • 3.28 GB Reclaimed"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Hero summary savings card (y: 160..300)
    draw.rounded_rectangle([36, 160, WIDTH - 36, 290], radius=20, fill=(19, 36, 56), outline=(2, 132, 199), width=2)
    f_hdr = get_font(20, bold=True, is_chinese=is_chinese)
    f_big = get_font(42, bold=True, is_chinese=is_chinese)
    f_lbl = get_font(18, bold=False, is_chinese=is_chinese)
    
    saved_title = "已释放相册空间" if is_chinese else "TOTAL SPACE RECLAIMED"
    draw.text((60, 180), saved_title, fill=(56, 189, 248), font=f_hdr)
    draw.text((60, 212), "3.28 GB", fill=(255, 255, 255), font=f_big)
    pct_txt = "立省 78% 空间 • 0 丢失原片" if is_chinese else "Saved 78% Space • Zero Original Loss"
    draw.text((260, 226), pct_txt, fill=(52, 211, 153), font=get_font(22, bold=True, is_chinese=is_chinese))
    
    # Item 1: Summer_Trip_4K.mp4 (Hero Card)
    y1 = 310
    draw.rounded_rectangle([36, y1, WIDTH - 36, y1 + 330], radius=18, fill=(30, 41, 59), outline=(59, 130, 246), width=2)
    # Thumbnail placeholder: scenic gradient
    draw.rounded_rectangle([56, y1 + 24, 210, y1 + 170], radius=12, fill=(14, 116, 144))
    draw.text((70, y1 + 80), "4K 60FPS", fill=(255, 255, 255), font=get_font(22, bold=True))
    
    # Title & Stats
    item_title = "Summer_Trip_4K.mp4"
    draw.text((230, y1 + 24), item_title, fill=(255, 255, 255), font=get_font(24, bold=True))
    draw.text((230, y1 + 60), "4K UHD (3840x2160) • HEVC 硬件编码", fill=(148, 163, 184), font=get_font(18, is_chinese=True))
    
    # Size pill
    draw.rounded_rectangle([230, y1 + 96, 420, y1 + 146], radius=10, fill=(38, 24, 34), outline=(244, 63, 94), width=1)
    draw.text((246, y1 + 108), "1.24 GB", fill=(244, 114, 182), font=get_font(22, bold=True))
    
    draw.text((435, y1 + 110), "➔", fill=(148, 163, 184), font=get_font(24, bold=True, is_chinese=True))
    
    draw.rounded_rectangle([470, y1 + 96, 660, y1 + 146], radius=10, fill=(16, 38, 44), outline=(16, 185, 129), width=1)
    draw.text((486, y1 + 108), "285 MB", fill=(52, 211, 153), font=get_font(22, bold=True))
    
    # Result Pill: Saved 955 MB (-77%)
    draw.rounded_rectangle([56, y1 + 195, WIDTH - 56, y1 + 255], radius=12, fill=(6, 78, 59))
    save_msg = "✓ 成功省下 955 MB (-77%) • 画质完全保留" if is_chinese else "✓ Reclaimed 955 MB (-77%) • Crystal-Clear HD"
    draw.text((76, y1 + 210), save_msg, fill=(209, 250, 229), font=get_font(20, bold=True, is_chinese=is_chinese))
    
    # Action buttons
    draw.rounded_rectangle([56, y1 + 270, 400, y1 + 315], radius=8, fill=(51, 65, 85))
    draw.text((120, y1 + 280), "前后对比" if is_chinese else "Compare Quality", fill=(255, 255, 255), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.rounded_rectangle([420, y1 + 270, WIDTH - 56, y1 + 315], radius=8, fill=(2, 132, 199))
    draw.text((480, y1 + 280), "查看相册" if is_chinese else "Open in Album", fill=(255, 255, 255), font=get_font(18, bold=True, is_chinese=is_chinese))
    
    # Item 2: Family_Drone_Sunset.mp4
    y2 = 660
    draw.rounded_rectangle([36, y2, WIDTH - 36, y2 + 200], radius=18, fill=(30, 41, 59))
    draw.rounded_rectangle([56, y2 + 24, 180, y2 + 140], radius=12, fill=(67, 56, 202))
    draw.text((70, y2 + 70), "1080P", fill=(255, 255, 255), font=get_font(20, bold=True))
    draw.text((200, y2 + 24), "Family_Drone_Sunset.mp4", fill=(255, 255, 255), font=get_font(22, bold=True))
    draw.text((200, y2 + 60), "860 MB ➔ 180 MB • 省 680 MB (-79%)" if is_chinese else "860 MB ➔ 180 MB • Saved 680 MB (-79%)", fill=(52, 211, 153), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((200, y2 + 100), "原片已安全存入保险箱 (30天还原)" if is_chinese else "Original backed up in Safe Vault (30d undo)", fill=(148, 163, 184), font=get_font(17, is_chinese=is_chinese))
    
    # Item 3: Conference_Keynote.mp4
    y3 = 880
    draw.rounded_rectangle([36, y3, WIDTH - 36, y3 + 200], radius=18, fill=(30, 41, 59))
    draw.rounded_rectangle([56, y3 + 24, 180, y3 + 140], radius=12, fill=(124, 45, 18))
    draw.text((70, y3 + 70), "1080P", fill=(255, 255, 255), font=get_font(20, bold=True))
    draw.text((200, y3 + 24), "Conference_Keynote.mp4", fill=(255, 255, 255), font=get_font(22, bold=True))
    draw.text((200, y3 + 60), "2.10 GB ➔ 450 MB • 省 1.65 GB (-78%)" if is_chinese else "2.10 GB ➔ 450 MB • Saved 1.65 GB (-78%)", fill=(52, 211, 153), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((200, y3 + 100), "原片已安全存入保险箱 (30天还原)" if is_chinese else "Original backed up in Safe Vault (30d undo)", fill=(148, 163, 184), font=get_font(17, is_chinese=is_chinese))
    
    # Bottom Fixed Action Button (y: 1420..1520)
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(2, 132, 199))
    btn_txt = "完成 • 全部保存至相册" if is_chinese else "Done • All Saved to Album"
    draw.text((240, btn_y + 26), btn_txt, fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 2: SPLIT-SCREEN VIDEO COMPARE =================
def render_screen_02(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "双轨画质对比" if is_chinese else "Side-by-Side Compare"
    sub = "毫秒级同步联动播放 • 100% 细节不糊" if is_chinese else "Millisecond Sync Playback • Pixel-Sharp"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Viewport Area (y: 160..1050)
    vy1, vy2 = 160, 1050
    draw.rounded_rectangle([36, vy1, WIDTH - 36, vy2], radius=20, fill=(0, 0, 0), outline=(59, 130, 246), width=2)
    
    # Scenic procedural graphic inside viewport
    # Left side: Nature mountain landscape
    for y in range(vy1 + 2, vy2 - 2):
        for x in range(38, WIDTH - 38, 4):
            factor = (y - vy1) / float(vy2 - vy1)
            # Create rich scenery gradient (Azure sky -> Mountain peaks -> Emerald lake)
            if factor < 0.4:
                r, g, b = int(30 + 40 * factor), int(70 + 80 * factor), int(160 + 50 * factor)
            elif factor < 0.7:
                r, g, b = int(45 + 50 * factor), int(80 + 30 * factor), int(100 - 30 * factor)
            else:
                r, g, b = int(10 + 20 * factor), int(90 + 50 * factor), int(110 + 20 * factor)
            draw.rectangle([x, y, x + 3, y], fill=(r, g, b))
    
    # Split slider line down middle (cx = WIDTH // 2 = 430)
    cx = WIDTH // 2
    draw.line([(cx, vy1 + 2), (cx, vy2 - 2)], fill=(56, 189, 248), width=4)
    
    # Curtain slider grab button
    draw.ellipse([cx - 40, 560, cx + 40, 640], fill=(2, 132, 199), outline=(255, 255, 255), width=3)
    draw.text((cx - 16, 582), "◀ ▶", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=True))
    
    # Left Label Overlay: ORIGINAL
    draw.rounded_rectangle([56, vy1 + 24, 280, vy1 + 84], radius=10, fill=(0, 0, 0, 180), outline=(244, 63, 94), width=2)
    orig_lbl = "原片 1.24 GB" if is_chinese else "ORIGINAL 1.24 GB"
    draw.text((72, vy1 + 38), orig_lbl, fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    
    # Right Label Overlay: COMPRESSED
    draw.rounded_rectangle([WIDTH - 290, vy1 + 24, WIDTH - 56, vy1 + 84], radius=10, fill=(0, 0, 0, 180), outline=(16, 185, 129), width=2)
    comp_lbl = "压缩后 285 MB" if is_chinese else "SLIMMED 285 MB"
    draw.text((WIDTH - 274, vy1 + 38), comp_lbl, fill=(52, 211, 153), font=get_font(20, bold=True, is_chinese=is_chinese))
    
    # Scrub bar under viewport (y: 1080..1160)
    draw.rounded_rectangle([36, 1080, WIDTH - 36, 1150], radius=12, fill=(30, 41, 59))
    draw.text((60, 1102), "01:24 / 03:40", fill=(148, 163, 184), font=get_font(18, bold=True))
    draw.rounded_rectangle([200, 1110, WIDTH - 60, 1120], radius=5, fill=(71, 85, 105))
    draw.rounded_rectangle([200, 1110, 520, 1120], radius=5, fill=(56, 189, 248))
    draw.ellipse([512, 1102, 532, 1128], fill=(255, 255, 255))
    
    # Metrics comparison cards (y: 1180..1360)
    draw.rounded_rectangle([36, 1180, WIDTH - 36, 1340], radius=16, fill=(30, 41, 59))
    m1_title = "码率对比 (Bitrate)" if is_chinese else "Bitrate Metrics"
    draw.text((60, 1200), m1_title, fill=(56, 189, 248), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((60, 1235), "原码率: 24.5 Mbps  ➔  压缩后: 5.6 Mbps (节省 77%)" if is_chinese else "Original: 24.5 Mbps  ➔  Slimmed: 5.6 Mbps (-77%)", fill=(255, 255, 255), font=get_font(19, bold=True, is_chinese=is_chinese))
    draw.text((60, 1275), "分辨率: 3840x2160 (4K) ➔ 1920x1080 (FHD超清)" if is_chinese else "Resolution: 4K UHD ➔ 1080p FHD (Crystal-Clear)", fill=(148, 163, 184), font=get_font(18, is_chinese=is_chinese))
    
    # Bottom Button
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(2, 132, 199))
    draw.text((250, btn_y + 26), "满意画质 • 确认替换" if is_chinese else "Confirm & Save Video", fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 3: SAFE VAULT UNDO =================
def render_screen_03(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "原片安全保险箱" if is_chinese else "Safe Recovery Vault"
    sub = "原片沙盒安全隔离 • 30天随时一键原样还原" if is_chinese else "30-Day Retention Sandbox • 1-Tap Undo Anytime"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Shield Trust Banner (y: 160..290)
    draw.rounded_rectangle([36, 160, WIDTH - 36, 280], radius=18, fill=(19, 36, 56), outline=(16, 185, 129), width=2)
    b_title = "🛡️ 100% 零删丢风险保障" if is_chinese else "🛡️ 100% Undoable • Zero Loss Guarantee"
    draw.text((60, 180), b_title, fill=(52, 211, 153), font=get_font(24, bold=True, is_chinese=is_chinese))
    b_desc = "替换原片时，原始超清文件自动移入本地安全沙盒。\n30 天宽限期内可随时无损复原回相册原路径。" if is_chinese else "When replacing originals, files are safely isolated in local storage.\nRestore uncompressed files back to camera roll anytime in 30 days."
    draw.text((60, 218), b_desc, fill=(226, 232, 240), font=get_font(17, is_chinese=is_chinese))
    
    # Vault Items List
    items = [
        ("Summer_Trip_4K.mp4", "1.24 GB", "原相册路径: /DCIM/Camera", "29 天后自动清理" if is_chinese else "Auto-expires in 29 days"),
        ("Family_Drone_Sunset.mp4", "860 MB", "原相册路径: /DCIM/Drone", "29 天后自动清理" if is_chinese else "Auto-expires in 29 days"),
        ("Hiking_Summit_Photo.jpg", "18.4 MB", "原相册路径: /DCIM/Camera", "26 天后自动清理" if is_chinese else "Auto-expires in 26 days"),
        ("Podcast_Interview.wav", "185 MB", "原录音路径: /Recordings", "22 天后自动清理" if is_chinese else "Auto-expires in 22 days"),
    ]
    
    start_y = 310
    for i, (name, size, path, expire) in enumerate(items):
        iy = start_y + i * 190
        draw.rounded_rectangle([36, iy, WIDTH - 36, iy + 165], radius=16, fill=(30, 41, 59), outline=(71, 85, 105), width=1)
        
        # File type pill
        draw.rounded_rectangle([56, iy + 20, 140, iy + 64], radius=8, fill=(59, 130, 246))
        draw.text((70, iy + 28), "SAFE", fill=(255, 255, 255), font=get_font(16, bold=True))
        
        draw.text((155, iy + 20), name, fill=(255, 255, 255), font=get_font(22, bold=True))
        draw.text((155, iy + 56), f"{size} • {expire}", fill=(244, 114, 182), font=get_font(17, bold=True, is_chinese=is_chinese))
        draw.text((56, iy + 85), path, fill=(148, 163, 184), font=get_font(15, is_chinese=is_chinese))
        
        # Undo button
        draw.rounded_rectangle([WIDTH - 240, iy + 95, WIDTH - 56, iy + 145], radius=10, fill=(2, 132, 199))
        undo_lbl = "一键还原原片" if is_chinese else "Restore to Album"
        draw.text((WIDTH - 225, iy + 107), undo_lbl, fill=(255, 255, 255), font=get_font(17, bold=True, is_chinese=is_chinese))
        
    # Bottom setting note
    draw.text((160, 1380), "可在「设置」中随时调整 7~30 天保留天数" if is_chinese else "Customizable 7 to 30 days retention in Settings", fill=(148, 163, 184), font=get_font(18, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 4: VISUAL STORAGE TREEMAP =================
def render_screen_04(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "全景存储区块树图" if is_chinese else "Visual Storage Treemap"
    sub = "自适应色块透视机身存储 • 一秒揪出大文件" if is_chinese else "Interactive Block Map • Spot Gigabyte Hogs"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Storage partition summary pill
    draw.rounded_rectangle([36, 160, WIDTH - 36, 230], radius=14, fill=(30, 41, 59))
    draw.text((56, 175), "内部共享存储空间 (128 GB)" if is_chinese else "Internal Shared Storage (128 GB)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((56, 202), "已用 89.4 GB / 剩余 38.6 GB (深度扫描 100% 覆盖)" if is_chinese else "Used: 89.4 GB / Free: 38.6 GB (100% Full Scan)", fill=(56, 189, 248), font=get_font(16, is_chinese=is_chinese))
    
    # Big Treemap Canvas Area (y: 250..1200)
    tx, ty, tw, th = 36, 250, WIDTH - 72, 950
    draw.rounded_rectangle([tx, ty, tx + tw, ty + th], radius=18, fill=(15, 23, 42), outline=(59, 130, 246), width=2)
    
    # Block 1: DCIM / Camera (Largest block, left top 60% width, 65% height)
    b1_w = int(tw * 0.62)
    b1_h = int(th * 0.65)
    draw.rounded_rectangle([tx + 4, ty + 4, tx + b1_w - 4, ty + b1_h - 4], radius=12, fill=(2, 132, 199), outline=(56, 189, 248), width=3)
    draw.text((tx + 24, ty + 24), "DCIM / Camera", fill=(255, 255, 255), font=get_font(26, bold=True))
    draw.text((tx + 24, ty + 64), "64.4 GB (48%)", fill=(255, 255, 255), font=get_font(28, bold=True))
    draw.text((tx + 24, ty + 104), "1,240 个视频与相片 • 可瘦身 45 GB" if is_chinese else "1,240 Videos & Photos • Reclaimable 45 GB", fill=(224, 242, 254), font=get_font(18, is_chinese=is_chinese))
    
    # Active inspection indicator inside DCIM
    draw.rounded_rectangle([tx + 24, ty + b1_h - 90, tx + b1_w - 30, ty + b1_h - 24], radius=10, fill=(15, 23, 42))
    draw.text((tx + 44, ty + b1_h - 70), "🔍 点击进入批量一键压缩" if is_chinese else "🔍 Tap to Batch Compress", fill=(56, 189, 248), font=get_font(20, bold=True, is_chinese=is_chinese))
    
    # Block 2: Movies / Videos (Right top 38% width, 45% height)
    b2_x = tx + b1_w
    b2_w = tw - b1_w
    b2_h = int(th * 0.45)
    draw.rounded_rectangle([b2_x + 4, ty + 4, tx + tw - 4, ty + b2_h - 4], radius=12, fill=(79, 70, 229), outline=(129, 140, 248), width=1)
    draw.text((b2_x + 18, ty + 20), "Movies / Clips", fill=(255, 255, 255), font=get_font(22, bold=True))
    draw.text((b2_x + 18, ty + 54), "18.2 GB", fill=(255, 255, 255), font=get_font(24, bold=True))
    
    # Block 3: Downloads (Right middle)
    b3_y = ty + b2_h
    b3_h = int(th * 0.30)
    draw.rounded_rectangle([b2_x + 4, b3_y + 4, tx + tw - 4, b3_y + b3_h - 4], radius=12, fill=(217, 119, 6), outline=(251, 191, 36), width=1)
    draw.text((b2_x + 18, b3_y + 16), "Downloads", fill=(255, 255, 255), font=get_font(22, bold=True))
    draw.text((b2_x + 18, b3_y + 46), "9.6 GB", fill=(255, 255, 255), font=get_font(24, bold=True))
    
    # Block 4: Audio / Music (Bottom left)
    b4_y = ty + b1_h
    b4_w = int(tw * 0.36)
    b4_h = th - b1_h
    draw.rounded_rectangle([tx + 4, b4_y + 4, tx + b4_w - 4, ty + th - 4], radius=12, fill=(5, 150, 105), outline=(52, 211, 153), width=1)
    draw.text((tx + 16, b4_y + 16), "Audio", fill=(255, 255, 255), font=get_font(20, bold=True))
    draw.text((tx + 16, b4_y + 46), "5.4 GB", fill=(255, 255, 255), font=get_font(22, bold=True))
    
    # Block 5: Free Space (Bottom right)
    b5_x = tx + b4_w
    b5_y = ty + b1_h
    b5_w = tw - b4_w
    draw.rounded_rectangle([b5_x + 4, b5_y + 4, tx + tw - 4, ty + th - 4], radius=12, fill=(51, 65, 85), outline=(100, 116, 139), width=1)
    draw.text((b5_x + 18, b5_y + 16), "Free Space (剩余)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=True))
    draw.text((b5_x + 18, b5_y + 46), "38.6 GB", fill=(52, 211, 153), font=get_font(24, bold=True))
    
    # Bottom Action Button
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(2, 132, 199))
    draw.text((220, btn_y + 26), "一键透视扫描全部目录" if is_chinese else "Deep Scan Full Storage", fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 5: COMPRESSION PRESETS & SHARING =================
def render_screen_05(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "压缩配置与目标大小" if is_chinese else "Smart Compression Presets"
    sub = "社交软件指定大小 • 智能演算最优画质" if is_chinese else "WhatsApp & Discord Limits • Optimal Bitrate"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Segmented Control: [ Videos ] [ Photos ] [ Audio ] (y: 160..230)
    draw.rounded_rectangle([36, 160, WIDTH - 36, 230], radius=14, fill=(30, 41, 59))
    # Active tab: Videos (highlighted blue)
    seg_w = (WIDTH - 72) // 3
    draw.rounded_rectangle([40, 164, 40 + seg_w, 226], radius=10, fill=(2, 132, 199))
    draw.text((80, 180), "视频 (12)" if is_chinese else "Videos (12)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((40 + seg_w + 60, 180), "相片" if is_chinese else "Photos", fill=(148, 163, 184), font=get_font(20, is_chinese=is_chinese))
    draw.text((40 + seg_w * 2 + 60, 180), "音频" if is_chinese else "Audio", fill=(148, 163, 184), font=get_font(20, is_chinese=is_chinese))
    
    # Quick Social Media Presets Section (y: 260..420)
    draw.text((40, 260), "社交直传快捷目标 (保证不超标)" if is_chinese else "Social Share Presets (Guaranteed Under Limit)", fill=(56, 189, 248), font=get_font(20, bold=True, is_chinese=is_chinese))
    
    presets = [
        ("WhatsApp", "16 MB", "聊天视频免裁剪"),
        ("Discord", "25 MB", "无 Nitro 直传"),
        ("Gmail", "50 MB", "大附件邮件投递")
    ] if is_chinese else [
        ("WhatsApp", "16 MB", "No cropping required"),
        ("Discord", "25 MB", "No Nitro needed"),
        ("Gmail", "50 MB", "Standard attachment")
    ]
    
    pw = (WIDTH - 72 - 32) // 3
    for i, (app_name, lim, desc) in enumerate(presets):
        px = 36 + i * (pw + 16)
        draw.rounded_rectangle([px, 300, px + pw, 420], radius=14, fill=(30, 41, 59), outline=(59, 130, 246) if i == 0 else (71, 85, 105), width=2 if i == 0 else 1)
        draw.text((px + 20, 316), app_name, fill=(255, 255, 255), font=get_font(20, bold=True))
        draw.text((px + 20, 348), lim, fill=(52, 211, 153), font=get_font(26, bold=True))
        draw.text((px + 16, 386), desc, fill=(148, 163, 184), font=get_font(13, is_chinese=True))
        
    # Quality Presets Row (Fixed Balanced without truncation)
    draw.text((40, 460), "压缩模式 (已消除文字截断)" if is_chinese else "Compression Mode (Full Text)", fill=(56, 189, 248), font=get_font(20, bold=True, is_chinese=is_chinese))
    
    modes = [
        ("均衡模式 (Balanced)", "省 70%~80%", "推荐日常拍摄", True),
        ("超清模式 (High Quality)", "省 50%~60%", "重要旅行回忆", False),
        ("极限瘦身 (Max Savings)", "省 85%+", "微信工作备忘", False)
    ] if is_chinese else [
        ("Balanced Mode", "Save 70%~80%", "Recommended for daily", True),
        ("High Quality", "Save 50%~60%", "Crystal-clear 4K clips", False),
        ("Max Savings", "Save 85%+", "Fast sharing & archives", False)
    ]
    
    for i, (m_title, m_pct, m_sub, is_active) in enumerate(modes):
        my = 500 + i * 115
        draw.rounded_rectangle([36, my, WIDTH - 36, my + 95], radius=14, fill=(19, 36, 56) if is_active else (30, 41, 59), outline=(2, 132, 199) if is_active else (71, 85, 105), width=2 if is_active else 1)
        draw.text((60, my + 18), m_title, fill=(255, 255, 255), font=get_font(22, bold=True, is_chinese=is_chinese))
        draw.text((60, my + 54), m_sub, fill=(148, 163, 184), font=get_font(16, is_chinese=is_chinese))
        draw.text((WIDTH - 240, my + 30), m_pct, fill=(52, 211, 153), font=get_font(24, bold=True, is_chinese=is_chinese))
        
    # Advanced Options Box (y: 880..1300)
    draw.rounded_rectangle([36, 880, WIDTH - 36, 1180], radius=16, fill=(30, 41, 59))
    draw.text((60, 905), "硬件加速引擎: GPU HEVC (H.265)" if is_chinese else "Hardware Engine: GPU HEVC (H.265)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((60, 940), "保持原始拍摄时间、位置 GPS 与声道" if is_chinese else "Preserves creation date, GPS, and audio tracks", fill=(148, 163, 184), font=get_font(16, is_chinese=is_chinese))
    draw.line([(60, 980), (WIDTH - 60, 980)], fill=(51, 65, 85))
    
    draw.text((60, 1010), "原片安全保险箱: 启用 (30天随时还原)" if is_chinese else "Safe Vault Protection: Enabled (30d undo)", fill=(52, 211, 153), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((60, 1045), "替换原片后原文件移入隔离沙盒，绝不永久丢失" if is_chinese else "Originals kept in sandbox. Never permanently deleted.", fill=(148, 163, 184), font=get_font(16, is_chinese=is_chinese))
    
    # Bottom Button
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(2, 132, 199))
    draw.text((220, btn_y + 26), "开始极速压缩 (预估省 4.8 GB)" if is_chinese else "Start Compression (Save ~4.8 GB)", fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 6: BATCH PHOTO & AUDIO =================
def render_screen_06(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "照片与音频批量瘦身" if is_chinese else "Batch Photo & Audio"
    sub = "相册大图与录音批量处理 • 一键减负" if is_chinese else "Bulk Library Cleanup • Shrink Photos & Notes"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Section 1: Photos Batch (y: 160..600)
    draw.rounded_rectangle([36, 160, WIDTH - 36, 560], radius=18, fill=(30, 41, 59))
    draw.text((60, 185), "📷 相册照片批量压缩 (48 张已选)" if is_chinese else "📷 Batch Camera Photos (48 Selected)", fill=(56, 189, 248), font=get_font(22, bold=True, is_chinese=is_chinese))
    
    # Stats comparison
    draw.rounded_rectangle([60, 230, 380, 310], radius=12, fill=(15, 23, 42))
    draw.text((80, 245), "原片体积: 218 MB" if is_chinese else "Original: 218 MB", fill=(244, 114, 182), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.text((80, 275), "平均每张 4.5 MB" if is_chinese else "Avg 4.5 MB / photo", fill=(148, 163, 184), font=get_font(15, is_chinese=is_chinese))
    
    draw.rounded_rectangle([420, 230, WIDTH - 60, 310], radius=12, fill=(16, 38, 44))
    draw.text((440, 245), "压缩后: 48 MB (-78%)" if is_chinese else "Slimmed: 48 MB (-78%)", fill=(52, 211, 153), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.text((440, 275), "立省 170 MB • 保持清晰" if is_chinese else "Saved 170 MB • High Quality", fill=(148, 163, 184), font=get_font(15, is_chinese=is_chinese))
    
    # Photo grid mini thumbnails (4 in a row)
    for c in range(4):
        gx = 60 + c * 185
        draw.rounded_rectangle([gx, 340, gx + 165, 470], radius=10, fill=(14, 116, 144 + c * 20))
        draw.text((gx + 20, 430), f"IMG_{c+101}.jpg", fill=(255, 255, 255), font=get_font(14))
    draw.text((60, 500), "支持 JPEG, PNG, WebP • 自动保留 EXIF 拍摄时间与 GPS" if is_chinese else "Supports JPEG, PNG, WebP • Retains EXIF metadata & GPS", fill=(148, 163, 184), font=get_font(16, is_chinese=is_chinese))
    
    # Section 2: Audio Batch (y: 600..1050)
    draw.rounded_rectangle([36, 600, WIDTH - 36, 1000], radius=18, fill=(30, 41, 59))
    draw.text((60, 625), "🎙️ 录音与无损音频转制 (8 项已选)" if is_chinese else "🎙️ Voice Notes & Audio Transcoding", fill=(56, 189, 248), font=get_font(22, bold=True, is_chinese=is_chinese))
    
    draw.rounded_rectangle([60, 670, 380, 750], radius=12, fill=(15, 23, 42))
    draw.text((80, 685), "原体积: 320 MB (WAV)" if is_chinese else "Original: 320 MB (WAV)", fill=(244, 114, 182), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.text((80, 715), "无损超大录音" if is_chinese else "Uncompressed WAV", fill=(148, 163, 184), font=get_font(15, is_chinese=is_chinese))
    
    draw.rounded_rectangle([420, 670, WIDTH - 60, 750], radius=12, fill=(16, 38, 44))
    draw.text((440, 685), "转制后: 48 MB (-85%)" if is_chinese else "Slimmed: 48 MB (-85%)", fill=(52, 211, 153), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.text((440, 715), "AAC / Opus 高保真" if is_chinese else "AAC / Opus High Fidelity", fill=(148, 163, 184), font=get_font(15, is_chinese=is_chinese))
    
    draw.text((60, 790), "• 会议录音_20261104.wav (185 MB ➔ 28 MB)" if is_chinese else "• Meeting_Audio_20261104.wav (185 MB ➔ 28 MB)", fill=(255, 255, 255), font=get_font(18, is_chinese=is_chinese))
    draw.text((60, 830), "• 课堂讲座录音.flac (135 MB ➔ 20 MB)" if is_chinese else "• Lecture_Podcast.flac (135 MB ➔ 20 MB)", fill=(255, 255, 255), font=get_font(18, is_chinese=is_chinese))
    draw.text((60, 880), "高保真声学编码，语音清晰度 100% 保留" if is_chinese else "Acoustic speech encoding: 100% vocal clarity retained", fill=(148, 163, 184), font=get_font(16, is_chinese=is_chinese))
    
    # Bottom Button
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(2, 132, 199))
    draw.text((220, btn_y + 26), "一键瘦身 (立省 442 MB)" if is_chinese else "Start Batch Compression (Save 442 MB)", fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 7: DUPLICATE & BIG FILES =================
def render_screen_07(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "重复文件与大文件清理" if is_chinese else "Duplicate & Big Files"
    sub = "精准哈希比对 • 一键清除重复副本" if is_chinese else "Multi-Point Hash Matching • Clean Wasted Duplicates"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Segmented Tab: [ 重复文件 (Duplicates) ] [ 超大文件 (Big Files) ]
    draw.rounded_rectangle([36, 160, WIDTH - 36, 230], radius=14, fill=(30, 41, 59))
    seg_w = (WIDTH - 72) // 2
    draw.rounded_rectangle([40, 164, 40 + seg_w, 226], radius=10, fill=(2, 132, 199))
    draw.text((120, 180), "重复文件 (3组)" if is_chinese else "Duplicates (3 groups)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((40 + seg_w + 100, 180), "超大文件 (>100MB)" if is_chinese else "Big Files (>100MB)", fill=(148, 163, 184), font=get_font(20, is_chinese=is_chinese))
    
    # Banner: 1.46 GB wasted by duplicates
    draw.rounded_rectangle([36, 250, WIDTH - 36, 350], radius=16, fill=(38, 24, 34), outline=(244, 63, 94), width=1)
    draw.text((60, 270), "⚠️ 发现 3 组完全重复文件 • 浪费 1.46 GB" if is_chinese else "⚠️ 3 Duplicate Groups Found • 1.46 GB Wasted", fill=(244, 114, 182), font=get_font(22, bold=True, is_chinese=is_chinese))
    draw.text((60, 305), "智能推荐保留拍摄时间最早的高清原片，安全删除副本" if is_chinese else "Smart Select: Keep highest quality originals, delete duplicates", fill=(226, 232, 240), font=get_font(16, is_chinese=is_chinese))
    
    # Group 1
    y1 = 370
    draw.rounded_rectangle([36, y1, WIDTH - 36, y1 + 220], radius=16, fill=(30, 41, 59))
    draw.text((60, y1 + 18), "Group 1: Screen_Recording_4K.mp4" if not is_chinese else "第 1 组: 4K屏幕录像副本 (2个完全相同文件)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((WIDTH - 200, y1 + 18), "浪费 540 MB" if is_chinese else "Wasted 540 MB", fill=(244, 114, 182), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.text((60, y1 + 60), "✓ [保留] /DCIM/ScreenRecorder/rec_01.mp4 (540 MB)" if is_chinese else "✓ [Keep] /DCIM/ScreenRecorder/rec_01.mp4 (540 MB)", fill=(52, 211, 153), font=get_font(17, is_chinese=is_chinese))
    draw.text((60, y1 + 100), "✗ [删除] /Download/rec_01_copy.mp4 (540 MB)" if is_chinese else "✗ [Delete] /Download/rec_01_copy.mp4 (540 MB)", fill=(244, 114, 182), font=get_font(17, is_chinese=is_chinese))
    draw.rounded_rectangle([60, y1 + 145, 260, y1 + 195], radius=8, fill=(2, 132, 199))
    draw.text((85, y1 + 158), "比对哈希值 (Hash: Match)" if is_chinese else "Exact Match (Hash)", fill=(255, 255, 255), font=get_font(15, bold=True, is_chinese=is_chinese))
    
    # Group 2
    y2 = 610
    draw.rounded_rectangle([36, y2, WIDTH - 36, y2 + 220], radius=16, fill=(30, 41, 59))
    draw.text((60, y2 + 18), "Group 2: Drone_Flyover_RAW.mp4" if not is_chinese else "第 2 组: 无人机航拍素材副本 (2个文件)", fill=(255, 255, 255), font=get_font(20, bold=True, is_chinese=is_chinese))
    draw.text((WIDTH - 200, y2 + 18), "浪费 920 MB" if is_chinese else "Wasted 920 MB", fill=(244, 114, 182), font=get_font(18, bold=True, is_chinese=is_chinese))
    draw.text((60, y2 + 60), "✓ [保留] /DCIM/Drone/flight_raw.mp4 (920 MB)" if is_chinese else "✓ [Keep] /DCIM/Drone/flight_raw.mp4 (920 MB)", fill=(52, 211, 153), font=get_font(17, is_chinese=is_chinese))
    draw.text((60, y2 + 100), "✗ [删除] /Backup/temp_flight.mp4 (920 MB)" if is_chinese else "✗ [Delete] /Backup/temp_flight.mp4 (920 MB)", fill=(244, 114, 182), font=get_font(17, is_chinese=is_chinese))
    draw.rounded_rectangle([60, y2 + 145, 260, y2 + 195], radius=8, fill=(2, 132, 199))
    draw.text((85, y2 + 158), "比对哈希值 (Hash: Match)" if is_chinese else "Exact Match (Hash)", fill=(255, 255, 255), font=get_font(15, bold=True, is_chinese=is_chinese))
    
    # Bottom Button
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(244, 63, 94))
    draw.text((220, btn_y + 26), "一键清除重复副本 (立省 1.46 GB)" if is_chinese else "Clean Selected Duplicates (1.46 GB)", fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

# ================= SCREEN 8: 100% OFFLINE & PRIVATE =================
def render_screen_08(is_chinese=False):
    img, draw = create_base_canvas()
    draw_status_bar(draw, WIDTH, is_chinese)
    title = "隐私合规与本机安全" if is_chinese else "100% Offline & Private"
    sub = "零网络权限 • 零云端上传 • 数据不出设备" if is_chinese else "Zero Network Permissions • Zero Cloud Uploads"
    draw_top_nav(draw, title, sub, is_chinese)
    
    # Giant Shield Hero Badge (y: 160..480)
    draw.rounded_rectangle([36, 160, WIDTH - 36, 480], radius=22, fill=(19, 36, 56), outline=(2, 132, 199), width=2)
    
    # Center shield icon
    cx = WIDTH // 2
    draw.ellipse([cx - 50, 200, cx + 50, 300], fill=(2, 132, 199))
    draw.text((cx - 24, 222), "🛡️", font=get_font(42, bold=True, is_chinese=True))
    
    draw.text((cx - 180 if is_chinese else cx - 190, 320), "100% 本机离线 • 绝不联网" if is_chinese else "100% On-Device • Zero Network", fill=(255, 255, 255), font=get_font(28, bold=True, is_chinese=is_chinese))
    badge_sub = "SpaceSlimmer 清单无 INTERNET 权限申请，从底层断绝云端泄露" if is_chinese else "No INTERNET permission requested in Manifest. Physical zero upload."
    draw.text((cx - 270 if is_chinese else cx - 290, 370), badge_sub, fill=(56, 189, 248), font=get_font(18, is_chinese=is_chinese))
    
    # 4 Trust Guarantees
    trusts = [
        ("🚫 零网络权限申请", "物理断网机制，任何个人照片与视频绝不可能上传到任何服务器"),
        ("👤 无需注册账号", "即装即用，不收集任何邮箱、手机号或设备识别符"),
        ("⚡ 芯片级硬件加速驱动", "由高通/联发科/Exynos 本地 GPU 驱动，发热低、速度快、省电池"),
        ("📦 独家 30 天原片保险箱", "本地沙盒安全备份，随时一键还原原片，享有无风险后悔权"),
    ] if is_chinese else [
        ("🚫 Zero Network Permissions", "No INTERNET in manifest. Your videos & photos physically never leave phone."),
        ("👤 No Account Required", "Instant access without email, phone number, or login credentials."),
        ("⚡ GPU Hardware Accelerated", "Driven by Qualcomm/MediaTek on-device chips. Fast, cool, battery friendly."),
        ("📦 30-Day Safe Recovery Vault", "Isolated sandbox retention. Restore original files anytime with 1 tap."),
    ]
    
    start_y = 510
    for i, (t_title, t_desc) in enumerate(trusts):
        ty = start_y + i * 155
        draw.rounded_rectangle([36, ty, WIDTH - 36, ty + 135], radius=16, fill=(30, 41, 59))
        draw.text((60, ty + 20), t_title, fill=(52, 211, 153), font=get_font(22, bold=True, is_chinese=is_chinese))
        draw.text((60, ty + 60), t_desc, fill=(226, 232, 240), font=get_font(16, is_chinese=is_chinese))
        
    # Lifetime Reclaimed Stats Card
    draw.rounded_rectangle([36, 1160, WIDTH - 36, 1300], radius=16, fill=(15, 23, 42), outline=(59, 130, 246), width=1)
    draw.text((60, 1185), "累计已为本机节省空间" if is_chinese else "Lifetime Device Storage Reclaimed", fill=(148, 163, 184), font=get_font(18, is_chinese=is_chinese))
    draw.text((60, 1220), "18.4 GB (已成功压缩 86 个项目)" if is_chinese else "18.4 GB (86 Media Items Processed)", fill=(56, 189, 248), font=get_font(28, bold=True, is_chinese=is_chinese))
    
    # Bottom Button
    btn_y = 1420
    draw.rounded_rectangle([36, btn_y, WIDTH - 36, btn_y + 90], radius=16, fill=(2, 132, 199))
    draw.text((280, btn_y + 26), "确定 • 安全退出" if is_chinese else "Back to Home", fill=(255, 255, 255), font=get_font(24, bold=True, is_chinese=is_chinese))
    
    return img

def main():
    os.makedirs("store/screen-pic/clean/en", exist_ok=True)
    os.makedirs("store/screen-pic/clean/zh", exist_ok=True)
    
    renderers = [
        (1, render_screen_01),
        (2, render_screen_02),
        (3, render_screen_03),
        (4, render_screen_04),
        (5, render_screen_05),
        (6, render_screen_06),
        (7, render_screen_07),
        (8, render_screen_08),
    ]
    
    print("Generating 8 Clean English UI Screens...")
    for idx, fn in renderers:
        img_en = fn(is_chinese=False)
        out_en = f"store/screen-pic/clean/en/screenshot_{idx:02d}.jpg"
        img_en.save(out_en, "JPEG", quality=95)
        # Also copy to root clean/ for backward compatibility
        if idx <= 5:
            img_en.save(f"store/screen-pic/clean/screenshot_{idx:02d}.jpg", "JPEG", quality=95)
        print(f"  Saved EN: {out_en}")
        
    print("Generating 8 Clean Chinese UI Screens...")
    for idx, fn in renderers:
        img_zh = fn(is_chinese=True)
        out_zh = f"store/screen-pic/clean/zh/screenshot_{idx:02d}.jpg"
        img_zh.save(out_zh, "JPEG", quality=95)
        print(f"  Saved ZH: {out_zh}")
        
    print("All 16 clean UI screens generated with zero personal photos, zero carrier bars, and zero truncation!")

if __name__ == "__main__":
    main()
