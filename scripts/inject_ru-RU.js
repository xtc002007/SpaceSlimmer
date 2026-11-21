(() => {
  const data = {"name": "SpaceSlimmer: Сжатие видео", "short": "Сжатие видео и фото до 80%. Визуальная карта памяти и безопасный сейф.", "full": "Заканчивается память на телефоне? Надоело удалять памятные видео и 4K-ролики, чтобы освободить место?\n\nПопробуйте SpaceSlimmer — умный локальный компрессор видео и анализатор хранилища, который уменьшает размер тяжелых видео и фото на 50%–80% без видимой потери качества и без риска потерять исходные файлы.\n\nПОЧЕМУ SPACESLIMMER?\n\n1. 100% Офлайн и Конфиденциально (Без облака)\nВаши личные видео и галерея никогда не покидают устройство. Все сжатие выполняется локально с аппаратным ускорением GPU. Без регистрации и аккаунтов, полная безопасность данных.\n\n2. Безопасный Сейф оригиналов (Восстановление за 7–30 дней)\nБоитесь потерять качество? При замене оригиналов SpaceSlimmer сохраняет исходные файлы в изолированном Сейфе на настраиваемый срок от 7 до 30 дней. Восстановите оригинал в один клик в любой момент.\n\n3. Сравнение до и после в реальном времени\nСравнивайте исходное и сжатое видео на разделенном экране с помощью интерактивного слайдера. Видео воспроизводятся синхронно, позволяя оценить четкость деталей перед сохранением.\n\n4. Визуальная карта памяти (Treemap)\nИнтуитивная цветная блочная карта наглядно покажет, какие папки и гигантские видеофайлы занимают больше всего места.\n\n5. Заданный размер для WhatsApp и Telegram\nЛегко задайте точный целевой размер (WhatsApp 16 МБ, Gmail 50 МБ, Discord 25 МБ) для отправки видео без ограничений.\n\nОСНОВНЫЕ ВОЗМОЖНОСТИ:\n- Аппаратное ускорение GPU (H.264 и HEVC/H.265): быстро и без перегрева.\n- Уменьшение разрешения из 4K в четкое 1080p с колоссальной экономией памяти.\n- Пакетное сжатие фотографий (JPEG, PNG, WebP) и аудиофайлов.\n- Поиск дубликатов и проводник больших файлов.\n\nОсвободите память смартфона прямо сейчас с помощью SpaceSlimmer!"};
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
    locale: "ru-RU",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()