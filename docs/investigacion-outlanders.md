# Investigación: Monster Hunter Outlanders

Fecha de consulta: 13 de noviembre de 2026. Este documento identifica material y límites; no incorpora videos, datos de entrenamiento ni modelos, y no demuestra compatibilidad del prototipo con el juego.

## Fuentes del juego

- [Página oficial](https://monsterhunteroutlanders.com/en/): lanzamiento para Android/iOS, requisitos recomendados y compatibilidad con mando. No garantiza que todos los menús acepten mando.
- [EULA oficial, secciones 7(c), 7(h), 7(i) y 8](https://monsterhunteroutlanders.com/useragreement/en/index.html): el acuerdo enumera bots y programas de automatización entre las actividades prohibidas y contempla medidas frente a trampas, incluida la terminación del acceso. Las condiciones y su interpretación pueden variar según región; compruébelas antes de usar el prototipo.
- [Guía de controles de GameWith](https://gamewith.ai/monster-hunter-outlanders/en/articles/controller-support): referencia independiente de controles, incluyendo los ajustes de mando. Debe contrastarse con la versión y configuración reales.
- [Vídeo de una cacería en RedMagic 10S Pro](https://www.youtube.com/watch?v=r5yECsENERw): material visual de un dispositivo distinto; puede servir para estudiar interfaz y escenas, pero no demuestra el funcionamiento en 8S Pro ni proporciona datos de pulsaciones sincronizadas. Es un vídeo de una versión anterior al lanzamiento global.
- [Lista de reproducción de partidas](https://www.youtube.com/playlist?list=PLeryE-e3frBCRDKHSbCTC8QdJ_DvqfrE5): material visual de terceros de exploración y combate; revisar fecha, versión, licencia y permiso de uso antes de descargar o entrenar con él. No hay permiso concedido por enlazarlo.

## Investigación para modelos

- [Imitation Learning in Modern Video Games](https://github.com/microsoft/imitation_learning_in_modern_video_games): ejemplo de clonación de comportamiento en otros juegos, con código bajo MIT. Necesita demostraciones con acciones sincronizadas; no es una política entrenada para Outlanders.
- [Flujo de detección de monstruos en videojuegos](https://github.com/china2546/botchinas): ejemplo de extracción de fotogramas, etiquetado y entrenamiento; no acredita que incluya un detector entrenado para este juego. Revisar licencias de todas las dependencias y datos antes de reutilizar.
- [ONNX Runtime: desarrollo móvil](https://onnxruntime.ai/docs/tutorials/mobile/): describe CPU, XNNPACK, NNAPI y cuantización, y recomienda medir el rendimiento en el dispositivo concreto. La interfaz del modelo debe coincidir con la implementación; un detector genérico no es una política de acciones.

## Material aún no encontrado ni validado

No se ha identificado en esta búsqueda una política ONNX entrenada y verificable para Outlanders, ni un conjunto público reutilizable de partidas del juego con pulsaciones multitáctiles sincronizadas. Videos normales pueden orientar el etiquetado visual, pero normalmente no registran el inicio, duración y posición de cada dedo, pulsaciones simultáneas ni la acción prevista. Tampoco se verificó que los autores permitan su uso para entrenamiento.

Para una evaluación real harían falta, como mínimo, escenas pertinentes y sus licencias, datos temporales de entradas y acciones, una separación de entrenamiento y pruebas por sesión, así como evaluación en dispositivo y en la versión específica del juego. El contrato actual de la app solo admite un toque con coordenadas y confianza: no representa joysticks, cámara, multitáctil o gestos mantenidos. No se ha entrenado ni descargado un modelo.
