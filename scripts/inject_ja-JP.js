(() => {
  const data = {"name": "SpaceSlimmer: 動画圧縮・容量節約", "short": "動画や写真を最大80%圧縮。容量ツリーマップと安全な復元金庫。", "full": "スマホのストレージ不足でお困りですか？大切な思い出の動画や写真を泣く泣く消していませんか？\n\nSpaceSlimmer（スペーススリマー）は、画質を保ちながら動画や写真のファイルサイズを最大50%〜80%削減できる、オフライン対応の動画圧縮＆ストレージ分析アプリです。\n\n通常のクリーナーアプリと違い、SpaceSlimmerは大切なデータを不用意に削除しません。「思い出をそのまま残し、サイズだけを劇的に減らす」安心設計です。\n\n選ばれる理由：\n\n1. 100% 完全オフライン・安心のプライバシー\nクラウドへのアップロードは一切行いません。すべての動画・写真の圧縮は端末内のGPUハードウェアアクセラレーションで高速にローカル処理されます。アカウント登録も不要です。\n\n2. 元ファイル保護金庫（7〜30日間の安心復元）\n圧縮後に「やっぱり元のファイルに戻したい」と思っても安心です。元の高画質ファイルは保護金庫に自動隔離バックアップされ、設定した7〜30日間以内ならいつでもワンタップで完全に復元できます。\n\n3. リアルタイム前後比較スライダー\n圧縮前後の動画を同じ画面上で左右に並べて同時に再生・比較できます。細部の画質やシャープさを自分の目で確認してから保存できるので安心です。\n\n4. 視覚的ストレージツリーマップ\nどのフォルダや動画がスマホの容量を圧迫しているか、見やすいカラーブロック図で一目で把握できます。\n\n5. 送信用ターゲットサイズ指定\nLINEやメール、Discordなどの添付制限に合わせて、目標のMBサイズを指定して圧縮できます。\n\n主な機能：\n- ハードウェアGPU対応（H.264 / HEVC・H.265）でバッテリー消費を抑え高速変換\n- 4K動画を鮮明な1080pにダウンスケールして大幅軽量化\n- 写真（JPEG, PNG, WebP）や音声の一括圧縮\n- 重複ファイル検出＆大容量ファイル一覧\n\nSpaceSlimmerでスマホの空き容量を今すぐ安全に増やしましょう！"};
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
    locale: "ja-JP",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()