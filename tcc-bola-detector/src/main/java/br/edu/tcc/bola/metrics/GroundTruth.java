package br.edu.tcc.bola.metrics;

import java.util.Set;

/**
 * Gabarito (ground truth) dos endpoints realmente vulneráveis a BOLA em cada
 * benchmark. Usado para calcular TP/FP/FN/TN de forma honesta, comparando os
 * vereditos do detector contra a verdade conhecida.
 *
 * A chave de cada endpoint é "METODO path" em minúsculas, ex.:
 *   "get /books/v1/{book_title}"
 *
 * As classificações baseiam-se na documentação oficial de cada alvo e na
 * verificação manual realizada durante os experimentos.
 */
public final class GroundTruth {

    private GroundTruth() {}

    /**
     * crAPI — dos 9 endpoints candidatos (com path parameter), estes são de
     * fato vulneráveis a BOLA. Os endpoints de vídeo ({video_id}) referem-se a
     * recursos que não existem para os usuários de teste e não são considerados
     * vulneráveis neste cenário.
     */
    public static final Set<String> CRAPI_VULNERABLE = Set.of(
        "get /identity/api/v2/vehicle/{vehicleid}/location",
        "get /community/api/v2/community/posts/{postid}",
        "post /community/api/v2/community/posts/{postid}/comment",
        "get /workshop/api/shop/orders/{order_id}",
        "put /workshop/api/shop/orders/{order_id}"
    );

    /**
     * VAmPI — todos os endpoints com path parameter (username/book_title) são
     * vulneráveis a BOLA segundo a documentação do projeto: o controle de acesso
     * usa o identificador da URL em vez do token do usuário autenticado.
     */
    public static final Set<String> VAMPI_VULNERABLE = Set.of(
        "get /books/v1/{book_title}",
        "get /users/v1/{username}",
        "delete /users/v1/{username}",
        "put /users/v1/{username}/email",
        "put /users/v1/{username}/password"
    );

    /** Normaliza "METODO path" para a chave canônica (minúsculas). */
    public static String key(String method, String path) {
        return (method + " " + path).toLowerCase();
    }

    public static boolean isVulnerable(Set<String> truth, String method, String path) {
        return truth.contains(key(method, path));
    }
}
