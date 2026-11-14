# ListApp: especificación funcional

App de **magia**: una herramienta «oculta» en el móvil. Parece una app normal de listas, pero introduce en secreto palabras
preparadas en un número elegido por el espectador. Android + iOS, todo en local, orientación vertical.

## El efecto
El mago enseña el móvil y cuenta que hace listas de todo (juegos de mesa, música, países visitados…). El espectador dice un número
(p. ej. 16). El mago localiza la app de listas, entra y pide leer el elemento 16 de varias listas: «Splendor», «Extremoduro»,
«Francia». Al final revela un sobre, visible desde el principio, con una foto suya jugando al Splendor en Francia con Extremoduro sonando.

## El método
1. Al abrir, la app **simula el escritorio del móvil**: tres páginas deslizables que son capturas de pantalla reales del móvil.
2. **Páginas 1 y 2 = dígitos.** Cada una tiene una botonera invisible (10 cajas: 1,2,…,9,0). Al arrastrar de una página a la siguiente,
   el punto donde se pone el dedo al empezar a arrastrar da el dígito (página 1 = decenas, página 2 = unidades).
3. **Página 3 = icono de la app de listas** (una caja invisible sobre el icono de la captura). Al pulsarlo se «abre» la app y, en ese
   momento, se inserta cada palabra configurada en la posición del número (decenas·10 + unidades) de su lista.
4. Las listas no se vuelven a modificar hasta el **reset**: cerrar la app o pulsar el botón oculto de reset.
5. Botón oculto **reset** (por defecto arriba a la izquierda) y botón oculto **configuración** (arriba a la derecha) en las pantallas de inicio.
   No son botones normales: hay que **mantener el dedo quieto** el tiempo configurado (1,5 s por defecto; vibración corta al activarse); un toque no hace nada (R11).

## Requisitos
**R1. Escritorio simulado.** 3 páginas con imagen de fondo propia (capturas de galería). Sin imagen (app nueva) se muestra un marcador con el recuadro del botón oculto de configuración dibujado con línea discontinua y la indicación de mantenerlo pulsado.
**R2. Dígitos.** Páginas 1 y 2: 10 cajas configurables por página (posición y tamaño libres, independientes entre páginas).
Si el dedo empieza fuera de las cajas, se aplica el modo elegido en config (**outsideMode**): *Ignorar* (el dígito no cambia y se cambia de página),
*Bloquear* (no se puede deslizar sin tocar una caja válida) o *Dígito 0* (vale 0 y se cambia de página). Si al abrir la app falta algún dígito,
se abre sin insertar nada. El número 00 no inserta nada.
El dígito se decide al poner el dedo (en qué caja cae) y se confirma en cuanto la página cambia, sin esperar a que termine la animación: se puede deslizar muy rápido.
**R3. Icono.** Página 3: una caja configurable que abre la app de listas. Tiene dos modos: *Invisible* (solo un botón sobre el icono de la captura) e
*Icono*: la caja tapa el icono original con el fondo (pantalla completa: el mismo trozo de la «imagen limpia», o el color de al lado si no hay;
iconos deslizantes: abre un hueco en la capa y deja ver el fondo de pantalla) y dibuja encima uno de los iconos incrustados en la app
(Apple Notas, Apple Recordatorios, Workflowy, Listonic, AnyList), con esquinas redondeadas. El icono va **centrado en la caja, cuadrado y con tamaño propio**
(≈ 17 % del ancho de pantalla × un zoom ajustable con un deslizador, del 40 % al 250 %): la caja solo decide la posición y qué se tapa, y el icono nunca se deforma.
**R4. Máscaras** (se configuran una vez, comunes a las tres páginas; cada página dibuja las suyas y viajan con su captura al deslizar): cajas que tapan elementos de la captura.
Cada máscara se rellena con un color o con la «imagen limpia» (captura sin esos elementos, recortada en la misma zona) y puede mostrar
la **hora real del sistema** en tiempo real (HH:mm) con color y tamaño configurables.
**R5. App de listas real**, con diseño moderno (tarjetas, cabecera grande, tema claro/oscuro del sistema).
- Lista de listas: botón «+» (diálogo) para crear; deslizar una tarjeta a la izquierda descubre *Renombrar* y *Borrar*.
- Elementos numerados: tocar una fila la edita en línea; «Siguiente» del teclado guarda y abre otra fila justo debajo; la fila «Añadir elemento»
  (y el «+» de arriba) añade al final; deslizar a la izquierda descubre *Borrar* (hasta el final, borra); **mantener pulsado 1 s** activa el modo
  mover (vibración) y se arrastra para subir o bajar, con desplazamiento automático en los bordes.
**R6. Pares lista → palabra** (menú secreto): cualquier número de pares. Se elige la lista en un desplegable con las listas existentes que aún no tienen par (se asocia por nombre, sin distinguir mayúsculas).
Si la lista no existe o es más corta que el número, no se toca. Si la palabra ya está en la lista (literal, sin distinguir mayúsculas),
se **mueve** a esa posición y el resto se reordena; si no está, **sustituye** al elemento de esa posición (el número de elementos de la lista nunca cambia).
**R7. Reset.** Deshace lo insertado (restaura cada lista tal y como estaba antes del truco), olvida los dígitos y vuelve a la página 1.
Al arrancar la app (cierre real) también se hace el reset, aunque se hubiera cerrado sin él.
**R8. Configuración**, organizada en secciones desplegables (se recuerda cuáles están abiertas al ir a un editor y volver):
- *Efecto*: las palabras (lista → palabra), con el botón «Añadir palabra» (R6).
- *Escritorio*: «Modo y capturas» (R10), «Dedo fuera de las cajas» (R2), «Botones ocultos» (R11) y «Máscaras» (R4), con los editores visuales de cajas.
- *Aspecto de las listas*: personalizador de la app de listas «falsa» con vista previa fija que se actualiza al momento (R12).
- *Instrucciones*: texto sencillo y sin tecnicismos con el objetivo de la app, el efecto paso a paso y para qué sirve cada sección de la configuración.
**R10. Modos del escritorio** (config). Dos modos para construir las páginas:
- *Pantalla completa*: cada página es una captura entera que se desliza (R1). Las máscaras viajan con su página.
- *Iconos deslizantes*: como en el iPhone, el **fondo de pantalla queda fijo** y solo se desliza la **capa de iconos**. El usuario importa la imagen
  original del fondo (con zoom y posición ajustables) y, por cada página, dos capturas idénticas salvo el fondo (color liso negro y color liso blanco;
  la pantalla de config las explica con dos dibujos de ejemplo). La app calcula la transparencia exacta de cada píxel (bordes, sombras, cristal del dock)
  y guarda la capa ya procesada. Se retiró el modo de «color único» por ser menos exacto y complicar las instrucciones.
- En *Iconos deslizantes* las **máscaras son fijas** (no se deslizan) y pueden rellenarse con el fondo de pantalla. Las **zonas fijas** (barra superior,
  dock) se dibujan con la capa de la pantalla 1 sobre el fondo y no se deslizan. Las cajas de dígitos e icono funcionan igual.
**R11. Botones ocultos.** Reset y configuración son dos cajas invisibles que se activan manteniendo el dedo quieto dentro. Su posición y tamaño se eligen
en el editor «Botones ocultos» (una sola posición para todas las pantallas, con las capturas disponibles como fondo de ayuda) y cada una tiene su tiempo
de pulsación (0,5 a 5 s; por defecto 1,5 s).
**R12. Aspecto de las listas.** Personalizador (Config → Aspecto de las listas) para que la app de listas se parezca a la que el mago usa de verdad:
tema (del sistema, claro, oscuro); colores de fondo, letra, detalles (números y botones) y tarjetas (cada uno automático, de una paleta o #RRGGBB);
filas como tarjetas, con separadores o sin separadores; esquinas (rectas, suaves, redondas); números en círculo o solo el número; tamaño de letra
(pequeña, normal, grande); tipo de letra (normal, con serifa, monoespaciada) y densidad de las filas (compacta, normal, amplia). Con vista previa
(elementos o lista de listas) y botón «Restablecer el aspecto». Solo afecta a la app de listas; la configuración y el escritorio no cambian.
**R13. Icono y nombre de la app.** Desde Configuración → «Icono y nombre de la app» se elige, ya instalada, el icono y el nombre que enseña el móvil
en el lanzador. Solo entre los preestablecidos (son recursos compilados). *Android*: icono y nombre con alias de actividad (5 iconos × 5 nombres; por defecto
Apple Notas + «ListApp»; nombres: ListApp, Listas, Notas, Recordatorios, Lists). *iOS*: solo el icono (principal Apple Notas; alternativo Apple Recordatorios);
el nombre no se puede cambiar y el sistema muestra su aviso. **Al tocar un icono o un nombre sale una confirmación** («¿Cambiar icono?» / «¿Cambiar nombre?»,
con el cambio y «La aplicación se reiniciará por completo (se cerrará y volverá a abrir)» en Android); si se cancela no cambia nada. En Android se abre la app de nuevo
por el alias nuevo antes de desactivar el anterior. Al arrancar se normaliza para que haya un único alias activo. El apartado plegado muestra la miniatura y el valor actual.
**R14. Guía de introducción.** Modal de 6 pasos con ilustraciones (dibujadas en la app): qué es, preparar las palabras, hacer las capturas, marcar las cajas,
usarlo en directo (incluido el reset) y cómo seguir explorando (opciones avanzadas e Instrucciones). Se abre con el botón «Guía» de la pantalla de bienvenida
(app nueva, bajo el texto) y con «Ver la guía rápida» al principio de las Instrucciones. Navegación con Anterior / Siguiente, puntos de progreso y «Cerrar».
**R9. Local.** Sin red ni cuentas. Ajustes en SharedPreferences/UserDefaults; imágenes en ficheros internos.

## Preguntas abiertas
- Icono de la app: se ha dibujado uno provisional de «documento con lista»; el usuario puede aportar el suyo en `data/icon.png`.
- Números de más de dos dígitos (>99): no contemplado.
- El reset restaura la lista entera: lo que se edite a mano en una lista afectada durante la sesión se pierde al resetear.
