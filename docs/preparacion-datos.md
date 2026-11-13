# Preparación local de datos de acciones

`tools/prepare_dataset.py` prepara ejemplos a partir de un vídeo **propio o cuyo uso esté autorizado** y una lista de acciones reales, sincronizadas con el vídeo. No descarga vídeos, no detecta acciones automáticamente, no registra toques y no entrena un modelo. Una grabación de pantalla por sí sola generalmente **no identifica las pulsaciones**: registrar o anotar esas acciones requiere una fuente fiable y permiso para usarla. Se desconoce si el juego permite el uso de tales herramientas; consulte su EULA. No use automatización contra el juego sin autorización del proveedor.

## Requisitos

Python 3.9 o posterior y FFmpeg en el `PATH`. Los archivos de vídeo, etiquetas y capturas podrían contener datos privados, chat o identificadores: revise y elimine información sensible antes de compartir. El directorio `data/` está excluido de Git. No confíe en la declaración de derechos como prueba legal: es información suministrada por quien ejecuta la herramienta.

## Formato de anotaciones

Archivo UTF-8 JSON Lines, un objeto por línea, en orden de `time_s` estrictamente creciente. Los tiempos son segundos respecto al principio del vídeo y representan el momento del ejemplo, que debe sincronizarse manualmente o mediante una fuente de entrada confiable. Coordenadas normalizadas de 0 a 1 respecto a la imagen capturada, origen arriba a la izquierda. Ejemplo ilustrativo **no procedente del juego**:

```jsonl
{"time_s":0.5,"action":"tap","start":[0.8,0.6]}
{"time_s":1.0,"action":"swipe","start":[0.2,0.8],"end":[0.5,0.8],"duration_ms":350}
{"time_s":2.0,"action":"hold","start":[0.15,0.8],"duration_ms":800}
{"time_s":3.0,"action":"wait","duration_ms":500}
```

Solo se admiten esas cuatro clases y campos. El formato **no admite acciones simultáneas**, trayectorias intermedias, presión ni etiquetado multitoque; se rechazan campos adicionales en vez de descartarlos silenciosamente. No es suficiente para reproducir controles complejos. El timestamp selecciona el primer fotograma que FFmpeg puede extraer tras la posición solicitada; no garantiza que preceda al toque, ni sincronización de fotograma exacta con vídeos de tasa variable. Valide el desfase y el contenido de los fotogramas manualmente antes de entrenar.

## Ejecución

```sh
python3 tools/prepare_dataset.py \
  --video data/mi-sesion.mp4 \
  --annotations data/mi-sesion.jsonl \
  --output data/ejemplos-sesion-01 \
  --session sesion-01 \
  --rights 'Grabación propia y etiquetas propias; uso autorizado'
```

El resultado contiene `frames/`, `samples.jsonl` y `metadata.json` con las huellas SHA-256, origen declarado y partición. Se asigna una partición determinista basada en el hash del vídeo; con pocas sesiones podrían faltar particiones. `--split train|val|test` permite designar la partición, pero es responsabilidad del usuario mantener todas las grabaciones relacionadas (misma cacería, originales y recortes, repetición o duplicados transcodificados) dentro de la misma partición; el hash no reconoce vídeos equivalentes. Reserve sesiones independientes para validación y prueba. Se rechaza un destino existente al inicio y se eliminan los temporales si la extracción falla. No ejecute procesos concurrentes sobre el mismo destino.

La salida es un **formato de intercambio**, no un modelo ONNX ni una entrada directa al prototipo Android. La preparación, anotación y evaluación de datos reales, el entrenamiento y la adaptación del motor de inferencia siguen pendientes.

## Pruebas

```sh
python3 -m unittest discover -s tests -v
```

Las pruebas unitarias simulan FFmpeg; se realizó además una prueba de extracción con un vídeo sintético y FFmpeg real en el entorno de desarrollo. No se ha probado con material del juego.
