package ma.emsi.dhissi.tp1.web;

import jakarta.enterprise.inject.Instance;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.dhissi.tp1.llm.JsonUtilPourGemini;
import ma.emsi.dhissi.tp1.llm.LlmInteraction;
import ma.emsi.dhissi.tp1.llm.RequeteException;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Named("chat")
@ViewScoped
public class ChatBean implements Serializable {

    private List<SelectItem> listeRolesSysteme;
    private String roleSysteme;
    private boolean roleSystemeChangeable = true;

    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }
    public void setRoleSystemeChangeable(boolean b) { this.roleSystemeChangeable = b; }

    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }


    private String role;
    private boolean roleChoisi;
    private String question;
    private String reponse;
    private String historique = "";
    private String clefBase64;

    // ====== AJOUTS DEBUG ======
    private boolean debug;                 // affiche/masque le panel debug
    private String texteRequeteJson;       // JSON prettifié envoyé
    private String texteReponseJson;       // JSON brut reçu

    private boolean conversationDemarree = false;

    @Inject private FacesContext faces;
    @Inject private Instance<JsonUtilPourGemini> jsonUtilProvider;
    private JsonUtilPourGemini jsonUtil;

    // ==== Getters/Setters ====
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isRoleChoisi() { return roleChoisi; }
    public void setRoleChoisi(boolean roleChoisi) { this.roleChoisi = roleChoisi; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }
    public String getHistorique() { return historique; }
    public void setHistorique(String historique) { this.historique = historique; }
    public String getClefBase64() { return clefBase64; }
    public boolean isChiffreur() { return role != null && role.equalsIgnoreCase("chiffreur"); }

    // ==== Getters/Setters DEBUG ====
    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
    public String getTexteRequeteJson() { return texteRequeteJson; }
    public void setTexteRequeteJson(String texteRequeteJson) { this.texteRequeteJson = texteRequeteJson; }
    public String getTexteReponseJson() { return texteReponseJson; }
    public void setTexteReponseJson(String texteReponseJson) { this.texteReponseJson = texteReponseJson; }

    // ==== Actions appelées par la page ====
    public void verrouillerRole() {
        if (role != null && !role.isBlank()) {
            roleChoisi = true;
            clefBase64 = isChiffreur() ? genererCleBase64() : null;
        }
    }

    public void envoyer() {
        try {
            if (jsonUtil == null) jsonUtil = jsonUtilProvider.get();

            // Vérifs UX basiques
            if (this.question == null || this.question.isBlank()) {
                faces.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_WARN,
                        "Question manquante",
                        "Veuillez saisir une question."
                ));
                return;
            }

            // Au premier envoi, on pousse le rôle SYSTÈME sélectionné
            if (!conversationDemarree) {
                if (this.roleSysteme == null || this.roleSysteme.isBlank()) {
                    faces.addMessage(null, new FacesMessage(
                            FacesMessage.SEVERITY_WARN,
                            "Rôle système manquant",
                            "Veuillez choisir un rôle de l'API."
                    ));
                    return;
                }
                jsonUtil.setSystemRole(this.roleSysteme);   // ⬅️ remplace mapRoleSystem(role)
                conversationDemarree = true;
                this.roleSystemeChangeable = false;         // optionnel : verrouiller la liste
            }

            LlmInteraction interaction = jsonUtil.envoyerRequete(this.question);
            this.reponse = interaction.reponseExtraite();

            // Mode debug : JSON envoyé/reçu
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.reponseJson();

            // Historique d'affichage
            appendHistorique(this.question, this.reponse);

        } catch (RequeteException e) {
            faces.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Erreur API Gemini",
                    e.getMessage()
            ));
        } catch (IllegalStateException e) {
            faces.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Configuration manquante",
                    e.getMessage()
            ));
        } catch (jakarta.ws.rs.ProcessingException e) {
            Throwable cause = e.getCause();
            String details = (cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage()
                    : e.getMessage());
            faces.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Erreur réseau vers l’API",
                    details
            ));
        } catch (Exception e) {
            faces.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Erreur de connexion",
                    e.toString()
            ));
        }
    }


    public void effacerDerniere() {
        if (historique == null || historique.isBlank()) return;
        int lastSep = historique.lastIndexOf("\n---\n");
        historique = (lastSep > 0) ? historique.substring(0, lastSep) : "";
        reponse = null;
        faces.addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO,
                "Historique mis à jour",
                "La dernière interaction a été retirée de l’affichage (contexte LLM non modifié)."
        ));
    }

    public void nouveauChat() {
        // ✅ réactiver le choix du rôle + vider la valeur
        this.roleSystemeChangeable = true;
        this.roleSysteme = null;

        // (si tu conserves encore l’ancien flag)
        this.roleChoisi = false;

        // reset conversation / UI
        this.question = null;
        this.reponse = null;
        this.historique = "";
        this.clefBase64 = null;
        this.conversationDemarree = false;

        // reset debug JSON
        this.texteRequeteJson = null;
        this.texteReponseJson = null;

        // nouvelle conv côté LLM
        this.jsonUtil = jsonUtilProvider.get();

        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Nouveau chat", "Session réinitialisée."));
    }


    /** Bascule l'affichage du panel de debug */
    public void toggleDebug() { this.debug = !this.debug; }

    // ==== Helpers ====
    private void appendHistorique(String q, String r) {
        String bloc = "Vous: " + safe(q) + "\n" + "Modèle: " + safe(r) + "\n---\n";
        this.historique = (historique == null || historique.isBlank()) ? bloc : historique + bloc;
    }
    private String safe(String s) { return s == null ? "" : s; }
    private String genererCleBase64() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }


    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            // === Assistant ===
            String role = """
                You are a helpful and concise assistant. 
                Answer clearly and help the user find information efficiently.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            // === Traducteur Anglais-Français ===
            role = """
                Tu es un traducteur FR-EN/EN-FR. 
                Réponds uniquement par la traduction, sans explications.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            // === Guide touristique ===
            role = """
                Tu es un guide touristique local, chaleureux et précis. 
                Donne des informations pratiques, concises et intéressantes 
                sur les lieux, coutumes et prix moyens.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide Touristique"));

            // === Chiffreur ===
            role = """
                Tu réponds normalement, mais une clé (affichée dans l’interface utilisateur)
                est fournie à l’utilisateur. Utilise-la si nécessaire.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Chiffreur"));

            // === ROT13 ===
            role = """
                Réponds en appliquant un chiffrement ROT13 sur tout le texte de sortie.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "ROT13"));

            // === Gamer ===
            role = """
                YOU ARE A FULL-TIME GAMER AND STREAMER. 
                Speak casually, use gaming slang, and comment like a Twitch streamer.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Gamer"));
            role = """
                Tu es un humoriste plein d’esprit.
                Réponds avec humour et jeux de mots, tout en gardant le sens de la réponse.
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Humoriste"));
        }

        return this.listeRolesSysteme;
    }

}
