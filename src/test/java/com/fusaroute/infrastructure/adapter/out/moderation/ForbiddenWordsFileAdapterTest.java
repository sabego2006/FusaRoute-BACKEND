package com.fusaroute.infrastructure.adapter.out.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lectura del banco de palabras prohibidas (RF-08, SCRUM-91).
 *
 * Usa el archivo real del classpath y no uno de prueba: lo que interesa comprobar es
 * que el archivo que se va a desplegar se parsea bien. Un archivo de prueba aparte
 * verificaria el parser contra datos que nadie usa.
 */
class ForbiddenWordsFileAdapterTest {

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    @Test
    void carga_el_banco_del_classpath() {
        Set<String> words = new ForbiddenWordsFileAdapter(
                resourceLoader, ForbiddenWordsFileAdapter.DEFAULT_LOCATION).words();

        assertThat(words).isNotEmpty();
        assertThat(words).contains("idiota", "gonorrea");
    }

    /**
     * Las lineas de comentario y las vacias no entran al banco. Importa porque el
     * archivo arranca con un encabezado largo que explica el criterio de la lista: si
     * esas lineas entraran, el filtro rechazaria comentarios por palabras como
     * "banco" o "formato".
     */
    @Test
    void ignora_los_comentarios_y_las_lineas_vacias() {
        Set<String> words = new ForbiddenWordsFileAdapter(
                resourceLoader, ForbiddenWordsFileAdapter.DEFAULT_LOCATION).words();

        assertThat(words).doesNotContain("", "formato", "banco");
        assertThat(words).noneMatch(word -> word.startsWith("#"));
    }

    /**
     * Las palabras que NO deben estar, y por que importa que no esten: cada una
     * tumbaria comentarios legitimos en esta aplicacion concreta. "negro" aparece al
     * describir el letrero de una ruta, "cono" es un cono de la via, y "ladron" es el
     * aviso mas util que la caja de comentarios puede recibir. Si alguien las agrega
     * al archivo, este test lo detiene y el encabezado del archivo explica el motivo.
     */
    @Test
    void el_banco_no_trae_palabras_con_uso_legitimo_en_esta_aplicacion() {
        Set<String> words = new ForbiddenWordsFileAdapter(
                resourceLoader, ForbiddenWordsFileAdapter.DEFAULT_LOCATION).words();

        assertThat(words).doesNotContain("negro", "negra", "blanco", "cono",
                "ladron", "ladrones", "rata", "ratas", "animal", "indio");
    }

    /**
     * Sin archivo el adaptador NO rompe el arranque: deja el banco vacio y el resto
     * de las validaciones del comentario sigue en pie. Es una decision consciente y
     * esta explicada en el javadoc del adaptador.
     */
    @Test
    void si_el_archivo_no_existe_el_banco_queda_vacio_y_no_rompe_el_arranque() {
        Set<String> words = new ForbiddenWordsFileAdapter(
                resourceLoader, "classpath:moderation/no-existe.txt").words();

        assertThat(words).isEmpty();
    }

    /**
     * El archivo existe pero no se puede leer (permisos, archivo corrupto en el jar).
     * Es la otra mitad de la decision de arriba y la que no se ve a simple vista: el
     * adaptador tiene que degradarse igual, no propagar la excepcion y tumbar el
     * arranque de toda la aplicacion por un archivo de moderacion.
     */
    @Test
    void si_el_archivo_existe_pero_no_se_puede_leer_el_banco_queda_vacio() {
        // ResourceLoader no es interfaz funcional (declara dos metodos), asi que se
        // extiende el loader real en vez de pasarle una lambda.
        ResourceLoader ilegible = new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return true;
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        throw new IOException("archivo ilegible");
                    }
                };
            }
        };

        Set<String> words = new ForbiddenWordsFileAdapter(
                ilegible, ForbiddenWordsFileAdapter.DEFAULT_LOCATION).words();

        assertThat(words).isEmpty();
    }

    /**
     * El banco se lee una vez y queda inmutable: quien lo reciba no puede agregarle
     * palabras en caliente. Si fuera mutable, un cambio accidental desde el dominio
     * alteraria el filtro para todas las peticiones siguientes del proceso.
     */
    @Test
    void el_banco_que_devuelve_es_inmutable() {
        Set<String> words = new ForbiddenWordsFileAdapter(
                resourceLoader, ForbiddenWordsFileAdapter.DEFAULT_LOCATION).words();

        assertThatThrownBy(() -> words.add("otra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
