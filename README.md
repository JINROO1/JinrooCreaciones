# Jinroo Agent — prototipo Android

Prototipo local de captura con consentimiento, inferencia ONNX y toque mediante `su -c input tap`. **No es un APK terminado ni una solución indetectable**: falta aportar y validar un modelo entrenado, no se han medido latencia ni compatibilidad en RedMagic 8S Pro y no se ha compilado en este entorno (no hay JDK ni Android SDK). Úselo únicamente donde tenga autorización; la automatización puede incumplir las reglas del juego.

## Estado y limitaciones importantes

- El proyecto no incluye pesos ni entrenamiento: un modelo visual genérico no sabe jugar por sí solo. Sin `model.onnx`, el servicio se detiene mostrando un error. Tampoco se ha verificado un modelo de ejemplo.
- Contrato exacto: una única entrada `float32` `[1,3,224,224]` RGB NCHW en rango 0–1 y una única salida `float32` `[1,3]` = x normalizada, y normalizada, confianza. Se ejecuta un toque si ambas coordenadas están entre 0 y 1 y la confianza es al menos 0,8. El modelo debe entrenarse para la aplicación concreta y usar ese contrato; cuantización interna puede usarse siempre que la interfaz siga siendo float32. La confianza no garantiza que un toque sea correcto.
- MediaProjection requiere consentimiento visible en cada sesión. No se eluden `FLAG_SECURE` ni otras protecciones; las pantallas protegidas pueden aparecer negras. Se captura un frame reciente con ImageReader, pero **no se garantiza 30–60 FPS**; la inferencia está limitada intencionadamente a un ciclo cada 500 ms más el tiempo de ejecución.
- Root se solicita mediante el ejecutable `su` convencional y su gestor (KernelSU), no mediante una hipotética API JNI de KernelSU. JNI solo informa del UID efectivo del propio proceso; no prueba la disponibilidad de `su` (la comprobación real ejecuta `su -c id`). La inyección usa `input tap`, no eventos físicos; no incluye JNI InputManager, `sendevent`, `uinput`, presión, deslizamientos, curvas ni evasión de detección. No oculta paquetes, root ni rastros. `setup.sh` es solo diagnóstico, no un módulo KernelSU.
- No hay selector ni comprobación del juego en primer plano: las acciones pueden afectar a **cualquier aplicación visible**. Pruebe con un entorno de prueba; detenga el servicio antes de salir. La captura depende de la pantalla y puede detenerse al apagarse, cambiar orientación o revocarse. No se garantiza funcionamiento con pantalla apagada ni recuperación automática.
- La ejecución usa el proveedor CPU predeterminado de ONNX Runtime, no NNAPI/XNNPACK configurados explícitamente. Consumo de memoria, tamaño y rendimiento dependen del modelo aportado.

## Requisitos

Android Studio, JDK 17, SDK Platform 35, Android SDK Build-Tools, NDK y CMake 3.22.1 instalados desde SDK Manager. Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 1.9.24, ONNX Runtime Android 1.20.0. El APK contiene solamente bibliotecas nativas `arm64-v8a`; mínimo Android 8 (API 26), objetivo API 35. El dispositivo debe tener KernelSU y un `su` operativo, y el usuario debe aprobar la solicitud de root. El build necesita acceso a Google Maven, Maven Central y al servidor de distribuciones de Gradle.

## Compilar e instalar

1. Coloque un modelo compatible como `app/src/main/assets/model.onnx` (el archivo está excluido de Git). Revise el origen y la licencia de los pesos y verifique el contrato antes de utilizarlo.
2. Configure `ANDROID_HOME` o cree `local.properties` con `sdk.dir=/ruta/al/Android/Sdk`. Desde la raíz ejecute `./gradlew assembleDebug`. La salida es `app/build/outputs/apk/debug/app-debug.apk`, firmada con la clave de depuración generada localmente.
3. Con el dispositivo autorizado para depuración USB, ejecute `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Abra la app, pulse **Comprobar acceso root** y apruebe el acceso en KernelSU Manager. Pulse **Iniciar captura y agente**, conceda la captura y, si se solicita, las notificaciones. Compruebe el estado en la app y pulse **Detener** para terminar. La sesión de captura debe autorizarse de nuevo tras reiniciar el servicio.

Para release, ejecute `./gradlew assembleRelease`. La configuración no contiene credenciales y genera un APK **sin firmar** en `app/build/outputs/apk/release/`. Fírmelo después de alinear, con herramientas del SDK (adapte la versión de build-tools):

```sh
keytool -genkeypair -v -keystore release.jks -alias jinroo -keyalg RSA -keysize 3072 -validity 10000
$ANDROID_HOME/build-tools/35.0.0/zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk app-release-aligned.apk
$ANDROID_HOME/build-tools/35.0.0/apksigner sign --ks release.jks --ks-key-alias jinroo --out app-release-signed.apk app-release-aligned.apk
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app-release-signed.apk
adb install -r app-release-signed.apk
```

`keytool` y `apksigner` solicitarán las contraseñas interactivamente. Guarde la clave fuera del repositorio y haga una copia segura; un APK firmado con otra clave no actualizará el de depuración. Desinstalar antes puede borrar datos. No se entrega ni se comparte ninguna clave de firma.

## Comprobación automatizada

El flujo `.github/workflows/android.yml` instala el SDK, ejecuta `testDebugUnitTest` y compila un APK de depuración en GitHub Actions. El artefacto se llama `jinroo-prototype-debug-no-model`: no contiene modelo, porque este está excluido de Git, y **no es un agente funcional para juegos**. El flujo solo se ejecutará cuando se publique la rama o se lance manualmente; todavía no ha sido ejecutado ni verificado. Las pruebas unitarias cubren la validación y conversión de coordenadas, no el comportamiento del dispositivo, la captura ni la inferencia real.
