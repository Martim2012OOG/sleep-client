# Segurança / Security

O código está público para **qualquer pessoa o poder ler e confirmar que não tem vírus**, mas **não é open source**: a licença
(PolyForm Strict 1.0.0, ver `LICENSE`) proíbe copiar/distribuir o código e fazer versões modificadas.

Encontraste uma falha de segurança? **Não a publiques em Issues nem em Pull Requests.** Avisa em privado:
GitHub → separador **Security** → **Report a vulnerability** (aviso privado). Respondo e corrijo antes de qualquer divulgação.

The source is public so anyone can audit it, but it is source-available, not open source (see `LICENSE`).
Found a vulnerability? Please do not open a public issue: use Security > Report a vulnerability (private advisory).

## Como confirmar que o que descarregas é o que está aqui
- Cada release publica o SHA-256 dos ficheiros. Compara com `shasum -a 256 ficheiro` (Mac/Linux) ou `Get-FileHash ficheiro` (Windows).
- Os jars e instaladores são compilados pelo GitHub Actions deste repositório (ver `.github/workflows`), não no PC de ninguém.
