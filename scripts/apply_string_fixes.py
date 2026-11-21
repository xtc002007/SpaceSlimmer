# -*- coding: utf-8 -*-
import os
import re
import xml.etree.ElementTree as ET

NEW_APP_STRINGS = {
    'values': {
        'profile_offline_edition_title': '100% Local &amp; Private',
        'profile_offline_edition_desc': 'All compression runs locally on device. Zero cloud uploads, no account required, complete data privacy.',
        'profile_offline_unlimited': 'Unlocked',
    },
    'values-en': {
        'profile_offline_edition_title': '100% Local &amp; Private',
        'profile_offline_edition_desc': 'All compression runs locally on device. Zero cloud uploads, no account required, complete data privacy.',
        'profile_offline_unlimited': 'Unlocked',
    },
    'values-zh': {
        'profile_offline_edition_title': '100% 本地隐私版',
        'profile_offline_edition_desc': '所有媒体文件全程在手机本地处理。绝不上传云端，无需注册登录，数据完全属于你。',
        'profile_offline_unlimited': '全功能已解锁',
    },
    'values-zh-rTW': {
        'profile_offline_edition_title': '100% 本地隱私版',
        'profile_offline_edition_desc': '所有媒體檔案全程在手機本地處理。絕不進行雲端上傳，無需註冊登入，數據完全屬於你。',
        'profile_offline_unlimited': '全功能已解鎖',
    },
    'values-de': {
        'profile_offline_edition_title': '100% Lokal &amp; Sicher',
        'profile_offline_edition_desc': 'Alle Komprimierungen erfolgen lokal auf Ihrem Gerät. Kein Cloud-Upload, kein Konto erforderlich, voller Datenschutz.',
        'profile_offline_unlimited': 'Freigeschaltet',
    },
    'values-es': {
        'profile_offline_edition_title': '100% Local y Privado',
        'profile_offline_edition_desc': 'Todo el procesamiento se ejecuta localmente en el dispositivo. Sin cargas en la nube, sin necesidad de cuenta, máxima privacidad.',
        'profile_offline_unlimited': 'Desbloqueado',
    },
    'values-fr': {
        'profile_offline_edition_title': '100% Local et Privé',
        'profile_offline_edition_desc': 'Tous les traitements s\\\'effectuent localement sur l\\\'appareil. Aucun envoi dans le cloud, aucun compte requis, confidentialité totale.',
        'profile_offline_unlimited': 'Débloqué',
        'share_savings_text': 'J\\\'ai économisé %1$s d\\\'espace avec SpaceSlimmer !',
        'replace_save_as_new_hint': 'Ce fichier ne peut pas écraser l\\\'original. Le résultat compressé sera enregistré comme un nouvel élément dans la galerie.',
    },
    'values-ja': {
        'profile_offline_edition_title': '100% ローカル＆プライベート',
        'profile_offline_edition_desc': 'すべての処理は端末内でローカルに実行されます。クラウド送信なし、アカウント登録不要、プライバシーを完全に保護します。',
        'profile_offline_unlimited': '全機能解放済み',
    },
    'values-ko': {
        'profile_offline_edition_title': '100% 로컬 &amp; 개인정보 보호',
        'profile_offline_edition_desc': '모든 압축 처리가 기기 내에서 로컬로 실행됩니다. 클라우드 전송 없음, 계정 가입 불필요, 완벽한 개인정보 보호.',
        'profile_offline_unlimited': '모든 기능 잠금 해제',
    },
    'values-pt-rBR': {
        'profile_offline_edition_title': '100% Local e Seguro',
        'profile_offline_edition_desc': 'Todo o processamento é executado localmente no dispositivo. Zero envios para nuvem, sem necessidade de conta, privacidade total.',
        'profile_offline_unlimited': 'Desbloqueado',
    },
    'values-ru': {
        'profile_offline_edition_title': '100% Локально и Безопасно',
        'profile_offline_edition_desc': 'Вся обработка выполняется локально на устройстве. Никаких облачных загрузок, аккаунт не требуется, полная конфиденциальность.',
        'profile_offline_unlimited': 'Разблокировано',
        'share_savings_text': 'Я сэкономил(а) %1$s памяти с помощью SpaceSlimmer!',
        'replace_save_as_new_hint': 'Этот файл невозможно перезаписать. Сжатый результат будет сохранён как новый объект галереи.',
    },
}

NEW_CORE_STRINGS = {
    'values': {
        'vault_original_file': 'Photo Library File (ID: %1$s)',
        'vault_original_path': 'Original Photo Location',
    },
    'values-en': {
        'vault_original_file': 'Photo Library File (ID: %1$s)',
        'vault_original_path': 'Original Photo Location',
    },
    'values-zh': {
        'vault_original_file': '相册原文件 (ID: %1$s)',
        'vault_original_path': '相册原位置',
    },
    'values-zh-rTW': {
        'vault_original_file': '相簿原檔案 (ID: %1$s)',
        'vault_original_path': '相簿原位置',
    },
    'values-de': {
        'vault_original_file': 'Originaldatei aus Fotomediathek (ID: %1$s)',
        'vault_original_path': 'Ursprünglicher Speicherort',
    },
    'values-es': {
        'vault_original_file': 'Archivo original de la galería (ID: %1$s)',
        'vault_original_path': 'Ubicación original en la galería',
    },
    'values-fr': {
        'vault_original_file': 'Fichier d\\\'origine de la galerie (ID: %1$s)',
        'vault_original_path': 'Emplacement d\\\'origine dans la galerie',
    },
    'values-ja': {
        'vault_original_file': 'アルバムの元ファイル (ID: %1$s)',
        'vault_original_path': 'アルバムの元の保存場所',
    },
    'values-ko': {
        'vault_original_file': '앨범 원본 파일 (ID: %1$s)',
        'vault_original_path': '앨범 원본 위치',
    },
    'values-pt-rBR': {
        'vault_original_file': 'Arquivo original da galeria (ID: %1$s)',
        'vault_original_path': 'Localização original na galeria',
    },
    'values-ru': {
        'vault_original_file': 'Исходный файл галереи (ID: %1$s)',
        'vault_original_path': 'Исходное расположение в галерее',
    },
}

def update_app_strings():
    app_res = 'android/app/src/main/res'
    for loc, items in NEW_APP_STRINGS.items():
        file_path = os.path.join(app_res, loc, 'strings.xml')
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        def fix_footer(m):
            tag_content = m.group(1).replace('100%%', '100%')
            return f'<string name="storage_ledger_dialog_footer" formatted="false">{tag_content}</string>'
        content = re.sub(r'<string name="storage_ledger_dialog_footer"(?: formatted="false")?>(.*?)</string>', fix_footer, content)

        def fix_strict(m):
            tag_content = m.group(1).replace('15%%', '15%')
            return f'<string name="video_strict_target" formatted="false">{tag_content}</string>'
        content = re.sub(r'<string name="video_strict_target"(?: formatted="false")?>(.*?)</string>', fix_strict, content)

        for k, v in items.items():
            pattern = rf'<string name="{k}"(?: formatted="[^"]*")?>.*?</string>'
            fmt_attr = ' formatted="false"' if k == 'profile_offline_edition_title' else ''
            new_line = f'<string name="{k}"{fmt_attr}>{v}</string>'
            if re.search(pattern, content):
                content = re.sub(pattern, new_line, content)
            else:
                content = content.replace('</resources>', f'    {new_line}\n</resources>')

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated app strings: {loc}")

def update_core_strings():
    core_res = 'android/core-android/src/main/res'
    for loc, items in NEW_CORE_STRINGS.items():
        file_path = os.path.join(core_res, loc, 'strings.xml')
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        for k, v in items.items():
            pattern = rf'<string name="{k}"(?: formatted="[^"]*")?>.*?</string>'
            fmt_attr = ' formatted="false"' if k == 'profile_offline_edition_title' else ''
            new_line = f'<string name="{k}"{fmt_attr}>{v}</string>'
            if re.search(pattern, content):
                content = re.sub(pattern, new_line, content)
            else:
                content = content.replace('</resources>', f'    {new_line}\n</resources>')

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated core strings: {loc}")

if __name__ == '__main__':
    update_app_strings()
    update_core_strings()
    print("All string updates applied.")
