# 🤖 MediBot G1 — Sistema HMI/HRI para Logística Hospitalaria Autónoma

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.7.3-green.svg?style=flat&logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Compose Compiler](https://img.shields.io/badge/Compose-Material_3-blue.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room Database](https://img.shields.io/badge/Room-2.6.1_(SQLite)-orange.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Platform](https://img.shields.io/badge/Platform-Android_SDK_35_(Min_28)-brightgreen.svg?style=flat&logo=android)](https://developer.android.com)
[![Offline First](https://img.shields.io/badge/Operation-100%25_Autonomous_Offline-red.svg?style=flat)](https://github.com/Fashisus07/MediBot)

Aplicación Android nativa desarrollada en **Kotlin** y **Jetpack Compose (Material 3)** que opera como interfaz de supervisión y comando (**HMI/HRI - Human-Machine Interface / Human-Robot Interaction**) para el robot humanoide **Unitree G1 EDU**.

El sistema está diseñado para la **logística intrahospitalaria autónoma** de insumos críticos: muestras biológicas urgentes (gases en sangre), hemoterapia (plasma y glóbulos rojos) y farmacia de alta vigilancia entre áreas críticas (Laboratorio Central, Quirófanos y UTI).

> **Proyecto Universitario:** UADE — Desarrollo de Aplicaciones 1 (Trabajo Práctico Obligatorio).  
> **Modalidad:** 100% Offline / Local Autónomo (Simulación de alta fidelidad con Corrutinas y Room Database en el propio dispositivo, sin dependencias de servidor externo ni hardware físico).

---

## 📸 Estética Visual y Diseño Dark Mode Tech/Medical

La interfaz adopta una paleta **Dark Mode Tech/Medical** de alto contraste diseñada para salas de control hospitalario, enfermería y visibilidad en entornos clínicos:

| Color | Hex | Uso en la Aplicación |
| :--- | :--- | :--- |
| **Dark Background** | `#0B0F19` | Fondo principal de la aplicación |
| **Dark Surface** | `#141B2D` | Superficies de tarjetas y scaffolds |
| **Dark Surface Variant**| `#1A2332` | Contenedores secundarios y badges |
| **Neon Cyan** | `#00F5D4` | Acento principal, telemetría normal y rutas |
| **Terminal Green** | `#00E676` | Estado operativo online, confirmaciones y éxito |
| **Warning Amber** | `#FFAB00` | Alertas medias, batería baja y datos antiguos (STALE) |
| **Critical Red** | `#FF1744` | Detención de emergencia, ruptura de cadena de frío y tamper |
| **Electric Violet** | `#7C4DFF` | Modo Ingeniero (FAB), validación NFC y subsistemas |

---

## 🏛️ Arquitectura de Software (Clean Architecture + MVVM)

El proyecto implementa **Clean Architecture** estricta junto con el patrón **MVVM** (Model-View-ViewModel) y flujo unidireccional de datos (**UDF - Unidirectional Data Flow**):

```
┌────────────────────────────────────────────────────────┐
│                   CAPA DE PRESENTACIÓN                 │
│  MainActivity (Scaffold + NavigationBar + FAB Debug)   │
│  Screens: RobotSelection, Dashboard, Missions, History │
│  Components: HospitalMapCanvas, TelemetryCard, etc.    │
└───────────────────────────┬────────────────────────────┘
                            │  Observa StateFlow<SupervisionUiState>
                            │  Envía RobotCommand
┌───────────────────────────▼────────────────────────────┐
│                    CAPA DE VIEWMODEL                   │
│                  SupervisionViewModel                  │
│  - Ticker de 1s para detección de Stale Data (>4000ms) │
│  - Orquestación de comandos y registro en Room        │
└───────────────────────────┬────────────────────────────┘
                            │  Interactúa con la interfaz RobotRepository
┌───────────────────────────▼────────────────────────────┐
│                CAPA DE DOMINIO Y DATOS                 │
│  interface RobotRepository                             │
│  └── SimulatedRobotRepository                          │
│      - Loop telemétrico autónomo (1000ms)              │
│      - Máquina de estados finita y reglas de negocio   │
│      - Inyector de 5 escenarios de prueba              │
└───────────────────────────┬────────────────────────────┘
                            │  Persistencia asíncrona
┌───────────────────────────▼────────────────────────────┐
│              CAPA DE PERSISTENCIA LOCAL                │
│                 AppDatabase (Room SQLite)              │
│  - MissionHistoryDao / MissionHistoryEntity            │
│  - EventLogDao / EventLogEntity                        │
└────────────────────────────────────────────────────────┘
```

---

## 🗺️ Circuito y Nodos Hospitalarios (Mapa 2D)

El robot navega a través de dos plantas conectadas por un ascensor montacamillas:

```
[PLANTA BAJA]                                     [TRANSICIÓN]             [PISO 2]
[ DOCK_BASE ] ──> [ LAB_CENTRAL ] ──> [ FIRE_DOOR_PB ] ──> [ ELEVATOR_BANK ] ──> [ SAFE_POINT_P2 ] ──> [ UTI_NURSING ]
  Base Carga        Laboratorio          Puerta               Ascensor               Receso Seguro        Enfermería
  Laboratorio       Bioquímico         Cortafuego           Hospitalario               de Espera          Terapia Int.
```

- **Planta Baja (PB):**
  - `DOCK_BASE`: Estación de recarga inductiva y reposo.
  - `LAB_CENTRAL`: Área de extracción y carga de muestras bioquímicas.
  - `FIRE_DOOR_PB`: Puerta cortafuego principal (interacción de brazo robótico).
- **Transición Vertical:**
  - `ELEVATOR_BANK`: Ascensor hospitalario montacamillas (enlace BACnet/IP).
- **Piso 2 (P2):**
  - `SAFE_POINT_P2`: Nicho de seguridad en pasillo quirúrgico para desvíos o esperas.
  - `UTI_NURSING`: Puesto de Enfermería en Unidad de Terapia Intensiva (entrega con NFC).

---

## 🩺 Variables de Telemetría Médica y Robótica

Actualizadas dinámicamente cada **1000ms** mediante `StateFlow<TelemetryState>`:

### 1. Variables Críticas del Servicio Médico (`ServicePayloadTelemetry`)
1. **Temperatura del Compartimento (`compartmentTempCelsius`):**
   - Rango normal: **2.0°C a 6.0°C**.
   - > 6.0°C: Alerta de Advertencia.
   - > 6.5°C: **Alerta Crítica de Cadena de Frío** (Riesgo biológico de hemólisis y desnaturalización).
2. **Estabilidad de Tubos / Muestras (`bioSampleStabilityIndexG`):**
   - Rango normal durante marcha: **< 0.30G**.
   - > 0.60G: **Riesgo Crítico de Hemólisis** mecánica por vibración acelerométrica.
3. **Cerradura Electromagnética Inteligente (`SmartLockState`):**
   - `SEALED_LOCKED`: Cerradura bloqueada y sellada herméticamente.
   - `UNLOCKED_NFC_AUTHORIZED`: Desbloqueo autorizado mediante credencial de enfermería.
   - `TAMPER_ALERT`: **Alerta de Violación/Manipulación no autorizada**.

### 2. Variables de Cinemática y HRI (Unitree G1 EDU)
- **Batería:** Porcentaje (0..100%), temperatura del pack y autonomía estimada en minutos.
- **Postura:** `BIPED_STANDING`, `BIPED_WALKING`, `OPERATING_DOOR_ARM`, `CROUCHED_SAFE_LOCK`.
- **HRI:** Color RGB del anillo LED cefálico y estado del sintetizador de voz embarcado.
- **Conectividad:** Enlace Wi-Fi 6 (`ONLINE`, `DEGRADED`, `OFFLINE`), latencia en milisegundos y nivel de señal (dBm).

---

## 🎮 Consola de Comandos y Ciclo de Vida Discreto

El sistema implementa el principio de seguridad: **"Una orden enviada NO equivale a una orden ejecutada"**.
La detención por software no reemplaza el botón físico de parada de emergencia (E-Stop).

### Comandos Disponibles:
1. `START_MISSION(mission)` — Inicia la misión si se cumplen todas las precondiciones.
2. `PAUSE_MISSION` — Pausa la marcha (prohibido pausar durante el cruce del ascensor).
3. `RESUME_MISSION` — Reanuda el avance pausado.
4. `CANCEL_MISSION` — Aborta la misión y detiene al robot.
5. `RETURN_TO_SAFE_POINT` — Desvía al humanoide al punto seguro más cercano.
6. `OPERATIONAL_STOP` — Detención inmediata por software con confirmación.
7. `ACKNOWLEDGE_ALERT(alertId)` — Reconocimiento (ACK) obligatorio de incidentes.

### Estados de Ejecución (`CommandExecutionState`):
- `IDLE`: En espera de órdenes.
- `PENDING`: Orden enviada (latencia simulada de 1200ms con spinner visual).
- `CONFIRMED`: Transición aceptada y confirmada por el robot.
- `REJECTED`: Rechazo por violación de reglas de seguridad (batería < 20%, compuerta abierta, etc.).
- `NO_RESPONSE`: Timeout tras 2500ms al encontrarse en estado `OFFLINE`.

---

## ⏱️ Detección de Datos Antiguos (Stale Data Engine)

En `SupervisionViewModel`, un **ticker reactivo de 1 segundo** evalúa:
$$\Delta t = \text{System.currentTimeMillis}() - \text{telemetry.lastUpdatedTimestamp}$$

- Si $\Delta t > 4000\text{ ms}$ (ej. corte de enlace de radio en subsuelos o ascensor):
  - `isTelemetryStale` se establece en `true`.
  - La UI reduce la opacidad al 55%, aplica bordes ámbar/gris y muestra el badge `"DATOS ANTIGUOS (hace Xs)"`.
  - Cualquier comando emitido entra en `PENDING` por 2.5s y finaliza en `NO_RESPONSE`, registrándose en Room como evento de auditoría.

---

## 🧪 Inyector de Simulación (5 Escenarios de Prueba Obligatorios)

Accesible desde el botón flotante (**FAB morado con ícono de insecto/debug**):

1. **Escenario 1 — Misión Normal:**
   Inicia en Laboratorio con 85% de batería y cápsula a 4.0°C. Avanza por los nodos cada 2s, cruza el ascensor, llega a UTI, solicita validación NFC de enfermería y persiste la misión como `COMPLETED` en Room.
2. **Escenario 2 — Batería Baja (14% → 7%):**
   Reduce la batería al 14% (alerta `WARNING`), deshabilita el despacho de nuevas misiones y a los 5s cae al 7%, activando la postura bípeda agachada `CROUCHED_SAFE_LOCK` (`LOW_BATTERY_CROUCHED`) para evitar desplomes.
3. **Escenario 3 — Desconexión Wi-Fi 6 y Recuperación:**
   Corta el enlace (`OFFLINE`). A los 4s la interfaz pasa a estado `STALE`. Los comandos responden con `NO_RESPONSE`. Incluye botón interactivo para *"Restablecer Enlace Wi-Fi 6"*.
4. **Escenario 4 — Incidente en Pasillo (Camilla a 0.35m):**
   Detiene automáticamente el avance en `FIRE_DOOR_PB`, cambia a `INCIDENT_BLOCKED`, emite alerta crítica sonora/visual y exige la decisión del operador: *Reanudar* o *Desviar a Punto Seguro*.
5. **Escenario 5 — Falla de Cadena de Frío / Tamper (8.4°C):**
   Eleva la temperatura a 8.4°C y activa `TAMPER_ALERT`. Emite alerta de riesgo biológico y exige al operador presionar `RECONOCER (ACK)` y abortar/desviar.
- **Switch Extra:** *"Forzar Rechazo del Próximo Comando"* para demostrar el estado `REJECTED` en cualquier orden enviada.

---

## 📱 Pantallas de la Aplicación

La navegación inferior cuenta con 4 secciones:

1. **`Robot` (RobotSelectionScreen):**
   Ficha técnica detallada del Unitree G1 EDU (sensores LiDAR Livox Mid-360, cámaras Intel RealSense D435i, manos Dex3-1, actuadores de 120Nm), estado de enlace y botón para acceder a la consola.
2. **`Dashboard` (DashboardScreen):**
   Header con batería, conectividad, badge `STALE` y botón de Detención Operativa. Banner de alertas con ACK, esquema táctico 2D `HospitalMapCanvas`, grilla de cards de telemetría y consola de comandos con feedback reactivo.
3. **`Misiones` (MissionsScreen):**
   Catálogo de 3 misiones preconfiguradas con checklist interactivo de precondiciones antes del despacho (Batería > 20%, cerradura sellada, temp < 6.0°C y robot en reposo).
4. **`Historial` (HistoryScreen):**
   Pestañas reactivas conectadas a Room SQLite: *Misiones Ejecutadas* (con duraciones, temperaturas y batería final) y *Log de Auditoría / Eventos*. Incluye botones para insertar datos semilla de prueba o limpiar la base de datos.

---

## 📂 Estructura del Código Fuente

```
app/src/main/java/com/medibot/g1/
├── MainActivity.kt                      # Scaffold, NavigationBar, BottomSheet FAB
├── MediBotApplication.kt                # Inicialización de AppDatabase (Room)
│
├── data/
│   ├── model/
│   │   ├── HospitalNode.kt              # Nodos del mapa hospitalario (PB a P2)
│   │   ├── Enums.kt                     # Estados operativos, posturas, cerradura, etc.
│   │   ├── RobotAlert.kt                # Alertas del sistema y flag de ACK
│   │   ├── ServicePayloadTelemetry.kt   # Variables médicas (Temp, G, Cerradura)
│   │   ├── MissionConfig.kt             # Modelo de misiones y prioridades
│   │   ├── TelemetryState.kt            # Estado global telemétrico
│   │   ├── RobotCommand.kt              # Sealed class de comandos
│   │   └── CommandExecutionState.kt     # Estados Idle, Pending, Confirmed, etc.
│   │
│   ├── local/
│   │   ├── AppDatabase.kt               # Base de datos Room
│   │   ├── MissionHistoryEntity.kt      # Tabla de misiones finalizadas
│   │   ├── EventLogEntity.kt            # Tabla de auditoría y comandos
│   │   ├── MissionHistoryDao.kt         # DAO de misiones con Flow
│   │   └── EventLogDao.kt               # DAO de eventos con Flow
│   │
│   └── repository/
│       ├── RobotRepository.kt           # Contrato de repositorio desacoplado
│       └── SimulatedRobotRepository.kt  # Motor de simulación y máquina de estados
│
└── ui/
    ├── theme/
    │   ├── Color.kt                     # Paleta Dark Mode Tech/Medical
    │   ├── Type.kt                      # Tipografía técnica
    │   └── Theme.kt                     # Material Theme
    │
    ├── viewmodel/
    │   └── SupervisionViewModel.kt      # Ticker Stale Data y persistencia Room
    │
    ├── components/
    │   ├── TelemetryCard.kt             # Card con soporte STALE, WARNING, ERROR
    │   ├── HospitalMapCanvas.kt         # Mapa 2D con Canvas y nodos pulsantes
    │   ├── CommandControlPanel.kt       # Botones de control + Aviso legal HRI
    │   ├── AlertBanner.kt               # Banner interactivo de alertas con ACK
    │   └── SimulatorControlBottomSheet.kt # Inyector modal de los 5 escenarios
    │
    └── screens/
        ├── RobotSelectionScreen.kt      # Ficha técnica de hardware
        ├── DashboardScreen.kt           # Supervisión telemétrica y control
        ├── MissionsScreen.kt            # Catálogo y checklist de precondiciones
        └── HistoryScreen.kt             # Visualización Room en tiempo real
```

---

## 🛠️ Requisitos y Compilación

- **Android Studio:** Ladybug (2024.2.1+) o Meerkat (2024.3.1+)
- **JDK:** Java 17 o superior
- **Android SDK:** Compile SDK 35 / Min SDK 28
- **Kotlin:** 2.0.21 con plugin Compose Compiler integrado

Para compilar e instalar desde la terminal:

```bash
# Compilar APK de depuración
./gradlew assembleDebug

# Ejecutar pruebas unitarias
./gradlew test

# Instalar en dispositivo o emulador conectado
./gradlew installDebug
```

---

## 📖 Documentación Adicional

En la raíz del proyecto se encuentra el archivo técnico detallado:
- 📄 **[DOCS_TECNICOS_TP.md](DOCS_TECNICOS_TP.md)**:
  - Diagrama de Arquitectura completo en **Mermaid**.
  - Contratos formales JSON propuestos (`telemetry_payload.json`, `command_request.json`, `command_response.json`).
  - Matriz de los **5 Riesgos Operativos y de HRI** en entornos hospitalarios y sus mitigaciones en UI y lógica.

---

**Desarrollado para:** UADE — Licenciatura / Ingeniería en Informática  
**Materia:** Desarrollo de Aplicaciones 1 — Trabajo Práctico Obligatorio
