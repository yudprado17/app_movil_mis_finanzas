# Mis Finanzas - Gestor de Gastos Personales

Aplicación nativa para la plataforma Android desarrollada en **Java**, diseñada para la gestión integral de finanzas personales. Este repositorio contiene la documentación completa y el código fuente correspondiente a las entregas de **Momento 1** y **Momento 2**.

**Estudiante:** Yudnaris Maria Prado Robles  
**Asignatura:** Desarrollo de Aplicaciones Móviles (Android Nativo)  
**Plataforma:** Android 7.0+ (API level 24+) | Java 17 | SQLite Local & Firebase Cloud Firestore  

---

## 🏛️ Momento 1: Diseño y Arquitectura Inicial (SQLite Local)

En el **Momento 1**, se concibió y diseñó la arquitectura base del Gestor de Gastos Personales funcionando 100% de manera local y offline mediante **SQLite**:

### 1. 🗄️ Persistencia de Datos con SQLite
- Implementación de `DatabaseHelper.java` extendiendo `SQLiteOpenHelper`.
- **Esquema Relacional (`transacciones`):**
  - `id`: INTEGER PRIMARY KEY AUTOINCREMENT
  - `concepto`: TEXT NOT NULL
  - `monto`: REAL NOT NULL
  - `tipo`: TEXT NOT NULL (`Ingreso` / `Gasto`)
  - `categoria`: TEXT NOT NULL
  - `fecha`: TEXT NOT NULL (`YYYY-MM-DD`)
- Operaciones CRUD nativas mediante `SQLiteDatabase` (`insert`, `query`, `update`, `delete`).
- Filtros y búsquedas por concepto y categoría usando cláusulas `LIKE` relacionales.

### 2. 🎨 Diseño de Interfaz y UI/UX (Momento 1)
- **Patrón MVC (Model-View-Controller):** Separación limpia entre modelo (`Transaction.java`), vistas XML (`activity_main.xml`, `activity_transaction_form.xml`, `item_transaction.xml`) y controladores (`MainActivity`, `TransactionFormActivity`).
- **Material Design 3:** Uso de tarjetas `CardView` con bordes redondeados (12dp), sombreado y elevación.
- **Identificación Visual Instantánea:** 
  - 🟢 Color verde e ícono distintivo para transacciones de tipo **Ingreso**.
  - 🔴 Color rojo e ícono distintivo para transacciones de tipo **Gasto**.
- **Componentes de Entrada:**
  - `DatePickerDialog` integrado para la selección fácil e intuitiva de fechas sin errores de formato.
  - `TextInputLayout` de Material Components con bordes delgados y mensajes explicativos.

---

## ☁️ Momento 2: Evolución a la Nube (Google Cloud Firebase Firestore)

En el **Momento 2**, la aplicación evolucionó hacia una solución Cloud NoSQL con sincronización en tiempo real sin perder la solidez estética y de diseño construida en el Momento 1. Para ver el informe técnico y académico completo, consulta [`docs/DOCUMENTACION_MOMENTO_2.md`](docs/DOCUMENTACION_MOMENTO_2.md).

### 1. 🔄 Sincronización Multi-dispositivo en Tiempo Real
- Conexión del `RecyclerView` principal ([`MainActivity.java`](app/src/main/java/com/misfinanzas/gestorgastos/MainActivity.java)) mediante `SnapshotListener` (`addSnapshotListener`) a la colección `transacciones`.
- Reflejo instantáneo de cambios en múltiples dispositivos sin necesidad de refrescar la pantalla.

### 2. ⚡ CRUD NoSQL (Cloud Firestore)
- Mapeo POJO con [`Transaction.java`](app/src/main/java/com/misfinanzas/gestorgastos/model/Transaction.java) integrando `@DocumentId` para identificación única en la nube.
- Operaciones `add`, `get`, `update` y `delete` asíncronas optimizadas en [`FirestoreManager.java`](app/src/main/java/com/misfinanzas/gestorgastos/database/FirestoreManager.java).

### 3. 🛡️ Validaciones UI y Calidad de Software
- Validaciones inmediatas mediante `TextWatcher` en los formularios ([`TransactionFormActivity.java`](app/src/main/java/com/misfinanzas/gestorgastos/TransactionFormActivity.java)).
- Prevención de doble envío (`btnSave.setEnabled(false)`) y loaders de progreso (`ProgressBar`).
- Gestión eficiente de memoria liberando `ListenerRegistration` en `onDestroy()`.

---

## 📂 Estructura del Código

```
app/src/main/java/com/misfinanzas/gestorgastos/
├── adapter/
│   └── TransactionAdapter.java     # Adaptador de RecyclerView para transacciones (POJO / ViewHolder)
├── database/
│   ├── DatabaseHelper.java         # Persistencia SQLite (Diseño Momento 1)
│   └── FirestoreManager.java       # Operaciones CRUD y Tiempo Real Firestore (Momento 2)
├── model/
│   └── Transaction.java            # Entidad POJO modelo de transacción (soporta ID SQLite y Cloud DocumentId)
├── MainActivity.java               # Dashboard, Lista principal, Balance global y Filtros
└── TransactionFormActivity.java    # Formulario para Crear, Editar y Eliminar transacciones
```

---

## ⚙️ Guía de Configuración e Integración

1. Clonar el repositorio: `git clone https://github.com/yudprado17/app_movil_mis_finanzas.git`
2. Abrir el proyecto en **Android Studio (Ladybug / Jellyfish)**.
3. Para probar la integración Cloud NoSQL (Momento 2), colocar el archivo `google-services.json` en la carpeta `app/`.

---

## 🚀 Compilación del Proyecto

Para compilar la aplicación APK desde la consola (Windows):

```bash
./gradlew.bat assembleDebug
```
El instalador APK resultante se genera en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📸 Capturas de Pantalla (Diseño UI Momento 1 & 2)

| Pantalla Principal (Historial y Balance) | Formulario de Registro (Creación/Edición) |
| :---: | :---: |
| ![Historial de Transacciones](docs/screenshots/lista_transacciones.jpg) | ![Registro de Transacción](docs/screenshots/formulario_registro.jpg) |

