# -*- coding: utf-8 -*-
import json

with open("scripts/all_locales_map.json", "r", encoding="utf-8") as f:
    locales = json.load(f)

# Split into individual locales for easy injection
for loc, data in locales.items():
    with open(f"scripts/inject_{loc}.js", "w", encoding="utf-8") as f:
        f.write(f"""(() => {{
  const data = {json.dumps(data, ensure_ascii=False)};
  const nameInput = Array.from(document.querySelectorAll('input[type="text"]'))[0];
  const shortInput = Array.from(document.querySelectorAll('input[type="text"]'))[1];
  const fullTextarea = document.querySelector('textarea');
  
  function setVal(el, val) {{
    if (!el) return;
    el.value = val;
    el.dispatchEvent(new Event('input', {{ bubbles: true }}));
    el.dispatchEvent(new Event('change', {{ bubbles: true }}));
    el.dispatchEvent(new Event('blur', {{ bubbles: true }}));
  }}
  
  setVal(nameInput, data.name);
  setVal(shortInput, data.short);
  setVal(fullTextarea, data.full);
  
  const saveDraftBtn = Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').trim() === '保存为草稿' && !b.disabled);
  if (saveDraftBtn) saveDraftBtn.click();
  
  return {{
    locale: "{loc}",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  }};
}})()""")

print(f"Generated {len(locales)} single-locale injectors!")
