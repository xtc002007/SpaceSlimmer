(() => {
  const data = {"name": "SpaceSlimmer: Compresser Vidéo", "short": "Compressez vidéos et photos jusqu'à 80%. Treemap visuelle et coffre sécurisé.", "full": "Plus d'espace sur votre smartphone ? Fatigué de devoir supprimer vos vidéos 4K et souvenirs de famille pour libérer de la mémoire ?\n\nDécouvrez SpaceSlimmer : le compresseur vidéo intelligent et analyseur de stockage conçu pour réduire le poids de vos vidéos et photos de 50% à 80% sans perte visuelle de qualité et sans jamais perdre vos fichiers originaux.\n\nPOURQUOI CHOISIR SPACESLIMMER ?\n\n1. 100% Hors-ligne et Privé (Aucun envoi dans le cloud)\nVos vidéos et photos privées ne quittent jamais votre téléphone. Tout l'encodage est exécuté localement grâce à l'accélération matérielle. Aucun compte requis, zéro suivi, confidentialité absolue.\n\n2. Coffre-fort Sécurisé des Originaux (Restauration en 7 à 30 jours)\nPeur d'altérer la qualité ? En remplaçant vos fichiers, SpaceSlimmer isole vos originaux dans un Coffre sécurisé pendant 7 à 30 jours configurables. Restaurez vos fichiers d'origine intacts en un seul geste.\n\n3. Curseur de Comparaison en Temps Réel\nNe devinez plus le résultat : comparez l'avant et l'après sur écran partagé interactif. Les deux vidéos défilent en synchronisation parfaite pour inspecter la netteté avant d'enregistrer.\n\n4. Arborescence Visuelle du Stockage (Treemap)\nVisualisez immédiatement quels dossiers et grosses vidéos saturent votre mémoire grâce à une carte en blocs colorés.\n\n5. Taille Cible pour WhatsApp et Réseaux\nDéfinissez la taille limite exacte (WhatsApp 16 Mo, Gmail 50 Mo, Discord 25 Mo) pour envoyer vos vidéos sans blocage.\n\nFONCTIONNALITÉS CLÉS :\n- Accélération GPU matérielle (H.264 et HEVC / H.265) rapide et économe en batterie.\n- Réduction fluide de la 4K en 1080p haute définition.\n- Compression par lots de photos (JPEG, PNG, WebP) et audio (WAV/FLAC vers AAC/Opus).\n- Détecteur de doublons et explorateur de gros fichiers.\n\nTéléchargez SpaceSlimmer dès aujourd'hui et libérez de l'espace facilement !"};
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
    locale: "fr-CA",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()