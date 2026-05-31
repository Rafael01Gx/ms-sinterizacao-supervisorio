#!/bin/bash

# Endereço IP e porta para o servidor ENIP
ADDRESS="127.0.0.1:44818"

# Encontra o diretório do script para que ele possa ser executado de qualquer lugar
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
TAG_FILE="$SCRIPT_DIR/tags_supervisorio.yml"

# --- Verificações de Dependências ---
if ! command -v yq &> /dev/null; then
    echo "Erro: yq não está instalado. Instale-o para continuar (pip install yq)."
    exit 1
fi
if ! command -v jq &> /dev/null; then
    echo "Erro: jq não está instalado (dependência do yq). Instale-o para continuar."
    echo "Visite https://jqlang.github.io/jq/download/ para instruções."
    exit 1
fi
if [ ! -f "$TAG_FILE" ]; then
    echo "Erro: Arquivo de tags não encontrado em $TAG_FILE"
    exit 1
fi

# --- Construção dos Argumentos ---
# 1. Lê o YAML com yq.
# 2. Remove os carriage returns (\r) do Windows com 'sed'.
# 3. Junta tudo em uma única linha com 'xargs'.
TAGS=$(yq -r '.tags[] | to_entries | .[] | .key + "=" + .value' "$TAG_FILE" | sed 's/\r$//' | xargs)

if [ -z "$TAGS" ]; then
    echo "Nenhuma tag encontrada em $TAG_FILE. Verifique o arquivo."
    exit 1
fi

# --- Execução do Servidor ---
CMD="python -m cpppo.server.enip --address $ADDRESS $TAGS"
echo "Executando o servidor cpppo com o seguinte comando:"
echo "$CMD"
echo "----------------------------------------------------"

$CMD
