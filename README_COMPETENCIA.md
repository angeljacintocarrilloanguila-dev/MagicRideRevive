# Magic Ride Reborn - versión competencia

Esta versión conserva la esencia del juego recordado: bruja en escoba, desplazamiento lateral, bosque nocturno, luna, estrellas, obstáculos, energía, puntaje por distancia y ranking tipo amigos.

## Competencia incluida
- Modo "Competir con amiga".
- Barra de progreso Julio vs Amiga.
- Bruja fantasma de la amiga en pantalla.
- Ranking de sala con código `PASEO2026`.
- Guarda los mejores puntajes localmente.

## Para hacerlo online real
El código visual ya está preparado. Para que dos celulares se sincronicen de verdad se debe conectar un backend como Firebase Realtime Database, Supabase o un servidor propio. La lógica necesaria es:

1. Crear sala por código.
2. Guardar nombre del jugador y distancia máxima.
3. Consultar cada pocos segundos los mejores puntajes de esa sala.
4. Dibujar la bruja fantasma y el ranking con esos datos.

## APK
Este proyecto se puede subir a GitHub y compilar con GitHub Actions desde la pestaña Actions. El APK aparece como artefacto `MagicRideRevive-debug-apk`.
