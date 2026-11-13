#!/system/bin/sh
# Optional diagnostic, not a KernelSU module and does not change permissions.
set -eu
printf 'UID: '; id -u
if command -v su >/dev/null 2>&1; then
  echo 'su encontrado; conceda o deniegue acceso desde KernelSU Manager al solicitarlo la aplicación.'
else
  echo 'No se ha encontrado su en el PATH.'
fi
