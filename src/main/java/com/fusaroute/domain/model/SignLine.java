package com.fusaroute.domain.model;

/**
 * Una linea de texto dentro de una placa de letrero (SCRUM-175/176).
 *
 * {@code background} y {@code foreground} vienen YA RESUELTOS: en el letrero fisico
 * una linea puede traer su propio color o heredar el de toda la placa, pero esa
 * resolucion se hizo una vez al modelar el dato (ver V11), no en cada lectura. Son
 * {@code null} cuando ni la linea ni la placa tienen color registrado.
 *
 * @param text texto tal como se lee en la placa, en mayusculas o minusculas segun
 *     como este pintado — no se normaliza
 * @param background color de fondo en hex ({@code #RRGGBB}), estimado a ojo sobre la
 *     foto; {@code null} si no se registro
 * @param foreground color del texto en hex; {@code null} si no se registro
 */
public record SignLine(String text, String background, String foreground) {
}
