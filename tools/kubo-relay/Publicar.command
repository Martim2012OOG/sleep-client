#!/bin/bash
# EndLock.net: puts the home page (endlock.net) and Kubo Host World (host.endlock.net) online.
# Double-click this file on the Mac. The first time it opens the browser to log in to Cloudflare.
cd "$(dirname "$0")"
echo "== EndLock.net -> Cloudflare =="
if ! command -v npx >/dev/null 2>&1; then
  echo ""
  echo "Falta o Node.js. Instala-o em https://nodejs.org (botão LTS) e volta a abrir este ficheiro."
  open "https://nodejs.org"
  read -n 1 -s -r -p "Carrega numa tecla para fechar..."
  exit 1
fi
npx --yes wrangler@3 whoami >/dev/null 2>&1 || npx --yes wrangler@3 login
npx --yes wrangler@3 deploy
echo ""
echo "Feito! Abre https://endlock.net e https://host.endlock.net/<o teu nick>"
read -n 1 -s -r -p "Carrega numa tecla para fechar..."
