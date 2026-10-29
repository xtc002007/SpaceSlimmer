import os
from PIL import Image, ImageDraw, ImageFont

store_dir = r"d:\Workspace\SpaceSlimmer\release\store_assets"
os.makedirs(store_dir, exist_ok=True)

def get_font(size):
    font_paths = [
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\msyhbd.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\arial.ttf"
    ]
    for p in font_paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()

# 1. Feature Graphic: 1024 x 500 (RGB, no alpha)
def generate_feature_graphic():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), (26, 35, 58))
    draw = ImageDraw.Draw(img)
    
    # Subtle gradient background
    for y in range(h):
        r = int(26 + (43 - 26) * (y / h))
        g = int(35 + (76 - 35) * (y / h))
        b = int(58 + (126 - 58) * (y / h))
        draw.line([(0, y), (w, y)], fill=(r, g, b))
        
    # Decorative radar rings on the right
    center_x, center_y = 760, 250
    for rad, alpha in [(220, (56, 189, 248)), (160, (96, 165, 250)), (100, (147, 197, 253))]:
        draw.ellipse([center_x - rad, center_y - rad, center_x + rad, center_y + rad], outline=alpha, width=3)
    
    # Center vault icon on right
    draw.rounded_rectangle([center_x - 50, center_y - 50, center_x + 50, center_y + 50], radius=16, fill=(56, 189, 248))
    # Icon compression arrows
    draw.polygon([(center_x - 40, center_y), (center_x - 20, center_y - 15), (center_x - 20, center_y + 15)], fill=(255, 255, 255))
    draw.polygon([(center_x + 40, center_y), (center_x + 20, center_y - 15), (center_x + 20, center_y + 15)], fill=(255, 255, 255))
    
    # Left text: App Name & Features
    font_title = get_font(56)
    font_sub = get_font(26)
    font_tag = get_font(20)
    
    draw.text((90, 110), "SpaceSlimmer", fill=(255, 255, 255), font=font_title)
    draw.text((90, 190), "空间瘦身 · 手机媒体本地智能压缩", fill=(56, 189, 248), font=font_sub)
    draw.text((90, 245), "• 空间雷达精准预估可省空间，批量压缩", fill=(226, 232, 240), font=font_tag)
    draw.text((90, 285), "• 硬件视频编码（H.264 / HEVC），速度快", fill=(226, 232, 240), font=font_tag)
    draw.text((90, 325), "• 全程本地处理，不上传云端，隐私无忧", fill=(226, 232, 240), font=font_tag)
    draw.text((90, 365), "• 原片自动备份到保险箱，随时安全恢复", fill=(226, 232, 240), font=font_tag)
    
    path = os.path.join(store_dir, "feature_graphic_1024x500.png")
    img.save(path, "PNG")
    print("Saved feature graphic:", path)

# 2. Screenshots: 1080 x 1920 (9:16)
def generate_screenshot(index, title, subtitle, bullets, card_content):
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), (15, 23, 42)) # Slate 900
    draw = ImageDraw.Draw(img)
    
    # Top banner gradient
    for y in range(500):
        r = int(15 + (43 - 15) * (y / 500))
        g = int(23 + (76 - 23) * (y / 500))
        b = int(42 + (126 - 42) * (y / 500))
        draw.line([(0, y), (w, y)], fill=(r, g, b))
        
    font_head = get_font(52)
    font_sub = get_font(30)
    font_body = get_font(28)
    
    # Title & Subtitle
    draw.text((70, 100), title, fill=(255, 255, 255), font=font_head)
    draw.text((70, 180), subtitle, fill=(56, 189, 248), font=font_sub)
    
    # Phone frame mockup
    phone_x1, phone_y1 = 80, 320
    phone_x2, phone_y2 = 1000, 1840
    # Outer bezel
    draw.rounded_rectangle([phone_x1, phone_y1, phone_x2, phone_y2], radius=48, fill=(30, 41, 59), outline=(71, 85, 105), width=4)
    # Screen inner
    inner_pad = 20
    draw.rounded_rectangle([phone_x1 + inner_pad, phone_y1 + inner_pad, phone_x2 - inner_pad, phone_y2 - inner_pad], radius=36, fill=(15, 23, 42))
    
    # Mock status bar
    draw.text((phone_x1 + 60, phone_y1 + 40), "09:41", fill=(148, 163, 184), font=get_font(22))
    draw.text((phone_x2 - 160, phone_y1 + 40), "5G  100%", fill=(148, 163, 184), font=get_font(22))
    
    # Content cards inside phone
    card_y = phone_y1 + 100
    for card in card_content:
        c_title, c_val, c_color = card
        draw.rounded_rectangle([phone_x1 + 50, card_y, phone_x2 - 50, card_y + 160], radius=24, fill=(30, 41, 59), outline=(51, 65, 85), width=2)
        draw.text((phone_x1 + 80, card_y + 30), c_title, fill=(148, 163, 184), font=font_sub)
        draw.text((phone_x1 + 80, card_y + 80), c_val, fill=c_color, font=get_font(42))
        card_y += 190
        
    # Bullet points card
    draw.rounded_rectangle([phone_x1 + 50, card_y + 20, phone_x2 - 50, phone_y2 - 60], radius=24, fill=(30, 41, 59))
    b_y = card_y + 60
    for b in bullets:
        draw.text((phone_x1 + 80, b_y), b, fill=(241, 245, 249), font=font_body)
        b_y += 65
        
    path = os.path.join(store_dir, f"screenshot_{index}.png")
    img.save(path, "PNG")
    print(f"Saved screenshot {index}:", path)

generate_feature_graphic()

generate_screenshot(
    1,
    "空间雷达 · 快速定位臃肿媒体",
    "智能分析视频、相册与录音占用",
    [
        "✓ 智能扫描相册与大体积视频",
        "✓ 自动过滤已经高压缩率文件",
        "✓ 压缩前准确展示预估可省体积",
        "✓ 支持按体积大小或类型快速筛选"
    ],
    [
        ("相册预计可省空间", "18.4 GB", (56, 189, 248)),
        ("待处理大体积视频", "24 个视频 · 12.8 GB", (244, 114, 182)),
        ("可压缩高清照片", "356 张照片 · 5.6 GB", (74, 222, 128))
    ]
)

generate_screenshot(
    2,
    "本地硬件编码 · 隐私保险箱",
    "全程在手机本机处理，绝不上传云端",
    [
        "✓ 硬件加速视频编码（H.264 / HEVC）",
        "✓ 替换原文件前自动存入隔离保险箱",
        "✓ 保险箱到期前随时一键原样恢复",
        "✓ 无账号、无网络上传，彻底保护隐私"
    ],
    [
        ("当前视频硬件编码", "HEVC (H.265) 硬件加速", (74, 222, 128)),
        ("原片保险箱保护", "18 项已保护原件", (251, 191, 36)),
        ("任务安全状态", "设备温控正常 · 全程在离线处理", (56, 189, 248))
    ]
)
