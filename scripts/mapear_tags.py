from pycomm3 import LogixDriver
import json

IP_CLP = '128.1.200.50'

print(f"Tentando conectar ao CLP no IP {IP_CLP}...")

try:
    with LogixDriver(IP_CLP) as plc:
        if not plc.connected:
            print("Não foi possível estabelecer conexão com o equipamento.")
        else:
            print("Conectado com sucesso! Extraindo mapa de tags...")
            tags_do_clp = plc.tags

            arquivo_txt = 'lista_tags.txt'
            with open(arquivo_txt, 'w', encoding='utf-8') as f:
                f.write("# --- TAGS ENCONTRADAS NO CLP ---\n")
                for tag_name, tag_info in tags_do_clp.items():
                    tipo_dado = tag_info.get('data_type', 'UNKNOWN')
                    # Escreve no formato exato que o PLC4X precisa: Nome:TIPO
                    f.write(f"- \"{tag_name}:{tipo_dado}\"\n")
            
            print(f"\nSucesso! O arquivo '{arquivo_txt}' foi gerado na pasta atual.")
            print(f"Total de tags mapeadas: {len(tags_do_clp)}")

except Exception as e:
    print(f"\n[ERRO] Falha crítica na comunicação: {e}")
    print("Possíveis causas:")
    print("1. O IP está inacessível ou o CLP está offline.")
    print("2. O equipamento no IP informado não usa o protocolo Rockwell Logix (CIP).")