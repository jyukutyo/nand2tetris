public class JackToken {
    private final TokenType tokenType;
    private final String value;
    public JackToken(TokenType t, String v) {
        this.tokenType = t;
        this.value = v;
    }
    public TokenType tokenType() {
        return this.tokenType;
    }
    public String value() {
        return value;
    }
}