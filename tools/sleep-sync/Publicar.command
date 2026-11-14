#!/bin/bash
# sleep-sync: publica o Worker novo (so o sincronismo de badges / musica) na tua conta Cloudflare.
# Duplo clique no Mac. A primeira vez abre o browser para entrares no Cloudflare (a conta nobrega.martimrebelo2).
cd "$(dirname "$0")"
if ! command -v npx >/dev/null 2>&1; then
  echo "Falta o Node.js: https://nodejs.org (botao LTS)"; open "https://nodejs.org"; read -n 1 -s -r -p "Carrega numa tecla..."; exit 1
fi
npx --yes wrangler@3 whoami >/dev/null 2>&1 || npx --yes wrangler@3 login
npx --yes wrangler@3 deploy
echo ""
echo "Feito. O endereco aparece acima (https://sleep-sync.<subdominio>.workers.dev). Testa: abre-o no browser, deve dizer 'sleep-sync ok'."
read -n 1 -s -r -p "Carrega numa tecla para fechar..."
