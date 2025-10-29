package ma.emsi.dhissi.tp1.llm;




public class RequeteException extends Exception {
    private int status;
    private String requeteJson;

    public RequeteException() { super(); }
    public RequeteException(int status) { super("Erreur HTTP : " + status); this.status = status; }
    public RequeteException(String message) { super(message); }
    public RequeteException(String message, String requeteJson) { super(message); this.requeteJson = requeteJson; }

    public int getStatus() { return status; }
    public String getRequeteJson() { return requeteJson; }
}
