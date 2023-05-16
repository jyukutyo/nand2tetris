import java.util.*;
import java.nio.file.*;
import java.util.stream.*;

public class JackTokenizer {

    public static void main(String... args) throws Exception {
        Path path = Paths.get(args[0]);
        List<Path> files;
        if (Files.isDirectory(path)) {
            files = Files.list(path).filter(p -> p.getFileName().toString().endsWith(".jack")).collect(Collectors.toList());
        } else {
            files = new ArrayList<>();
            files.add(path);
        }

        for (Path p: files) {
            JackTokenizer t = new JackTokenizer(p);
            List<String> tokens = new ArrayList<>();
            tokens.add("<tokens>");
            while (t.hasMoreTokens()) {
                t.advance();
                switch (t.tokenType()) {
                    case KEYWORD -> tokens.add("<keyword> " + t.keyWord().toString().toLowerCase() + " </keyword>");
                    case IDENTIFIER -> tokens.add("<identifier> " + t.identifier() + " </identifier>");
                    case INT_CONST -> tokens.add("<integerConstant> " + t.intVal() + " </integerConstant>");
                    case STRING_CONST -> tokens.add("<stringConstant> " + t.stringVal() + " </stringConstant>");
                    case SYMBOL -> {
                        switch (t.symbol()) {
                            case "&" -> tokens.add("<symbol> &amp; </symbol>");
                            case "<" -> tokens.add("<symbol> &lt; </symbol>");
                            case ">" -> tokens.add("<symbol> &gt; </symbol>");
                            default -> tokens.add("<symbol> " + t.symbol() + " </symbol>");
                        }
                    }
                }
            }
            tokens.add("</tokens>");
            String fileName = p.getFileName().toString();
            Files.write(Paths.get(p.getParent().toString(), fileName.substring(0, fileName.lastIndexOf(".")) + "T_.xml"), tokens, StandardOpenOption.CREATE);
        }
    }    

    private Iterator<String> lineItr;
    private String currentLine;
    private int currentIndex;
    private JackToken currentToken;

    public JackTokenizer(Path file) {
        try {
            lineItr = Files.readAllLines(file)
                    .stream()
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.matches("^\\s*//.*$"))
                    .collect(Collectors.toList())
                    .iterator();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasMoreTokens;
    private boolean useCache = false;

    public boolean hasMoreTokens() {

        if (useCache) {
            return hasMoreTokens;
        }

        useCache = true;
        if ((currentLine == null || currentLine.length() - 1 < currentIndex) && !lineItr.hasNext()) {
            hasMoreTokens = false;
            return false;
        }

        if (this.currentLine == null || this.currentLine.length() - 1 < currentIndex)  {
            currentLine = lineItr.next();
            currentIndex = 0;
        }

        char nextChar;
        do {
            if (this.currentLine.matches("^\\s*/\\*.*$")) {
                while (!this.currentLine.matches("^.*\\*/\\s*")) {
                    this.currentLine = lineItr.next();
                }
                this.currentLine = lineItr.next();
                this.currentIndex = 0;
            }
            this.currentLine = this.currentLine.replaceAll("//.*$", "");
            if (this.currentLine.length() <= this.currentIndex) {
                if (lineItr.hasNext()) {
                    this.currentLine = lineItr.next();
                    this.currentIndex = 0;
                } else {
                    hasMoreTokens = false;
                    return false;
                }
            }
            nextChar = this.currentLine.charAt(this.currentIndex);
            this.currentIndex++; 
        } while (Character.isWhitespace(nextChar));

        hasMoreTokens = true;
        return true;
    }

    private char nextChar() {
        return this.currentLine.charAt(this.currentIndex++);
    }

    public void advance() {
        useCache = false;
        char currentChar = this.currentLine.charAt(currentIndex - 1);
        this.currentToken = switch (currentChar) {
            case '{', '}', '(', ')', '[', ']', '.', ',', ';', '+', '-', '*', '/', '&', '|', '<', '>', '=', '~' -> new JackToken(TokenType.SYMBOL, String.valueOf(currentChar));
            case '"' -> {
                int startIndex = this.currentIndex;
                while (nextChar() != '"') {}
                yield new JackToken(TokenType.STRING_CONST, this.currentLine.substring(startIndex, this.currentIndex - 1));
            }
            default -> {
                if (Character.isLetter(currentChar)) {
                    int startIndex = this.currentIndex - 1;
                    while (Character.isLetterOrDigit(nextChar())) {}
                    this.currentIndex--;
                    String word = this.currentLine.substring(startIndex, this.currentIndex);
                    yield switch (word) {
                        case "class", "constructor", "function", "method", "field", "static", "var", "int", "char", "boolean", "void", "true", "false", "null", "this", "let", "do", "if", "else", "while", "return"  -> new JackToken(TokenType.KEYWORD, word);
                        default -> new JackToken(TokenType.IDENTIFIER, word);
                    };
                } else if (Character.isDigit(currentChar)) {
                    int startIndex = this.currentIndex;
                    while (Character.isDigit(nextChar())) {}
                    this.currentIndex--;
                    yield new JackToken(TokenType.INT_CONST, this.currentLine.substring(startIndex - 1, this.currentIndex));
                } 
                throw new RuntimeException("\"" + String.valueOf(currentChar) + "\"");
            }
        };
    }

    public TokenType tokenType() {
        return this.currentToken.tokenType();
    }

    public KeyWordType keyWord() {
        return switch(this.currentToken.value()) {
            case "class" -> KeyWordType.CLASS;
            case "method" -> KeyWordType.METHOD;
            case "function" -> KeyWordType.FUNCTION;
            case "constructor" -> KeyWordType.CONSTRUCTOR;
            case "int" -> KeyWordType.INT;
            case "boolean" -> KeyWordType.BOOLEAN;
            case "char" -> KeyWordType.CHAR;
            case "void" -> KeyWordType.VOID;
            case "var" -> KeyWordType.VAR;
            case "static" -> KeyWordType.STATIC;
            case "field" -> KeyWordType.FIELD;
            case "let" -> KeyWordType.LET;
            case "do" -> KeyWordType.DO;
            case "if" -> KeyWordType.IF;
            case "else" -> KeyWordType.ELSE;
            case "while" -> KeyWordType.WHILE;
            case "return" -> KeyWordType.RETURN;
            case "true" -> KeyWordType.TRUE;
            case "false" -> KeyWordType.FALSE;
            case "null" -> KeyWordType.NULL;
            case "this" -> KeyWordType.THIS;
            default -> throw new RuntimeException(this.currentToken.value());
        };
    }
    
    public String symbol() {
        return this.currentToken.value();
    }

    public String identifier() {
        return this.currentToken.value();
    }

    public int intVal() {
        return Integer.parseInt(this.currentToken.value());
    }

    public String stringVal() {
        return this.currentToken.value();
    }

    public boolean isSymbol(String... candidates) {
        return this.currentToken.tokenType() == TokenType.SYMBOL && Arrays.asList(candidates).contains(this.symbol());
    }

    public boolean isKeyWord() {
        return this.currentToken.tokenType() == TokenType.KEYWORD;
    }

    public boolean isKeyWord(KeyWordType... keys) {
        return this.currentToken.tokenType() == TokenType.KEYWORD && Arrays.asList(keys).contains(this.keyWord());
    }

    @Override
    public String toString() {
        return "JackTokenizer: " + this.currentToken.value() + " : \"" + this.currentLine + "\"";
    }
}
