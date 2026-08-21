# 📄 Documentación Técnica y Académica — Momento 2

**Aplicación Móvil:** Mis Finanzas - Gestor de Gastos Personales  
**Estudiante:** Yudnaris Maria Prado Robles  
**Asignatura:** Desarrollo de Aplicaciones Móviles (Android Nativo)  
**Plataforma:** Android (Java Nativo - API Level 24+ / Java 17)  
**Persistencia Cloud:** Google Cloud Firebase Firestore  
**Fecha de Entrega:** 20 de Agosto de 2026  

---

## 📑 Tabla de Contenidos
1. [Resumen Ejecutivo](#1-resumen-ejecutivo)
2. [Objetivos del Momento 2](#2-objetivos-del-momento-2)
3. [Arquitectura y Stack Tecnológico Cloud](#3-arquitectura-y-stack-tecnológico-cloud)
4. [Pilares de Implementación](#4-pilares-de-implementación)
   - [Pilar 1: Integración NoSQL y Modelo POJO](#pilar-1-integración-nosql-y-modelo-pojo)
   - [Pilar 2: Gestor Firestore Asíncrono (FirestoreManager)](#pilar-2-gestor-firestore-asíncrono-firestoremanager)
   - [Pilar 3: Reactividad y Sincronización en Tiempo Real](#pilar-3-reactividad-y-sincronización-en-tiempo-real)
   - [Pilar 4: UX, Validaciones y Control de Concurrencia](#pilar-4-ux-validaciones-y-control-de-concurrencia)
   - [Pilar 5: Gestión Eficiente de Memoria y Ciclo de Vida](#pilar-5-gestión-eficiente-de-memoria-y-ciclo-de-vida)
5. [Comparativa: Momento 1 (SQLite) vs. Momento 2 (Firestore Cloud)](#5-comparativa-momento-1-sqlite-vs-momento-2-firestore-cloud)
6. [Guía de Configuración e Integración Firebase](#6-guía-de-configuración-e-integración-firebase)
7. [Conclusiones](#7-conclusiones)

---

## 1. Resumen Ejecutivo

En la fase de **Momento 2**, la aplicación **Mis Finanzas** realiza una evolución cualitativa fundamental desde un modelo monolítico offline basado en **SQLite local** a una arquitectura distribuida en la nube sustentada por **Google Cloud Firebase Firestore**. 

Esta transformación permite que las operaciones sobre ingresos y gastos se sincronicen de manera **reactiva e instantánea** entre múltiples dispositivos. Al mismo tiempo, se conservan las bases de diseño de **Material Design 3**, validaciones robustas de entrada con `TextWatcher` y optimizaciones de memoria en el ciclo de vida de los componentes nativos.

---

## 2. Objetivos del Momento 2

- **Evolución Cloud NoSQL:** Migrar la persistencia relacional a una estructura orientada a documentos en Firestore (Colección `transacciones`).
- **Sincronización Multi-dispositivo Reactiva:** Implementar escuchadores en tiempo real (`SnapshotListener`) que reflejen los cambios sin refrescos manuales.
- **Robustez en Operaciones Asíncronas:** Implementar gestión de estados de carga (`ProgressBar`), deshabilitación preventiva de botones para evitar duplicidad de envíos y manejo seguro de callbacks de éxito/error.
- **Prevención de Fugas de Memoria (Memory Leaks):** Asegurar la liberación de recursos en segundo plano desregistrando los listeners de Firestore en `onDestroy()`.
- **Mantenimiento del Estándar UI/UX:** Respetar la identidad gráfica (verde para Ingresos, rojo para Gastos), tarjetas `CardView`, selectores `DatePickerDialog` y formateo dinámico de monedas.

---

## 3. Arquitectura y Stack Tecnológico Cloud

```
+-----------------------------------------------------------------------+
|                              VISTA (UI)                               |
|   MainActivity.java                   TransactionFormActivity.java    |
|   (Dashboard, Lista Reactiva)          (Formulario Crear/Editar)      |
+-----------------------------------+-----------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                          CONTROLADOR / POJO                           |
|                         Transaction.java                              |
|           - @DocumentId String id                                     |
|           - concepto, monto, tipo, fecha, categoria                   |
+-----------------------------------+-----------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                           CAPA DE DATOS                               |
|                       FirestoreManager.java                           |
|   - addSnapshotListener(orderBy "fecha" DESC)                         |
|   - addTransaction(), getTransactionById(), update(), delete()        |
+-----------------------------------+-----------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                         CLOUD FIREBASE NOSQL                          |
|                 Colección Firestore: "transacciones"                  |
+-----------------------------------------------------------------------+
```

### Tecnologías Aplicadas:
- **Android Native (Java 17 / API Level 24+ - Android 7.0 a Android 14)**
- **Firebase Firestore SDK (`com.google.firebase:firebase-firestore`)**
- **Google Play Services Base (`com.google.gms:google-services`)**
- **Material Components Android (Material Design 3)**

---

## 4. Pilares de Implementación

### Pilar 1: Integración NoSQL y Modelo POJO
La clase [`Transaction.java`](file:///d:/Mis%20Documentos/APK/app/src/main/java/com/misfinanzas/gestorgastos/model/Transaction.java) adapta la entidad POJO para cumplir con los requerimientos de serialización NoSQL de Firestore:
- Decoración del campo `id` con `@DocumentId` para mapear automáticamente el identificador único del documento en Firestore.
- Inclusión del constructor por defecto `public Transaction() {}` necesario para la conversión mediante `doc.toObject(Transaction.class)`.

### Pilar 2: Gestor Firestore Asíncrono (FirestoreManager)
El componente [`FirestoreManager.java`](file:///d:/Mis%20Documentos/APK/app/src/main/java/com/misfinanzas/gestorgastos/database/FirestoreManager.java) encapsula las interacciones con la base de datos distribuida:
- **Colección:** `transacciones`.
- **Ordenamiento:** `orderBy("fecha", Query.Direction.DESCENDING)`.
- **Métodos Asíncronos:** `addTransaction`, `getTransactionById`, `updateTransaction` y `deleteTransaction` aceptando `OnSuccessListener` y `OnFailureListener`.

### Pilar 3: Reactividad y Sincronización en Tiempo Real
En [`MainActivity.java`](file:///d:/Mis%20Documentos/APK/app/src/main/java/com/misfinanzas/gestorgastos/MainActivity.java):
- Se suscribe un `SnapshotListener` mediante `firestoreManager.listenToTransactions(...)`.
- Ante cualquier alta, baja o modificación en la nube, el listado local se actualiza automáticamente.
- Los totales del Dashboard (`tvTotalBalance`, `tvTotalIncome`, `tvTotalExpense`) se recalculan dinámicamente en tiempo real.

### Pilar 4: UX, Validaciones y Control de Concurrencia
En [`TransactionFormActivity.java`](file:///d:/Mis%20Documentos/APK/app/src/main/java/com/misfinanzas/gestorgastos/TransactionFormActivity.java):
- **Validaciones en tiempo real:** Uso de `TextWatcher` con `TextInputLayout.setError(...)` para campos obligatorios y montos numéricos válidos.
- **Prevención de doble toque:** Al hacer clic en "Guardar", se inhabilita el botón (`btnSave.setEnabled(false)`) y se muestra un `ProgressBar` mientras la red responde.
- **Selector de Fechas:** `DatePickerDialog` que garantiza el almacenamiento bajo la norma `YYYY-MM-DD`.

### Pilar 5: Gestión Eficiente de Memoria y Ciclo de Vida
- Al iniciar la actividad principal, se guarda la referencia `ListenerRegistration`.
- En el método `onDestroy()` de `MainActivity.java`, se invoca `firestoreListenerRegistration.remove()`, deteniendo la transmisión de datos cuando la Activity se destruye.

---

## 5. Comparativa: Momento 1 (SQLite) vs. Momento 2 (Firestore Cloud)

| Característica | Momento 1 (SQLite Local) | Momento 2 (Firebase Firestore) |
|----------------|--------------------------|--------------------------------|
| **Arquitectura de Datos** | Relacional (Tablas / Columnas) | NoSQL (Colección / Documentos) |
| **Sincronización** | Local (Un solo dispositivo) | Nube (Multi-dispositivo en tiempo real) |
| **Actualización UI** | Consultas manuales post-CRUD | `SnapshotListener` en tiempo real |
| **Manejo de Errores** | Excepciones SQL síncronas | Callbacks asíncronos (`Success/Failure`) |
| **Conexión a Red** | Funciona 100% offline nativo | Online con soporte de caché Firestore |

---

## 6. Guía de Configuración e Integración Firebase

Para habilitar el funcionamiento de Momento 2 en un entorno nuevo de desarrollo:

1. **Registrar la aplicación** en la consola de Firebase con el nombre de paquete: `com.misfinanzas.gestorgastos`.
2. **Descargar y ubicar** el archivo `google-services.json` dentro del directorio `app/`.
3. **Verificar el plugin de Gradle** en `app/build.gradle`:
   ```groovy
   apply plugin: 'com.google.gms.google-services'
   ```
4. **Reglas de seguridad recomendadas (Firestore Rules):**
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /transacciones/{document} {
         allow read, write: if true; // Ajustar según requerimientos de autenticación
       }
     }
   }
   ```

---

## 7. Conclusiones

La entrega de **Momento 2** consolida la aplicación **Mis Finanzas** como una solución Android moderna, combinando un diseño limpio con el rendimiento y reactividad de la infraestructura Serverless en la nube de Google Cloud Firebase.
