# -*- coding: utf-8 -*-
"""
Generates production Google Play Store listings for 12 key locales:
en-US, en-IN, hi-IN, es-419, pt-BR, zh-CN, zh-TW, ja-JP, ko-KR, de-DE, fr-FR, id, ru-RU.
Also outputs clean listing_en.txt, listing_zh.txt, and update_payload.json.
All copies adhere to:
- Title <= 30 characters
- Short description <= 80 characters
- Full description <= 4000 characters
- Free of internal developer jargon (no "WinDirStat", "cushion shading", "3-chunk hash")
- 100% aligned with offline Route A (100% on-device, zero uploads, 7-30 day customizable vault)
"""
import os
import json

LOCALES_DATA = {
    "en-US": {
        "release_notes": "- 100% on-device local compression: Zero cloud uploads, total privacy.\n- Enhanced H.264 & HEVC hardware video compression with up to 80% space savings.\n- Interactive visual storage treemap to easily spot hidden heavy files.\n- Safe Recovery Vault with customizable 7 to 30-day restore protection.\n- Performance improvements and UI bug fixes.",
        "name": "SpaceSlimmer: Video Compressor",
        "short": "Compress videos & photos up to 80%. Visual storage treemap & safe vault undo.",
        "full": """Running out of phone storage? Tired of being forced to delete precious family memories and 4K videos just to make space?

Meet SpaceSlimmer — the intelligent on-device video compressor, photo optimizer, and visual storage analyzer designed to shrink bulky videos and photos by up to 50%–80% without visible loss in quality, and without ever losing your original files.

Unlike ordinary cleaner apps that delete your files without remorse, SpaceSlimmer’s promise is simple: Keep your memories, drop the file size, and always have the option to undo.

WHY CHOOSE SPACESLIMMER?

1. 100% Offline & Private (Zero Cloud Uploads)
Your private videos and camera roll never leave your phone. All video, photo, and audio encoding is performed entirely on your device using hardware acceleration and high-speed native processing. No account required, zero tracking, total privacy.

2. Original Safe Vault (Never Worry About Damaging Originals)
Afraid of losing quality? When you choose to replace original files, SpaceSlimmer safely backs them up into an isolated sandbox Vault with a customizable 7 to 30-day grace period. If you ever change your mind, restore your original uncompressed files with a single tap.

3. Real-Time Split-Screen Comparison Slider
Never guess what your compressed video looks like. Use the interactive before-and-after curtain slider to compare original and compressed footage side-by-side in real-time. Both sides scrub and play in millisecond sync so you can verify sharpness and facial details before saving.

4. Visual Storage Treemap (See What’s Taking Up Space)
Stop guessing where your gigabytes disappeared. SpaceSlimmer displays your storage as an intuitive, colorful visual block map. Tap into any block to instantly locate hidden gigabyte-hogging videos, cache folders, and oversized downloads.

5. Smart Target Size for Easy Sharing
Need to send a video through messaging apps? Choose quick presets for WhatsApp (16MB), Discord (25MB), or Gmail (50MB), or enter an exact target megabyte limit. SpaceSlimmer automatically calculates the optimal bitrate to guarantee your video fits.

6. Smart Skip Guard (Zero Quality Loss)
SpaceSlimmer’s intelligent engine inspects existing codecs and bitrates. If a file is already optimized or further compression would degrade quality without meaningful savings, it automatically skips it.

POWERFUL FEATURES:

* High-Speed Video Compressor
- Hardware GPU Acceleration: Fast H.264 and HEVC (H.265) encoding that stays cool and saves battery.
- Flexible Downscaling: Seamlessly downscale bulky 4K 60fps clips into crystal-clear 1080p.
- Metadata Preservation: Retains video orientation, shooting timestamp, and audio sync.

* Batch Photo & Audio Slimmer
- Batch compress camera photos (JPEG, PNG, WebP) in bulk.
- Transcode large voice notes and lossless WAV/FLAC audio into ultra-compact AAC/Opus, saving up to 80% space.

* Deep Storage Cleanup Tools
- Duplicate File Finder: Fast multi-point comparison identifies exact duplicate photos and videos.
- Big File Explorer: Sort and filter all files across your device by size, date, or category.
- Storage Growth Tracker: Compare storage snapshots over time to catch apps that grow out of control.

PERFECT FOR:
- Parents capturing hours of family and kid videos.
- Travelers and creators with full camera rolls after vacations.
- Anyone sharing video clips on WhatsApp, Discord, or Instagram.
- Anyone with a 64GB, 128GB, or 256GB device looking to reclaim gigabytes!

Free up your phone storage today with SpaceSlimmer — The smartest, safest video compressor!"""
    },
    "en-IN": {
        "release_notes": "- Fast on-device video compression: Save up to 80% phone space.\n- WhatsApp & social media presets (16MB/25MB/50MB) for quick sharing.\n- Visual storage block treemap to find heavy videos and cache folders.\n- Safe Recovery Vault to restore original files anytime.\n- Improved stability and zero battery drain.",
        "name": "SpaceSlimmer: Video Compressor",
        "short": "Compress videos & photos up to 80%. Visual storage treemap & safe vault undo.",
        "full": """Running out of phone storage? Tired of being forced to delete precious family memories and 4K videos just to make space?

Meet SpaceSlimmer — the intelligent on-device video compressor, photo optimizer, and visual storage analyzer designed to shrink bulky videos and photos by up to 50%–80% without visible loss in quality, and without ever losing your original files.

Unlike ordinary cleaner apps that delete your files without remorse, SpaceSlimmer’s promise is simple: Keep your memories, drop the file size, and always have the option to undo.

WHY CHOOSE SPACESLIMMER?

1. 100% Offline & Private (Zero Cloud Uploads)
Your private videos and camera roll never leave your phone. All video, photo, and audio encoding is performed entirely on your device using hardware acceleration and high-speed native processing. No account required, zero tracking, total privacy.

2. Original Safe Vault (Never Worry About Damaging Originals)
Afraid of losing quality? When you choose to replace original files, SpaceSlimmer safely backs them up into an isolated sandbox Vault with a customizable 7 to 30-day grace period. If you ever change your mind, restore your original uncompressed files with a single tap.

3. Real-Time Split-Screen Comparison Slider
Never guess what your compressed video looks like. Use the interactive before-and-after curtain slider to compare original and compressed footage side-by-side in real-time. Both sides scrub and play in millisecond sync so you can verify sharpness and facial details before saving.

4. Visual Storage Treemap (See What’s Taking Up Space)
Stop guessing where your gigabytes disappeared. SpaceSlimmer displays your storage as an intuitive, colorful visual block map. Tap into any block to instantly locate hidden gigabyte-hogging videos, cache folders, and oversized downloads.

5. Smart Target Size for Easy Sharing
Need to send a video through messaging apps? Choose quick presets for WhatsApp (16MB), Discord (25MB), or Gmail (50MB), or enter an exact target megabyte limit. SpaceSlimmer automatically calculates the optimal bitrate to guarantee your video fits.

6. Smart Skip Guard (Zero Quality Loss)
SpaceSlimmer’s intelligent engine inspects existing codecs and bitrates. If a file is already optimized or further compression would degrade quality without meaningful savings, it automatically skips it.

POWERFUL FEATURES:

* High-Speed Video Compressor
- Hardware GPU Acceleration: Fast H.264 and HEVC (H.265) encoding that stays cool and saves battery.
- Flexible Downscaling: Seamlessly downscale bulky 4K 60fps clips into crystal-clear 1080p.
- Metadata Preservation: Retains video orientation, shooting timestamp, and audio sync.

* Batch Photo & Audio Slimmer
- Batch compress camera photos (JPEG, PNG, WebP) in bulk.
- Transcode large voice notes and lossless WAV/FLAC audio into ultra-compact AAC/Opus, saving up to 80% space.

* Deep Storage Cleanup Tools
- Duplicate File Finder: Fast multi-point comparison identifies exact duplicate photos and videos.
- Big File Explorer: Sort and filter all files across your device by size, date, or category.
- Storage Growth Tracker: Compare storage snapshots over time to catch apps that grow out of control.

PERFECT FOR:
- Parents capturing hours of family and kid videos.
- Travelers and creators with full camera rolls after vacations.
- Anyone sharing video clips on WhatsApp, Discord, or Instagram.
- Anyone with a 64GB, 128GB, or 256GB device looking to reclaim gigabytes!

Free up your phone storage today with SpaceSlimmer — The smartest, safest video compressor!"""
    },
    "hi-IN": {
        "release_notes": "- 100% ऑन-डिवाइस कंप्रेशन: फोन का स्पेस 80% तक बचाएं।\n- WhatsApp और सोशल मीडिया के लिए 16MB/25MB के आसान प्रीसेट।\n- विजुअल स्टोरेज ट्रीमैन से बड़ी और छिपी फाइलों को तुरंत खोजें।\n- सेफ रिकवरी वॉल्ट: मूल फाइलों को कभी भी 1-टैप में वापस पाएं।\n- प्रदर्शन में सुधार और बग फिक्स।",
        "name": "SpaceSlimmer: वीडियो कंप्रेसर",
        "short": "वीडियो और फोटो को 80% तक कंप्रेस करें, विजुअल स्टोरेज मैप और सुरक्षित वॉल्ट।",
        "full": """क्या आपके फोन का स्टोरेज भर गया है? क्या आपको स्पेस बनाने के लिए अपनी पसंदीदा पारिवारिक यादें और 4K वीडियो हटाने पड़ रहे हैं?

मिलिए SpaceSlimmer से — एक स्मार्ट ऑन-डिवाइस वीडियो कंप्रेसर और विजुअल स्टोरेज क्लीनर जो बिना किसी दृश्य गुणवत्ता नुकसान के और बिना आपके मूल फोटो-वीडियो खोए, मीडिया साइज को 50% से 80% तक छोटा करता है।

SpaceSlimmer की खासियतें:

1. 100% ऑफलाइन और पूरी तरह सुरक्षित
आपके वीडियो और फोटो कभी भी फोन से बाहर नहीं जाते। पूरा कंप्रेशन आपके फोन के हार्डवेयर चिप और नेटिव इंजन पर होता है। कोई अकाउंट नहीं, कोई इंटरनेट अपलोड नहीं, 100% गोपनीयता।

2. ओरिजिनल सेफ वॉल्ट (7 से 30 दिन का बैकअप)
क्वालिटी खराब होने का कोई डर नहीं! जब आप ओरिजिनल वीडियो को बदलते हैं, तो SpaceSlimmer ओरिजिनल फाइल को 7 से 30 दिनों के लिए एक सुरक्षित वॉल्ट में सुरक्षित रखता है। एक टैप में जब चाहें ओरिजिनल फाइल वापस पाएं।

3. रीयल-टाइम साइड-बाय-साइड तुलना स्लाइडर
कंप्रेशन के बाद वीडियो कैसा दिखेगा? दोनों वीडियो को साथ चलाकर स्लाइडर से देखें। चेहरे की डिटेल और शार्पनेस चेक करें, संतुष्ट होने पर ही सेव करें।

4. विजुअल स्टोरेज मैप (कहाँ भरा है स्टोरेज?)
आसान रंगीन ब्लॉक्स में देखें कि फोन का स्पेस किस फोल्डर या बड़े वीडियो ने घेरा हुआ है।

5. WhatsApp और सोशल मीडिया के लिए टारगेट साइज
WhatsApp (16MB), Gmail (50MB) के लिए एक क्लिक में वीडियो का साइज सेट करें।

मुख्य फीचर्स:
- जीपीयू हार्डवेयर एक्सेलेरेशन (H.264 और H.265 / HEVC)
- 4K से 1080p में आसान डाउनस्केलिंग
- फोटो और ऑडियो का बल्क कंप्रेशन
- डुप्लिकेट फोटो और वीडियो क्लीनर

आज ही SpaceSlimmer डाउनलोड करें और अपने फोन का स्टोरेज फ्री करें!"""
    },
    "es-419": {
        "release_notes": "- Compresión 100% en el dispositivo: Ahorra hasta un 80% de espacio sin subir nada a la nube.\n- Ajustes predefinidos para WhatsApp (16MB) y redes sociales.\n- Mapa visual de almacenamiento para detectar archivos pesados al instante.\n- Bóveda de seguridad (Safe Vault) para restaurar originales cuando quieras.\n- Mejoras de rendimiento y corrección de errores.",
        "name": "SpaceSlimmer: Comprimir Video",
        "short": "Comprime videos y fotos hasta un 80%. Mapa visual y bóveda de restauración.",
        "full": """¿Te estás quedando sin espacio en el teléfono? ¿Cansado de borrar fotos y videos 4K familiares solo para liberar memoria?

Descubre SpaceSlimmer: el compresor de video inteligente, optimizador de fotos y analizador visual de almacenamiento diseñado para reducir el tamaño de tus videos y fotos entre un 50% y un 80% sin pérdida visual de calidad y sin perder jamás tus archivos originales.

A diferencia de los limpiadores comunes que eliminan tus archivos para siempre, la promesa de SpaceSlimmer es clara: conserva tus recuerdos, reduce el tamaño y ten siempre la opción de restaurar el original.

¿POR QUÉ ELEGIR SPACESLIMMER?

1. 100% Local y Privado (Cero cargas a la nube)
Tus videos y carrete jamás salen de tu teléfono. Toda la codificación se realiza localmente con aceleración de hardware. No necesitas cuenta ni registro, sin rastreo, privacidad total.

2. Bóveda Segura de Originales (Restauración en 7 a 30 días)
¿Temes que el resultado no te convenza? Al reemplazar los archivos originales, SpaceSlimmer los resguarda en una Bóveda protegida durante un período configurable de 7 a 30 días. Restaura tus archivos originales intactos con un solo toque.

3. Deslizador de Comparación en Tiempo Real
No adivines cómo quedará el video comprimido. Compara el antes y el después con un control interactivo en pantalla dividida que reproduce ambos videos en sincronía de milisegundos para verificar cada detalle antes de guardar.

4. Mapa Visual de Almacenamiento (Treemap)
Descubre al instante qué carpetas y videos gigantes están consumiendo tu memoria con un mapa de bloques visual e interactivo.

5. Tamaño Objetivo para Enviar por WhatsApp
Define el límite exacto en megabytes (WhatsApp 16MB, Gmail 50MB, Discord 25MB) para enviar videos sin que la aplicación de mensajería los bloquee.

6. Protección contra Falsa Compresión
Si un archivo ya está optimizado o comprimirlo lo empeoraría, SpaceSlimmer lo detecta y lo omite automáticamente.

CARACTERÍSTICAS PRINCIPALES:
- Aceleración por GPU (H.264 y HEVC / H.265): rápido y sin sobrecalentar el móvil.
- Conversión de 4K a 1080p nítido para ahorrar gigabytes masivos.
- Compresión por lotes de fotos (JPEG, PNG, WebP) y audios (WAV/FLAC a AAC/Opus).
- Buscador de archivos duplicados y explorador de archivos grandes.

¡Descarga SpaceSlimmer hoy y recupera espacio en tu teléfono de forma segura!"""
    },
    "pt-BR": {
        "release_notes": "- Compressão 100% local no aparelho: Libere até 80% de espaço com privacidade total.\n- Predefinições rápidas para WhatsApp (16MB) e redes sociais.\n- Mapa visual de blocos (Treemap) para achar arquivos pesados num toque.\n- Cofre Seguro (Safe Vault) para restaurar arquivos originais a qualquer momento.\n- Melhorias de desempenho e correções de estabilidade.",
        "name": "SpaceSlimmer: Compactar Vídeo",
        "short": "Comprima vídeos e fotos em até 80%. Mapa de memória e cofre de restauração.",
        "full": """Sem espaço no celular? Cansado de ter que apagar fotos e vídeos 4K da família só para liberar armazenamento?

Conheça o SpaceSlimmer: o compressor inteligente de vídeos e fotos e analisador visual de memória que reduz o tamanho dos seus arquivos de 50% a 80% sem perda visual de qualidade e sem jamais perder seus originais.

Ao contrário de limpadores comuns que excluem tudo sem volta, a proposta do SpaceSlimmer é simples: mantenha suas memórias, diminua o tamanho e tenha sempre a opção de restaurar.

POR QUE ESCOLHER O SPACESLIMMER?

1. 100% Local e Privado (Zero Upload na Nuvem)
Seus vídeos e fotos particulares nunca saem do celular. Toda a compressão roda diretamente no processador do seu aparelho com aceleração por hardware. Sem conta, sem cadastro, privacidade absoluta.

2. Cofre Seguro de Originais (Recuperação em 7 a 30 dias)
Medo de perder a qualidade original? Ao substituir arquivos, o SpaceSlimmer isola os originais em um Cofre seguro com prazo configurável de 7 a 30 dias. Restaure o arquivo original intacto com um simples toque.

3. Comparador em Tempo Real com Cortina Deslizante
Veja exatamente o resultado antes de salvar. Compare lado a lado o vídeo original e o comprimido com sincronia de milissegundos para conferir detalhes e nitidez.

4. Mapa Visual de Armazenamento
Veja onde foram parar seus gigabytes com um mapa visual em blocos intuitivos. Localize na hora vídeos pesados e pastas ocultas.

5. Tamanho Alvo para Envio no WhatsApp
Escolha predefinições para WhatsApp (16MB), Discord (25MB) ou Gmail (50MB) para garantir que seus vídeos caibam no limite de envio.

6. Proteção Inteligente contra Compressão Desnecessária
Detecta arquivos que já estão comprimidos para não recompactar sem ganho real de espaço.

PRINCIPAIS RECURSOS:
- Aceleração por GPU (H.264 e HEVC/H.265) veloz e econômica em bateria.
- Redimensionamento inteligente de 4K para 1080p nítido.
- Compressão em lote de fotos (JPEG, PNG, WebP) e áudio (WAV/FLAC para AAC/Opus).
- Localizador de arquivos duplicados e explorador de arquivos gigantes.

Baixe o SpaceSlimmer agora e recupere o espaço do seu smartphone!"""
    },
    "zh-CN": {
        "release_notes": "- 100% 本机离线处理：零网络请求，彻底保护相册隐私。\n- 升级 H.264 与 HEVC 硬件加速视频压缩，单视频最高立省 80% 空间。\n- 电脑级区块树图全景透视机身存储，大文件与缓存一目了然。\n- 原片安全隔离保险箱，支持 7~30 天随时一键原样复原回相册。\n- 修复多项界面显示细节并提升多核压缩稳定性。",
        "name": "SpaceSlimmer: 视频压缩与存储瘦身",
        "short": "智能视频照片无损压缩，释放高达80%存储，全景树图与30天原片保险箱。",
        "full": """手机存储空间经常告急？舍不得删除珍贵的高清视频和相册回忆？

SpaceSlimmer 是一款专为手机深度减负打造的智能媒体压缩与存储全景分析神器！最高可为你瘦身 50%~80% 的视频与相册体积，全程肉眼无损画质，且绝不会丢失原始文件。

不同于粗暴删除文件的普通清理工具，SpaceSlimmer 的承诺很简单：留住回忆，大幅压缩，并且永远拥有随时后悔还原的机会！

为什么选择 SPACESLIMMER？

1. 100% 本机离线处理（零云端上传，极致隐私）
你的相册与隐私视频绝不上传任何服务器。所有视频、图片与音频压缩均在手机本地由芯片硬件加速与高性能原生引擎驱动。无需注册登录，零广告打扰，彻底保护用户数据安全。

2. 原片隔离保险箱（可自定义 7~30 天后悔宽限期）
担心压缩画质不满意？替换原文件时，SpaceSlimmer 会自动将原始未压缩文件隔离保存在沙盒保险箱中，享有长达 7~30 天的宽限期。在此期间，只需一键即可随时将原片完整复原回相册。零风险，安心瘦身！

3. 实时前后对比百叶窗（所见即所得）
无需凭空猜测压缩效果。内置实时双轨前后对比滑块，原片与压缩后画面毫秒级同步联动播放，随时拖动检视人脸细节与画质清晰度，满意后再保存。

4. 电脑级区块树图（全景透视机身空间）
告别盲目翻找大文件。SpaceSlimmer 将直观的自适应区块树图带入手机端。色彩分明的矩形方块直观展现机身各个目录与大文件的真实占用，哪里的文件在偷吃空间，一目了然。

5. 智能指定目标体积（社交分享绝不超标）
针对微信、邮件、Discord、WhatsApp 等聊天工具的发送限制，可自由指定压缩后的目标大小（例如 16MB、25MB、50MB）。智能引擎自动演算最优码率，确保体积达标且画质最佳。

6. 智能避重守护（杜绝反向变大）
自动检测文件的现有编码与码率。对于已经高度压缩或再压缩无益的文件，智能跳过，绝不做多余处理。

核心功能一览：

* 高性能视频极速压缩
- 手机硬件 GPU 加速：支持 H.264 与 HEVC (H.265) 硬件编码，省电低温、速度飞快。
- 智能分辨率下调：可将庞大的 4K 60帧视频平滑转为清爽的 1080p，单视频立省数个 GB。
- 完整保留拍摄时间、相机型号、旋转方向及多声道音轨。

* 照片与音频批量瘦身
- 支持 JPEG、PNG、WebP 高清大图批量瘦身，相册一键清理。
- 将录音、会议记录、无损 WAV/FLAC 快速转制为超省空间的 AAC/Opus。

* 深度存储清理利器
- 重复文件排查：精准排查完全一致的重复照片与多余视频副本。
- 全盘大文件排查：快速按大小、类型筛选整个手机的重量级文件。
- 历史快照对比：追踪两次扫描之间的空间净增减，揪出默默变大的应用缓存。

适合人群：
- 经常录制家庭与萌娃生活视频的家长
- 旅拍、自媒体博主与视频创作者
- 需要通过社交软件高频互传视频的职场人士
- 使用 64GB / 128GB / 256GB 存储空间、希望延长手机使用寿命的用户

立即下载 SpaceSlimmer，让你的手机存储轻装上阵！"""
    },
    "zh-TW": {
        "release_notes": "- 100% 本地端離線處理：零雲端上傳，徹底守護隱私。\n- 升級 H.264 與 HEVC 硬體晶片加速壓縮，最高立省 80% 容量。\n- 區塊樹狀圖全景透視內部空間，隱藏巨型檔案無所遁形。\n- 原檔安全隔離保險箱，支援 7~30 天隨時一鍵原樣復原。\n- 提升執行效能並修正多項介面顯示細節。",
        "name": "SpaceSlimmer: 影片壓縮與儲存瘦身",
        "short": "智慧影片相片無損壓縮，釋放高達80%容量，全景樹圖與安全保險箱復原。",
        "full": """手機容量總是不足？捨不得刪除珍貴的高畫質影片與家庭回憶？

SpaceSlimmer 是一款專為手機空間深度瘦身打造的智慧媒體壓縮與儲存全景分析利器！最高可為您節省 50%~80% 的影片與相片空間，肉眼無損畫質，且絕不遺失原始檔案。

有別於粗暴刪除檔案的一般清理工具，SpaceSlimmer 的承諾很簡單：守護回憶、大幅縮小體積，並永遠保有隨時還原的機會！

為什麼選擇 SPACESLIMMER？

1. 100% 裝置本機離線處理（零雲端上傳，極致隱私）
您的隱私相片與影片絕不傳輸至任何外部伺服器。全部處理均於手機端由晶片硬體加速驅動。無需註冊登入，零追蹤，徹底守護您的個人隱私。

2. 原檔安全保險箱（自訂 7~30 天寬限復原期）
擔心壓縮效果不如預期？選擇取代原檔時，SpaceSlimmer 會自動將原始未壓縮檔案備份於獨立沙盒保險箱中，提供 7~30 天自訂寬限期。在此期間隨時一鍵完整還原回相簿。

3. 即時前後對比拉簾滑桿（所見即所得）
無需猜測壓縮畫質。內建即時雙軌對比拉簾，原片與壓縮畫面毫秒級同步播放，隨時檢視人臉細節與清晰度，確認滿意後再儲存。

4. 視覺化儲存區塊樹圖（全景看透空間佔用）
透過直觀的多彩區塊圖形，一目了然看清究竟是哪些大型影片、暫存快取或下載檔案佔滿了手機儲存空間。

5. 智慧指定目標大小（通訊分享絕不超標）
可快速設定 LINE、WhatsApp (16MB) 或 Gmail (50MB) 等分享限制大小，系統自動演算最佳畫質與碼率。

核心功能：
- GPU 硬體加速（H.264 與 HEVC / H.265），極速省電低發熱。
- 4K 平滑降階至 1080p，省下大量空間。
- 相片與音訊批次壓縮。
- 重複檔案比對與大檔案快速檢索。

立即下載 SpaceSlimmer，讓您的手機空間重獲新生！"""
    },
    "ja-JP": {
        "release_notes": "- 100% 完全オフライン処理：クラウドへの送信なし、完全なプライバシー保護。\n- ハードウェアアクセラレーションによる高速圧縮で最大80%の空き容量を確保。\n- 直感的なストレージツリーマップで隠れた大容量ファイルを瞬時に特定。\n- 元のファイルをいつでも復元できる安心セーフティVault機能。\n- 動作パフォーマンスの向上と細かな不具合の修正。",
        "name": "SpaceSlimmer: 動画圧縮・容量節約",
        "short": "動画や写真を最大80%圧縮。容量ツリーマップと安全な復元金庫。",
        "full": """スマホのストレージ不足でお困りですか？大切な思い出の動画や写真を泣く泣く消していませんか？

SpaceSlimmer（スペーススリマー）は、画質を保ちながら動画や写真のファイルサイズを最大50%〜80%削減できる、オフライン対応の動画圧縮＆ストレージ分析アプリです。

通常のクリーナーアプリと違い、SpaceSlimmerは大切なデータを不用意に削除しません。「思い出をそのまま残し、サイズだけを劇的に減らす」安心設計です。

選ばれる理由：

1. 100% 完全オフライン・安心のプライバシー
クラウドへのアップロードは一切行いません。すべての動画・写真の圧縮は端末内のGPUハードウェアアクセラレーションで高速にローカル処理されます。アカウント登録も不要です。

2. 元ファイル保護金庫（7〜30日間の安心復元）
圧縮後に「やっぱり元のファイルに戻したい」と思っても安心です。元の高画質ファイルは保護金庫に自動隔離バックアップされ、設定した7〜30日間以内ならいつでもワンタップで完全に復元できます。

3. リアルタイム前後比較スライダー
圧縮前後の動画を同じ画面上で左右に並べて同時に再生・比較できます。細部の画質やシャープさを自分の目で確認してから保存できるので安心です。

4. 視覚的ストレージツリーマップ
どのフォルダや動画がスマホの容量を圧迫しているか、見やすいカラーブロック図で一目で把握できます。

5. 送信用ターゲットサイズ指定
LINEやメール、Discordなどの添付制限に合わせて、目標のMBサイズを指定して圧縮できます。

主な機能：
- ハードウェアGPU対応（H.264 / HEVC・H.265）でバッテリー消費を抑え高速変換
- 4K動画を鮮明な1080pにダウンスケールして大幅軽量化
- 写真（JPEG, PNG, WebP）や音声の一括圧縮
- 重複ファイル検出＆大容量ファイル一覧

SpaceSlimmerでスマホの空き容量を今すぐ安全に増やしましょう！"""
    },
    "ko-KR": {
        "release_notes": "- 100% 온디바이스 로컬 압축: 클라우드 업로드 없는 완벽한 개인정보 보호.\n- 하드웨어 가속 비디오 압축으로 최대 80%의 저장 공간 절약.\n- 대용량 파일을 한눈에 찾아내는 시각적 스토리지 트리맵.\n- 원본 파일을 언제든 1탭으로 복원할 수 있는 세이프 볼트.\n- 성능 개선 및 UI 버그 수정.",
        "name": "SpaceSlimmer: 동영상 용량 줄이기",
        "short": "동영상과 사진을 최대 80% 압축. 시각적 용량 분석 트리맵 및 안전 금고 복원.",
        "full": """스마트폰 저장 공간이 부족하신가요? 용량을 확보하기 위해 소중한 추억의 영상과 4K 동영상을 억지로 삭제하고 계신가요?

SpaceSlimmer는 눈에 띄는 화질 저하 없이 동영상과 사진 용량을 최대 50%~80%까지 줄여주는 온디바이스 미디어 압축 및 스마트 저장공간 분석 앱입니다.

무조건 파일을 삭제하는 일반 정리 앱과 달리, SpaceSlimmer의 약속은 간단합니다. 소중한 추억은 그대로 간직하고, 용량만 대폭 줄이며, 언제든 원본으로 되돌릴 수 있습니다.

SPACESLIMMER의 특별함:

1. 100% 오프라인 & 철저한 개인정보 보호
소중한 개인 영상과 사진은 절대 기기 외부로 전송되지 않습니다. 모든 인코딩은 스마트폰의 하드웨어 가속을 통해 기기 내부에서 로컬로 처리됩니다. 회원가입이나 계정 없이 완전히 안전합니다.

2. 원본 보관 금고 (7~30일 안심 복원)
원본 교체 시 원본 파일이 격리된 안전 금고에 7일~30일 동안 보관됩니다. 화질이 마음에 들지 않으면 언제든 탭 한 번으로 원본을 그대로 복구할 수 있습니다.

3. 실시간 분할 화면 비교 슬라이더
압축 후 화질을 미리 확인하세요. 압축 전과 후의 영상을 나란히 놓고 실시간 동기화 재생하며 디테일을 직접 확인한 뒤 저장할 수 있습니다.

4. 시각적 저장소 트리맵
스마트폰의 어떤 폴더와 거대 동영상이 용량을 차지하고 있는지 직관적인 컬러 블록 지도로 한눈에 파악하세요.

5. 메신저 공유 맞춤 목표 용량
카카오톡, 디스코드, 이메일 등의 전송 용량 제한(16MB, 25MB, 50MB 등)에 맞춰 자동으로 최적 비트레이트를 계산합니다.

주요 기능:
- GPU 하드웨어 가속(H.264, HEVC/H.265)으로 발열과 배터리 소모 최소화
- 4K 동영상을 선명한 1080p로 다운스케일링
- 사진 및 오디오 대량 일괄 압축
- 중복 파일 및 대용량 파일 정리기

지금 SpaceSlimmer를 다운로드하고 스마트폰 용량을 안전하게 확보하세요!"""
    },
    "de-DE": {
        "release_notes": "- 100% lokale Komprimierung auf dem Gerät: Kein Cloud-Upload, voller Datenschutz.\n- GPU-Hardware-Beschleunigung spart bis zu 80% Speicherplatz.\n- Visuelle Treemap zur schnellen Erkennung großer Dateien und Cache-Ordner.\n- Sicherer Wiederherstellungs-Tresor zum Zurücksetzen von Originalen.\n- Verbesserte Stabilität und Fehlerbehebungen.",
        "name": "SpaceSlimmer: Video Kompressor",
        "short": "Videos & Fotos bis zu 80% verkleinern. Visuelle Speicherkarte & sicherer Tresor.",
        "full": """Geht Ihnen der Speicherplatz auf dem Smartphone aus? Müssen Sie kostbare Familienvideos und 4K-Aufnahmen löschen, um Platz zu schaffen?

SpaceSlimmer ist Ihr intelligenter On-Device Video-Komprimierer und Speicher-Analysator, der schwere Videos und Fotos um 50% bis 80% verkleinert – ohne sichtbaren Qualitätsverlust und ohne Ihre Originaldateien zu gefährden.

WARUM SPACESLIMMER?

1. 100% Offline & Privat (Kein Cloud-Upload)
Ihre privaten Videos und Fotos verlassen niemals Ihr Gerät. Die gesamte Verarbeitung erfolgt lokal auf Ihrem Prozessor über Hardware-Beschleunigung. Keine Registrierung, kein Tracking, voller Datenschutz.

2. Sicherer Original-Tresor (Wiederherstellung in 7 bis 30 Tagen)
Haben Sie Sorge vor Qualitätsverlust? Beim Ersetzen von Dateien sichert SpaceSlimmer die Originale in einem geschützten Tresor mit 7 bis 30 Tagen Widerrufsfrist. Stellen Sie Ihre unkomprimierten Originale jederzeit mit einem Fingertipp wieder her.

3. Echtzeit-Vergleichsschieberegler
Überzeugen Sie sich selbst: Vergleichen Sie Vorher und Nachher im geteilten Bildschirm. Beide Videos laufen synchron, sodass Sie Details und Schärfe vor dem Speichern genau prüfen können.

4. Visuelle Speicherkarte (Treemap)
Schluss mit dem Rätselraten: Farbige, interaktive Kacheln zeigen Ihnen auf einen Blick, welche großen Videos oder Cache-Ordner Ihren Speicher belegen.

5. Zielgröße für WhatsApp & E-Mail
Wählen Sie einfach Vorgaben für WhatsApp (16MB), Discord (25MB) oder Gmail (50MB). SpaceSlimmer berechnet automatisch die optimale Bitrate.

HAUPTFUNKTIONEN:
- GPU-Hardware-Beschleunigung (H.264 und HEVC/H.265): Schnell und akkuschonend.
- Herunterskalieren von 4K auf gestochen scharfes 1080p.
- Stapelkomprimierung für Fotos (JPEG, PNG, WebP) und Audio.
- Duplikate-Finder und Große-Dateien-Finder.

Holen Sie sich jetzt wertvollen Speicherplatz zurück mit SpaceSlimmer!"""
    },
    "fr-FR": {
        "release_notes": "- Compression 100% locale sur l'appareil : Aucun envoi sur le cloud, confidentialité totale.\n- Accélération matérielle : économisez jusqu'à 80% d'espace de stockage.\n- Carte visuelle de stockage (Treemap) pour repérer les gros fichiers cachés.\n- Coffre-fort sécurisé (Safe Vault) pour restaurer vos originaux à tout moment.\n- Améliorations des performances et corrections d'anomalies.",
        "name": "SpaceSlimmer: Compresser Vidéo",
        "short": "Compressez vidéos et photos jusqu'à 80%. Treemap visuelle et coffre sécurisé.",
        "full": """Plus d'espace sur votre smartphone ? Fatigué de devoir supprimer vos vidéos 4K et souvenirs de famille pour libérer de la mémoire ?

Découvrez SpaceSlimmer : le compresseur vidéo intelligent et analyseur de stockage conçu pour réduire le poids de vos vidéos et photos de 50% à 80% sans perte visuelle de qualité et sans jamais perdre vos fichiers originaux.

POURQUOI CHOISIR SPACESLIMMER ?

1. 100% Hors-ligne et Privé (Aucun envoi dans le cloud)
Vos vidéos et photos privées ne quittent jamais votre téléphone. Tout l'encodage est exécuté localement grâce à l'accélération matérielle. Aucun compte requis, zéro suivi, confidentialité absolue.

2. Coffre-fort Sécurisé des Originaux (Restauration en 7 à 30 jours)
Peur d'altérer la qualité ? En remplaçant vos fichiers, SpaceSlimmer isole vos originaux dans un Coffre sécurisé pendant 7 à 30 jours configurables. Restaurez vos fichiers d'origine intacts en un seul geste.

3. Curseur de Comparaison en Temps Réel
Ne devinez plus le résultat : comparez l'avant et l'après sur écran partagé interactif. Les deux vidéos défilent en synchronisation parfaite pour inspecter la netteté avant d'enregistrer.

4. Arborescence Visuelle du Stockage (Treemap)
Visualisez immédiatement quels dossiers et grosses vidéos saturent votre mémoire grâce à une carte en blocs colorés.

5. Taille Cible pour WhatsApp et Réseaux
Définissez la taille limite exacte (WhatsApp 16 Mo, Gmail 50 Mo, Discord 25 Mo) pour envoyer vos vidéos sans blocage.

FONCTIONNALITÉS CLÉS :
- Accélération GPU matérielle (H.264 et HEVC / H.265) rapide et économe en batterie.
- Réduction fluide de la 4K en 1080p haute définition.
- Compression par lots de photos (JPEG, PNG, WebP) et audio (WAV/FLAC vers AAC/Opus).
- Détecteur de doublons et explorateur de gros fichiers.

Téléchargez SpaceSlimmer dès aujourd'hui et libérez de l'espace facilement !"""
    },
    "id": {
        "release_notes": "- Kompresi 100% offline di perangkat: Tanpa unggah ke cloud, privasi total terjaga.\n- Akselerasi perangkat keras menghemat ruang penyimpanan hingga 80%.\n- Peta visual penyimpanan (Treemap) untuk menemukan file besar tersembunyi.\n- Safe Vault untuk memulihkan file asli kapan saja dengan 1 ketukan.\n- Peningkatan performa dan perbaikan bug antarmuka.",
        "name": "SpaceSlimmer: Kompres Video",
        "short": "Kompres video & foto hingga 80%. Peta visual penyimpanan & brankas pemulihan.",
        "full": """Memori ponsel Anda hampir habis? Bosan harus menghapus video 4K dan kenangan keluarga hanya untuk mengosongkan ruang?

Gunakan SpaceSlimmer — kompresor video cerdas dan penganalisis penyimpanan visual yang mengecilkan ukuran video dan foto hingga 50%–80% tanpa penurunan kualitas yang terlihat, dan tanpa pernah kehilangan file asli Anda!

MENGAPA MEMILIH SPACESLIMMER?

1. 100% Offline & Privasi Terjaga (Tanpa Unggah ke Cloud)
Video dan foto pribadi Anda tidak pernah keluar dari ponsel. Semua pemrosesan dijalankan secara lokal menggunakan akselerasi perangkat keras. Tanpa perlu akun, tanpa pelacakan, privasi total.

2. Brankas Aman untuk File Asli (Pemulihan 7 hingga 30 Hari)
Takut kualitas berkurang? Saat mengganti file, SpaceSlimmer mencadangkan file asli ke dalam Brankas terisolasi selama 7 hingga 30 hari. Pulihkan file asli kapan saja hanya dengan satu ketukan.

3. Slider Perbandingan Waktu Nyata
Bandingkan video sebelum dan sesudah kompresi secara berdampingan dengan slider tirai interaktif. Kedua video diputar secara sinkron sehingga Anda dapat memeriksa ketajaman sebelum menyimpan.

4. Peta Blok Penyimpanan Visual
Lihat dengan jelas folder atau video raksasa mana yang menghabiskan memori ponsel Anda dengan peta blok warna yang intuitif.

5. Ukuran Target untuk WhatsApp & Media Sosial
Pilih preset cepat untuk WhatsApp (16MB), Discord (25MB), atau Gmail (50MB) agar video Anda pas dan mudah dikirim.

FITUR UTAMA:
- Akselerasi GPU perangkat keras (H.264 dan HEVC/H.265) yang cepat dan hemat baterai.
- Downscale 4K ke 1080p yang jernih untuk menghemat banyak gigabyte.
- Kompresi massal foto (JPEG, PNG, WebP) dan audio.
- Pembersih file duplikat dan penjelajah file besar.

Unduh SpaceSlimmer sekarang dan bebaskan ruang memori HP Anda!"""
    },
    "ru-RU": {
        "release_notes": "- 100% локальное сжатие на устройстве: Без облачных загрузок, полная конфиденциальность.\n- Аппаратное ускорение видео: экономия до 80% памяти устройства.\n- Наглядная карта памяти (Treemap) для быстрого поиска тяжелых файлов.\n- Безопасное хранилище (Safe Vault) для отмены и восстановления оригиналов.\n- Повышение производительности и исправление ошибок интерфейса.",
        "name": "SpaceSlimmer: Сжатие видео",
        "short": "Сжатие видео и фото до 80%. Визуальная карта памяти и безопасный сейф.",
        "full": """Заканчивается память на телефоне? Надоело удалять памятные видео и 4K-ролики, чтобы освободить место?

Попробуйте SpaceSlimmer — умный локальный компрессор видео и анализатор хранилища, который уменьшает размер тяжелых видео и фото на 50%–80% без видимой потери качества и без риска потерять исходные файлы.

ПОЧЕМУ SPACESLIMMER?

1. 100% Офлайн и Конфиденциально (Без облака)
Ваши личные видео и галерея никогда не покидают устройство. Все сжатие выполняется локально с аппаратным ускорением GPU. Без регистрации и аккаунтов, полная безопасность данных.

2. Безопасный Сейф оригиналов (Восстановление за 7–30 дней)
Боитесь потерять качество? При замене оригиналов SpaceSlimmer сохраняет исходные файлы в изолированном Сейфе на настраиваемый срок от 7 до 30 дней. Восстановите оригинал в один клик в любой момент.

3. Сравнение до и после в реальном времени
Сравнивайте исходное и сжатое видео на разделенном экране с помощью интерактивного слайдера. Видео воспроизводятся синхронно, позволяя оценить четкость деталей перед сохранением.

4. Визуальная карта памяти (Treemap)
Интуитивная цветная блочная карта наглядно покажет, какие папки и гигантские видеофайлы занимают больше всего места.

5. Заданный размер для WhatsApp и Telegram
Легко задайте точный целевой размер (WhatsApp 16 МБ, Gmail 50 МБ, Discord 25 МБ) для отправки видео без ограничений.

ОСНОВНЫЕ ВОЗМОЖНОСТИ:
- Аппаратное ускорение GPU (H.264 и HEVC/H.265): быстро и без перегрева.
- Уменьшение разрешения из 4K в четкое 1080p с колоссальной экономией памяти.
- Пакетное сжатие фотографий (JPEG, PNG, WebP) и аудиофайлов.
- Поиск дубликатов и проводник больших файлов.

Освободите память смартфона прямо сейчас с помощью SpaceSlimmer!"""
    }
}

def main():
    os.makedirs('store/locales', exist_ok=True)
    for loc, data in LOCALES_DATA.items():
        # Validate limits
        name_len = len(data['name'])
        short_len = len(data['short'])
        full_len = len(data['full'])
        print(f"[{loc}] name={name_len}/30, short={short_len}/80, full={full_len}/4000")
        assert name_len <= 30, f"Title too long in {loc}: {name_len}"
        assert short_len <= 80, f"Short description too long in {loc}: {short_len}"
        assert full_len <= 4000, f"Full description too long in {loc}: {full_len}"

        loc_file = f"store/locales/{loc}.json"
        with open(loc_file, 'w', encoding='utf-8') as f:
            json.dump({
                "locale": loc,
                "name": data["name"],
                "short": data["short"],
                "full": data["full"],
                "release_notes": data.get("release_notes", "")
            }, f, ensure_ascii=False, indent=2)
        print(f"Saved: {loc_file}")

    # Output clean listing_en.txt
    en = LOCALES_DATA["en-US"]
    with open("store/listing_en.txt", "w", encoding="utf-8") as f:
        f.write(f"{en['name']}\n\n{en['short']}\n\n{en['full']}\n")
    print("Saved: store/listing_en.txt")

    # Output clean listing_zh.txt
    zh = LOCALES_DATA["zh-CN"]
    with open("store/listing_zh.txt", "w", encoding="utf-8") as f:
        f.write(f"{zh['name']}\n\n{zh['short']}\n\n{zh['full']}\n")
    print("Saved: store/listing_zh.txt")

    # Output clean update_payload.json
    payload = {
        "name": en["name"],
        "short": en["short"],
        "full": en["full"],
        "tags": [
            "Video Compressor",
            "Storage Cleaner",
            "File Manager",
            "Video Players & Editors",
            "Tools"
        ],
        "locales": LOCALES_DATA
    }
    with open("store/update_payload.json", "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("Saved: store/update_payload.json")

if __name__ == '__main__':
    main()
