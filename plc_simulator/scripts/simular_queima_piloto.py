import time
from pycomm3 import LogixDriver

# Conecta no seu próprio simulador local (cpppo)
# init_tags=False é crucial para compatibilidade com simuladores simples como o cpppo
with LogixDriver('127.0.0.1', init_tags=False) as plc:
    print("Simulador de dinâmica de processo iniciado...")

    # Define um estado inicial conhecido no simulador para evitar valores nulos
    print("Inicializando tags no simulador...")
    plc.write(('iTIT_02_Ignicao_PV', 25.0), ('oAciona_Ignicao', False), ('oGLP_Principal_Abrir', False))

    while True:
        try:
            # 1. Lê os comandos que o seu Spring Boot (ou Node-RED) enviou para o simulador
            tags = plc.read('oAciona_Ignicao', 'iTIT_02_Ignicao_PV', 'oGLP_Principal_Abrir')

            # Atribui valores padrão caso a leitura falhe ou retorne None
            oAciona_Ignicao = tags[0].value if tags[0].value is not None else False
            temp_atual = tags[1].value if tags[1].value is not None else 25.0
            valvula_aberta = tags[2].value if tags[2].value is not None else False

            # 2. Simula a física: Se a ignição e a válvula estiverem ligadas, a temperatura sobe
            if oAciona_Ignicao and valvula_aberta:
                if temp_atual < 1200.0: # Limite térmico do simulador
                    nova_temp = temp_atual + 15.5 # Sobe 15.5 graus por segundo
                    plc.write(('iTIT_02_Ignicao_PV', nova_temp))
                    print(f"Queimador Ligado. Temperatura subindo: {nova_temp:.1f}°C")
            else:
                # Se desligar, a temperatura esfria gradativamente
                if temp_atual > 25.0:
                    nova_temp = temp_atual - 5.0
                    plc.write(('iTIT_02_Ignicao_PV', nova_temp))
                    print(f"Queimador Desligado. Temperatura caindo: {nova_temp:.1f}°C")
                else:
                    # Garante que a temperatura não caia abaixo da ambiente
                    plc.write(('iTIT_02_Ignicao_PV', 25.0))

            time.sleep(1) # Aguarda 1 segundo antes do próximo ciclo físico

        except Exception as e:
            print(f"Ocorreu um erro no loop: {e}")
            time.sleep(5) # Aguarda um pouco antes de tentar novamente
