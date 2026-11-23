# 🏝️ Modo vacaciones

App Android que quita las notificaciones de las apps que elijas desde que la activas hasta que la desactivas (o hasta la fecha/hora que pongas).

## Qué hace

- **Selector de apps**: lista todas las apps instaladas (con icono y buscador) para marcar las que no quieres que te molesten.
- **Activar / desactivar** con un botón o desde el mosaico **«Vacaciones»** de ajustes rápidos.
- **Fecha y hora de fin**: se apaga sola en el momento que elijas.
- **Resumen** al terminar: cuántas notificaciones se quitaron y de qué apps.
- Sobrevive a reinicios del móvil.

Cómo funciona: usa el permiso de *Acceso a notificaciones* (`NotificationListenerService`). Cuando llega una notificación de una app marcada, la quita al momento. Al activarlo también limpia las que ya estaban en la barra. Las notificaciones fijas (música, llamadas en curso…) no se pueden quitar.

## Compilar el APK con GitHub Actions

1. Sube esta carpeta a un repo de GitHub (el workflow está en `.github/workflows/build-apk.yml`).
2. Cada push a `main`/`master` compila el APK → pestaña **Actions** → la ejecución → artefacto **ModoVacaciones-apk**.
3. También se puede lanzar a mano: Actions → *Compilar APK* → **Run workflow**.
4. Si subes un tag `v*` (p. ej. `git tag v1.0 && git push --tags`) además crea una **Release** con `ModoVacaciones.apk`, más cómoda de descargar desde el móvil.

El APK va firmado con `app/keystore/modovacaciones.p12` (incluido en el repo) para que cada versión nueva se instale encima de la anterior sin desinstalar.

## Primera vez en el móvil

1. Instala el APK.
2. Abre la app → **Dar acceso a notificaciones** y activa *Modo vacaciones*.
   - En Android 13 o superior, si sale en gris / «Ajuste restringido»: *Info de la app* → **⋮** → **Permitir ajustes restringidos**, y vuelve a intentarlo.
3. **Elegir apps** → marca las que quieras silenciar.
4. (Opcional) **Elegir fecha y hora** para que se apague solo. Si la app te lo sugiere, concede *Alarmas y recordatorios* para que el aviso de fin sea puntual.
5. **Activar modo vacaciones**.

## Detalles técnicos

- Java puro, sin dependencias (solo el SDK de Android). minSdk 26 (Android 8), targetSdk 35.
- AGP 8.7.3, Gradle 8.9 (lo instala el workflow; no hace falta wrapper).
- Paquete: `com.dani.modovacaciones`.
