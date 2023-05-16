import java.util.*;
import java.nio.file.*;
import java.util.stream.*;

class JackAnalyzer {
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
            CompilationEngine ce = new CompilationEngine(t);
            try {
                ce.compileClass();
            } finally {
                String fileName = p.getFileName().toString();
                Files.write(Paths.get(p.getParent().toString(), fileName.substring(0, fileName.lastIndexOf(".")) + "_.xml"), ce.getOutput(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);           
            }
        }
    }

    private static class CompilationEngine {
        private JackTokenizer t;
        private List<String> output;
        public CompilationEngine(JackTokenizer t) {
            this.t = t;
            this.output = new ArrayList<>();
        }

        public List<String> getOutput() {
            return this.output;
        }

        public void compileClass() {
            output.add("<class>");
            while (t.hasMoreTokens()) {
                t.advance();
                if (t.isKeyWord()) {
                    switch (t.keyWord()) {
                        case CLASS -> outputToken();
                        case STATIC, FIELD -> {
                            if (t.hasMoreTokens()) {
                                compileClassVarDec();
                            }
                        }
                        case METHOD, CONSTRUCTOR, FUNCTION -> {
                            if (t.hasMoreTokens()) {
                                compileSubroutineDec();
                            }
                        }
                        default -> {}
                    }
                } else {
                    outputToken();
                }
            }
            output.add("</class>");
        }

        public void compileClassVarDec() {
            output.add("<classVarDec>");
            do {
                outputToken();
                t.advance();
                if (t.isSymbol(";")) {
                    break;
                }
            } while (t.hasMoreTokens());
            outputToken();
            t.advance();
            output.add("</classVarDec>");
        }

        public void compileSubroutineDec() {
            output.add("<subroutineDec>");
            do {
                if (t.isSymbol("(")) {
                    outputToken();
                    t.advance();
                    compileParameterList();
                    outputToken();
                } else if (t.isSymbol("{")) {
                    compileSubroutineBody();
                    break;
                } else {
                    outputToken();
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</subroutineDec>");
        }

        public void compileParameterList() {
            output.add("<parameterList>");
            while (t.hasMoreTokens()) {
                if (t.isSymbol(")")) {
                    break;
                }
                outputToken();
                t.advance();
            };
            output.add("</parameterList>");
        }

        public void compileSubroutineBody() {
            output.add("<subroutineBody>");
            do {
                if (t.isSymbol("}")) {
                    outputToken();
                    break;
                } else if (t.isSymbol("{")) {
                    outputToken();
                } else if (t.isKeyWord(KeyWordType.VAR)) {
                    compileVarDec();
                } else {
                    compileStatements();
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</subroutineBody>");
        }

        public void compileVarDec() {
            output.add("<varDec>");
            do {
                outputToken();
                if (t.isSymbol(";")) {
                    break;
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</varDec>");
        }

        public void compileStatements() {
            output.add("<statements>");
            while (true) {
                if (t.isSymbol("}")) {
                    break;
                }
                compileStatement();
            }
            output.add("</statements>");
        }

        public void compileStatement() {
            if (t.tokenType() == TokenType.KEYWORD) {
                KeyWordType k = t.keyWord();
                if (t.hasMoreTokens()) {
                    switch (t.keyWord()) {
                        case LET -> compileLet();
                        case IF -> compileIf();
                        case WHILE -> compileWhile();
                        case DO -> compileDo();
                        case RETURN -> compileReturn();
                        default -> {}
                    }
                }
            } else {
                throw new RuntimeException(t.toString());
            }
        }

        public void compileLet() {
            output.add("<letStatement>");
            do {
                if (t.isSymbol("=")) {
                    outputToken();
                    t.advance();
                    compileExpression();
                } else if (t.isSymbol("[")) {
                    outputToken();
                    t.advance();
                    compileExpression();
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                } else {
                    outputToken();
                }
                if (t.isSymbol(";")) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</letStatement>");
            System.out.println("let end" + t);                            
        }

        public void compileDo() {
            output.add("<doStatement>");
            do {
                if (t.isSymbol(";")) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }                 
                if (t.isSymbol("(")) {
                    // subroutine call
                    outputToken();
                    t.advance();
                    compileExpressionList();
                    outputToken();
                    if (t.hasMoreTokens()){
                        t.advance();
                    }
                } else {
                    outputToken();
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</doStatement>");
        }

        public void compileReturn() {
            output.add("<returnStatement>");
            do {
                if (t.isKeyWord(KeyWordType.RETURN)) {
                    outputToken();
                } else if (t.isSymbol(";")) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                } else {
                    compileExpression();
                    if (t.isSymbol(";")) {
                        outputToken();
                        if (t.hasMoreTokens()) {
                            t.advance();
                        }
                        break;
                    }
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</returnStatement>");    
        }

        public void compileExpression() {
            output.add("<expression>");
            do {
                // token is op?
                if (t.isSymbol("+", "-", "*", "/", "&", "|", "<", ">", "=")) {
                    outputToken();
                    t.advance();
                } else {
                    // term
                    compileTerm();
                    if (t.isSymbol(")", "]", ";", ",")) {
                        break;
                    }
                }
            } while (t.hasMoreTokens());
            output.add("</expression>");
            System.out.println("exp end" + t);
        }

        public void compileTerm() {
            output.add("<term>");
            TokenType previousType = t.tokenType();
            while (t.hasMoreTokens())  {
                if (t.isSymbol(";", ",", ")", "=", "+", "<", "&", ">", "-", "]", "/")) {
                    break;
                }
                if (previousType == TokenType.IDENTIFIER && t.isSymbol("(")) {
                    // subroutine call
                    outputToken();
                    t.advance();
                    compileExpressionList();
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                } else {
                    switch (t.tokenType()) {
                        case INT_CONST, STRING_CONST, KEYWORD, IDENTIFIER -> outputToken();
                        case SYMBOL -> {
                            if (t.isSymbol("[", "(")) {
                                outputToken();
                                t.advance();
                                compileExpression();
                                outputToken();
                                if (t.hasMoreTokens()) {
                                    t.advance();
                                }
                            } else if (t.isSymbol("-", "~")) {
                                // unary op
                                outputToken();
                                t.advance();
                                compileTerm();
                            } else {
                                outputToken();
                            }
                        }
                    }
                }
                if (t.isSymbol("]")) {
                    outputToken();
                    t.advance();
                    break;
                } else if (t.isSymbol(")")) {
                    break;
                }
                previousType = t.tokenType();
                t.advance();
            } 
            output.add("</term>");
        }

        public void compileExpressionList() {
            output.add("<expressionList>");
            do {
                if (t.isSymbol(")")) {
                    break;
                }
                compileExpression();
                if (t.isSymbol(")")) {
                    break;
                }
                if (t.isSymbol(",")) {
                    outputToken();
                }
                t.advance();
            } while (t.hasMoreTokens());
            output.add("</expressionList>");            
        }

        public void compileIf() {
            output.add("<ifStatement>");
            do {
                if (t.isSymbol("(")) {
                    outputToken();
                    t.advance();
                    compileExpression();
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                } else if (t.isSymbol("{")) {
                    outputToken();
                    t.advance();
                    compileStatements();
                } else {
                    outputToken();
                }
                if (t.isSymbol("}")) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }                
                t.advance();                  
            } while (t.hasMoreTokens());
            output.add("</ifStatement>");
        }

        public void compileWhile() {
            output.add("<whileStatement>");
            while (t.hasMoreTokens()) {
                if (t.isSymbol("(")) {
                    outputToken();
                    t.advance();
                    compileExpression();
                    outputToken();
                } else if (t.isSymbol("{")) {
                    outputToken();
                    t.advance();
                    compileStatements();
                } else if (t.isSymbol(")")) {
                    t.advance();
                    continue;
                } else {
                    outputToken();
                }
                if (t.isSymbol("}")) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }                
                t.advance();                  
            }
            output.add("</whileStatement>");
        }

        private void outputToken() {
            String line = startTag(t.tokenType());
            switch (t.tokenType()) {
                case KEYWORD -> line += t.keyWord().name().toLowerCase();
                case IDENTIFIER -> line += t.identifier();
                case SYMBOL -> {
                    line += switch (t.symbol()) {
                        case "<" -> "&lt;";
                        case ">" -> "&gt;";
                        case "&" -> "&amp;";
                        default -> t.symbol();
                    };
                }
                case INT_CONST -> line += t.intVal();
                case STRING_CONST -> line += t.stringVal();
            }
            line += endTag(t.tokenType());
            output.add(line);
        }

        private String startTag(TokenType tt) {
            return switch (tt) {
                case STRING_CONST -> "<stringConstant>";
                case INT_CONST -> "<integerConstant>";
                default -> "<" + tt.name().toLowerCase() + ">";
            };
        }

        private String endTag(TokenType tt) {
            return switch (tt) {
                case STRING_CONST -> "</stringConstant>";
                case INT_CONST -> "</integerConstant>";
                default -> "</" + tt.name().toLowerCase() + ">";
            };
        }
    }
}