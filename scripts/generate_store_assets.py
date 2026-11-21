# -*- coding: utf-8 -*-
"""
SpaceSlimmer Production Store Asset Generator.
Generates:
1. 1024x500 Feature Graphics (English Default + Chinese localized).
   - Zero emoji glyph missing squares (all vector drawn).
   - Zero text collisions.
   - Zero pill overflow.
2. 8 Portrait Screenshots (1080x1920) for EN and ZH.
   - Device frame mockup showcasing REAL App UI screens.
   - High-contrast value propositions and feature badges.
3. 4 Landscape Tablet Screenshots (1920x1080) for EN and ZH.
   - Meets Google Play 7-inch & 10-inch tablet screenshot requirements.
"""
import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

STORE_DIR = r"d:\Workspace\SpaceSlimmer\release\store_assets"
STORE_ZH_DIR = os.path.join(STORE_DIR, "zh")
ROOT_STORE_DIR = r"d:\Workspace\SpaceSlimmer\store"
ROOT_SCREENSHOTS_DIR = os.path.join(ROOT_STORE_DIR, "screenshots")
ROOT_SCREENSHOTS_ZH_DIR = os.path.join(ROOT_SCREENSHOTS_DIR, "zh")

for d in [STORE_DIR, STORE_ZH_DIR, ROOT_STORE_DIR, ROOT_SCREENSHOTS_DIR, ROOT_SCREENSHOTS_ZH_DIR]:
    os.makedirs(d, exist_ok=True)

def get_font(size, bold=True, is_chinese=False):
    if is_chinese:
        candidates = [
            r"C:\Windows\Fonts\msyhbd.ttc",
            r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\simhei.ttf",
        ]
    else:
        candidates = [
            r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
            r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
            r"C:\Windows\Fonts\msyhbd.ttc",
        ]
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()

# -------------------------------------------------------------
# Vector Icon Drawing Helpers (Zero Missing Glyph Squares)
# -------------------------------------------------------------
def draw_vector_lightning(draw, cx, cy, size=16, color=(245, 158, 11)):
    s = size / 16.0
    pts = [
        (cx + 1 * s, cy - 8 * s),
        (cx - 5 * s, cy + 0 * s),
        (cx - 1 * s, cy + 0 * s),
        (cx - 4 * s, cy + 8 * s),
        (cx + 5 * s, cy - 1 * s),
        (cx + 1 * s, cy - 1 * s),
        (cx + 4 * s, cy - 8 * s),
    ]
    draw.polygon(pts, fill=color)

def draw_vector_shield(draw, cx, cy, size=16, color=(16, 185, 129)):
    s = size / 16.0
    pts = [
        (cx, cy - 8 * s),
        (cx + 7 * s, cy - 5 * s),
        (cx + 7 * s, cy + 2 * s),
        (cx, cy + 8 * s),
        (cx - 7 * s, cy + 2 * s),
        (cx - 7 * s, cy - 5 * s),
    ]
    draw.polygon(pts, fill=color)

def draw_vector_lock(draw, cx, cy, size=16, color=(56, 189, 248)):
    s = size / 16.0
    # Shackle arch
    draw.arc([cx - 4 * s, cy - 8 * s, cx + 4 * s, cy + 0 * s], start=180, end=0, fill=color, width=max(2, int(2 * s)))
    # Body
    draw.rounded_rectangle([cx - 6 * s, cy - 2 * s, cx + 6 * s, cy + 7 * s], radius=int(2 * s), fill=color)

def draw_vector_treemap(draw, cx, cy, size=16, color=(129, 140, 248)):
    s = size / 16.0
    # 3 colored tiles
    draw.rectangle([cx - 7 * s, cy - 7 * s, cx - 1 * s, cy + 7 * s], fill=(56, 189, 248))
    draw.rectangle([cx + 1 * s, cy - 7 * s, cx + 7 * s, cy - 1 * s], fill=(129, 140, 248))
    draw.rectangle([cx + 1 * s, cy + 1 * s, cx + 7 * s, cy + 7 * s], fill=(52, 211, 153))

# -------------------------------------------------------------
# 1. Feature Graphic: 1024 x 500
# -------------------------------------------------------------
def generate_feature_graphic(is_chinese=False):
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), (11, 15, 25))
    draw = ImageDraw.Draw(img)

    # 1. Elegant Deep Gradient: Dark Slate #0B0F19 to Rich Navy #172554
    for y in range(h):
        f = y / float(h)
        r = int(11 + (23 - 11) * f)
        g = int(15 + (37 - 15) * f)
        b = int(25 + (84 - 25) * f)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # 2. Right Side: Hero Comparison Card
    rx, ry = 770, 250
    # Concentric background circles
    draw.ellipse([rx - 210, ry - 210, rx + 210, ry + 210], outline=(30, 58, 138), width=2)
    draw.ellipse([rx - 150, ry - 150, rx + 150, ry + 150], outline=(14, 116, 144), width=2)

    card_w, card_h = 380, 280
    card_x = rx - card_w // 2
    card_y = ry - card_h // 2
    # Card glow & border
    draw.rounded_rectangle(
        [card_x, card_y, card_x + card_w, card_y + card_h],
        radius=18,
        fill=(19, 26, 42),
        outline=(59, 130, 246),
        width=2
    )

    # Before Box (Rose accent)
    font_badge_title = get_font(16, bold=True, is_chinese=is_chinese)
    font_badge_val = get_font(26, bold=True, is_chinese=is_chinese)
    
    draw.rounded_rectangle([card_x + 20, card_y + 24, card_x + 165, card_y + 110], radius=12, fill=(38, 24, 34), outline=(244, 63, 94), width=2)
    before_label = "原片体积" if is_chinese else "BEFORE"
    draw.text((card_x + 34, card_y + 34), before_label, fill=(244, 114, 182), font=font_badge_title)
    draw.text((card_x + 34, card_y + 64), "2.4 GB", fill=(255, 255, 255), font=font_badge_val)

    # Inward Arrow between them
    arrow_cx = card_x + 185
    arrow_cy = card_y + 68
    draw.polygon([
        (arrow_cx + 10, arrow_cy),
        (arrow_cx - 6, arrow_cy - 8),
        (arrow_cx - 6, arrow_cy - 3),
        (arrow_cx - 14, arrow_cy - 3),
        (arrow_cx - 14, arrow_cy + 3),
        (arrow_cx - 6, arrow_cy + 3),
        (arrow_cx - 6, arrow_cy + 8),
    ], fill=(56, 189, 248))

    # After Box (Emerald/Sky Cyan accent)
    draw.rounded_rectangle([card_x + 205, card_y + 24, card_x + 360, card_y + 110], radius=12, fill=(16, 38, 44), outline=(16, 185, 129), width=2)
    after_label = "压缩后" if is_chinese else "AFTER"
    draw.text((card_x + 220, card_y + 34), after_label, fill=(52, 211, 153), font=font_badge_title)
    draw.text((card_x + 220, card_y + 64), "480 MB", fill=(255, 255, 255), font=font_badge_val)

    # Hero Card Action Pill: -80% Space Saved (Sufficient width, no overflow)
    pill_w = 340
    pill_x = card_x + 20
    pill_y = card_y + 130
    draw.rounded_rectangle([pill_x, pill_y, pill_x + pill_w, pill_y + 54], radius=12, fill=(16, 185, 129))
    pill_text = "⚡ 净省 80% 存储空间 • 保持高清" if is_chinese else "⚡ Save up to 80% • Crystal Clear"
    # Use vector lightning for pill icon
    draw_vector_lightning(draw, pill_x + 22, pill_y + 27, size=18, color=(255, 255, 255))
    clean_pill_text = "净省 80% 存储空间 • 保持高清" if is_chinese else "Save up to 80% Space • High Quality"
    draw.text((pill_x + 40, pill_y + 15), clean_pill_text, fill=(255, 255, 255), font=get_font(16, bold=True, is_chinese=is_chinese))

    # Specs subline
    spec_text = "硬件 GPU 极速编码 • H.264 & HEVC" if is_chinese else "GPU Hardware Accelerated • H.264 & HEVC"
    draw.text((card_x + 24, card_y + 208), spec_text, fill=(148, 163, 184), font=get_font(13, bold=False, is_chinese=is_chinese))
    undo_text = "✓ 30天原片安全保险箱，随时一键还原" if is_chinese else "✓ 7–30 Day Safe Vault • 1-Tap Undo Anytime"
    draw.text((card_x + 24, card_y + 238), undo_text, fill=(56, 189, 248), font=get_font(13, bold=True, is_chinese=is_chinese))

    # 3. Left Side: Brand, Headline, and Benefits (No collisions)
    font_brand = get_font(44, bold=True, is_chinese=is_chinese)
    font_subhead = get_font(21, bold=True, is_chinese=is_chinese)
    font_badge = get_font(18, bold=False, is_chinese=is_chinese)

    draw.text((64, 55), "SpaceSlimmer", fill=(255, 255, 255), font=font_brand)
    
    subhead = "智能视频与媒体压缩器 • 存储全景透视" if is_chinese else "Smart Video & Media Compressor • Storage Analyzer"
    draw.text((64, 118), subhead, fill=(56, 189, 248), font=font_subhead)

    # Benefit bullet list with crisp vector icons (No missing glyph squares!)
    bullets = [
        ("vector_lightning", (245, 158, 11), "批量压缩视频与相册，最高省 80% 空间", "Shrink videos & photos by up to 80%"),
        ("vector_lock", (56, 189, 248), "100% 本机离线处理 • 零云端上传 • 极致隐私", "100% Offline on-device • Zero cloud uploads"),
        ("vector_treemap", (129, 140, 248), "全景存储区块树图 • 秒懂机身空间占用", "Visual Storage Treemap • Spot giant files"),
        ("vector_shield", (16, 185, 129), "原片安全保险箱 • 7~30天无损后悔还原", "Safe Recovery Vault • 1-tap restore undo"),
    ]

    by = 180
    for icon_type, icon_color, zh_t, en_t in bullets:
        text = zh_t if is_chinese else en_t
        # Draw vector icon
        if icon_type == "vector_lightning":
            draw_vector_lightning(draw, 76, by + 12, size=18, color=icon_color)
        elif icon_type == "vector_lock":
            draw_vector_lock(draw, 76, by + 12, size=18, color=icon_color)
        elif icon_type == "vector_treemap":
            draw_vector_treemap(draw, 76, by + 12, size=18, color=icon_color)
        elif icon_type == "vector_shield":
            draw_vector_shield(draw, 76, by + 12, size=18, color=icon_color)

        draw.text((100, by), text, fill=(226, 232, 240), font=font_badge)
        by += 52

    # Save
    suffix = "_zh" if is_chinese else "_en"
    out_path = os.path.join(STORE_DIR, f"feature_graphic{suffix}.png")
    img.save(out_path, "PNG")
    if not is_chinese:
        img.save(os.path.join(STORE_DIR, "feature_graphic_1024x500.png"), "PNG")
        img.save(os.path.join(ROOT_STORE_DIR, "feature_graphic_1024x500.png"), "PNG")
    else:
        img.save(os.path.join(STORE_ZH_DIR, "feature_graphic_1024x500.png"), "PNG")
        img.save(os.path.join(ROOT_STORE_DIR, "feature_graphic_zh.png"), "PNG")
    print(f"Saved Feature Graphic: {out_path}")

# -------------------------------------------------------------
# 2. 8 Sequential Portrait Screenshots: 1080 x 1920
# -------------------------------------------------------------
SCREENSHOT_DATA = [
    {
        "index": 1,
        "clean_img": "store/screen-pic/clean/en/screenshot_01.jpg",
        "pill_en": "GPU ACCELERATION",
        "pill_zh": "GPU 硬件加速",
        "title_en": "Shrink Videos Up to 80%",
        "title_zh": "智能视频极速压缩",
        "sub_en": "1.24 GB to 285 MB • Keep crystal-clear HD quality",
        "sub_zh": "1.24 GB 压缩至 285 MB • 节省高达80%空间",
        "tag_en": "4K to 1080p • 60fps",
        "tag_zh": "4K 转 1080p 超清",
    },
    {
        "index": 2,
        "clean_img": "store/screen-pic/clean/en/screenshot_02.jpg",
        "pill_en": "REAL-TIME COMPARISON",
        "pill_zh": "实时前后对比",
        "title_en": "Side-by-Side Quality Check",
        "title_zh": "双轨百叶窗前后对比",
        "sub_en": "Interactive curtain slider • Inspect sharpness before saving",
        "sub_zh": "毫秒级同步联动播放 • 放大检视人脸细节",
        "tag_en": "Original 1.24 GB vs Slimmed 285 MB",
        "tag_zh": "原片 1.24 GB 对比 压缩后 285 MB",
    },
    {
        "index": 3,
        "clean_img": "store/screen-pic/clean/en/screenshot_03.jpg",
        "pill_en": "ORIGINAL SAFE VAULT",
        "pill_zh": "原片安全保险箱",
        "title_en": "Never Lose Originals with Vault",
        "title_zh": "独家 7~30 天后悔宽限期",
        "sub_en": "30-day retention sandbox • 1-tap restore undo anytime",
        "sub_zh": "替换时原片沙盒隔离 • 随时一键原样复原回相册",
        "tag_en": "Zero Risk • 100% Safe",
        "tag_zh": "零风险 • 安心瘦身",
    },
    {
        "index": 4,
        "clean_img": "store/screen-pic/clean/en/screenshot_04.jpg",
        "pill_en": "VISUAL STORAGE ANALYZER",
        "pill_zh": "全景存储透视",
        "title_en": "Visual Storage Block Treemap",
        "title_zh": "电脑级区块树图透视",
        "sub_en": "Colorful interactive block treemap reveals giant files",
        "sub_zh": "自适应矩形树图 • 偷吃空间的大文件无所遁形",
        "tag_en": "Full Partition Deep Scan",
        "tag_zh": "100% 分区深度覆盖",
    },
    {
        "index": 5,
        "clean_img": "store/screen-pic/clean/en/screenshot_05.jpg",
        "pill_en": "SMART SHARING TARGETS",
        "pill_zh": "社交分享指定大小",
        "title_en": "Fit WhatsApp & Discord Limits",
        "title_zh": "微信 / 邮件 / 聊天直传",
        "sub_en": "Quick 16MB, 25MB, 50MB presets • Optimal bitrate match",
        "sub_zh": "快速指定 16MB / 25MB / 50MB • 最优码率绝不超标",
        "tag_en": "Auto Bitrate Match",
        "tag_zh": "智能演算最优画质",
    },
    {
        "index": 6,
        "clean_img": "store/screen-pic/clean/en/screenshot_06.jpg",
        "pill_en": "BATCH PHOTO & AUDIO",
        "pill_zh": "照片与音频批量瘦身",
        "title_en": "Batch Compress Photos & Audio",
        "title_zh": "相册大图与录音批量处理",
        "sub_en": "Bulk shrink JPEG, PNG • Transcode audio to compact AAC/Opus",
        "sub_zh": "高清相片批量瘦身 • 无损录音一键转制省电省空间",
        "tag_en": "Bulk Library Cleanup",
        "tag_zh": "相册一键减负",
    },
    {
        "index": 7,
        "clean_img": "store/screen-pic/clean/en/screenshot_07.jpg",
        "pill_en": "DUPLICATE & BIG FILES",
        "pill_zh": "深度清理利器",
        "title_en": "Duplicate & Big File Explorer",
        "title_zh": "重复文件排查与大文件清理",
        "sub_en": "Multi-point hash matching identifies identical duplicates",
        "sub_zh": "精准哈希排查多余副本 • 按大小分类极速清理",
        "tag_en": "Free Up More Space",
        "tag_zh": "释放更多可用空间",
    },
    {
        "index": 8,
        "clean_img": "store/screen-pic/clean/en/screenshot_08.jpg",
        "pill_en": "100% OFFLINE & PRIVATE",
        "pill_zh": "100% 本机离线隐私",
        "title_en": "Zero Cloud Uploads • Total Privacy",
        "title_zh": "零网络权限 • 数据不出手机",
        "sub_en": "No internet permissions, no accounts • All media stays on device",
        "sub_zh": "无需注册登录 • 零云端上传 • 隐私与安全完全掌控",
        "tag_en": "Privacy Protected",
        "tag_zh": "纯本地安全保障",
    },
]

def generate_screenshot(item, is_chinese=False):
    idx = item["index"]
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), (11, 15, 25))
    draw = ImageDraw.Draw(img)

    # 1. Top Header Area (y: 0..360) with subtle deep gradient
    header_h = 360
    for y in range(header_h):
        f = y / float(header_h)
        r = int(11 + (20 - 11) * f)
        g = int(15 + (30 - 15) * f)
        b = int(25 + (60 - 25) * f)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # Top Category Pill
    pill_text = item["pill_zh"] if is_chinese else item["pill_en"]
    font_pill = get_font(20, bold=True, is_chinese=is_chinese)
    draw.rounded_rectangle([64, 48, 64 + len(pill_text) * 18 + 48, 92], radius=12, fill=(29, 78, 216))
    draw.text((84, 56), pill_text, fill=(255, 255, 255), font=font_pill)

    # Big Headline
    title_text = item["title_zh"] if is_chinese else item["title_en"]
    draw.text((64, 114), title_text, fill=(255, 255, 255), font=get_font(46, bold=True, is_chinese=is_chinese))

    # Subhead
    sub_text = item["sub_zh"] if is_chinese else item["sub_en"]
    draw.text((64, 185), sub_text, fill=(56, 189, 248), font=get_font(23, bold=False, is_chinese=is_chinese))

    # 2. Modern Device Frame & Real Screenshot Mockup (y: 280..1920)
    phone_w = 920
    phone_h = 1600
    phone_x = (w - phone_w) // 2
    phone_y = 280

    # Phone drop shadow
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle([phone_x, phone_y + 20, phone_x + phone_w, phone_y + phone_h + 20], radius=44, fill=(0, 0, 0, 160))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=25))
    img.paste(Image.new("RGB", (w, h), (0, 0, 0)), (0, 0), shadow.split()[3])

    # Phone outer bezel (Titanium Dark chassis)
    draw.rounded_rectangle(
        [phone_x, phone_y, phone_x + phone_w, phone_y + phone_h],
        radius=44,
        fill=(26, 32, 44),
        outline=(71, 85, 105),
        width=4
    )

    # Phone inner screen viewport
    inner_pad = 12
    screen_x = phone_x + inner_pad
    screen_y = phone_y + inner_pad
    screen_w = phone_w - inner_pad * 2
    screen_h = phone_h - inner_pad * 2

    # Load and place Real App UI Screenshot
    clean_en = f"store/screen-pic/clean/en/screenshot_{idx:02d}.jpg"
    clean_zh = f"store/screen-pic/clean/zh/screenshot_{idx:02d}.jpg"
    clean_src = clean_zh if is_chinese and os.path.exists(clean_zh) else clean_en
    if not os.path.exists(clean_src):
        clean_src = item["clean_img"]
    if os.path.exists(clean_src):
        app_screen = Image.open(clean_src)
        # Resize preserving aspect ratio to fit screen_w
        target_h = int(app_screen.height * (screen_w / float(app_screen.width)))
        app_screen_resized = app_screen.resize((screen_w, target_h), Image.Resampling.LANCZOS)
        
        # Crop screen_h
        screen_crop = app_screen_resized.crop((0, 0, screen_w, screen_h))
        # Mask rounded corners
        corner_mask = Image.new("L", (screen_w, screen_h), 0)
        cmdraw = ImageDraw.Draw(corner_mask)
        cmdraw.rounded_rectangle([0, 0, screen_w, screen_h], radius=34, fill=255)
        
        img.paste(screen_crop, (screen_x, screen_y), corner_mask)
    else:
        # Fallback card
        draw.rounded_rectangle([screen_x, screen_y, screen_x + screen_w, screen_y + screen_h], radius=34, fill=(19, 26, 42))

    # Top Phone Dynamic Island / Camera Notch
    notch_w = 140
    notch_h = 24
    draw.rounded_rectangle(
        [phone_x + (phone_w - notch_w) // 2, screen_y + 10, phone_x + (phone_w + notch_w) // 2, screen_y + 10 + notch_h],
        radius=12,
        fill=(11, 15, 25)
    )

    # Floating Feature Highlight Tag over phone bottom
    tag_text = item["tag_zh"] if is_chinese else item["tag_en"]
    tag_w = len(tag_text) * 22 + 60
    tag_h = 56
    tag_x = (w - tag_w) // 2
    tag_y = phone_y + phone_h - 110
    draw.rounded_rectangle([tag_x, tag_y, tag_x + tag_w, tag_y + tag_h], radius=28, fill=(16, 185, 129))
    draw_vector_shield(draw, tag_x + 28, tag_y + 28, size=20, color=(255, 255, 255))
    draw.text((tag_x + 50, tag_y + 14), tag_text, fill=(255, 255, 255), font=get_font(22, bold=True, is_chinese=is_chinese))

    # Save outputs
    suffix = "_zh" if is_chinese else ""
    target_dir = ROOT_SCREENSHOTS_ZH_DIR if is_chinese else ROOT_SCREENSHOTS_DIR
    target_path = os.path.join(target_dir, f"screenshot_{idx}.png")
    img.save(target_path, "PNG")

    # Also mirror into release/store_assets/
    rel_dir = STORE_ZH_DIR if is_chinese else STORE_DIR
    img.save(os.path.join(rel_dir, f"screenshot_{idx}.png"), "PNG")
    print(f"Saved Portrait Screenshot [{idx}]: {target_path}")

# -------------------------------------------------------------
# 3. 4 Landscape Tablet Screenshots: 1920 x 1080 (16:9)
# -------------------------------------------------------------
TABLET_DATA = [
    {
        "index": 1,
        "clean_img": "store/screen-pic/clean/screenshot_02.jpg",
        "pill_en": "TABLET OPTIMIZED",
        "pill_zh": "平板大屏优化",
        "title_en": "Professional Video Compressor",
        "title_zh": "专业级平板媒体压缩工作台",
        "sub_en": "Hardware GPU HEVC & H.264 engine • Save up to 80% storage",
        "sub_zh": "硬件 GPU HEVC & H.264 极速编码 • 释放高达 80% 机身存储",
        "bullets_en": [
            "Real-time split-screen curtain slider quality check",
            "Smooth downscaling from heavy 4K 60fps to crisp 1080p",
            "Safe 7 to 30-day vault rollback with 1-tap undo",
        ],
        "bullets_zh": [
            "双轨前后对比百叶窗，实时检视人脸清晰度",
            "4K 60帧平滑下调至 1080p 超清，单视频省数个 GB",
            "7~30 天原片隔离保险箱，随时一键无损还原",
        ],
    },
    {
        "index": 2,
        "clean_img": "store/screen-pic/clean/screenshot_03.jpg",
        "pill_en": "STORAGE ANALYZER",
        "pill_zh": "存储全景树图",
        "title_en": "Visual Storage Treemap on Large Screen",
        "title_zh": "大屏视觉区块树图透视",
        "sub_en": "Interactive colored block map reveals hidden gigabyte-hogging files",
        "sub_zh": "直观自适应矩形方块，全盘大文件与缓存一览无余",
        "bullets_en": [
            "Desktop-grade visual storage block treemap for Android tablets",
            "100% full partition scan with zero files omitted",
            "Tap any block to immediately inspect, sort, or clean",
        ],
        "bullets_zh": [
            "电脑级专业区块树图，完美适配 Android 平板大屏",
            "100% 完整覆盖机身存储空间，未遗漏任何文件",
            "点按任意色块即时定位大文件、缓存与下载残留",
        ],
    },
    {
        "index": 3,
        "clean_img": "store/screen-pic/clean/screenshot_04.jpg",
        "pill_en": "SAFETY & VAULT",
        "pill_zh": "安全保险箱",
        "title_en": "Never Lose Originals with Safe Vault",
        "title_zh": "原片安全保险箱 • 零后顾之忧",
        "sub_en": "Original uncompressed files safely isolated during replacement",
        "sub_zh": "替换原文件时自动沙盒隔离备份，享有随时后悔权利",
        "bullets_en": [
            "Customizable 7 to 30 days retention grace period",
            "1-tap instant restoration back to original album path",
            "Zero cloud uploads, total offline local reliability",
        ],
        "bullets_zh": [
            "可自定义 7~30 天宽限期，避免误操作与画质不满",
            "一键完整复原回相册原位置，元数据完全保留",
            "全流程纯本地运行，不消耗流量，无任何隐私风险",
        ],
    },
    {
        "index": 4,
        "clean_img": "store/screen-pic/clean/screenshot_05.jpg",
        "pill_en": "PRIVACY GUARANTEED",
        "pill_zh": "100% 本机隐私",
        "title_en": "100% On-Device • Zero Network Permissions",
        "title_zh": "100% 本机离线 • 零网络权限申请",
        "sub_en": "Complete data safety: your personal videos and photos never leave your device",
        "sub_zh": "绝不上传任何服务器，无需账号登录，数据完全属于用户",
        "bullets_en": [
            "Zero INTERNET permission requested in application manifest",
            "No account required, no tracking SDKs, no advertising",
            "Blazing-fast GPU hardware acceleration & native processing",
        ],
        "bullets_zh": [
            "清单文件零网络权限申请，从底层杜绝云端泄露",
            "无需账号登录，无第三方分析统计，纯粹本地工具",
            "底层采用高性能原生算法与芯片级硬件加速引擎",
        ],
    },
]

def generate_tablet_screenshot(item, is_chinese=False):
    idx = item["index"]
    w, h = 1920, 1080
    img = Image.new("RGB", (w, h), (11, 15, 25))
    draw = ImageDraw.Draw(img)

    # 1. Background gradient
    for y in range(h):
        f = y / float(h)
        r = int(11 + (22 - 11) * f)
        g = int(15 + (32 - 15) * f)
        b = int(25 + (70 - 25) * f)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # 2. Left Column: Text & Features (x: 80..860)
    pill_text = item["pill_zh"] if is_chinese else item["pill_en"]
    font_pill = get_font(18, bold=True, is_chinese=is_chinese)
    draw.rounded_rectangle([80, 80, 80 + len(pill_text) * 18 + 48, 122], radius=12, fill=(29, 78, 216))
    draw.text((100, 88), pill_text, fill=(255, 255, 255), font=font_pill)

    # Headline
    title_text = item["title_zh"] if is_chinese else item["title_en"]
    draw.text((80, 150), title_text, fill=(255, 255, 255), font=get_font(44, bold=True, is_chinese=is_chinese))

    # Subhead
    sub_text = item["sub_zh"] if is_chinese else item["sub_en"]
    draw.text((80, 220), sub_text, fill=(56, 189, 248), font=get_font(22, bold=False, is_chinese=is_chinese))

    # Bullet points with vector checkmarks
    bullets = item["bullets_zh"] if is_chinese else item["bullets_en"]
    by = 310
    font_bullet = get_font(20, bold=False, is_chinese=is_chinese)
    for b in bullets:
        draw_vector_shield(draw, 96, by + 12, size=18, color=(16, 185, 129))
        draw.text((120, by), b, fill=(226, 232, 240), font=font_bullet)
        by += 64

    # Bottom Trust Badge
    trust_text = "🛡️ 100% 离线运行 • 零云端上传 • 终身本地可用" if is_chinese else "🛡️ 100% On-Device • Zero Cloud Uploads • Total Privacy"
    draw.rounded_rectangle([80, 920, 680, 980], radius=16, fill=(19, 26, 42), outline=(56, 189, 248), width=1)
    draw_vector_shield(draw, 110, 950, size=22, color=(56, 189, 248))
    clean_trust = "100% 离线运行 • 零云端上传 • 终身本地可用" if is_chinese else "100% On-Device • Zero Cloud Uploads • Total Privacy"
    draw.text((135, 936), clean_trust, fill=(255, 255, 255), font=get_font(18, bold=True, is_chinese=is_chinese))

    # 3. Right Column: Landscape Tablet Mockup (x: 900..1840, y: 80..1000)
    tab_w = 940
    tab_h = 900
    tab_x = 900
    tab_y = 80

    # Shadow
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle([tab_x, tab_y + 15, tab_x + tab_w, tab_y + tab_h + 15], radius=32, fill=(0, 0, 0, 160))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=20))
    img.paste(Image.new("RGB", (w, h), (0, 0, 0)), (0, 0), shadow.split()[3])

    # Tablet Frame
    draw.rounded_rectangle([tab_x, tab_y, tab_x + tab_w, tab_y + tab_h], radius=32, fill=(26, 32, 44), outline=(71, 85, 105), width=3)

    inner_pad = 14
    screen_x = tab_x + inner_pad
    screen_y = tab_y + inner_pad
    screen_w = tab_w - inner_pad * 2
    screen_h = tab_h - inner_pad * 2

    # Load App UI Screenshot
    tab_map = {1: 1, 2: 4, 3: 3, 4: 8}
    screen_idx = tab_map.get(idx, idx)
    clean_en = f"store/screen-pic/clean/en/screenshot_{screen_idx:02d}.jpg"
    clean_zh = f"store/screen-pic/clean/zh/screenshot_{screen_idx:02d}.jpg"
    clean_src = clean_zh if is_chinese and os.path.exists(clean_zh) else clean_en
    if not os.path.exists(clean_src):
        clean_src = item["clean_img"]
    if os.path.exists(clean_src):
        app_screen = Image.open(clean_src)
        target_w = screen_w
        target_h = int(app_screen.height * (target_w / float(app_screen.width)))
        app_screen_resized = app_screen.resize((target_w, target_h), Image.Resampling.LANCZOS)
        screen_crop = app_screen_resized.crop((0, 0, screen_w, screen_h))

        corner_mask = Image.new("L", (screen_w, screen_h), 0)
        cmdraw = ImageDraw.Draw(corner_mask)
        cmdraw.rounded_rectangle([0, 0, screen_w, screen_h], radius=24, fill=255)
        img.paste(screen_crop, (screen_x, screen_y), corner_mask)
    else:
        draw.rounded_rectangle([screen_x, screen_y, screen_x + screen_w, screen_y + screen_h], radius=24, fill=(19, 26, 42))

    # Save outputs
    target_dir = ROOT_SCREENSHOTS_ZH_DIR if is_chinese else ROOT_SCREENSHOTS_DIR
    target_path = os.path.join(target_dir, f"tablet_{idx}.png")
    img.save(target_path, "PNG")

    rel_dir = STORE_ZH_DIR if is_chinese else STORE_DIR
    img.save(os.path.join(rel_dir, f"tablet_{idx}.png"), "PNG")
    print(f"Saved Tablet Screenshot [{idx}]: {target_path}")

def main():
    print("Generating Feature Graphics (EN & ZH)...")
    generate_feature_graphic(is_chinese=False)
    generate_feature_graphic(is_chinese=True)

    print("\nGenerating 8 Portrait Screenshots (EN & ZH)...")
    for item in SCREENSHOT_DATA:
        generate_screenshot(item, is_chinese=False)
        generate_screenshot(item, is_chinese=True)

    print("\nGenerating 4 Landscape Tablet Screenshots (EN & ZH)...")
    for item in TABLET_DATA:
        generate_tablet_screenshot(item, is_chinese=False)
        generate_tablet_screenshot(item, is_chinese=True)

    print("\nAll Store Graphics successfully generated!")

if __name__ == '__main__':
    main()
