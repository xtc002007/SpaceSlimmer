import os
from PIL import Image, ImageDraw

def create_app_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Background circle/rounded rect with deep space steel blue
    bg_color = (43, 76, 126, 255) # #2B4C7E
    corner_radius = int(size * 0.22)
    draw.rounded_rectangle([0, 0, size, size], radius=corner_radius, fill=bg_color)
    
    # Outer radar ring
    center = size / 2
    r_outer = size * 0.36
    draw.ellipse(
        [center - r_outer, center - r_outer, center + r_outer, center + r_outer],
        outline=(96, 165, 250, 160),
        width=max(2, int(size * 0.025))
    )
    
    # Inner radar ring
    r_inner = size * 0.24
    draw.ellipse(
        [center - r_inner, center - r_inner, center + r_inner, center + r_inner],
        outline=(147, 197, 253, 200),
        width=max(2, int(size * 0.02))
    )
    
    # Center vault/storage box
    box_half = size * 0.11
    draw.rounded_rectangle(
        [center - box_half, center - box_half, center + box_half, center + box_half],
        radius=int(size * 0.03),
        fill=(56, 189, 248, 255) # #38BDF8 cyan
    )
    
    # Inward compression arrows
    arrow_len = size * 0.09
    arrow_w = size * 0.05
    # Left arrow pointing right
    draw.polygon([
        (center - box_half - arrow_len, center),
        (center - box_half - arrow_len * 0.4, center - arrow_w),
        (center - box_half - arrow_len * 0.4, center - arrow_w * 0.4),
        (center - box_half, center - arrow_w * 0.4),
        (center - box_half, center + arrow_w * 0.4),
        (center - box_half - arrow_len * 0.4, center + arrow_w * 0.4),
        (center - box_half - arrow_len * 0.4, center + arrow_w)
    ], fill=(255, 255, 255, 240))
    
    # Right arrow pointing left
    draw.polygon([
        (center + box_half + arrow_len, center),
        (center + box_half + arrow_len * 0.4, center - arrow_w),
        (center + box_half + arrow_len * 0.4, center - arrow_w * 0.4),
        (center + box_half, center - arrow_w * 0.4),
        (center + box_half, center + arrow_w * 0.4),
        (center + box_half + arrow_len * 0.4, center + arrow_w * 0.4),
        (center + box_half + arrow_len * 0.4, center + arrow_w)
    ], fill=(255, 255, 255, 240))
    
    # Top arrow pointing down
    draw.polygon([
        (center, center - box_half - arrow_len),
        (center - arrow_w, center - box_half - arrow_len * 0.4),
        (center - arrow_w * 0.4, center - box_half - arrow_len * 0.4),
        (center - arrow_w * 0.4, center - box_half),
        (center + arrow_w * 0.4, center - box_half),
        (center + arrow_w * 0.4, center - box_half - arrow_len * 0.4),
        (center + arrow_w, center - box_half - arrow_len * 0.4)
    ], fill=(255, 255, 255, 240))
    
    # Bottom arrow pointing up
    draw.polygon([
        (center, center + box_half + arrow_len),
        (center - arrow_w, center + box_half + arrow_len * 0.4),
        (center - arrow_w * 0.4, center + box_half + arrow_len * 0.4),
        (center - arrow_w * 0.4, center + box_half),
        (center + arrow_w * 0.4, center + box_half),
        (center + arrow_w * 0.4, center + box_half + arrow_len * 0.4),
        (center + arrow_w, center + box_half + arrow_len * 0.4)
    ], fill=(255, 255, 255, 240))
    
    return img

# Output densities
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

base_res = r"d:\Workspace\SpaceSlimmer\android\app\src\main\res"
for folder, sz in densities.items():
    target_dir = os.path.join(base_res, folder)
    os.makedirs(target_dir, exist_ok=True)
    icon = create_app_icon(sz)
    icon.save(os.path.join(target_dir, "ic_launcher.png"), "PNG")
    icon.save(os.path.join(target_dir, "ic_launcher_round.png"), "PNG")
    print(f"Saved {sz}x{sz} icon to {folder}")

# Also save high-res 512x512 for Google Play Store Listing!
store_assets_dir = r"d:\Workspace\SpaceSlimmer\release\store_assets"
os.makedirs(store_assets_dir, exist_ok=True)
store_icon = create_app_icon(512)
store_icon.save(os.path.join(store_assets_dir, "icon-512.png"), "PNG")
print("Saved 512x512 Play Store icon to store_assets/icon-512.png")
