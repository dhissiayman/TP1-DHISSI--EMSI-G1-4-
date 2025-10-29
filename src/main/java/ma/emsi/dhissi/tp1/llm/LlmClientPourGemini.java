package ma.emsi.dhissi.tp1.llm;


import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.logging.LoggingFeature;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client REST pour l'API Gemini (generateContent).
 * - Lit la clé dans System Property PUIS dans l'Env (fallback).
 * - Logge les requêtes/réponses HTTP (payload inclus) dans la console de Payara.
 */
@Dependent
public class LlmClientPourGemini implements Serializable {


    private static final String MODEL = "gemini-2.5-flash";

    private final String key;
    private final Client clientRest;
    private final WebTarget target;

    public LlmClientPourGemini() {

        String k = System.getProperty("GEMINI_KEY");

        if (k == null || k.isBlank()) {
            k = System.getenv("GEMINI_KEY");
        }
        if (k == null || k.isBlank()) {
            throw new IllegalStateException(
                    "Clé API introuvable. Définis GEMINI_KEY comme System Property (-DGEMINI_KEY=...) " +
                            "ou comme variable d'environnement."
            );
        }
        this.key = k;

        // 3) Client REST + Logging HTTP
        this.clientRest = ClientBuilder.newClient();

        Logger httpLogger = Logger.getLogger("GeminiHttp");
        this.clientRest.register(new LoggingFeature(
                httpLogger,
                Level.INFO,
                LoggingFeature.Verbosity.PAYLOAD_TEXT,
                8192
        ));


        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent?key=" + this.key;


        this.target = clientRest.target(url);
    }

    /** Appel normal utilisé par JsonUtilPourGemini */
    public Response envoyerRequete(Entity<String> requestEntity) {
        Invocation.Builder request = target.request(MediaType.APPLICATION_JSON_TYPE);
        return request.post(requestEntity);
    }


    public Response testDirect() {
        String body = """
          {"system_instruction":{"parts":[{"text":"helpful assistant"}]},
           "contents":[{"role":"user","parts":[{"text":"ping"}]}]}
        """;
        return target.request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(body));
    }

    public void closeClient() {
        if (this.clientRest != null) this.clientRest.close();
    }
}
