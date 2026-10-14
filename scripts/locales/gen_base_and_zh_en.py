# -*- coding: utf-8 -*-
import xml.etree.ElementTree as ET
import os
from scripts.new_keys import NEW_KEYS_ZH, NEW_KEYS_EN

def escape_xml_val(val):
    if not val:
        return ""
    # Standard XML entities
    val = val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # Escape unescaped apostrophes
    # If already escaped as \', avoid double escaping
    parts = val.split(r"\'")
    val = r"\'".join([p.replace("'", r"\'") for p in parts])
    # Escape double quotes
    parts = val.split(r'\"')
    val = r'\"'.join([p.replace('"', r'\"') for p in parts])
    return val

def build_xml(key_order, mapping, plurals=None):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for k in key_order:
        val = mapping.get(k, "")
        esc = escape_xml_val(val)
        lines.append(f'    <string name="{k}">{esc}</string>')
    
    if plurals:
        for p_name, p_items in plurals.items():
            lines.append(f'    <plurals name="{p_name}">')
            for q, text in p_items.items():
                esc = escape_xml_val(text)
                lines.append(f'        <item quantity="{q}">{esc}</item>')
            lines.append('    </plurals>')
            
    lines.append('</resources>\n')
    return "\n".join(lines)

def run():
    # Read existing
    tree_zh = ET.parse('android/app/src/main/res/values-zh/strings.xml')
    zh_keys = []
    zh_map = {}
    for elem in tree_zh.getroot().findall('string'):
        name = elem.attrib['name']
        zh_keys.append(name)
        zh_map[name] = elem.text or ""
        
    for k, v in NEW_KEYS_ZH.items():
        if k not in zh_map:
            zh_keys.append(k)
        zh_map[k] = v

    tree_en = ET.parse('android/app/src/main/res/values-en/strings.xml')
    en_map = {elem.attrib['name']: elem.text or "" for elem in tree_en.getroot().findall('string')}
    en_map['app_name_en'] = 'SpaceSlimmer'
    en_map.update(NEW_KEYS_EN)

    # Plurals for English
    en_plurals = {
        "selected_photos_count": {
            "one": "%1$d photo selected",
            "other": "%1$d photos selected"
        },
        "selected_files_count": {
            "one": "%1$d file selected",
            "other": "%1$d files selected"
        },
        "saved_to_gallery_count": {
            "one": "Saved %1$d to the SpaceSlimmer album",
            "other": "Saved %1$d to the SpaceSlimmer album"
        },
        "replaced_gallery_count": {
            "one": "Replaced %1$d file",
            "other": "Replaced %1$d files"
        },
        "vault_expiring_days": {
            "one": "%1$d vault backup expires within 3 days",
            "other": "%1$d vault backups expire within 3 days"
        }
    }

    # Plurals for Chinese (other only)
    zh_plurals = {
        "selected_photos_count": {
            "other": "已选 %1$d 张照片"
        },
        "selected_files_count": {
            "other": "已选 %1$d 个文件"
        },
        "saved_to_gallery_count": {
            "other": "已保存 %1$d 个到相册「空间瘦身」"
        },
        "replaced_gallery_count": {
            "other": "已替换 %1$d 个"
        },
        "vault_expiring_days": {
            "other": "%1$d 个保险箱备份将在 3 天内到期"
        }
    }

    # 1. Base values/strings.xml -> English (Standard Android fallback)
    os.makedirs('android/app/src/main/res/values', exist_ok=True)
    with open('android/app/src/main/res/values/strings.xml', 'w', encoding='utf-8') as f:
        f.write(build_xml(zh_keys, en_map, en_plurals))

    # 2. values-en/strings.xml -> English
    os.makedirs('android/app/src/main/res/values-en', exist_ok=True)
    with open('android/app/src/main/res/values-en/strings.xml', 'w', encoding='utf-8') as f:
        f.write(build_xml(zh_keys, en_map, en_plurals))

    # 3. values-zh/strings.xml -> Simplified Chinese
    os.makedirs('android/app/src/main/res/values-zh', exist_ok=True)
    with open('android/app/src/main/res/values-zh/strings.xml', 'w', encoding='utf-8') as f:
        f.write(build_xml(zh_keys, zh_map, zh_plurals))

    print(f"Successfully generated Base (EN), values-en (EN), and values-zh (ZH) with {len(zh_keys)} keys each!")

if __name__ == '__main__':
    run()
