(() => {
  const data = {"name": "SpaceSlimmer: Compactar Vídeo", "short": "Comprima vídeos e fotos em até 80%. Mapa de memória e cofre de restauração.", "full": "Sem espaço no celular? Cansado de ter que apagar fotos e vídeos 4K da família só para liberar armazenamento?\n\nConheça o SpaceSlimmer: o compressor inteligente de vídeos e fotos e analisador visual de memória que reduz o tamanho dos seus arquivos de 50% a 80% sem perda visual de qualidade e sem jamais perder seus originais.\n\nAo contrário de limpadores comuns que excluem tudo sem volta, a proposta do SpaceSlimmer é simples: mantenha suas memórias, diminua o tamanho e tenha sempre a opção de restaurar.\n\nPOR QUE ESCOLHER O SPACESLIMMER?\n\n1. 100% Local e Privado (Zero Upload na Nuvem)\nSeus vídeos e fotos particulares nunca saem do celular. Toda a compressão roda diretamente no processador do seu aparelho com aceleração por hardware. Sem conta, sem cadastro, privacidade absoluta.\n\n2. Cofre Seguro de Originais (Recuperação em 7 a 30 dias)\nMedo de perder a qualidade original? Ao substituir arquivos, o SpaceSlimmer isola os originais em um Cofre seguro com prazo configurável de 7 a 30 dias. Restaure o arquivo original intacto com um simples toque.\n\n3. Comparador em Tempo Real com Cortina Deslizante\nVeja exatamente o resultado antes de salvar. Compare lado a lado o vídeo original e o comprimido com sincronia de milissegundos para conferir detalhes e nitidez.\n\n4. Mapa Visual de Armazenamento\nVeja onde foram parar seus gigabytes com um mapa visual em blocos intuitivos. Localize na hora vídeos pesados e pastas ocultas.\n\n5. Tamanho Alvo para Envio no WhatsApp\nEscolha predefinições para WhatsApp (16MB), Discord (25MB) ou Gmail (50MB) para garantir que seus vídeos caibam no limite de envio.\n\n6. Proteção Inteligente contra Compressão Desnecessária\nDetecta arquivos que já estão comprimidos para não recompactar sem ganho real de espaço.\n\nPRINCIPAIS RECURSOS:\n- Aceleração por GPU (H.264 e HEVC/H.265) veloz e econômica em bateria.\n- Redimensionamento inteligente de 4K para 1080p nítido.\n- Compressão em lote de fotos (JPEG, PNG, WebP) e áudio (WAV/FLAC para AAC/Opus).\n- Localizador de arquivos duplicados e explorador de arquivos gigantes.\n\nBaixe o SpaceSlimmer agora e recupere o espaço do seu smartphone!"};
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
    locale: "pt-BR",
    nameLen: nameInput ? nameInput.value.length : 0,
    shortLen: shortInput ? shortInput.value.length : 0,
    fullLen: fullTextarea ? fullTextarea.value.length : 0,
    saved: !!saveDraftBtn
  };
})()