# PSDamageTracker

[English](README.md) | **Español**

Plugin de Minecraft que registra el daño de los jugadores contra mobs de **MythicMobs** y muestra una clasificación al terminar cada combate. Permite personalizar los resultados, guardar estadísticas en SQLite y consultar posiciones mediante PlaceholderAPI.

[Instalación](#instalación) · [Configuración](#configuración) · [Comandos](#comandos-y-permisos) · [Placeholders](#placeholderapi) · [Compilación](#compilación)

## Características

- Registro de daño cuerpo a cuerpo y de proyectiles disparados por jugadores.
- Seguimiento independiente de cada entidad, incluso cuando hay varios mobs con el mismo ID.
- Clasificaciones por daño, con posición y porcentaje de participación.
- Mensajes de victoria globales o para los participantes, además de mensajes personales y para quienes no participaron.
- Plantillas con MiniMessage y códigos de color tradicionales con `&`.
- Persistencia opcional de estadísticas acumuladas por jugador y tipo de boss en SQLite.
- Integraciones opcionales con PlaceholderAPI y FancyHolograms.

## Requisitos

| Componente | Requisito |
| --- | --- |
| Servidor | API Bukkit/Spigot 1.19 como base de compilación; `api-version: '1.19'`. |
| Java | Java 17 como mínimo para el plugin; utiliza la versión que requiera tu servidor. |
| MythicMobs | Obligatorio. El proyecto compila contra la API 5.4.1. |
| PlaceholderAPI | Opcional, para consultar el daño del jugador en combates activos y posiciones del ranking persistido. API de compilación: 2.11.6. |
| FancyHolograms | Opcional, para mostrar resultados en hologramas temporales. API de compilación: 2.4.2. |

Las versiones de las dependencias indicadas son las utilizadas para compilar, no una matriz de compatibilidad verificada. Elige versiones compatibles con tu servidor y comprueba el conjunto antes de instalarlo en producción.

## Instalación

1. Compila el proyecto siguiendo las [instrucciones de compilación](#compilación).
2. Detén el servidor e instala MythicMobs y las integraciones opcionales que quieras utilizar.
3. Copia `build/libs/DamageTracker-2.2.0.jar` a la carpeta `plugins/` del servidor.
4. Inicia el servidor. Se generará la carpeta `plugins/PSDamageTracker/` con la configuración.
5. Ajusta los IDs de MythicMobs y las plantillas. Aplica los cambios con `/damagetracker reload`.

Kotlin y las bibliotecas de formato de texto se incluyen en el JAR generado por Shadow; no necesitas instalar un plugin adicional para proporcionar Kotlin.

## Configuración

Los archivos se crean durante el primer inicio:

| Archivo | Contenido |
| --- | --- |
| [`config.yml`](src/main/resources/config.yml) | Opciones por boss, configuración predeterminada, formatos numéricos y persistencia. |
| [`tracked_bosses.yml`](src/main/resources/tracked_bosses.yml) | Lista de IDs internos de MythicMobs que se deben registrar. |
| [`victory_messages.yml`](src/main/resources/victory_messages.yml) | Plantillas de victoria, posiciones, resultados personales y mensajes para no participantes. |
| [`leaderboard_format.yml`](src/main/resources/leaderboard_format.yml) | Formato general de las líneas de clasificación. |

### Seleccionar bosses

En `tracked_bosses.yml`, añade los IDs internos de los mobs, respetando mayúsculas y minúsculas:

```yaml
tracked_bosses:
  enabled_bosses:
    - SkeletonKing
    - RAID_BOSS
```

Con `enabled_bosses: []` se registran todos los mobs de MythicMobs. Esta lista filtra el seguimiento; las opciones de mensajes se definen en `config.yml`.

### Personalizar los resultados

Añade una entrada bajo `bosses` en `config.yml`. Por ejemplo:

```yaml
bosses:
  SkeletonKing:
    victory_message_id: DEFAULT_VICTORY
    top_players_to_show: 3
    position_format_id: DEFAULT
    broadcast_message: true
    personal_message_id: DEFAULT
    non_participant_message_id: DEFAULT
    hologram_enabled: false
```

Los IDs de mensajes y formatos deben corresponder a las secciones de `victory_messages.yml`. Los bosses sin una sección propia usan `default_boss_config`; conviene incluir todas las opciones en cada sección personalizada.

- `broadcast_message: true` publica el mensaje de victoria en el servidor.
- `broadcast_message: false` lo envía a los participantes conectados.
- Con `broadcast_message: false`, `hologram_enabled: true` y FancyHolograms disponible, el mensaje de victoria se muestra en un holograma durante cinco minutos, sobre un participante conectado.

Los mensajes personales y para no participantes se procesan por separado. En la clasificación, las entradas de `position_formats` tienen prioridad sobre el formato general de `leaderboard_format.yml` para las posiciones que tengan plantilla.

### Variables de las plantillas

Estas variables pertenecen a los archivos de mensajes; son independientes de PlaceholderAPI.

| Contexto | Variables |
| --- | --- |
| Mensaje de victoria | `{boss_name}`, `{top_players}` |
| Formato de posición | `{position}`, `{player_name}`, `{damage}`, `{percentage}` |
| Mensaje personal | `{boss_name}`, `{player_name}`, `{position}`, `{damage}`, `{percentage}` |
| Mensaje para no participantes | `{boss_name}` |
| Formato general de clasificación | `{symbol}`, `{position}`, `{name}`, `{damage}`, `{percentage}` |

Ejemplo de plantilla en `victory_messages.yml`:

```yaml
messages:
  DEFAULT_VICTORY: |
    <gold>¡{boss_name} ha sido derrotado!</gold>
    <yellow>Mayores contribuciones:</yellow>
    {top_players}
```

### Guardar estadísticas

La persistencia está desactivada por defecto. Para guardar los resultados al morir un boss, modifica esta opción de `config.yml`:

```yaml
tracking_config:
  persist_data: true
```

Los resultados se acumulan por UUID del jugador e ID del boss en `plugins/PSDamageTracker/damage_tracker.db`. No se necesita un servidor de base de datos externo. Sin persistencia, los datos del combate se eliminan de memoria al morir el mob y no se añaden al ranking de PlaceholderAPI.

## Comandos y permisos

El alias `/dtr` puede sustituir a `/damagetracker`.

| Comando | Descripción | Permiso | Acceso predeterminado |
| --- | --- | --- | --- |
| `/damagetracker` | Muestra la ayuda. | Ninguno | Todos |
| `/damagetracker check <bossID>` | Muestra los tres primeros jugadores en los combates activos de ese ID. | `damageTracker.check` | Todos |
| `/damagetracker reload` | Recarga la configuración y las plantillas. | `damageTracker.reload` | Operadores |

`check` agrega el daño de las entidades activas con el mismo ID. Cuando termina un combate, sus datos dejan de aparecer en este comando; los resultados guardados se consultan mediante PlaceholderAPI.

## PlaceholderAPI

La expansión `dt` se registra automáticamente si PlaceholderAPI está instalado. No requiere descargar una expansión desde eCloud.

| Placeholder | Resultado |
| --- | --- |
| `%dt_damage%` | Daño total del jugador actual, identificado por UUID, en todos los combates activos registrados. |
| `%dt_damagetop_<bossID>_<posición>%` | Nombre del jugador en esa posición del ranking acumulado guardado. La primera posición es `1`. |

`%dt_damage%` requiere un contexto de jugador y devuelve `0.0` si no se proporciona un jugador o si no tiene daño en combates activos. La contribución de cada combate se retira del total al terminar o descartarse ese combate. Funciona sin persistencia y excluye los totales históricos de SQLite. Devuelve el valor numérico directamente, sin aplicar `damage_format`.

Por ejemplo, `%dt_damagetop_SkeletonKing_1%` devuelve el nombre del jugador con más daño acumulado contra `SkeletonKing`. Los IDs pueden contener guiones bajos.

Activa `tracking_config.persist_data` para registrar nuevos resultados para `%dt_damagetop_<bossID>_<posición>%`. Este placeholder de clasificación devuelve `No player found` si no hay un jugador en esa posición, o `Invalid position` si la posición no es válida.

## Compilación

Necesitas un **JDK 17** disponible para la toolchain. El repositorio incluye Gradle Wrapper, por lo que no hace falta instalar Gradle por separado. La primera ejecución requiere conexión para descargar Gradle y las dependencias.

Desde la raíz del proyecto, en Linux o macOS:

```sh
./gradlew clean build
```

En Windows con PowerShell:

```powershell
.\gradlew.bat clean build
```

La tarea `build` ejecuta las pruebas y genera el JAR con las dependencias incluidas mediante `shadowJar`. Para la versión actual, el archivo instalable es `build/libs/DamageTracker-2.2.0.jar`.

Para ejecutar únicamente las pruebas:

```sh
./gradlew test
```

En PowerShell, utiliza `.\gradlew.bat test`. Los informes quedan en `build/reports/tests/test/index.html`.

Las pruebas unitarias de seguimiento y persistencia están en [`src/test/kotlin`](src/test/kotlin). El script [`integration/run-paper.ps1`](integration/run-paper.ps1) permite comprobar un servidor local ya preparado; requiere ajustar la ruta de Java y disponer de Paper y sus plugins en `integration/paper-<versión>/`.

## Reportar problemas y contribuir

Incluye la versión de Minecraft, el software del servidor, Java, PSDamageTracker y las dependencias instaladas, junto con los pasos para reproducir el problema y los mensajes relevantes de la consola. Si el problema depende de una configuración, adjunta un ejemplo mínimo.

Para contribuir, mantén el estilo del código existente y ejecuta las pruebas antes de proponer cambios.
