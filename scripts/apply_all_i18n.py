# -*- coding: utf-8 -*-
import os
import json
import re
import xml.etree.ElementTree as ET

APP_RES = 'android/app/src/main/res'

LOCALES = {
    'values': 'en',
    'values-en': 'en',
    'values-zh': 'zh',
    'values-zh-rTW': 'zh-rTW',
    'values-ja': 'ja',
    'values-ko': 'ko',
    'values-es': 'es',
    'values-pt-rBR': 'pt-rBR',
    'values-de': 'de',
    'values-fr': 'fr',
    'values-ru': 'ru'
}

with open('scripts/translations_130.json', 'r', encoding='utf-8') as f:
    TRANS_130 = json.load(f)

# 17 new keys
NEW_KEYS = {
    "storage_internal_user_partition": {
        "en": "Internal Storage (User Data)",
        "zh": "内部存储 (用户数据分区)",
        "zh-rTW": "內部儲存 (使用者資料分區)",
        "ja": "内部ストレージ (ユーザーデータ)",
        "ko": "내부 저장공간 (사용자 데이터)",
        "es": "Almacenamiento interno (Datos de usuario)",
        "pt-rBR": "Armazenamento interno (Dados do usuário)",
        "de": "Interner Speicher (Benutzerdaten)",
        "fr": "Stockage interne (Données utilisateur)",
        "ru": "Внутренняя память (Данные пользователя)",
    },
    "storage_ledger_summary": {
        "en": "Device specs: %1$s · System & firmware reserved: %2$s",
        "zh": "整机 %1$s 规格 · 系统与固件预留约 %2$s",
        "zh-rTW": "全機 %1$s 規格 · 系統與韌體預留約 %2$s",
        "ja": "端末仕様：%1$s · システム・ファームウェア予約：%2$s",
        "ko": "기기 사양: %1$s · 시스템 및 펌웨어 예약: %2$s",
        "es": "Especificaciones del dispositivo: %1$s · Reservado para sistema y firmware: %2$s",
        "pt-rBR": "Especificações do dispositivo: %1$s · Reservado para sistema e firmware: %2$s",
        "de": "Gerätespezifikation: %1$s · System & Firmware reserviert: %2$s",
        "fr": "Spécifications de l'appareil: %1$s · Réservé au système et micrologiciel: %2$s",
        "ru": "Спецификация устройства: %1$s · Зарезервировано системой и прошивкой: %2$s",
    },
    "storage_ledger_view_btn": {
        "en": "View Breakdown",
        "zh": "查看账本",
        "zh-rTW": "檢視明細",
        "ja": "詳細を見る",
        "ko": "상세 내역 보기",
        "es": "Ver desglose",
        "pt-rBR": "Ver detalhamento",
        "de": "Aufschlüsselung anzeigen",
        "fr": "Voir la répartition",
        "ru": "Посмотреть детали",
    },
    "storage_ledger_dialog_title": {
        "en": "Storage Capacity Breakdown",
        "zh": "手机存储空间完整账本",
        "zh-rTW": "手機儲存空間完整明細",
        "ja": "ストレージ容量の詳細内訳",
        "ko": "저장 공간 용량 세부 내역",
        "es": "Desglose de capacidad de almacenamiento",
        "pt-rBR": "Detalhamento da capacidade de armazenamento",
        "de": "Aufschlüsselung der Speicherkapazität",
        "fr": "Détail de la capacité de stockage",
        "ru": "Полная расшифровка памяти",
    },
    "storage_ledger_dialog_desc": {
        "en": "Factory nominal hardware capacity is %1$s. Due to system architecture and partition layout, actual space is divided as follows:",
        "zh": "手机出厂硬件标称容量为 %1$s。由于系统底层架构与安全保护划分，各部分实际占用如下：",
        "zh-rTW": "手機原廠硬體標稱容量為 %1$s。由於系統底層架構與安全保護劃分，各部分實際佔用如下：",
        "ja": "工場出荷時の公称ハードウェア容量は %1$s です。システム構成とパーティション分割により、実際の領域は以下の通りです：",
        "ko": "출하 시 공칭 하드웨어 용량은 %1$s입니다. 시스템 아키텍처 및 파티션 분할로 인해 실제 공간은 다음과 같이 나뉩니다:",
        "es": "La capacidad nominal de hardware de fábrica es de %1$s. Debido a la arquitectura del sistema y particiones, el espacio real se distribuye así:",
        "pt-rBR": "A capacidade nominal de fábrica é de %1$s. Devido à arquitetura do sistema e partições, o espaço real é dividido assim:",
        "de": "Die Nennkapazität ab Werk beträgt %1$s. Aufgrund von Systemarchitektur und Partitionierung teilt sich der Speicher wie folgt auf:",
        "fr": "La capacité nominale matérielle est de %1$s. En raison de l'architecture système et des partitions, l'espace réel est réparti comme suit:",
        "ru": "Номинальная заводская емкость составляет %1$s. Из-за архитектуры системы и разметки разделов память распределена следующим образом:",
    },
    "storage_ledger_item_1": {
        "en": "1. Decimal-to-binary manufacturer conversion loss: ~17.6 GB",
        "zh": "1. 厂商十进制与系统换算损耗：约 17.6 GB",
        "zh-rTW": "1. 廠商十進位與系統換算損耗：約 17.6 GB",
        "ja": "1. 10進法表記と2進法計算の換算差：約 17.6 GB",
        "ko": "1. 십진수 및 이진수 환산 손실: 약 17.6 GB",
        "es": "1. Pérdida por conversión decimal a binario: ~17.6 GB",
        "pt-rBR": "1. Perda de conversão decimal para binário: ~17,6 GB",
        "de": "1. Umrechnungsverlust (Dezimal/Binär): ca. 17,6 GB",
        "fr": "1. Perte de conversion décimale/binaire: env. 17,6 Go",
        "ru": "1. Разница между десятичной и двоичной системами: ~17.6 ГБ",
    },
    "storage_ledger_item_2": {
        "en": "2. Android A/B dual system & hardware drivers: ~25–35 GB",
        "zh": "2. Android A/B 升级双系统与驱动：约 25~35 GB",
        "zh-rTW": "2. Android A/B 升級雙系統與驅動：約 25~35 GB",
        "ja": "2. Android A/Bデュアルシステムとドライバ：約 25〜35 GB",
        "ko": "2. Android A/B 듀얼 시스템 및 드라이버: 약 25~35 GB",
        "es": "2. Sistema dual Android A/B y controladores: ~25–35 GB",
        "pt-rBR": "2. Sistema duplo Android A/B e drivers: ~25–35 GB",
        "de": "2. Android A/B Dual-System & Treiber: ca. 25–35 GB",
        "fr": "2. Double système Android A/B et pilotes: env. 25–35 Go",
        "ru": "2. Двойная система Android A/B и драйверы: ~25–35 ГБ",
    },
    "storage_ledger_item_3": {
        "en": "3. Flash garbage collection & RAM extension: ~15–20 GB",
        "zh": "3. 闪存垃圾回收与运存拓展(RAM+)：约 15~20 GB",
        "zh-rTW": "3. 快閃記憶體垃圾回收與記憶體拓展(RAM+)：約 15~20 GB",
        "ja": "3. フラッシュGCとRAM拡張：約 15〜20 GB",
        "ko": "3. 플래시 가비지 컬렉션 및 가상 RAM 확장: 약 15~20 GB",
        "es": "3. Recolección de basura flash y extensión RAM+: ~15–20 GB",
        "pt-rBR": "3. Coleta de lixo de memória flash e extensão de RAM+: ~15–20 GB",
        "de": "3. Flash-Speicher-GC & RAM-Erweiterung: ca. 15–20 GB",
        "fr": "3. Récupération d'espace flash et extension RAM+: env. 15–20 Go",
        "ru": "3. Сборка мусора флэш-памяти и расширение RAM+: ~15–20 ГБ",
    },
    "storage_ledger_item_4": {
        "en": "4. User data & app usable partition: %1$s",
        "zh": "4. 用户与应用可用的完整物理分区：%1$s",
        "zh-rTW": "4. 使用者與應用程式可用的完整實體分區：%1$s",
        "ja": "4. ユーザーおよびアプリが利用可能な物理領域：%1$s",
        "ko": "4. 사용자와 앱이 사용 가능한 실제 파티션: %1$s",
        "es": "4. Partición utilizable para usuario y aplicaciones: %1$s",
        "pt-rBR": "4. Partição utilizável para usuário e apps: %1$s",
        "de": "4. Für Benutzer & Apps nutzbare Partition: %1$s",
        "fr": "4. Partition utilisable par l'utilisateur et les applications: %1$s",
        "ru": "4. Раздел для пользователя и приложений: %1$s",
    },
    "storage_ledger_dialog_footer": {
        "en": "This scan covers 100% of this partition with no files omitted.",
        "zh": "当前全景扫描已完整覆盖该分区内的全部 100% 空间，未遗漏任何文件。",
        "zh-rTW": "目前全景掃描已完整覆蓋該分區內的全部 100% 空間，未遺漏任何檔案。",
        "ja": "現在のスキャンはこのパーティション内の全ファイルを100%カバーしています。",
        "ko": "현재 전체 스캔은 이 파티션의 100% 공간을 누락 없이 포함합니다.",
        "es": "Este escaneo cubre el 100% de esta partición sin omitir archivos.",
        "pt-rBR": "Esta verificação cobre 100% desta partição sem omitir nenhum arquivo.",
        "de": "Dieser Scan deckt 100 % dieser Partition ab, ohne Dateien auszulassen.",
        "fr": "Cette analyse couvre 100 % de cette partition sans omettre aucun fichier.",
        "ru": "Текущее сканирование полностью охватывает 100% этого раздела без пропусков.",
    },
    "action_got_it": {
        "en": "Got It",
        "zh": "我知道了",
        "zh-rTW": "我知道了",
        "ja": "了解",
        "ko": "확인",
        "es": "Entendido",
        "pt-rBR": "Entendi",
        "de": "Verstanden",
        "fr": "Compris",
        "ru": "Понятно",
    },
    "storage_partition_spec": {
        "en": "User Partition %1$s (Device %2$s)",
        "zh": "用户分区 %1$s (整机 %2$s)",
        "zh-rTW": "使用者分區 %1$s (全機 %2$s)",
        "ja": "ユーザー領域 %1$s (全体 %2$s)",
        "ko": "사용자 파티션 %1$s (전체 %2$s)",
        "es": "Partición de usuario %1$s (Total %2$s)",
        "pt-rBR": "Partição do usuário %1$s (Total %2$s)",
        "de": "Benutzerpartition %1$s (Gerät %2$s)",
        "fr": "Partition utilisateur %1$s (Appareil %2$s)",
        "ru": "Пользовательский раздел %1$s (Устройство %2$s)",
    },
    "storage_folder_tag": {
        "en": "Folder",
        "zh": "文件夹",
        "zh-rTW": "資料夾",
        "ja": "フォルダ",
        "ko": "폴더",
        "es": "Carpeta",
        "pt-rBR": "Pasta",
        "de": "Ordner",
        "fr": "Dossier",
        "ru": "Папка",
    },
    "storage_system_stat_tag": {
        "en": "System Stat",
        "zh": "系统统计",
        "zh-rTW": "系統統計",
        "ja": "システム統計",
        "ko": "시스템 통계",
        "es": "Estadística del sistema",
        "pt-rBR": "Estatísticas do sistema",
        "de": "Systemstatistik",
        "fr": "Statistiques système",
        "ru": "Статистика системы",
    },
    "storage_collapsed_items": {
        "en": "%1$d other smaller items",
        "zh": "其他 %1$d 个较小项",
        "zh-rTW": "其他 %1$d 個較小項目",
        "ja": "その他 %1$d 個の小さな項目",
        "ko": "기타 %1$d개의 작은 항목",
        "es": "Otros %1$d elementos más pequeños",
        "pt-rBR": "Outros %1$d itens menores",
        "de": "Weitere %1$d kleinere Elemente",
        "fr": "Autres %1$d éléments plus petits",
        "ru": "Другие %1$d мелких элементов",
    },
    "storage_free_space": {
        "en": "Available Free Space",
        "zh": "剩余可用空间",
        "zh-rTW": "剩餘可用空間",
        "ja": "空き容量",
        "ko": "사용 가능한 여유 공간",
        "es": "Espacio libre disponible",
        "pt-rBR": "Espaço livre disponível",
        "de": "Verfügbarer freier Speicher",
        "fr": "Espace libre disponible",
        "ru": "Свободное место",
    },
    "storage_system_private_data": {
        "en": "System & Private App Data",
        "zh": "系统与私有数据",
        "zh-rTW": "系統與私有資料",
        "ja": "システムおよび非公開データ",
        "ko": "시스템 및 비공개 앱 데이터",
        "es": "Sistema y datos privados de apps",
        "pt-rBR": "Sistema e dados privados de apps",
        "de": "System- & private App-Daten",
        "fr": "Système et données privées d'applications",
        "ru": "Система и личные данные приложений",
    },
}

ALL_ADDITIONS = {}
for k, v in TRANS_130.items():
    ALL_ADDITIONS[k] = v
for k, v in NEW_KEYS.items():
    ALL_ADDITIONS[k] = v

def escape_xml_text(s):
    # Escape &, <, >
    # Also escape apostrophe as \' if not already escaped
    # Note: don't double escape &amp;
    s = s.replace('&', '&amp;') if '&amp;' not in s else s
    # Avoid double escaping quotes or apostrophes
    # For Android strings.xml, apostrophe must be \'
    clean = ""
    i = 0
    while i < len(s):
        if s[i] == "'" and (i == 0 or s[i-1] != '\\'):
            clean += "\\'"
        elif s[i] == '"' and (i == 0 or s[i-1] != '\\'):
            clean += '\\"'
        else:
            clean += s[i]
        i += 1
    return clean

for loc_folder, lang in LOCALES.items():
    file_path = os.path.join(APP_RES, loc_folder, 'strings.xml')
    if not os.path.exists(file_path):
        print(f"Error: {file_path} not found")
        continue

    tree = ET.parse(file_path)
    root = tree.getroot()
    existing_keys = {s.attrib['name'] for s in root.findall('string')}

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    new_lines = []
    for k in sorted(ALL_ADDITIONS.keys()):
        if k not in existing_keys:
            val_map = ALL_ADDITIONS[k]
            text = val_map.get(lang, val_map.get('en', ''))
            escaped = escape_xml_text(text)
            new_lines.append(f'    <string name="{k}">{escaped}</string>')

    if new_lines:
        print(f"Adding {len(new_lines)} keys to {loc_folder}/strings.xml")
        block = '\n'.join(new_lines) + '\n'
        # Insert before first <plurals or before </resources>
        plurals_pos = content.find('<plurals')
        if plurals_pos != -1:
            updated = content[:plurals_pos] + block + content[plurals_pos:]
        else:
            res_close = content.rfind('</resources>')
            updated = content[:res_close] + block + content[res_close:]

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(updated)
    else:
        print(f"No new keys needed for {loc_folder}/strings.xml")

print("Done applying i18n keys.")
