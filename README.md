# Mis Finanzas - Gestor de Gastos Personales (Momento 2)

Aplicación nativa para la plataforma Android desarrollada en **Java**, diseñada y evolucionada para la **Actividad Momento 2**.

**Estudiante:** Yudnaris Maria Prado Robles  
**Asignatura:** Desarrollo de Aplicaciones Móviles (Android Nativo)  
**Plataforma:** Android 7.0+ (API level 24+) | Java 17 | Firebase Firestore NoSQL  

---

## 📝 Descripción del Proyecto (Evolución a Momento 2)

En el **Momento 1**, los datos financieros estaban almacenados localmente en SQLite. En esta **Actividad Momento 2**, la aplicación da un salto de arquitectura hacia la nube utilizando **Google Cloud Firebase Firestore**, permitiendo **sincronización multi-dispositivo en tiempo real**.

---

## 🎯 Cumplimiento de los 4 Pilares Técnicos del Momento 2

### 1. 🔄 Sincronización en Tiempo Real (Real-time DB)
- El `RecyclerView` principal (`MainActivity.java`) está conectado a Firestore a través de un `SnapshotListener` (`addSnapshotListener`).
- Cualquier inserción, edición o eliminación realizada en un teléfono o desde la consola de Firebase se refleja **instantáneamente** en la pantalla sin necesidad de recargar manualmente.

### 2. ⚡ CRUD Completo en NoSQL (Cloud Firestore)
- **Create:** El formulario guarda nuevos documentos dentro de la colección `transacciones` en Cloud Firestore.
- **Read:** La lista mapea automáticamente cada documento a la clase POJO Java (`Transaction.java`) utilizando su `Document ID` (`@DocumentId`).
- **Update:** Permite modificar campos de un documento existente apuntando a su `Document ID` único.
- **Delete:** Permite la eliminación permanente de un documento apuntando a su `Document ID`.

### 3. 🎨 Diseño de Interfaz y Experiencia de Usuario (UI/UX)
- Listado tipo **CardView (Tarjetas)** con sombras, bordes redondeados (12dp) y distribución clara de espacio.
- Indicadores dinámicos con Vector Assets (verde para **Ingreso**, rojo para **Gasto**).
- Formularios construidos con `TextInputLayout` de **Material Design 3**, soportando mensajes de error flotantes y prefijos de moneda.

### 4. 🛡️ Validaciones y Rendimiento (Calidad de Software)
- **Validaciones en Tiempo Real (`TextWatcher`):**
  - Advertencia inmediata si el concepto tiene menos de 3 caracteres.
  - Advertencia inmediata si el monto ingresado es `$0` o negativo.
- **Prevención de Doble Envío:** Deshabilitación de botones de acción (`btnSave`, `btnDelete`) e integración de indicadores de carga (`ProgressBar`) mientras se procesa la petición en la nube.
- **Liberación de Memoria:** En `MainActivity.onDestroy()`, se invoca `listenerRegistration.remove()` para liberar la conexión de Firebase y evitar fugas de memoria (*memory leaks*).

---

## 📂 Estructura del Código

```
app/src/main/java/com/misfinanzas/gestorgastos/
├── adapter/
│   └── TransactionAdapter.java     # Adaptador de RecyclerView para Firestore POJO
├── database/
│   ├── DatabaseHelper.java         # Persistencia SQLite (Momento 1)
│   └── FirestoreManager.java       # Administrador CRUD y tiempo real de Firestore (Momento 2)
├── model/
│   └── Transaction.java            # Entidad POJO con soporte para Document ID (@DocumentId)
├── MainActivity.java               # Dashboard / Lista con SnapshotListener / Balance
└── TransactionFormActivity.java    # Formulario Crear / Editar / Eliminar con TextWatcher
```

---

## ⚙️ Guía de Configuración e Integración con Firebase

Para conectar la aplicación con tu propio proyecto de Firebase Cloud Firestore:

1. Ve a [Firebase Console](https://console.firebase.google.com/) y crea un nuevo proyecto (ej. `MisFinanzasApp`).
2. Agrega una **App Android** registrando el nombre de paquete:
   `com.misfinanzas.gestorgastos`
3. Descarga el archivo `google-services.json` generado por Firebase.
4. Copia el archivo `google-services.json` dentro de la carpeta `/app/` del proyecto.
5. En la consola de Firebase, ve a **Firestore Database** -> **Crear base de datos** (en modo prueba o reglas con lectura/escritura).
6. ¡Ejecuta la app en 2 dispositivos o emuladores para ver la sincronización en tiempo real!

---

## 🚀 Compilación Manual

Para compilar el archivo APK desde la consola en Windows:

```bash
./gradlew.bat assembleDebug
```
El binario compilado se ubica en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📸 Capturas de Pantalla (Screenshots)

| Pantalla Principal (Historial y Balance) | Formulario de Registro (Creación/Edición) |
| :---: | :---: |
| ![Historial de Transacciones](docs/screenshots/lista_transacciones.jpg) | ![Registro de Transacción](docs/screenshots/formulario_registro.jpg) |
