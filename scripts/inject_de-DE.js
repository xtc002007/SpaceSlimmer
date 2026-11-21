(() => {
  const data = {"name": "SpaceSlimmer: Video Kompressor", "short": "Videos & Fotos bis zu 80% verkleinern. Visuelle Speicherkarte & sicherer Tresor.", "full": "Geht Ihnen der Speicherplatz auf dem Smartphone aus? Müssen Sie kostbare Familienvideos und 4K-Aufnahmen löschen, um Platz zu schaffen?\n\nSpaceSlimmer ist Ihr intelligenter On-Device Video-Komprimierer und Speicher-Analysator, der schwere Videos und Fotos um 50% bis 80% verkleinert – ohne sichtbaren Qualitätsverlust und ohne Ihre Originaldateien zu gefährden.\n\nWARUM SPACESLIMMER?\n\n1. 100% Offline & Privat (Kein Cloud-Upload)\nIhre privaten Videos und Fotos verlassen niemals Ihr Gerät. Die gesamte Verarbeitung erfolgt lokal auf Ihrem Prozessor über Hardware-Beschleunigung. Keine Registrierung, kein Tracking, voller Datenschutz.\n\n2. Sicherer Original-Tresor (Wiederherstellung in 7 bis 30 Tagen)\nHaben Sie Sorge vor Qualitätsverlust? Beim Ersetzen von Dateien sichert SpaceSlimmer die Originale in einem geschützten Tresor mit 7 bis 30 Tagen Widerrufsfrist. Stellen Sie Ihre unkomprimierten Originale jederzeit mit einem Fingertipp wieder her.\n\n3. Echtzeit-Vergleichsschieberegler\nÜberzeugen Sie sich selbst: Vergleichen Sie Vorher und Nachher im geteilten Bildschirm. Beide Videos laufen synchron, sodass Sie Details und Schärfe vor dem Speichern genau prüfen können.\n\n4. Visuelle Speicherkarte (Treemap)\nSchluss mit dem Rätselraten: Farbige, interaktive Kacheln zeigen Ihnen auf einen Blick, welche großen Videos oder Cache-Ordner Ihren Speicher belegen.\n\n5. Zielgröße für WhatsApp & E-Mail\nWählen Sie einfach Vorgaben für WhatsApp (16MB), Discord (25MB) oder Gmail (50MB). SpaceSlimmer berechnet automatisch die optimale Bitrate.\n\nHAUPTFUNKTIONEN:\n- GPU-Hardware-Beschleunigung (H.264 und HEVC/H.265): Schnell und akkuschonend.\n- Herunterskalieren von 4K auf gestochen scharfes 1080p.\n- Stapelkomprimierung für Fotos (JPEG, PNG, WebP) und Audio.\n- Duplikate-Finder und Große-Dateien-Finder.\n\nHolen Sie sich jetzt wertvollen Speicherplatz zurück mit SpaceSlimmer!"};
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
    locale: "de-DE",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()