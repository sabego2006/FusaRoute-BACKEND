package com.fusaroute.infrastructure.adapter.out.moderation;

import com.fusaroute.domain.port.out.ForbiddenWordsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Adaptador de {@link ForbiddenWordsPort} que lee el banco de un archivo versionado
 * (RF-08, SCRUM-91).
 *
 * El archivo va en el repositorio y no en la base de datos a proposito: es una regla
 * de moderacion que se discute en un PR, no un dato que el administrador edite en
 * caliente. Cambiarlo deja rastro en el historial de git, que es lo que se quiere
 * para algo que decide si un comentario de un usuario se rechaza.
 *
 * <p>Se lee UNA SOLA VEZ, en el constructor, y queda en memoria inmutable. La razon
 * es que {@code words()} se llama en cada comentario enviado o editado, y leer el
 * archivo del classpath en cada peticion seria E/S por nada: el contenido no puede
 * cambiar sin reiniciar el proceso, porque vive dentro del jar.
 *
 * <p>Si el archivo no existe o no se puede leer, el adaptador NO rompe el arranque:
 * registra el fallo y se queda con un banco vacio. Es una decision consciente y va
 * declarada aqui porque tiene un costo: el filtro de palabras queda desactivado y las
 * otras dos reglas (longitud y enlaces) siguen funcionando. Un comentario grosero que
 * pasa es un problema que un administrador resuelve leyendo; un backend que no
 * arranca deja a toda la aplicacion caida, incluido el catalogo publico de rutas que
 * no tiene nada que ver con los comentarios. El WARN en el log es lo que hace que el
 * estado degradado sea visible y no silencioso.
 */
@Component
public class ForbiddenWordsFileAdapter implements ForbiddenWordsPort {

    static final String DEFAULT_LOCATION = "classpath:moderation/forbidden-words.txt";

    private static final Logger log = LoggerFactory.getLogger(ForbiddenWordsFileAdapter.class);

    private final Set<String> words;

    public ForbiddenWordsFileAdapter(ResourceLoader resourceLoader,
                                     @Value("${app.moderation.forbidden-words-location:"
                                             + DEFAULT_LOCATION + "}") String location) {
        this.words = Collections.unmodifiableSet(load(resourceLoader, location));
    }

    private static Set<String> load(ResourceLoader resourceLoader, String location) {
        Set<String> loaded = new HashSet<>();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("Banco de palabras prohibidas no encontrado en {}: el filtro de palabras"
                    + " queda desactivado y los comentarios solo se validan por longitud y enlaces", location);
            return loaded;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                // '#' comenta una linea entera. No hay comentario al final de una
                // linea con palabra: una palabra prohibida no lleva espacios, asi que
                // la linea completa es la palabra y no hace falta partirla.
                if (!word.isEmpty() && !word.startsWith("#")) {
                    loaded.add(word);
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo leer el banco de palabras prohibidas en {}: el filtro de palabras"
                    + " queda desactivado", location, e);
            return Set.of();
        }
        log.info("Banco de palabras prohibidas cargado desde {}: {} palabras", location, loaded.size());
        return loaded;
    }

    /**
     * Las palabras tal como estan en el archivo, sin normalizar: normalizar es la
     * regla de comparacion y vive en el dominio. Ver el javadoc del puerto.
     */
    @Override
    public Set<String> words() {
        return words;
    }
}
