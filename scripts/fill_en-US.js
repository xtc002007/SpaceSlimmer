(() => {
  const nameInput = Array.from(document.querySelectorAll('input[type="text"]'))[0];
  const shortInput = Array.from(document.querySelectorAll('input[type="text"]'))[1];
  const fullTextarea = document.querySelector('textarea');
  
  function setVal(el, val) {
    el.value = val;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
  }
  
  setVal(nameInput, "SpaceSlimmer: Video Compressor");
  setVal(shortInput, "Compress videos & photos up to 80%. Visual storage treemap & safe vault undo.");
  setVal(fullTextarea, "Running out of phone storage? Tired of being forced to delete precious family memories and 4K videos just to make space?\n\nMeet SpaceSlimmer — the intelligent on-device video compressor, photo optimizer, and visual storage analyzer designed to shrink bulky videos and photos by up to 50%–80% without visible loss in quality, and without ever losing your original files.\n\nUnlike ordinary cleaner apps that delete your files without remorse, SpaceSlimmer’s promise is simple: Keep your memories, drop the file size, and always have the option to undo.\n\nWHY CHOOSE SPACESLIMMER?\n\n1. 100% Offline & Private (Zero Cloud Uploads)\nYour private videos and camera roll never leave your phone. All video, photo, and audio encoding is performed entirely on your device using hardware acceleration and high-speed native processing. No account required, zero tracking, total privacy.\n\n2. Original Safe Vault (Never Worry About Damaging Originals)\nAfraid of losing quality? When you choose to replace original files, SpaceSlimmer safely backs them up into an isolated sandbox Vault with a customizable 7 to 30-day grace period. If you ever change your mind, restore your original uncompressed files with a single tap.\n\n3. Real-Time Split-Screen Comparison Slider\nNever guess what your compressed video looks like. Use the interactive before-and-after curtain slider to compare original and compressed footage side-by-side in real-time. Both sides scrub and play in millisecond sync so you can verify sharpness and facial details before saving.\n\n4. Visual Storage Treemap (See What’s Taking Up Space)\nStop guessing where your gigabytes disappeared. SpaceSlimmer displays your storage as an intuitive, colorful visual block map. Tap into any block to instantly locate hidden gigabyte-hogging videos, cache folders, and oversized downloads.\n\n5. Smart Target Size for Easy Sharing\nNeed to send a video through messaging apps? Choose quick presets for WhatsApp (16MB), Discord (25MB), or Gmail (50MB), or enter an exact target megabyte limit. SpaceSlimmer automatically calculates the optimal bitrate to guarantee your video fits.\n\n6. Smart Skip Guard (Zero Quality Loss)\nSpaceSlimmer’s intelligent engine inspects existing codecs and bitrates. If a file is already optimized or further compression would degrade quality without meaningful savings, it automatically skips it.\n\nPOWERFUL FEATURES:\n\n* High-Speed Video Compressor\n- Hardware GPU Acceleration: Fast H.264 and HEVC (H.265) encoding that stays cool and saves battery.\n- Flexible Downscaling: Seamlessly downscale bulky 4K 60fps clips into crystal-clear 1080p.\n- Metadata Preservation: Retains video orientation, shooting timestamp, and audio sync.\n\n* Batch Photo & Audio Slimmer\n- Batch compress camera photos (JPEG, PNG, WebP) in bulk.\n- Transcode large voice notes and lossless WAV/FLAC audio into ultra-compact AAC/Opus, saving up to 80% space.\n\n* Deep Storage Cleanup Tools\n- Duplicate File Finder: Fast multi-point comparison identifies exact duplicate photos and videos.\n- Big File Explorer: Sort and filter all files across your device by size, date, or category.\n- Storage Growth Tracker: Compare storage snapshots over time to catch apps that grow out of control.\n\nPERFECT FOR:\n- Parents capturing hours of family and kid videos.\n- Travelers and creators with full camera rolls after vacations.\n- Anyone sharing video clips on WhatsApp, Discord, or Instagram.\n- Anyone with a 64GB, 128GB, or 256GB device looking to reclaim gigabytes!\n\nFree up your phone storage today with SpaceSlimmer — The smartest, safest video compressor!");
  
  const saveBtn = Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').trim() === '保存' && !b.disabled);
  if (saveBtn) {
    saveBtn.click();
  }
  
  return {
    locale: "en-US",
    nameLen: nameInput.value.length,
    shortLen: shortInput.value.length,
    fullLen: fullTextarea.value.length,
    clickedSave: !!saveBtn
  };
})()