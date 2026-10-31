#!/bin/bash
# Tests if the EndLock Worker online is the NEW version (with voice chat + live invites).
# Double-click on the Mac. It only reads: nothing is changed.
HOST="${1:-host.endlock.net}"
echo "== Teste do servidor de voz: $HOST =="
check() {
  local path="$1" label="$2"
  local code body
  code=$(curl -s -m 15 -o /tmp/kubo-test-body -w '%{http_code}' "https://$HOST/$path")
  body=$(head -c 40 /tmp/kubo-test-body 2>/dev/null)
  # new Worker: answers 426 ("Kubo voice" / "Kubo live") to a normal request; old Worker: a web page (200/302/404)
  if [ "$code" = "426" ]; then
    echo "  OK   $label (HTTP 426 = pronto para WebSocket)"
    return 0
  fi
  echo "  FALHA $label (HTTP $code: $body)"
  return 1
}
ok=0
check "voice?room=teste-voz-123" "voz  /voice" || ok=1
check "live?name=teste_voz" "convites /live" || ok=1
echo ""
if [ $ok -eq 0 ]; then
  echo "O servidor está atualizado. Se o jogo ainda falha, reinicia o Minecraft."
else
  echo "O servidor está DESATUALIZADO (é a versão antiga, sem voz). Dá dois cliques em Publicar.command e depois corre este teste outra vez."
fi
echo ""
read -n 1 -s -r -p "Carrega numa tecla para fechar..."
