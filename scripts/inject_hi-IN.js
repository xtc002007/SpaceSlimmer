(() => {
  const data = {"name": "SpaceSlimmer: वीडियो कंप्रेसर", "short": "वीडियो और फोटो को 80% तक कंप्रेस करें, विजुअल स्टोरेज मैप और सुरक्षित वॉल्ट।", "full": "क्या आपके फोन का स्टोरेज भर गया है? क्या आपको स्पेस बनाने के लिए अपनी पसंदीदा पारिवारिक यादें और 4K वीडियो हटाने पड़ रहे हैं?\n\nमिलिए SpaceSlimmer से — एक स्मार्ट ऑन-डिवाइस वीडियो कंप्रेसर और विजुअल स्टोरेज क्लीनर जो बिना किसी दृश्य गुणवत्ता नुकसान के और बिना आपके मूल फोटो-वीडियो खोए, मीडिया साइज को 50% से 80% तक छोटा करता है।\n\nSpaceSlimmer की खासियतें:\n\n1. 100% ऑफलाइन और पूरी तरह सुरक्षित\nआपके वीडियो और फोटो कभी भी फोन से बाहर नहीं जाते। पूरा कंप्रेशन आपके फोन के हार्डवेयर चिप और नेटिव इंजन पर होता है। कोई अकाउंट नहीं, कोई इंटरनेट अपलोड नहीं, 100% गोपनीयता।\n\n2. ओरिजिनल सेफ वॉल्ट (7 से 30 दिन का बैकअप)\nक्वालिटी खराब होने का कोई डर नहीं! जब आप ओरिजिनल वीडियो को बदलते हैं, तो SpaceSlimmer ओरिजिनल फाइल को 7 से 30 दिनों के लिए एक सुरक्षित वॉल्ट में सुरक्षित रखता है। एक टैप में जब चाहें ओरिजिनल फाइल वापस पाएं।\n\n3. रीयल-टाइम साइड-बाय-साइड तुलना स्लाइडर\nकंप्रेशन के बाद वीडियो कैसा दिखेगा? दोनों वीडियो को साथ चलाकर स्लाइडर से देखें। चेहरे की डिटेल और शार्पनेस चेक करें, संतुष्ट होने पर ही सेव करें।\n\n4. विजुअल स्टोरेज मैप (कहाँ भरा है स्टोरेज?)\nआसान रंगीन ब्लॉक्स में देखें कि फोन का स्पेस किस फोल्डर या बड़े वीडियो ने घेरा हुआ है।\n\n5. WhatsApp और सोशल मीडिया के लिए टारगेट साइज\nWhatsApp (16MB), Gmail (50MB) के लिए एक क्लिक में वीडियो का साइज सेट करें।\n\nमुख्य फीचर्स:\n- जीपीयू हार्डवेयर एक्सेलेरेशन (H.264 और H.265 / HEVC)\n- 4K से 1080p में आसान डाउनस्केलिंग\n- फोटो और ऑडियो का बल्क कंप्रेशन\n- डुप्लिकेट फोटो और वीडियो क्लीनर\n\nआज ही SpaceSlimmer डाउनलोड करें और अपने फोन का स्टोरेज फ्री करें!"};
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
    locale: "hi-IN",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()