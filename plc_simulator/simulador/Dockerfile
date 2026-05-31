# Use uma imagem base do Python
FROM python:3.9-slim

# Instale as dependências do sistema (jq) e do Python
RUN apt-get update && apt-get install -y jq && \
    pip install cpppo yq

# Copie a pasta de scripts para dentro do contêiner
COPY scripts /app/scripts

# Defina o diretório de trabalho
WORKDIR /app

# Dê permissão de execução ao script
RUN chmod +x /app/scripts/run_cpppo_server.sh

# Comando para iniciar o servidor
CMD ["./scripts/run_cpppo_server.sh"]
