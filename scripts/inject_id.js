(() => {
  const data = {"name": "SpaceSlimmer: Kompres Video", "short": "Kompres video & foto hingga 80%. Peta visual penyimpanan & brankas pemulihan.", "full": "Memori ponsel Anda hampir habis? Bosan harus menghapus video 4K dan kenangan keluarga hanya untuk mengosongkan ruang?\n\nGunakan SpaceSlimmer — kompresor video cerdas dan penganalisis penyimpanan visual yang mengecilkan ukuran video dan foto hingga 50%–80% tanpa penurunan kualitas yang terlihat, dan tanpa pernah kehilangan file asli Anda!\n\nMENGAPA MEMILIH SPACESLIMMER?\n\n1. 100% Offline & Privasi Terjaga (Tanpa Unggah ke Cloud)\nVideo dan foto pribadi Anda tidak pernah keluar dari ponsel. Semua pemrosesan dijalankan secara lokal menggunakan akselerasi perangkat keras. Tanpa perlu akun, tanpa pelacakan, privasi total.\n\n2. Brankas Aman untuk File Asli (Pemulihan 7 hingga 30 Hari)\nTakut kualitas berkurang? Saat mengganti file, SpaceSlimmer mencadangkan file asli ke dalam Brankas terisolasi selama 7 hingga 30 hari. Pulihkan file asli kapan saja hanya dengan satu ketukan.\n\n3. Slider Perbandingan Waktu Nyata\nBandingkan video sebelum dan sesudah kompresi secara berdampingan dengan slider tirai interaktif. Kedua video diputar secara sinkron sehingga Anda dapat memeriksa ketajaman sebelum menyimpan.\n\n4. Peta Blok Penyimpanan Visual\nLihat dengan jelas folder atau video raksasa mana yang menghabiskan memori ponsel Anda dengan peta blok warna yang intuitif.\n\n5. Ukuran Target untuk WhatsApp & Media Sosial\nPilih preset cepat untuk WhatsApp (16MB), Discord (25MB), atau Gmail (50MB) agar video Anda pas dan mudah dikirim.\n\nFITUR UTAMA:\n- Akselerasi GPU perangkat keras (H.264 dan HEVC/H.265) yang cepat dan hemat baterai.\n- Downscale 4K ke 1080p yang jernih untuk menghemat banyak gigabyte.\n- Kompresi massal foto (JPEG, PNG, WebP) dan audio.\n- Pembersih file duplikat dan penjelajah file besar.\n\nUnduh SpaceSlimmer sekarang dan bebaskan ruang memori HP Anda!"};
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
    locale: "id",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()