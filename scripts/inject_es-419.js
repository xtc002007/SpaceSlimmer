(() => {
  const data = {"name": "SpaceSlimmer: Comprimir Video", "short": "Comprime videos y fotos hasta un 80%. Mapa visual y bóveda de restauración.", "full": "¿Te estás quedando sin espacio en el teléfono? ¿Cansado de borrar fotos y videos 4K familiares solo para liberar memoria?\n\nDescubre SpaceSlimmer: el compresor de video inteligente, optimizador de fotos y analizador visual de almacenamiento diseñado para reducir el tamaño de tus videos y fotos entre un 50% y un 80% sin pérdida visual de calidad y sin perder jamás tus archivos originales.\n\nA diferencia de los limpiadores comunes que eliminan tus archivos para siempre, la promesa de SpaceSlimmer es clara: conserva tus recuerdos, reduce el tamaño y ten siempre la opción de restaurar el original.\n\n¿POR QUÉ ELEGIR SPACESLIMMER?\n\n1. 100% Local y Privado (Cero cargas a la nube)\nTus videos y carrete jamás salen de tu teléfono. Toda la codificación se realiza localmente con aceleración de hardware. No necesitas cuenta ni registro, sin rastreo, privacidad total.\n\n2. Bóveda Segura de Originales (Restauración en 7 a 30 días)\n¿Temes que el resultado no te convenza? Al reemplazar los archivos originales, SpaceSlimmer los resguarda en una Bóveda protegida durante un período configurable de 7 a 30 días. Restaura tus archivos originales intactos con un solo toque.\n\n3. Deslizador de Comparación en Tiempo Real\nNo adivines cómo quedará el video comprimido. Compara el antes y el después con un control interactivo en pantalla dividida que reproduce ambos videos en sincronía de milisegundos para verificar cada detalle antes de guardar.\n\n4. Mapa Visual de Almacenamiento (Treemap)\nDescubre al instante qué carpetas y videos gigantes están consumiendo tu memoria con un mapa de bloques visual e interactivo.\n\n5. Tamaño Objetivo para Enviar por WhatsApp\nDefine el límite exacto en megabytes (WhatsApp 16MB, Gmail 50MB, Discord 25MB) para enviar videos sin que la aplicación de mensajería los bloquee.\n\n6. Protección contra Falsa Compresión\nSi un archivo ya está optimizado o comprimirlo lo empeoraría, SpaceSlimmer lo detecta y lo omite automáticamente.\n\nCARACTERÍSTICAS PRINCIPALES:\n- Aceleración por GPU (H.264 y HEVC / H.265): rápido y sin sobrecalentar el móvil.\n- Conversión de 4K a 1080p nítido para ahorrar gigabytes masivos.\n- Compresión por lotes de fotos (JPEG, PNG, WebP) y audios (WAV/FLAC a AAC/Opus).\n- Buscador de archivos duplicados y explorador de archivos grandes.\n\n¡Descarga SpaceSlimmer hoy y recupera espacio en tu teléfono de forma segura!"};
  const nameInput = Array.from(document.querySelectorAll('input[type="text"]'))[0];
  const shortInput = Array.from(document.querySelectorAll('input[type="text"]'))[1];
  const fullTextarea = document.querySelector('textarea');
  
  function setVal(el, val) {
    if (!el) return;
    el.value = val;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
  }
  
  setVal(nameInput, data.name);
  setVal(shortInput, data.short);
  setVal(fullTextarea, data.full);
  
  const saveDraftBtn = Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').trim() === '保存为草稿' && !b.disabled);
  if (saveDraftBtn) saveDraftBtn.click();
  
  return {
    locale: "es-419",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()