# Mis Finanzas - Gestor de Gastos Personales

Aplicación nativa para la plataforma Android desarrollada en **Java**, diseñada como parte de la entrega de la **Actividad Momento 1**.

**Estudiante:** Yudnaris Maria Prado Robles  
**Asignatura:** Desarrollo de Aplicaciones Móviles (Android Nativo)

---

## 📝 Descripción del Proyecto

**Mis Finanzas** es un gestor de gastos e ingresos personales que ayuda a los usuarios a llevar un registro diario de sus movimientos financieros de manera local y segura. La aplicación calcula automáticamente el balance general de forma dinámica y proporciona una interfaz limpia, profesional y moderna basada en los principios de **Material Design 3**.

### 🛠️ Características Principales
- **Persistencia Local (SQLite):** Implementación completa de operaciones CRUD (Crear, Leer, Actualizar, Eliminar) para el registro de transacciones.
- **Lista Dinámica (RecyclerView):** Visualización eficiente de transacciones con un adaptador personalizado que recicla las vistas utilizando el patrón `ViewHolder`.
- **Indicadores Visuales Dinámicos:** Cada transacción se distingue mediante colores e íconos intuitivos: verde para **Ingresos** y rojo para **Gastos**.
- **Balance General en Tiempo Real:** Cálculo automático del balance consolidado, total de ingresos y total de gastos en la parte superior de la pantalla.
- **Filtro de Búsqueda:** Buscador integrado que permite filtrar la lista de transacciones por concepto o categoría en tiempo real.
- **Campos Enriquecidos:** Cada movimiento incluye concepto, monto, tipo de transacción, fecha (con selector de calendario) y categoría.

---

## 📂 Estructura del Código

El proyecto sigue una separación clara de responsabilidades:
- **`model`:** Contiene la clase `Transaction.java` que representa la entidad financiera.
- **`database`:** Contiene `DatabaseHelper.java` que gestiona la creación de la base de datos SQLite y ejecuta las consultas CRUD.
- **`adapter`:** Contiene `TransactionAdapter.java` que mapea la lista de transacciones del SQLite al `RecyclerView`.
- **`ui/activities`:** Lógica principal de las pantallas (`MainActivity.java` y `TransactionFormActivity.java`).
- **`res/layout`:** Archivos XML de diseño de las pantallas y de los ítems de la lista.

---

## 🚀 Cómo Probar y Ejecutar la Aplicación

### Opción 1: Abrir en Android Studio (Recomendado para el Profesor)
1. Clona este repositorio o descarga el código fuente.
2. Abre **Android Studio**.
3. Selecciona **Open** (Abrir) y elige la carpeta raíz del proyecto.
4. Espera a que Gradle sincronice las dependencias del proyecto.
5. Conecta un dispositivo físico Android (con depuración USB activa) o inicia un emulador.
6. Presiona el botón verde de **Run** (Ejecutar) en Android Studio.

### Opción 2: Compilación Manual a APK
Si prefieres generar el archivo APK directamente desde la línea de comandos, puedes ejecutar el script de Gradle Wrapper incluido en la raíz del proyecto.

En Windows (PowerShell / CMD):
```bash
./gradlew.bat assembleDebug
```
El archivo APK generado se ubicará en la siguiente ruta:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📸 Capturas de Pantalla (Screenshots)

A continuación se muestran las capturas de la aplicación en funcionamiento:

| Pantalla Principal (Historial y Balance) | Formulario de Registro (Creación/Edición) |
| :---: | :---: |
| ![Historial de Transacciones](docs/screenshots/lista_transacciones.png) | ![Registro de Transacción](docs/screenshots/formulario_registro.png) |

*(Nota: Las imágenes de las capturas se guardan en la carpeta `docs/screenshots/` del repositorio).*
