# ProbadorApp

Aplicación Android para gestionar los probadores de la Tienda Departamental Rincón Sur (caso CITT). Detecta la ocupación de cada probador con sensores, entrega números de atención y los llama en orden, alerta al personal y lleva el control del personal de cada departamento.

**Asignatura:** DSY1105 Desarrollo de Aplicaciones Móviles, sección 009V, Grupo 6
**Estudiantes:** Eduardo Romero y Reinel Hernández
**Docente:** Sergio Fuentes

La especificación completa está en *ProbadorApp_Especificacion_y_Diseno_v3.0.pdf*.

## Cómo ejecutar

1. Abrir Android Studio y elegir **Open**, luego la carpeta `ProbadorApp-code`.
2. Esperar la sincronización de Gradle (Gradle Wrapper 8.13 y el JDK de Android Studio).
3. Ejecutar la configuración **app** en un emulador o dispositivo con Android 7.0 (API 24) o superior. Se recomienda un emulador de tablet en horizontal (Pixel Tablet) y también probar en un teléfono (Pixel 8) para ver el diseño compacto.
4. Iniciar sesión con un usuario de prueba.

### Usuarios de prueba

La contraseña de todos es `Rincon2026`.

| Correo | Rol |
|---|---|
| ana.perez@rinconsur.test | Jefa de tienda: ve los tres departamentos, mueve personal, cambia jefaturas, configura probadores y equipo, usa el simulador |
| valentina.rojas@rinconsur.test | Jefa del departamento Mujer |
| matias.gonzalez@rinconsur.test | Jefe del departamento Hombre |
| camila.munoz@rinconsur.test | Vendedora de Mujer |
| diego.torres@rinconsur.test | Vendedor de Hombre (parte fuera de turno) |

## Arquitectura

Patrón **MVVM** con repositorios y un motor de reglas:

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Model | `domain/model` | Datos del dominio: probadores, números, alertas, incidencias, objetos, personal, solicitudes, jefaturas. |
| Reglas y validaciones | `domain/rules`, `domain/validation` | Funciones puras con las reglas de negocio (RN) y las validaciones de los formularios. No dependen de Android y tienen pruebas unitarias. |
| Motor | `engine` | `MotorReglas` (señales, asignación de números, tiempos, incidencias, probadores), `GestionPersonal` (movimientos, solicitudes, jefaturas, cobertura) y `GestionObjetos`. |
| Repository | `data/repository`, `data/session`, `data/local` | Estado de la tienda con transacciones, sesión, y persistencia en Room y DataStore. |
| ViewModel | `ui/viewmodel` | Estado de cada pantalla con `StateFlow`; recibe los eventos de la interfaz y valida cada campo. |
| Screens y Composables | `ui/screen`, `ui/components`, `navigation` | Pantallas en Jetpack Compose con Material Design 3, componentes reutilizables y el grafo de navegación. |
| Recursos nativos | `nativo`, `ui/components/Camara.kt` | Notificaciones, texto a voz, ubicación y cámara. |

Las dependencias se crean una vez en `Contenedor.kt` (inyección manual).

## Funcionalidades implementadas

| Módulo | Pantallas | Formularios |
|---|---|---|
| Acceso | Iniciar sesión, Marcar turno, bloqueo por inactividad | F-01, F-02 |
| Operación | Panel de probadores, Detalle, Número sin celular, Cola del día, Alertas | |
| Incidencias | Incidencias, Reportar incidencia (con foto), Resumen | F-04 |
| Objetos encontrados | Objetos (búsqueda, entregar, derivar), Registrar objeto (con foto) | F-05 |
| Personal | Personal y cobertura, Mover persona, Solicitudes de refuerzo, Responder solicitud, Cambio de jefatura, Historial | F-06, F-07, F-08, F-09, F-10 |
| Configuración | Ajustes, Probadores y dispositivos, Configurar probador, Configuración del equipo | F-03, F-11, F-12 |
| Equipo del departamento | Pantalla de turnos con anuncio por voz | |
| Simulador | Señales manuales y modo automático | F-13 |

### Formularios y validación

Cada campo tiene ícono, muestra el error bajo el campo con un ícono de error y una animación, y el botón de envío queda deshabilitado mientras haya errores. La validación está en `domain/validation/Validaciones.kt` y en los ViewModel, separada de las pantallas.

### Persistencia local: un DAO por formulario

| Formulario | DAO | Tablas |
|---|---|---|
| F-01 Iniciar sesión | `UsuarioDao` | `usuarios` (hash y sal de la contraseña, nunca la contraseña) |
| F-02 Marcar turno | `TurnoDao` | `turnos` |
| F-03 Configuración del equipo | `EquipoDao` | `equipo` |
| F-04 Reportar incidencia | `IncidenciaDao` | `incidencias` |
| F-05 Registrar objeto encontrado | `ObjetoDao` | `objetos` |
| F-06 Mover persona | `MovimientoDao` | `movimientos` |
| F-07 y F-08 Solicitudes de refuerzo | `SolicitudDao` | `solicitudes` |
| F-09 Cambio de jefatura | `JefaturaDao` | `jefaturas`, `jefes_actuales` |
| F-10 Filtro del historial | Lee `TurnoDao`, `MovimientoDao`, `SolicitudDao` y `JefaturaDao` | |
| F-11 Configurar probador | `ProbadorDao` | `probadores` |
| F-12 Ajustes | `AjustesDao` | `parametros`, `minimos_personal` |
| F-13 Simulador | `SenalDao` | `senales` |

También se guardan la cola (`NumeroDao`), las ocupaciones (`OcupacionDao`) y el catálogo de prendas (`PrendaDao`). `PersistenciaOperacion` guarda cada cambio y recupera los datos al abrir la aplicación. DataStore guarda la sesión abierta, el modo automático y si ya se pidió el permiso de notificaciones.

### Recursos nativos

| Recurso | Dónde | Permiso |
|---|---|---|
| Cámara | Foto de evidencia en Reportar incidencia y Registrar objeto (`TakePicture` + `FileProvider`, vista previa con Coil y bordes redondeados) | `CAMERA`, con explicación previa y opción "Abrir configuración" |
| Notificaciones | Aviso de cada alerta nueva a las personas que corresponde | `POST_NOTIFICATIONS` (Android 13+), pedido al iniciar sesión |
| Ubicación | Identificar la tienda más cercana en la configuración del equipo | `ACCESS_COARSE_LOCATION`, con explicación previa; si se deniega, se elige de la lista |
| Texto a voz | Anuncio de cada llamado en la pantalla de turnos | No requiere permiso |

El estado de los permisos se ve en **Ajustes**.

### Animaciones

Errores de los formularios (`AnimatedVisibility`), color de las tarjetas de probador (`animateColorAsState`), barra de tiempo (`animateFloatAsState`), número entregado y llamado del televisor (`AnimatedContent`), foto (`Crossfade`), listas (`animateItem`), alertas del panel en el celular y mensaje de ajustes guardados.

### Diseño adaptable

`MainActivity` calcula la clase de tamaño de la ventana. En tablet horizontal el menú es fijo, en tablet vertical es una barra de íconos y en celular se abre con el botón de la barra superior. Las pantallas pasan de dos columnas a una según el ancho.

## Cómo probar la demostración

1. Entrar como **Ana** (jefa de tienda), abrir **Simulador** y activar el **Modo automático**.
2. En **Panel** se ven los probadores, la cola y las alertas cambiando en tiempo real.
3. Tocar un probador: **Reportar incidencia** con foto (abre el resumen) o **Registrar objeto encontrado**.
4. En **Personal**, mover a Diego o a Tomás de departamento. Hombre parte bajo su mínimo y genera una alerta de cobertura baja.
5. Entrar como **Matías** y en **Solicitudes** pedir un vendedor a Mujer; entrar como **Valentina** y aceptarla eligiendo a la vendedora.
6. Como Ana, **Cambio de jefatura**: elegir Mujer y a Matías; la app pide quién queda a cargo de Hombre.
7. **Historial** muestra marcas de turno, movimientos, solicitudes y jefaturas del día, con filtro por fecha y persona.
8. **Ajustes**: bajar el tiempo máximo a 1 minuto para ver alertas de tiempo excedido, o el bloqueo a 1 minuto para ver el bloqueo de la tablet.
9. **Equipo**: elegir "Equipo de departamento" para ver la pantalla de turnos con voz. Para volver, el ícono de engranaje pide las credenciales de Ana.
10. Cerrar y abrir la app: la sesión se recupera bloqueada y los datos siguen ahí.

## Pruebas unitarias

Clic derecho sobre `app/src/test/java` y **Run 'Tests in ...'**, o en la terminal:

```
./gradlew testDebugUnitTest
```

Son 49 pruebas de validaciones, reglas, motor, incidencias, objetos, personal, solicitudes, jefaturas, sesión, insignias del menú y recuperación de datos.

## Si Gradle no sincroniza

- **Versión de Kotlin incompatible en alguna biblioteca:** subir `kotlin` en `gradle/libs.versions.toml` a la que indique el mensaje y usar la versión de `ksp` que corresponda.
- **Falta el SDK 36:** instalarlo desde *Tools > SDK Manager*.
- **Al instalar sobre una versión anterior:** la base de datos cambió de versión y se recrea sola; los datos de prueba anteriores se borran.
