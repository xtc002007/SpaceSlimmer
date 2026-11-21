# -*- coding: utf-8 -*-
import json

with open("scripts/all_locales_map.json", "r", encoding="utf-8") as f:
    locales_map = json.load(f)

# Create a self-contained JS script
js_map = json.dumps(locales_map, ensure_ascii=False)

script = f"""(() => {{
  const LOCALES = {js_map};
  
  const btn = document.querySelector('console-block-1-column button');
  const btnText = btn ? btn.innerText : '';
  
  let targetCode = null;
  for (const code of Object.keys(LOCALES)) {{
    if (btnText.includes(code)) {{
      targetCode = code;
      break;
    }}
  }}
  
  if (!targetCode) {{
    return {{ error: 'No matching locale for ' + btnText }};
  }}
  
  const data = LOCALES[targetCode];
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
  
  // Click save draft
  const saveDraftBtn = Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').trim() === '保存为草稿' && !b.disabled);
  if (saveDraftBtn) {{
    saveDraftBtn.click();
  }}
  
  return {{
    locale: targetCode,
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  }};
}})()"""

with open("scripts/run_fill_current.js", "w", encoding="utf-8") as f:
    f.write(script)

print("Generated scripts/run_fill_current.js")
