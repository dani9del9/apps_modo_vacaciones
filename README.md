# 🏝️ Modo vacaciones

App Android que quita las notificaciones de las apps que elijas desde que la activas hasta que la desactivas (o hasta la fecha/hora que pongas).

## Qué hace

- **Grupos de apps**: crea grupos con nombre e icono (más de 1.400 emojis con buscador, el mismo catálogo que YTBox) (p. ej. «💬 Redes sociales», «💼 Trabajo») y añade las apps de cada uno desde una lista con buscador. En la pantalla principal marcas con un check qué grupos se silencian.
- **Activar / desactivar** con un botón o desde el mosaico **«Vacaciones»** de ajustes rápidos.
- **Fecha y hora de fin**: se apaga sola en el momento que elijas.
- **Mientras estabas fuera**: al volver puedes ver todas las notificaciones quitadas (agrupadas por app, con su contenido). Se guardan solo en el móvil.
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
3. **Nuevo grupo** → ponle nombre, icono y sus apps. Marca los grupos que quieras silenciar.
4. (Opcional) **Elegir fecha y hora** para que se apague solo. Si la app te lo sugiere, concede *Alarmas y recordatorios* para que el aviso de fin sea puntual.
5. **Activar modo vacaciones**.

## Detalles técnicos

- Java puro, sin dependencias (solo el SDK de Android). minSdk 26 (Android 8), targetSdk 35.
- AGP 8.7.3, Gradle 8.9 (lo instala el workflow; no hace falta wrapper).
- Paquete: `com.dani.modovacaciones`.
