package com.fusaroute.domain.model;

import com.fusaroute.domain.exception.FeedbackAlreadyAnsweredException;
import com.fusaroute.domain.exception.FeedbackContainsUrlException;
import com.fusaroute.domain.exception.FeedbackForbiddenWordException;
import com.fusaroute.domain.exception.FeedbackLengthException;
import com.fusaroute.domain.exception.FeedbackNotFoundException;
import com.fusaroute.domain.exception.InvalidFeedbackReplyException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comentario de un usuario y la respuesta del administrador (RF-08 y RF-14,
 * SCRUM-22 y SCRUM-23).
 *
 * Objeto de dominio puro: aqui viven TODAS las reglas del comentario, y el
 * controlador no repite ninguna. La validacion del formulario en Angular es solo
 * comodidad para el usuario; la que cuenta es esta, porque es la unica que un
 * cliente no puede saltarse.
 *
 * Es una clase inmutable con copias, al estilo de {@link User}, y no un record:
 * tiene transiciones de estado (editar, responder) y no solo datos.
 *
 * <p><b>Las tres reglas del texto</b> (SCRUM-91), en este orden:
 * <ol>
 *   <li>entre {@value #MIN_TEXT_LENGTH} y {@value #MAX_TEXT_LENGTH} caracteres, ya recortado;</li>
 *   <li>sin enlaces;</li>
 *   <li>sin palabras del banco que entrega {@code ForbiddenWordsPort}.</li>
 * </ol>
 * El orden no es casual: se revisa primero lo que no depende de datos externos, para
 * que un comentario de dos letras no gaste una lectura del banco de palabras.
 *
 * <p><b>El bloqueo tras la respuesta</b> (SCRUM-94 y SCRUM-95) es la regla que une
 * las dos historias: el autor edita o borra solo mientras el comentario este
 * {@link FeedbackStatus#PENDING}. No es una comodidad de interfaz: protege la
 * metrica 4 del panel de RF-13, que cuenta justo los pendientes.
 */
public final class Feedback {

    /** Minimo del criterio de aceptacion de RF-08. */
    public static final int MIN_TEXT_LENGTH = 10;

    /** Maximo del criterio de aceptacion de RF-08, y el ancho de la columna. */
    public static final int MAX_TEXT_LENGTH = 100;

    /**
     * Ancho de la respuesta del administrador. Mas holgado que el comentario a
     * proposito: el usuario pide algo en una linea, pero explicar por que una ruta
     * no pasa por un barrio puede tomar varias.
     */
    public static final int MAX_REPLY_LENGTH = 500;

    /**
     * Enlaces. Se buscan tres formas, porque quien quiera dejar un enlace no va a
     * escribir el esquema completo si basta con menos: {@code http://} o
     * {@code https://}, el prefijo {@code www.}, y el par {@code dominio.tld}
     * suelto, que es como se pega un enlace en una conversacion real
     * ("miren ejemplo.com").
     *
     * <p>La lista de dominios de primer nivel es CERRADA y no un patron generico de
     * "letras punto letras". La razon es un falso positivo que aparece en prosa
     * normal: al que escribe un comentario de 100 caracteres se le va el espacio
     * despues del punto ("buen servicio.Gracias"), y un patron generico lo tomaria
     * por un dominio y rechazaria un comentario legitimo. Con la lista cerrada se
     * escapan dominios raros; es el intercambio que se elige, porque un enlace
     * colado molesta menos que un comentario valido que el sistema no deja enviar.
     *
     * <p>Por lo mismo quedaron FUERA de la lista {@code .es} y {@code .me}, que son
     * dominios reales: los comentarios se escriben en espanol, donde "Es" y "Me"
     * empiezan frases todo el tiempo, y con ellos dentro "el servicio.Es muy bueno"
     * se rechazaria como enlace. Un patron que tumba comentarios validos es peor que
     * uno que deja pasar un enlace. Hay un test que fija esa decision.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.|\\b[a-z0-9][a-z0-9-]*\\."
                    + "(com|co|net|org|edu|gov|io|app|info|biz|ly|gl|xyz|online|site|link)\\b)",
            Pattern.CASE_INSENSITIVE);

    /** Separador de palabras: todo lo que no sea letra o digito. */
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final Long id;
    private final Long userId;
    private final String authorName;
    private final String text;
    private final FeedbackStatus status;
    private final String reply;
    private final Instant answeredAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Constructor completo, para rehidratar una fila existente desde la persistencia.
     * NO revalida el texto: las reglas de SCRUM-91 se aplican al escribir, no al
     * leer. Si el banco de palabras crece manana, los comentarios ya guardados
     * siguen pudiendo leerse; lo contrario dejaria la pantalla del usuario rota por
     * un cambio en un archivo de datos.
     *
     * <p>Lo que si se valida es la coherencia del estado, porque una fila con
     * {@code status = ANSWERED} y sin respuesta (o al contrario) no tiene como
     * mostrarse: es mejor romper la peticion que pintar un comentario respondido
     * con la respuesta en blanco.
     */
    public Feedback(Long id, Long userId, String authorName, String text, FeedbackStatus status,
                    String reply, Instant answeredAt, Instant createdAt, Instant updatedAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.authorName = Objects.requireNonNull(authorName, "authorName");
        this.text = Objects.requireNonNull(text, "text");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (status == FeedbackStatus.ANSWERED && (reply == null || reply.isBlank() || answeredAt == null)) {
            throw new IllegalArgumentException("Un comentario respondido necesita respuesta y fecha de respuesta");
        }
        if (status == FeedbackStatus.PENDING && (reply != null || answeredAt != null)) {
            throw new IllegalArgumentException("Un comentario pendiente no puede traer respuesta");
        }
        this.id = id;
        this.reply = reply;
        this.answeredAt = answeredAt;
    }

    /**
     * Comentario nuevo desde la caja (RF-08, SCRUM-92). Nace
     * {@link FeedbackStatus#PENDING} y sin respuesta, con {@code id} nulo hasta que
     * la base le asigne uno.
     *
     * @param authorName     nombre del autor, que se guarda copiado y no por
     *                       referencia al perfil. El criterio de aceptacion pide que
     *                       el comentario muestre con que nombre se escribio; si se
     *                       leyera del perfil en cada lectura, un cambio de nombre
     *                       reescribiria comentarios viejos de forma retroactiva.
     * @param forbiddenWords banco de palabras tal como lo entrega el puerto, sin
     *                       normalizar. Un conjunto vacio desactiva ese filtro.
     */
    public static Feedback create(Long userId, String authorName, String text,
                                  Set<String> forbiddenWords, Instant now) {
        Objects.requireNonNull(now, "now");
        String clean = validateText(text, forbiddenWords);
        return new Feedback(null, userId, authorName, clean, FeedbackStatus.PENDING,
                null, null, now, now);
    }

    /**
     * Las tres reglas de SCRUM-91 sobre el texto, aplicadas en orden. La reusa
     * {@link #editedTo} tal cual: editar tiene que validar lo mismo que crear, o el
     * filtro se saltaria enviando algo valido y editandolo despues.
     */
    private static String validateText(String text, Set<String> forbiddenWords) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() < MIN_TEXT_LENGTH || trimmed.length() > MAX_TEXT_LENGTH) {
            throw new FeedbackLengthException(
                    "El comentario debe tener entre " + MIN_TEXT_LENGTH + " y " + MAX_TEXT_LENGTH + " caracteres");
        }
        // Sobre el texto original y no sobre el normalizado: la normalizacion que usa
        // el banco de palabras no sirve aqui. Es la misma razon por la que este
        // metodo no reutiliza PlaceName.normalize, que ademas borra los puntos y
        // convertiria "ejemplo.com" en "ejemplocom", justo lo que hay que ver.
        if (URL_PATTERN.matcher(trimmed).find()) {
            throw new FeedbackContainsUrlException("El comentario no puede incluir enlaces");
        }
        if (containsForbiddenWord(trimmed, forbiddenWords)) {
            throw new FeedbackForbiddenWordException(
                    "El comentario incluye lenguaje que no se permite en la caja de comentarios");
        }
        return trimmed;
    }

    /**
     * Compara PALABRA COMPLETA y no subcadena. Con subcadena, una palabra prohibida
     * corta deja inservibles decenas de palabras legitimas que la contienen, y el
     * usuario recibe un rechazo que no entiende. A cambio se escapan las variantes
     * con sufijo; el banco es un disuasivo, no un filtro perfecto, y detras hay un
     * administrador leyendo.
     *
     * <p>El texto se parte por todo lo que no sea letra ni digito, asi que la
     * puntuacion pegada no esconde la palabra, y cada lado se compara normalizado:
     * minusculas y sin tildes, de modo que la misma palabra en mayusculas o con
     * tilde cae en la misma entrada del banco.
     */
    private static boolean containsForbiddenWord(String text, Set<String> forbiddenWords) {
        if (forbiddenWords == null || forbiddenWords.isEmpty()) {
            return false;
        }
        Set<String> banned = new HashSet<>();
        for (String word : forbiddenWords) {
            String folded = fold(word);
            if (!folded.isEmpty()) {
                banned.add(folded);
            }
        }
        if (banned.isEmpty()) {
            return false;
        }
        for (String token : WORD_SEPARATOR.split(fold(text))) {
            if (!token.isEmpty() && banned.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forma comparable de una palabra: minusculas y sin tildes. Nada mas.
     *
     * <p>No es {@code PlaceName.normalize}, aunque use la misma tecnica de NFD, y la
     * diferencia es deliberada: alla se borran los puntos y se colapsan los espacios
     * porque la pregunta es "son el mismo lugar", y aqui borrar los puntos taparia
     * los enlaces, que es justo la regla de al lado. Son dos reglas de negocio
     * distintas que casualmente comparten una linea de JDK.
     */
    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        // NFD separa la letra de su diacritico y \p{M} lo borra: asi la palabra con
        // tilde y sin tilde terminan siendo el mismo texto al comparar.
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Comprueba que {@code candidateUserId} sea el autor (SCRUM-94 y SCRUM-95).
     * Lanza {@link FeedbackNotFoundException} y no una excepcion de permiso: ver el
     * javadoc de esa clase para por que el comentario ajeno responde 404.
     */
    public void requireAuthoredBy(Long candidateUserId) {
        if (!userId.equals(candidateUserId)) {
            throw new FeedbackNotFoundException("El comentario no existe");
        }
    }

    /**
     * Comprueba que siga pendiente (SCRUM-94 y SCRUM-95). Se llama DESPUES de
     * {@link #requireAuthoredBy}: preguntar primero por el estado le contaria a un
     * tercero si el comentario de otro ya fue respondido.
     */
    public void requirePending() {
        if (status != FeedbackStatus.PENDING) {
            throw new FeedbackAlreadyAnsweredException(
                    "El comentario ya fue respondido y no se puede editar ni borrar");
        }
    }

    /**
     * Copia con el texto nuevo (SCRUM-94). El caso de uso ya comprobo autoria; aqui
     * se vuelve a exigir que siga pendiente y se revalida el texto, porque editar no
     * es menos escritura que crear.
     */
    public Feedback editedTo(String newText, Set<String> forbiddenWords, Instant now) {
        Objects.requireNonNull(now, "now");
        requirePending();
        String clean = validateText(newText, forbiddenWords);
        return new Feedback(id, userId, authorName, clean, status, reply, answeredAt, createdAt, now);
    }

    /**
     * Copia respondida (SCRUM-99 y SCRUM-100). Sirve para la primera respuesta y
     * para corregirla: en los dos casos queda UNA sola respuesta y
     * {@code answeredAt} pasa a {@code now}, que es lo que pide el criterio "si el
     * administrador necesita corregir su respuesta, la edita, no crea una segunda".
     *
     * <p>Que {@code answeredAt} se mueva al corregir no es un descuido: es lo que
     * hace que el contador de respuestas nuevas de SCRUM-101 vuelva a avisarle al
     * usuario de que lo que le respondieron cambio. Si se conservara el instante
     * original, una respuesta corregida pasaria inadvertida.
     */
    public Feedback answeredWith(String newReply, Instant now) {
        Objects.requireNonNull(now, "now");
        String clean = validateReply(newReply);
        return new Feedback(id, userId, authorName, text, FeedbackStatus.ANSWERED,
                clean, now, createdAt, now);
    }

    private static String validateReply(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFeedbackReplyException("La respuesta no puede ir vacia");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_REPLY_LENGTH) {
            throw new InvalidFeedbackReplyException(
                    "La respuesta no puede pasar de " + MAX_REPLY_LENGTH + " caracteres");
        }
        return trimmed;
    }

    /** Copia con el id que asigno la base. */
    public Feedback withId(Long newId) {
        return new Feedback(newId, userId, authorName, text, status, reply, answeredAt, createdAt, updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getText() {
        return text;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    /** {@code null} mientras este pendiente. */
    public String getReply() {
        return reply;
    }

    /** {@code null} mientras este pendiente. */
    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPending() {
        return status == FeedbackStatus.PENDING;
    }
}
