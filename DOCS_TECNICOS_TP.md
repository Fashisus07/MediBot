# MediBot G1: Sistema HMI/HRI para Robótica Hospitalaria (Unitree G1 EDU)
## Documentación Técnica de Arquitectura, Contratos y Seguridad Operativa

**Universidad:** UADE — Desarrollo de Aplicaciones 1 (TPO)  
**Plataforma:** Android Nativo (Kotlin 2.0.21, Jetpack Compose Material 3, Room, Coroutines/StateFlow)  
**Robot Target:** Humanoide Unitree G1 EDU (23 DOF, LiDAR Livox Mid-360, RGB-D RealSense D435i, Manos Dex3-1, Cápsula Peltier de Biocontrol)  
**Modalidad Operativa:** 100% Offline / Local Autónomo (Simulación de Alta Fidelidad en Dispositivo)

---

## 1. Diagrama de Arquitectura del Sistema (Mermaid)

El siguiente diagrama ilustra el flujo unidireccional de datos (UDF - *Unidirectional Data Flow*), la separación de capas Clean Architecture y la reactividad basada en `StateFlow` y Room `Flow`.

```mermaid
flowchart TD
    subgraph UI_Layer ["Capa de Presentación (Jetpack Compose)"]
        Activity["MainActivity.kt (Scaffold + NavigationBar)"]
        Screen1["RobotSelectionScreen\n(Ficha Técnica G1 EDU)"]
        Screen2["DashboardScreen\n(Header + Map2D + Cards + Console)"]
        Screen3["MissionsScreen\n(Catálogo + Checklist Precondiciones)"]
        Screen4["HistoryScreen\n(Misiones Ejecutadas + Auditoría)"]
        BottomSheet["SimulatorControlBottomSheet\n(Inyector 5 Escenarios + Force Reject)"]
        Components["Componentes Reutilizables:\nTelemetryCard, HospitalMapCanvas,\nCommandControlPanel, AlertBanner"]
    end

    subgraph ViewModel_Layer ["Capa de ViewModel (MVVM)"]
        VM["SupervisionViewModel"]
        Ticker["Ticker Autónomo 1s\n(Stale Data Evaluator: >4000ms)"]
        UiStateFlow["StateFlow&lt;SupervisionUiState&gt;"]
    end

    subgraph Domain_Data_Layer ["Capa de Dominio y Repositorio"]
        IRepo["interface RobotRepository"]
        SimRepo["SimulatedRobotRepository\n(Máquina de Estados + Loop 1s)"]
        Models["Modelos de Dominio:\nTelemetryState, RobotCommand,\nCommandExecutionState, RobotAlert,\nServicePayloadTelemetry, MissionConfig"]
    end

    subgraph Local_Storage_Layer ["Capa de Persistencia Local (Room SQLite)"]
        AppDb["AppDatabase (Room)"]
        MissionDao["MissionHistoryDao"]
        EventDao["EventLogDao"]
        MissionEntity["MissionHistoryEntity"]
        EventEntity["EventLogEntity"]
    end

    %% Relaciones y Flujos de Datos
    Activity --> VM
    Screen1 --> VM
    Screen2 --> VM
    Screen3 --> VM
    Screen4 --> VM
    BottomSheet --> VM
    Components -.-> Screen2

    VM -->|Observa telemetryFlow & commandStateFlow| IRepo
    VM -->|Emite StateFlow reactivo| UiStateFlow
    UiStateFlow --> Screen2
    UiStateFlow --> Screen3
    UiStateFlow --> Screen1

    VM -->|Ejecuta Ticker 1s| Ticker
    Ticker -->|Marca isTelemetryStale = true| UiStateFlow

    IRepo <|.. SimRepo
    SimRepo -->|Genera periódicamente| Models

    VM -->|Persiste misiones completadas/canceladas| MissionDao
    VM -->|Registra auditoría, comandos y alertas| EventDao
    MissionDao --> MissionEntity
    EventDao --> EventEntity
    MissionDao -->|Flow&lt;List&lt;MissionHistoryEntity&gt;&gt;| VM
    EventDao -->|Flow&lt;List&lt;EventLogEntity&gt;&gt;| VM
    AppDb --> MissionDao
    AppDb --> EventDao
```

### Principios de Diseño Aplicados:
1. **MVVM Estricto + UDF:** La UI nunca altera variables del robot directamente; despacha intenciones (`RobotCommand`) mediante `viewModel.sendCommand(command)`.
2. **Desacoplamiento de Repositorio:** `SupervisionViewModel` depende exclusivamente de la abstracción `RobotRepository`. La implementación `SimulatedRobotRepository` puede ser sustituida por una implementación real basada en ROS 2 / CycloneDDS (`UnitreeSDK2`) sin tocar una sola línea de Compose.
3. **Persistencia No Bloqueante:** Toda lectura de auditoría e historial se realiza mediante `Flow` reactivo de Room respaldado por corrutinas en `Dispatchers.IO`.

---

## 2. Contratos de Comunicación JSON Propuestos (Arquitectura HMI ↔ Robot)

Aunque el Unitree G1 utiliza internamente DDS/LCM sobre UDP, para interfaces HMI de supervisión médica en tablets o smartphones industriales se especifica un contrato JSON sobre WebSocket seguro (o IPC local).

### 2.1. Contrato de Telemetría Periódica (`telemetry_payload.json`)
Emitido cada 1000ms por el nodo de supervisión del robot:

```json
{
  "header": {
    "protocol_version": "v2.4-MED",
    "timestamp_ms": 1730827200000,
    "sequence_id": 48102
  },
  "identification": {
    "robot_id": "G1-EDU-MED01",
    "firmware_version": "v2.4.1-DEX3",
    "robot_model": "Unitree G1 EDU Humanoid",
    "degrees_of_freedom": 23
  },
  "connectivity": {
    "status": "ONLINE",
    "latency_ms": 38,
    "signal_dbm": -34,
    "interface": "Wi-Fi 6 (802.11ax) MIMO"
  },
  "power_subsystem": {
    "battery_percentage": 84,
    "pack_voltage_v": 52.8,
    "current_draw_a": 4.2,
    "battery_temp_celsius": 29.4,
    "estimated_minutes_left": 168
  },
  "operation": {
    "state": "EXECUTING_MISSION",
    "posture": "BIPED_WALKING",
    "current_node_id": "FIRE_DOOR_PB",
    "current_node_display": "PB - Puerta Cortafuego Principal",
    "speed_mps": 0.60,
    "mission_progress_pct": 40
  },
  "active_mission": {
    "mission_id": "MED-01-GAS",
    "name": "Urgencia: Gases en Sangre → UTI",
    "priority": "URGENT",
    "origin": "LAB_CENTRAL",
    "destination": "UTI_NURSING",
    "requires_nfc_handoff": true
  },
  "medical_payload": {
    "compartment_temp_celsius": 4.1,
    "bio_sample_stability_index_g": 0.14,
    "smart_lock": {
      "state": "SEALED_LOCKED",
      "solenoid_voltage_ok": true,
      "tamper_switch_triggered": false
    }
  },
  "hri_subsystem": {
    "head_led_ring_hex": "#00F5D4",
    "voice_synthesizer_status": "Transitando por: PB - Puerta Cortafuego Principal",
    "optical_privacy_shutter": "CLOSED"
  },
  "active_alerts": [
    {
      "id": "ALT-9821",
      "severity": "INFO",
      "title": "Aviso de Tránsito",
      "message": "Navegando hacia ascensor médico en piso PB.",
      "timestamp": 1730827195000,
      "is_acknowledged": true
    }
  ]
}
```

---

### 2.2. Contrato de Comando del Operador (`command_request.json`)
Enviado por la interfaz HMI hacia el robot:

```json
{
  "request_id": "REQ-7731-B89",
  "client_id": "MEDIBOT-TABLET-HMI-01",
  "operator_id": "NURSE_LIC_4892",
  "timestamp_ms": 1730827210000,
  "command": "START_MISSION",
  "parameters": {
    "mission_id": "MED-01-GAS",
    "name": "Urgencia: Gases en Sangre → UTI",
    "priority": "URGENT",
    "origin": "LAB_CENTRAL",
    "destination": "UTI_NURSING",
    "route": [
      "LAB_CENTRAL",
      "FIRE_DOOR_PB",
      "ELEVATOR_BANK",
      "SAFE_POINT_P2",
      "UTI_NURSING"
    ],
    "required_temp_range": {
      "min_celsius": 2.0,
      "max_celsius": 6.0
    },
    "max_vibration_g": 0.30,
    "requires_nfc_handoff": true
  }
}
```

---

### 2.3. Contratos de Respuesta (`command_response.json`)

#### Variante A: Confirmada (`CONFIRMED`)
```json
{
  "request_id": "REQ-7731-B89",
  "command": "START_MISSION",
  "execution_status": "CONFIRMED",
  "latency_ms": 1200,
  "timestamp_ms": 1730827211200,
  "result": {
    "code": 200,
    "message": "Misión 'Urgencia: Gases en Sangre → UTI' iniciada correctamente.",
    "new_operational_state": "EXECUTING_MISSION",
    "posture": "BIPED_WALKING"
  }
}
```

#### Variante B: Rechazada por Regla de Negocio (`REJECTED`)
*(Ejemplo: Intento de iniciar misión con compartimento caliente o batería insuficiente)*
```json
{
  "request_id": "REQ-7731-B90",
  "command": "START_MISSION",
  "execution_status": "REJECTED",
  "latency_ms": 1200,
  "timestamp_ms": 1730827211200,
  "result": {
    "code": 412,
    "reason": "Batería insuficiente (14%). Se requiere mínimo 20% para iniciar despacho de hemocomponentes.",
    "policy_violated": "POLICY_MIN_BATTERY_FOR_DISPATCH",
    "current_operational_state": "IDLE_AT_DOCK"
  }
}
```

#### Variante C: Sin Respuesta por Pérdida de Enlace (`NO_RESPONSE`)
*(Generado en cliente/HMI tras cumplirse el timeout de 2500ms al estar en estado `OFFLINE`)*
```json
{
  "request_id": "REQ-7731-B91",
  "command": "START_MISSION",
  "execution_status": "NO_RESPONSE",
  "timeout_ms": 2500,
  "timestamp_ms": 1730827212500,
  "result": {
    "code": 504,
    "reason": "Sin respuesta del robot tras 2500ms. El enlace de radio (Wi-Fi 6) se encuentra fuera de línea.",
    "is_stale_data": true,
    "remedial_action": "Restablezca el enlace Wi-Fi 6 o diríjase físicamente a la posición del robot."
  }
}
```

---

## 3. Matriz de 5 Riesgos Operativos y de HRI en Entornos Médicos

La integración de un robot humanoide bípedo (Unitree G1 EDU, 35 kg de masa de aleación y actuadores de hasta 120 Nm) en un hospital exige una rigurosa arquitectura de seguridad tanto en la lógica de control como en la interfaz hombre-máquina (HRI).

| # | Riesgo Crítico Identificado | Consecuencia Potencial | Mitigación en Interfaz (HMI / Compose) | Mitigación en Lógica de Negocio (Kotlin / Repo) |
|---|-----------------------------|------------------------|----------------------------------------|--------------------------------------------------|
| **1** | **Órdenes Accidentales del Operador** (Disparo involuntario de comandos de movimiento o detención) | Movimiento inesperado en áreas quirúrgicas o cancelación errónea de traslados críticos. | • Diálogo de confirmación rápida en Compose para la Detención Operativa.<br>• Checklist reactivo de pre-condiciones en `MissionsScreen` que deshabilita el botón de despacho si no se cumplen todas las reglas.<br>• Botones de acción contextuales según el estado operativo (ej. *Pausar* sólo habilitado durante `EXECUTING_MISSION`). | • Validación de pre-condiciones en `SimulatedRobotRepository.handleStartMission`: chequeo estricto de cerradura, temperatura y batería.<br>• Simulación de latencia de 1200ms con estado `PENDING` para evitar envíos en ráfaga.<br>• Máquina de estados finita que rechaza transiciones ilegales (`REJECTED`). |
| **2** | **Colisión con Pacientes, Camillas o Personal** (Tránsito en pasillos estrechos o cruce de puertas cortafuego) | Lesiones a pacientes vulnerables, daño a equipamiento médico o al humanoide. | • Esquema 2D `HospitalMapCanvas` que resalta en rojo pulsante el nodo de incidente (`INCIDENT_BLOCKED`).<br>• Banner de alerta crítica visible con detalles de la obstrucción y distancia detectada por LiDAR (ej. *"Camilla a 0.35m"*).<br>• Interfaz que fuerza al operador a decidir entre *Esperar y Reanudar* o *Desviar a Punto Seguro*. | • Integración en simulación del Escenario 4: ante obstáculo frontal el robot detiene automáticamente su velocidad (`speedMps = 0.0f`).<br>• Anillo LED HRI cambia de verde/cian a rojo (#FF1744) y sintetiza voz de alerta para advertir a los presentes en el pasillo.<br>• Prohibición estricta de reanudar automáticamente sin confirmación humana. |
| **3** | **Decisiones Basadas en Telemetría Congelada (Stale Data)** (Pérdida silenciosa de enlace Wi-Fi en zonas blindadas como radiología o ascensores) | El operador cree que el robot sigue avanzando normalmente cuando en realidad se encuentra detenido o bloqueado. | • Detector visual de datos antiguos: si `System.currentTimeMillis() - lastUpdatedTimestamp > 4000ms`, toda la interfaz pasa a modo `STALE`.<br>• Opacidad reducida al 55%, bordes en color gris/ámbar y badge destacado: `"DATOS ANTIGUOS (hace Xs)"`.<br>• Si el usuario presiona un botón en este estado, la UI muestra de inmediato feedback `NO_RESPONSE`. | • `SupervisionViewModel` ejecuta un `Ticker` independiente cada 1000ms comparando timestamps.<br>• El repositorio no actualiza marcas temporales si `connectionStatus == OFFLINE`.<br>• Registro en Room `EventLogEntity` catalogado como `WARNING: Comando emitido con Datos Antiguos`. |
| **4** | **Desplome del Humanoide por Agotamiento de Batería (0%)** | Caída repentina de 35 kg de estructura metálica sobre el piso hospitalario, con rotura de muestras y riesgo de trauma físico. | • Alerta preventiva `WARNING` al alcanzar 14% de batería.<br>• Bloqueo visual del botón de iniciar misión.<br>• Sugerencia visual en pantalla para despacho a *Punto Seguro* o *Base de Carga*. | • En el Escenario 2, al caer la batería al 7%, el robot fuerza automáticamente la postura bípeda agachada `CROUCHED_SAFE_LOCK` (`LOW_BATTERY_CROUCHED`).<br>• El centro de gravedad desciende a menos de 0.35m del suelo y se activan frenos magnéticos en los servos de rodillas y tobillos, impidiendo cualquier desplome descontrolado. |
| **5** | **Privacidad de Pacientes y Confidencialidad en Salas Médicas** (Grabación involuntaria en habitaciones o salas de terapia intensiva) | Violación de la ley de protección de datos médicos personales (HIPAA / Ley 25.326) por cámaras RealSense D435i. | • Ficha técnica en `RobotSelectionScreen` detallando el estado de los obturadores.<br>• Indicación HRI de *"Obturador Óptico Cerrado"* en navegación de planta.<br>• El robot no transmite video crudo a la HMI, únicamente coordenadas vectoriales de mapa 2D y métricas de temperatura/vibración. | • La navegación autónoma se realiza primariamente mediante LiDAR 3D (nube de puntos anónima Livox Mid-360) sin almacenamiento de imágenes fotográficas.<br>• Las cámaras RealSense operan con procesamiento de profundidad local en el robot (Edge AI) para evasión de obstáculos, descartando inmediatamente los frames de color (RGB) para preservar el secreto médico. |

---

## 4. Guía de Ejecución y Validación de Escenarios de Prueba

Para evaluar la aplicación en un emulador o dispositivo Android físico:

1. **Compilación:**
   ```bash
   ./gradlew assembleDebug
   ```
2. **Navegación:**
   - La pantalla principal inicial es el **Dashboard**.
   - Presione el botón flotante morado (`FAB` con ícono de insecto/debug) en cualquier momento para abrir el **Inyector de Simulación**.
3. **Validación de Escenarios:**
   - **Escenario 1 (Misión Normal):** Observe el avance por los 5 nodos en el `HospitalMapCanvas`, el cambio de postura a `OPERATING_DOOR_ARM`, el estado `WAITING_ELEVATOR`, la solicitud final de NFC en UTI y el guardado automático de la misión en Room (`HistoryScreen`).
   - **Escenario 2 (Batería Baja):** Compruebe la alerta amarilla al 14%, la imposibilidad de despachar misiones en `MissionsScreen`, y a los 5 segundos la transición a `LOW_BATTERY_CROUCHED` con alerta crítica.
   - **Escenario 3 (Desconexión / Stale Data):** Presione el botón de desconexión. Cuente 4 segundos y observe cómo las tarjetas de telemetría se atenúan y muestran el badge `STALE (hace 4s)`. Intente enviar un comando y observe el timeout `NO_RESPONSE` a los 2.5s. Luego presione "Restablecer Enlace Wi-Fi 6".
   - **Escenario 4 (Incidente Camilla):** Vea cómo el avance se detiene en la Puerta Cortafuego PB, el estado cambia a `INCIDENT_BLOCKED` y se exige decisión al operador.
   - **Escenario 5 (Falla de Cadena de Frío / Tamper):** Observe la subida de temperatura a 8.4°C en rojo, el aviso de riesgo biológico y la necesidad de presionar `RECONOCER (ACK)`.
   - **Switch Forzar Rechazo:** Active el switch en el panel debug y presione cualquier botón de la consola para ver el feedback naranja `REJECTED`.
   - **Pestaña Historial:** Vaya a `Historial` para constatar que todas las misiones y eventos quedaron guardados en Room Database con persistencia real.

