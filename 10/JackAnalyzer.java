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
            for (int i = 0; i < 3; i++) {
                if (!t.hasMoreTokens()) {
                    throw new RuntimeException();
                }
                t.advance();
                outputToken();
            }
            while (t.hasMoreTokens()) {
                t.advance();
                if (t.tokenType() == TokenType.KEYWORD) {
                    switch (t.keyWord()) {
                        case STATIC, FIELD -> compileClassVarDec();
                        case METHOD, CONSTRUCTOR, FUNCTION -> compileSubroutine();
                        default -> {}
                    }
                } else {
                    throw new RuntimeException(t.value() + ":" + t.line());
                }
            }
            output.add("</class>");
        }

        public void compileParameterList() {
            outputToken();
            output.add("<parameterList>");
            while (t.hasMoreTokens()) {
                t.advance();
                if (t.tokenType() == TokenType.SYMBOL && ")".equals(t.symbol())) {
                    break;
                }
                outputToken();
            }
            output.add("</parameterList>");
        }

        public void compileVarDec() {
            output.add("<varDec>");
            outputToken();
            while (t.hasMoreTokens()) {
                t.advance();
                outputToken();
                if (t.tokenType() == TokenType.SYMBOL && ";".equals(t.symbol())) {
                    break;
                }
            }
            output.add("</varDec>");
        }

        public void compileTerm() {
            output.add("<term>");
            TokenType previousType = t.tokenType();
            OUTER: while (t.hasMoreTokens()) {
                switch (t.tokenType()) {
                    case SYMBOL -> {
                        switch (t.symbol()) {
                            case "]", ")", ";", "<", ">", "=", "*", "/", "&", "|", "+" -> { 
                                break OUTER;
                            }
                        }
                        if (previousType == TokenType.IDENTIFIER && "(".equals(t.symbol())) {
                            outputToken();
                            compileExpressionList();
                            outputToken();
                        } else if ("[".equals(t.symbol()) || "(".equals(t.symbol())) {
                            outputToken();
                            t.advance();
                            compileExpression();
                            outputToken();
                        } else if ("-".equals(t.symbol()) || "~".equals(t.symbol())) {
                            compileTerm();
                        } else {
                            outputToken();
                        }
                    }
                    default -> {
                        outputToken();
                    } 
                } 
                previousType = t.tokenType();
                t.advance();
            }
            output.add("</term>");
        }

        public void compileExpressionList() {
            output.add("<expressionList>");
            while (t.hasMoreTokens()) {
                if (t.tokenType() == TokenType.SYMBOL && ")".equals(t.symbol())) {
                    break;
                } else if (t.tokenType() == TokenType.SYMBOL && ",".equals(t.symbol())) {
                    outputToken();
                }
                t.advance();
                if (t.tokenType() == TokenType.SYMBOL && ")".equals(t.symbol())) {
                    break;
                }                 
                compileExpression();
            }
            output.add("</expressionList>");            
        }

        public void compileExpression() {
            output.add("<expression>");
            do {
                if (t.tokenType() == TokenType.SYMBOL) {
                    switch (t.symbol()) {
                        case "+", "-", "*", "/", "&", "|", "<", ">" -> {
                            outputToken();
                            if (t.hasMoreTokens()) {
                                t.advance();
                            }
                            compileTerm();
                        }
                        case "=" -> {
                            if (t.hasMoreTokens()) {
                                t.advance();
                            }
                            compileTerm();
                        }                        
                        case "]", ")" -> {
                            output.add("</expression>");
                            return;
                        }
                        case ";" -> {
                            output.add("</expression>");
                            outputToken();
                            return;
                        }
                    }
                } else {
                    compileTerm();
                }
            } while (t.hasMoreTokens());
            output.add("</expression>");
        }

        public void compileReturn() {
            output.add("<returnStatement>");
            outputToken();
            t.hasMoreTokens();
            t.advance();
            if (t.tokenType() != TokenType.SYMBOL) {
                compileExpression();
            }
            outputToken();
            output.add("</returnStatement>");            
        }

        public void compileStatement() {
            if (t.tokenType() == TokenType.KEYWORD) {
                switch (t.keyWord()) {
                    case LET -> compileLet();
                    case IF -> compileIf();
                    case WHILE -> compileWhile();
                    case DO -> compileDo();
                    case RETURN -> compileReturn();
                    default -> {}
                }
            } else {
                throw new RuntimeException();
            }
        }

        public void compileLet() {
            output.add("<letStatement>");
            outputToken();
            if (t.hasMoreTokens()) {
                do {
                    if (t.tokenType() == TokenType.SYMBOL && ";".equals(t.symbol())) {
                        break;
                    }                
                    t.advance();
                    outputToken();
                    if (t.tokenType() == TokenType.SYMBOL && "[".equals(t.symbol())) {
                        if (t.hasMoreTokens()) {
                            t.advance();
                        }
                        compileExpression();
                        outputToken();
                    } else if (t.tokenType() == TokenType.SYMBOL && "=".equals(t.symbol())) {
                        compileExpression();
                    } 

                } while (t.hasMoreTokens());
            }
            output.add("</letStatement>");
        }

        public void compileIf() {
            output.add("<ifStatement>");
            output.add("</ifStatement>");
        }

        public void compileWhile() {
            output.add("<whileStatement>");
            outputToken();
            while (t.hasMoreTokens()) {
                if (t.tokenType() == TokenType.SYMBOL && "}".equals(t.symbol())) {
                    outputToken();
                    break;
                }
                t.advance();
                if (t.tokenType() == TokenType.SYMBOL && ("(".equals(t.symbol()))) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    compileExpression();
                    outputToken();
                } else if (t.tokenType() == TokenType.SYMBOL && "{".equals(t.symbol())) {
                    outputToken();
                    if (t.hasMoreTokens()) {
                        t.advance();
                        compileStatements();
                    }
                }                    
            }
            output.add("</whileStatement>");
            if (t.hasMoreTokens()) {
                t.advance();
            }
        }

        public void compileDo() {
            output.add("<doStatement>");
            outputToken();
            if (t.hasMoreTokens()) {
                do {
                    if (t.tokenType() == TokenType.SYMBOL && ";".equals(t.symbol())) {
                        break;
                    }                
                    t.advance();
                    outputToken();
                    if (t.tokenType() == TokenType.SYMBOL && "(".equals(t.symbol())) {
                        compileExpressionList();
                        outputToken();
                    } 
                } while (t.hasMoreTokens());
            }
            output.add("</doStatement>");
        }

        public void compileStatements() {
            output.add("<statements>");
            while (t.hasMoreTokens()) {
                if (t.tokenType() == TokenType.SYMBOL && ";".equals(t.symbol())) {
                    t.advance();
                }
                if (t.tokenType() == TokenType.SYMBOL && "}".equals(t.symbol())) {
                    break;
                }                    
                compileStatement();
            }
            output.add("</statements>");
        }

        public void compileSubroutineBody() {
            output.add("<subroutineBody>");
            outputToken();
            while (t.hasMoreTokens()) {
                t.advance();
                if (t.tokenType() == TokenType.KEYWORD && t.keyWord() == KeyWordType.VAR) {
                    compileVarDec();
                } else {
                    compileStatements();
                    outputToken();
                    break;
                }
            }
            output.add("</subroutineBody>");
        }

        public void compileSubroutine() {
            output.add("<subroutineDec>");
            outputToken();
            while (t.hasMoreTokens()) {
                t.advance();
                if (t.tokenType() == TokenType.SYMBOL && "(".equals(t.symbol())) {
                    compileParameterList();
                    outputToken();
                } else if (t.tokenType() == TokenType.SYMBOL && "{".equals(t.symbol())) {
                    compileSubroutineBody();
                    break;
                } else {
                    outputToken();
                }
            }
            output.add("</subroutineDec>");
            outputToken();
            if (t.hasMoreTokens()) {
                t.advance();
            }
        }

        public void compileClassVarDec() {
            output.add("<classVarDec>");
            outputToken();
            while (t.hasMoreTokens()) {
                t.advance();
                outputToken();
                if (t.tokenType() == TokenType.SYMBOL && ";".equals(t.symbol())) {
                    break;
                }
            }
            output.add("<classVarDec>");
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