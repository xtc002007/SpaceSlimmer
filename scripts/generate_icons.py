# -*- coding: utf-8 -*-
"""
SpaceSlimmer Production Icon Generator with 4x Supersampling.
Generates:
1. Google Play Store 512x512 High-Res Icon (Full-bleed square, high contrast, vivid blue/cyan gradient, no dark murky colors).
2. Android Adaptive Icon XML (ic_launcher_background.xml, ic_launcher_foreground.xml).
3. Android Legacy & Round Launcher Icons across all mipmap densities (mdpi to xxxhdpi).
"""
import os
import math
from PIL import Image, ImageDraw, ImageFilter

MIPMAP_DIRS = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

def render_high_res_icon(size=512, is_play_store=True, is_round=False):
    # Render at 4x scale for pristine supersampled anti-aliasing
    scale = 4
    w = size * scale
    h = size * scale

    # 1. Base Gradient: Vibrant Cobalt Blue (#1D4ED8) -> Azure (#0284C7) -> Vibrant Cyan (#06B6D4)
    # 135-degree diagonal gradient
    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(grad)

    c_start = (29, 78, 216)    # #1D4ED8
    c_mid = (2, 132, 199)      # #0284C7
    c_end = (6, 182, 212)      # #06B6D4

    for y in range(h):
        for x in range(0, w, 4):  # step for speed during gradient build
            factor = (x + y) / float(w + h)
            if factor < 0.5:
                sub_f = factor * 2.0
                r = int(c_start[0] + (c_mid[0] - c_start[0]) * sub_f)
                g = int(c_start[1] + (c_mid[1] - c_start[1]) * sub_f)
                b = int(c_start[2] + (c_mid[2] - c_start[2]) * sub_f)
            else:
                sub_f = (factor - 0.5) * 2.0
                r = int(c_mid[0] + (c_end[0] - c_mid[0]) * sub_f)
                g = int(c_mid[1] + (c_end[1] - c_mid[1]) * sub_f)
                b = int(c_mid[2] + (c_end[2] - c_mid[2]) * sub_f)
            gdraw.rectangle([x, y, x + 3, y], fill=(r, g, b, 255))

    # Masking for shapes
    mask = Image.new("L", (w, h), 0)
    mdraw = ImageDraw.Draw(mask)
    if is_play_store:
        mdraw.rectangle([0, 0, w, h], fill=255)
    elif is_round:
        mdraw.ellipse([0, 0, w, h], fill=255)
    else:
        radius = int(w * 0.22)
        mdraw.rounded_rectangle([0, 0, w, h], radius=radius, fill=255)

    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    base.paste(grad, (0, 0), mask)

    draw = ImageDraw.Draw(base)
    cx, cy = w / 2.0, h / 2.0

    # 2. Subtle Glowing Concentric Rings
    r_outer = w * 0.38
    r_inner = w * 0.28
    draw.ellipse(
        [cx - r_outer, cy - r_outer, cx + r_outer, cy + r_outer],
        outline=(255, 255, 255, 35),
        width=int(w * 0.012)
    )
    draw.ellipse(
        [cx - r_inner, cy - r_inner, cx + r_inner, cy + r_inner],
        outline=(255, 255, 255, 50),
        width=int(w * 0.009)
    )

    # 3. Soft Drop Shadow for Center Plate
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    plate_half = w * 0.17
    plate_radius = int(plate_half * 0.38)
    shadow_offset = int(w * 0.02)
    sdraw.rounded_rectangle(
        [cx - plate_half, cy - plate_half + shadow_offset, cx + plate_half, cy + plate_half + shadow_offset],
        radius=plate_radius,
        fill=(10, 25, 60, 90)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=int(w * 0.025)))
    base = Image.alpha_composite(base, shadow)
    draw = ImageDraw.Draw(base)

    # 4. Central Solid Pure White Squircle Card
    draw.rounded_rectangle(
        [cx - plate_half, cy - plate_half, cx + plate_half, cy + plate_half],
        radius=plate_radius,
        fill=(255, 255, 255, 255)
    )

    # 5. Vibrant Center Play / Media Triangle
    tri_w = plate_half * 0.55
    tri_h = plate_half * 0.65
    tri_shift = plate_half * 0.08
    p1 = (cx - tri_w * 0.45 + tri_shift, cy - tri_h * 0.5)
    p2 = (cx + tri_w * 0.65 + tri_shift, cy)
    p3 = (cx - tri_w * 0.45 + tri_shift, cy + tri_h * 0.5)
    draw.polygon([p1, p2, p3], fill=(2, 132, 199, 255))

    # 6. High-Contrast Compression Inward Arrows (→ [ ▶ ] ←)
    # Left Arrow pointing Right
    arrow_len = w * 0.09
    arrow_x_tip = cx - plate_half - w * 0.04
    arrow_x_base = arrow_x_tip - arrow_len
    arrow_y = cy
    wing_dx = w * 0.035
    wing_dy = w * 0.035
    bar_thick = max(4, int(w * 0.02))

    # Left arrow stem
    draw.line([(arrow_x_base, arrow_y), (arrow_x_tip, arrow_y)], fill=(255, 255, 255, 255), width=bar_thick)
    # Left arrow head
    draw.line([(arrow_x_tip - wing_dx, arrow_y - wing_dy), (arrow_x_tip, arrow_y)], fill=(255, 255, 255, 255), width=bar_thick)
    draw.line([(arrow_x_tip - wing_dx, arrow_y + wing_dy), (arrow_x_tip, arrow_y)], fill=(255, 255, 255, 255), width=bar_thick)

    # Right Arrow pointing Left
    r_arrow_x_tip = cx + plate_half + w * 0.04
    r_arrow_x_base = r_arrow_x_tip + arrow_len
    # Right arrow stem
    draw.line([(r_arrow_x_base, arrow_y), (r_arrow_x_tip, arrow_y)], fill=(255, 255, 255, 255), width=bar_thick)
    # Right arrow head
    draw.line([(r_arrow_x_tip + wing_dx, arrow_y - wing_dy), (r_arrow_x_tip, arrow_y)], fill=(255, 255, 255, 255), width=bar_thick)
    draw.line([(r_arrow_x_tip + wing_dx, arrow_y + wing_dy), (r_arrow_x_tip, arrow_y)], fill=(255, 255, 255, 255), width=bar_thick)

    # 7. Downsample to target size using high-quality Lanczos resampling
    final_img = base.resize((size, size), Image.Resampling.LANCZOS)
    if is_play_store:
        # Google Play requires 512x512 icon to be 100% opaque RGB (no transparency or alpha channel)
        solid_bg = Image.new("RGB", (size, size), (29, 78, 216))
        final_img = Image.alpha_composite(solid_bg.convert("RGBA"), final_img).convert("RGB")
    return final_img

def write_adaptive_xmls():
    bg_xml = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:angle="135"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:type="linear">
                <item android:offset="0.0" android:color="#FF1D4ED8" />
                <item android:offset="0.55" android:color="#FF0284C7" />
                <item android:offset="1.0" android:color="#FF06B6D4" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''
    fg_xml = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Subtle outer radar glow -->
    <path
        android:strokeColor="#55FFFFFF"
        android:strokeWidth="1.2"
        android:pathData="M54,22 A32,32 0 1,1 53.99,22"/>
    <!-- Central Pure White Squircle Card (Safe Zone 72dp) -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M43,36 h22 c3.5,0 6,2.5 6,6 v24 c0,3.5 -2.5,6 -6,6 h-22 c-3.5,0 -6,-2.5 -6,-6 v-24 c0,-3.5 2.5,-6 6,-6 z"/>
    <!-- Vibrant Blue Play Media Triangle -->
    <path
        android:fillColor="#FF0284C7"
        android:pathData="M51,46 L60,54 L51,62 Z"/>
    <!-- Inward Compression Arrows: Left Arrow pointing Right -->
    <path
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2.4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M23,54 L32,54 M28,50 L32,54 L28,58"/>
    <!-- Inward Compression Arrows: Right Arrow pointing Left -->
    <path
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2.4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M85,54 L76,54 M80,50 L76,54 L80,58"/>
</vector>
'''
    res_drawable = 'android/app/src/main/res/drawable'
    with open(os.path.join(res_drawable, 'ic_launcher_background.xml'), 'w', encoding='utf-8') as f:
        f.write(bg_xml)
    with open(os.path.join(res_drawable, 'ic_launcher_foreground.xml'), 'w', encoding='utf-8') as f:
        f.write(fg_xml)
    print("Updated ic_launcher_background.xml and ic_launcher_foreground.xml")

def main():
    print("Generating Google Play Store 512x512 icon...")
    store_icon = render_high_res_icon(size=512, is_play_store=True)
    os.makedirs('release/store_assets', exist_ok=True)
    os.makedirs('store', exist_ok=True)
    store_icon.save('release/store_assets/icon-512.png', 'PNG')
    store_icon.save('store/icon-512.png', 'PNG')
    print("Saved store 512x512 icons.")

    print("Generating Mipmap launcher icons...")
    app_res = 'android/app/src/main/res'
    for dir_name, size in MIPMAP_DIRS.items():
        out_dir = os.path.join(app_res, dir_name)
        os.makedirs(out_dir, exist_ok=True)

        rect_icon = render_high_res_icon(size=size, is_play_store=False, is_round=False)
        round_icon = render_high_res_icon(size=size, is_play_store=False, is_round=True)

        rect_icon.save(os.path.join(out_dir, 'ic_launcher.png'), 'PNG')
        round_icon.save(os.path.join(out_dir, 'ic_launcher_round.png'), 'PNG')
        print(f"  {dir_name}: {size}x{size}")

    print("Writing modern Adaptive Icon VectorDrawables...")
    write_adaptive_xmls()
    print("Done generating all icons.")

if __name__ == '__main__':
    main()
