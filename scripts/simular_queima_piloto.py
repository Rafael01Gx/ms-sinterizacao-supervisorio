import time
from pycomm3 import LogixDriver

# Conecta no seu próprio simulador local (cpppo)
with LogixDriver('127.0.0.1') as plc:
    print("Simulador de dinâmica de processo iniciado...")
    
    while True:
        # 1. Lê os comandos que o seu Spring Boot enviou para o simulador
        tags = plc.read('oAciona_Ignicao', 'iTIT_02_Ignicao_PV', 'oGLP_Principal_Abrir')
        
        oAciona_Ignicao = tags[0].value
        temp_atual = tags[1].value
        valvula_aberta = tags[2].value

        # 2. Simula a física: Se a ignição e a válvula estiverem ligadas, a temperatura sobe
        if oAciona_Ignicao and valvula_aberta:
            if temp_atual < 1200.0: # Limite térmico do simulador
                nova_temp = temp_atual + 15.5 # Sobe 15.5 graus por segundo
                plc.write('iTIT_02_Ignicao_PV', nova_temp)
                print(f"Queimador Ligado. Temperatura subindo: {nova_temp}°C")
        else:
            # Se desligar, a temperatura esfria gradativamente
            if temp_atual > 25.0:
                nova_temp = temp_atual - 5.0
                plc.write('iTIT_02_Ignicao_PV', nova_temp)

        time.sleep(1) # Aguarda 1 segundo antes do próximo ciclo físico