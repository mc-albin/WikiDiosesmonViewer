# WikiDiosesmonViewer

Abre la wiki de DiosesMon (https://ialbin73.com/wikidiosesmon) dentro de Minecraft 1.21.1 (Fabric). Es ligero: el navegador solo se crea al abrirlo y se libera unos segundos después de cerrarlo. Autor: iAlbin73.

## Instalación

1. Copia `WikiDiosesmonViewer.jar` en la carpeta `mods` de tu instancia.
2. Requisitos: Fabric Loader 0.16 o superior, Fabric API para 1.21.1 y Java 21.
3. Quita PiP Browser y MCEF si los tienes. Este mod ya trae MCEF dentro.
4. La primera vez que inicias el juego, MCEF descarga Chromium (unos 100 MB).
5. El servidor no necesita instalar nada. El mod es solo de cliente.

## Uso

- **Inicio (Home)**: abre la wiki en pantalla completa.
- **Fin (End)**: ventana pequeña. Puedes andar con WASD mientras la ves. Pulsa Fin otra vez para usar el ratón en ella: arrastra la cabecera para moverla y los bordes para cambiar su tamaño.
- **Esc**: cierra la wiki.
- **F5** recarga la página. **Ctrl +**, **Ctrl −** y **Ctrl 0** cambian el zoom.
- El botón **AFK** de la cabecera envía `/afk` y cuenta el tiempo. Si alguien te mueve mientras estás AFK, suena un aviso.

La wiki recuerda la última página y la posición de scroll. Se adapta a cualquier pantalla y a la escala de interfaz x1 a x4. Con x2 la página se ve al 100 %.

La configuración se guarda en `config/wikidiosesmonviewer.json`.

Para no quitarle FPS al juego, los procesos de Chromium usan prioridad de CPU baja: el juego va primero y la página usa lo que sobra. Con el juego minimizado, la página se pausa.

La sesión de la web (tu cuenta) se guarda al cerrar la wiki y al cerrar el juego, así que sigue abierta después de reiniciar o actualizar.

## Actualizaciones

Al iniciar el juego, el mod mira si hay una versión nueva en las releases de este repositorio. Si la hay, sale un aviso arriba y un botón **⬆ Actualizar** en la cabecera de la wiki. Al pulsarlo se descarga y se comprueba su huella SHA-256. La versión nueva se instala sola al cerrar el juego.

Para publicar una versión (PowerShell, en esta carpeta): `.\publicar.ps1 1.0.2 "Qué cambió"`.

## Compilar

Con Java 21: `gradlew build`. El jar queda en `build/libs/WikiDiosesmonViewer.jar`.

## Licencias

Incluye [MCEF](https://github.com/CinemaMod/mcef) 2.1.6 (LGPL-2.1).
