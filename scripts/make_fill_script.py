# -*- coding: utf-8 -*-
import json

def generate_fill_js(locale_key):
    with open(f"store/locales/{locale_key}.json", "r", encoding="utf-8") as f:
        d = json.load(f)
        
    name_json = json.dumps(d["name"], ensure_ascii=False)
    short_json = json.dumps(d["short"], ensure_ascii=False)
    full_json = json.dumps(d["full"], ensure_ascii=False)
    
    js = f"""(() => {{
  const nameInput = Array.from(document.querySelectorAll('input[type="text"]'))[0];
  const shortInput = Array.from(document.querySelectorAll('input[type="text"]'))[1];
  const fullTextarea = document.querySelector('textarea');
  
  function setVal(el, val) {{
    el.value = val;
    el.dispatchEvent(new Event('input', {{ bubbles: true }}));
    el.dispatchEvent(new Event('change', {{ bubbles: true }}));
    el.dispatchEvent(new Event('blur', {{ bubbles: true }}));
  }}
  
  setVal(nameInput, {name_json});
  setVal(shortInput, {short_json});
  setVal(fullTextarea, {full_json});
  
  const saveBtn = Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').trim() === '保存' && !b.disabled);
  if (saveBtn) {{
    saveBtn.click();
  }}
  
  return {{
    locale: "{locale_key}",
    nameLen: nameInput.value.length,
    shortLen: shortInput.value.length,
    fullLen: fullTextarea.value.length,
    clickedSave: !!saveBtn
  }};
}})()"""
    return js

if __name__ == "__main__":
    import sys
    loc = sys.argv[1] if len(sys.argv) > 1 else "en-US"
    js = generate_fill_js(loc)
    with open(f"scripts/fill_{loc}.js", "w", encoding="utf-8") as f:
        f.write(js)
    print(f"Generated scripts/fill_{loc}.js")
