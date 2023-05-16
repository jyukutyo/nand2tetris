import java.util.*;
import java.nio.file.*;
import java.rmi.server.SocketSecurityException;
import java.util.stream.*;

class JackCompiler {
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
                Files.write(Paths.get(p.getParent().toString(), fileName.substring(0, fileName.lastIndexOf(".")) + ".vm"), ce.getOutput(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);           
            }
        }
    }

    private static class CompilationEngine {
        private JackTokenizer t;
        private VMWriter w;
        private SymbolTable s;
        private String className;
        private int labelCount;

        public CompilationEngine(JackTokenizer t) {
            this.t = t;
            this.w = new VMWriter();
            this.s = new SymbolTable();
        }

        public List<String> getOutput() {
            return this.w.getOutput();
        }

        public void compileClass() {
            while (t.hasMoreTokens()) {
                t.advance();
                if (t.isKeyWord()) {
                    switch (t.keyWord()) {
                        case CLASS -> {
                            t.hasMoreTokens();
                            t.advance();
                            this.className = t.identifier();
                        }
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
                }
            }
        }

        public void compileClassVarDec() {
            emitVar();
        }

        public void compileSubroutineDec() {
            KeyWordType type = t.keyWord();
            s.startSubroutine();
            if (type == KeyWordType.METHOD) {
                s.define("this", this.className, AttributeKind.ARG);                   
            }
            String functionName = "";
            do {
                if (t.isSymbol("(")) {
                    t.advance();
                    compileParameterList();
                } else if (t.isSymbol("{")) {
                    compileSubroutineBody(functionName, type);
                    break;
                } else if (t.tokenType() == TokenType.IDENTIFIER) {
                    // function name
                    functionName = this.className + "." + t.identifier();
                }
                t.advance();
            } while (t.hasMoreTokens());
        }

        public void compileParameterList() {
            String typeName = null;
            while (t.hasMoreTokens()) {
                if (t.isSymbol(")")) {
                    break;
                }
                switch (t.tokenType()) {
                    case KEYWORD -> {
                        typeName = t.keyWord().name().toLowerCase();
                    }
                    case IDENTIFIER -> {
                        if (typeName == null) {
                            typeName = t.identifier();
                        } else {
                            s.define(t.identifier(), typeName, AttributeKind.ARG);
                            typeName = null;
                        }
                    }
                }
                t.advance();
            };
        }

        public void compileSubroutineBody(String functionName, KeyWordType type) {
            do {
                if (t.isSymbol("}")) {
                    break;
                } else if (t.isSymbol("{")) {
                } else if (t.isKeyWord(KeyWordType.VAR)) {
                    compileVarDec();
                } else {
                    w.writeFunction(functionName, s.varCount(AttributeKind.VAR));
                    if (type == KeyWordType.CONSTRUCTOR) {
                        w.writePush(Segment.CONSTANT, s.varCount(AttributeKind.FIELD));
                        w.writeCall("Memory.alloc", 1);
                        w.writePop(Segment.POINTER, 0);
                    } else if (type == KeyWordType.METHOD) {
                        w.writePush(Segment.ARGUMENT, 0);
                        w.writePop(Segment.POINTER, 0);
                    }
                    compileStatements();
                }
                t.advance();
            } while (t.hasMoreTokens());
        }

        private void emitVar() {
            AttributeKind kind = AttributeKind.NONE;
            String typeName = "";
            do {
                if (t.isSymbol(";")) {
                    break;
                }
                switch (t.tokenType()) {
                    case KEYWORD -> {
                        if (t.isKeyWord(KeyWordType.VAR, KeyWordType.STATIC, KeyWordType.FIELD)) {
                            kind = AttributeKind.valueOf(t.keyWord().name());
                        } else {
                            typeName = t.keyWord().name().toLowerCase();
                        }
                    }
                    case IDENTIFIER -> {
                        if (typeName.isEmpty()) {
                            typeName = t.identifier();
                        } else {
                            s.define(t.identifier(), typeName, kind);
                        }
                    }
                }
                t.advance();
            } while (t.hasMoreTokens());
        }

        public void compileVarDec() {
            emitVar();
        }

        public void compileStatements() {
            while (true) {
                if (t.isSymbol("}")) {
                    break;
                }
                compileStatement();
            }
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
            String varName = null;
            boolean isArray = false;
            do {
                if (t.isSymbol("=")) {
                    t.advance();
                    compileExpression();
                } else if (t.isSymbol("[")) {
                    t.advance();
                    Segment segment = switch (s.kindOf(varName)) {
                        case ARG -> Segment.ARGUMENT;
                        case VAR -> Segment.LOCAL;
                        case FIELD -> Segment.THIS;
                        case STATIC -> Segment.STATIC;
                        default -> throw new RuntimeException(varName);
                    };                    
                    w.writePush(segment, s.indexOf(varName));
                    compileExpression();
                    w.writeArithmetic(Command.ADD);
                    isArray = true;
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                } else if (t.isKeyWord(KeyWordType.LET)) {
                    t.advance();
                    varName = t.identifier();
                    t.hasMoreTokens();
                }

                if (t.isSymbol(";")) {
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }
                t.advance();
            } while (t.hasMoreTokens());

            if (isArray) {
                w.writePop(Segment.TEMP, 0);
                w.writePop(Segment.POINTER, 1);
                w.writePush(Segment.TEMP, 0);
                w.writePop(Segment.THAT, 0);
            } else {
                Segment segment = switch (s.kindOf(varName)) {
                    case ARG -> Segment.ARGUMENT;
                    case VAR -> Segment.LOCAL;
                    case FIELD -> Segment.THIS;
                    case STATIC -> Segment.STATIC;
                    default -> throw new RuntimeException(varName);
                };
                w.writePop(segment, s.indexOf(varName));
            }
        }

        public void compileDo() {
            String functionName = "";
            String varName = null;
            do {
                if (t.isSymbol(";")) {
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }                 
                if (t.isSymbol("(")) {
                    // subroutine call
                    t.advance();
                    int count = 0;
                    if (functionName.contains(".")) {
                        String target = functionName.substring(0, functionName.indexOf("."));
                        Segment segment = switch (s.kindOf(target)) {
                            case ARG -> Segment.ARGUMENT;
                            case VAR -> Segment.LOCAL;
                            case FIELD -> Segment.THIS;
                            case STATIC -> Segment.STATIC;
                            case NONE -> null;
                            default -> throw new RuntimeException(t.toString());
                        };
                        if (segment != null) {
                            w.writePush(segment, s.indexOf(target));
                            count++;                            
                        }
                        String className = s.kindOf(target) == AttributeKind.NONE ? target : s.typeOf(target);
                        functionName = className + functionName.substring(functionName.indexOf("."));
                    } else {
                        // method call for this
                        functionName = this.className + "." + functionName;
                        w.writePush(Segment.POINTER, 0);
                        count++;
                    }
                    count += compileExpressionList();
                    w.writeCall(functionName, count);
                    if (t.hasMoreTokens()){
                        t.advance();
                    }
                } else if (t.isSymbol(".")) {
                    functionName += ".";
                } else if (t.tokenType() == TokenType.IDENTIFIER) {
                    functionName += t.identifier();
                }
                t.advance();
            } while (t.hasMoreTokens());
            w.writePop(Segment.TEMP, 0);
        }

        public void compileReturn() {
            do {
                if (t.isKeyWord(KeyWordType.RETURN)) {
                } else if (t.isSymbol(";")) {
                    w.writePush(Segment.CONSTANT, 0);
                    w.writeReturn();
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                } else {
                    compileExpression();
                    if (t.isSymbol(";")) {
                        w.writeReturn();
                        if (t.hasMoreTokens()) {
                            t.advance();
                        }
                        break;
                    }
                }
                t.advance();
            } while (t.hasMoreTokens());
        }

        public void compileExpression() {
            String op = null;
            boolean isFirst = true;
            do {
                // token is op?
                if (t.isSymbol("+", "&", "|", "<", ">", "=", "*", "/") || (!isFirst && t.isSymbol("-"))) {
                    op = t.symbol();
                    t.advance();
                } else {
                    // term
                    compileTerm();
                    if ("*".equals(op)) {
                        w.writeCall("Math.multiply", 2);
                    } else if ("/".equals(op)) {
                        w.writeCall("Math.divide", 2);
                    } else if (op != null) {
                        w.writeArithmetic(Command.of(op));
                    }
                    op = null;
                    if (t.isSymbol(")", "]", ";", ",")) {
                        break;
                    }
                }
                isFirst = false;
            } while (t.hasMoreTokens());
        }

        public void compileTerm() {

            TokenType previousType = t.tokenType();
            String name = "";
            while (t.hasMoreTokens())  {
                if (t.isSymbol(";", ",", ")", "=", "+", "<", "&", ">", "]", "/", "*")) {
                    break;
                }
                if (previousType == TokenType.IDENTIFIER && t.isSymbol("(")) {
                    // subroutine call
                    t.advance();
                    int count = 0;

                    if (name.contains(".")) {
                        String target = name.substring(0, name.indexOf("."));
                        if (s.kindOf(target) == AttributeKind.NONE) {
                            name = target + name.substring(name.indexOf("."));
                        } else {
                            name = s.typeOf(target) + name.substring(name.indexOf("."));
                            count++;                            
                        }
                    } else {
                        // method call for this
                        name = this.className + "." + name;
                        w.writePush(Segment.POINTER, 0);
                        count++;
                    }

                    count += compileExpressionList();                    

                    w.writeCall(name, count);

                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                } else {
                    switch (t.tokenType()) {
                        case INT_CONST -> w.writePush(Segment.CONSTANT, t.intVal());
                        case STRING_CONST -> {
                            w.writePush(Segment.CONSTANT, t.stringVal().length());
                            w.writeCall("String.new", 1);
                            for (char ch: t.stringVal().toCharArray()) {
                                w.writePop(Segment.TEMP, 0);
                                w.writePush(Segment.TEMP, 0);
                                w.writePush(Segment.CONSTANT, ch);
                                w.writeCall("String.appendChar", 2);
                            }                            
                        }
                        case KEYWORD -> {
                            switch (t.keyWord()) {
                                case NULL, FALSE -> w.writePush(Segment.CONSTANT, 0);
                                case TRUE -> {
                                    w.writePush(Segment.CONSTANT, 1);
                                    w.writeArithmetic(Command.NEG);
                                }
                                case THIS -> {
                                    w.writePush(Segment.POINTER, 0);
                                }
                                default -> {}
                            }
                        }
                        case IDENTIFIER -> {
                            if (s.kindOf(t.identifier()) != AttributeKind.NONE) {
                                Segment segment = switch (s.kindOf(t.identifier())) {
                                    case ARG -> Segment.ARGUMENT;
                                    case VAR -> Segment.LOCAL;
                                    case FIELD -> Segment.THIS;
                                    case STATIC -> Segment.STATIC;
                                    default -> throw new RuntimeException(t.toString());
                                };
                                w.writePush(segment, s.indexOf(t.identifier()));
                            }
                            name += t.identifier();
                        }
                        case SYMBOL -> {
                            if (t.isSymbol("(")) {
                                t.advance();
                                compileExpression();
                                if (t.hasMoreTokens()) {
                                    t.advance();
                                }
                            } else if (t.isSymbol("[")) {
                                t.advance();
                                compileExpression();
                                w.writeArithmetic(Command.ADD);
                                w.writePop(Segment.POINTER, 1);
                                w.writePush(Segment.THAT, 0);
                                if (t.hasMoreTokens()) {
                                    t.advance();
                                }
                            } else if (t.isSymbol("-", "~")) {
                                String op = t.symbol();
                                // unary op
                                t.advance();
                                compileTerm();
                                if ("-".equals(op)) {
                                    w.writeArithmetic(Command.NEG);
                                } else {
                                    w.writeArithmetic(Command.NOT);
                                }
                            } else if (t.isSymbol(".")) {
                                name += ".";
                            }
                        }
                    }
                }
                if (t.isSymbol("]")) {
                    t.advance();
                    break;
                } else if (t.isSymbol(")", ";")) {
                    break;
                }
                previousType = t.tokenType();
                t.advance();
                if (t.isSymbol("-")) {
                    break;
                }
            } 
        }

        public int compileExpressionList() {
            int count = 0;
            do {
                if (t.isSymbol(")")) {
                    break;
                }
                if (!t.isSymbol(",")) {
                    compileExpression();
                    count++;
                }
                if (t.isSymbol(")")) {
                    break;
                }
                t.advance();
            } while (t.hasMoreTokens());
            return count;
        }

        public void compileIf() {
            String elseLabel = "ELSE" + labelCount;
            labelCount++;
            String followingLabel = "IFEND" + labelCount;
            boolean inIfBlock = true;
            do {
                if (t.isSymbol("(")) {
                    t.advance();
                    compileExpression();
                    w.writeArithmetic(Command.NOT);
                    w.writeIf(elseLabel);
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                } else if (t.isSymbol("{")) {
                    t.advance();
                    compileStatements();
                    if (inIfBlock) {
                        w.writeGoto(followingLabel);
                        w.writeLabel(elseLabel);
                        inIfBlock = false;
                    }
                }

                if (t.isSymbol("}")) {
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    if (!t.isKeyWord(KeyWordType.ELSE)) {
                        break;
                    }
                } else {
                    t.advance();                  
                }
            } while (t.hasMoreTokens());
            w.writeLabel(followingLabel);  
            labelCount++;
        }

        public void compileWhile() {
            String whileLabel = "WHILE" + labelCount;
            labelCount++;
            String followingLabel = "WHILEEND" + labelCount;
            w.writeLabel(whileLabel);
            while (t.hasMoreTokens()) {
                if (t.isSymbol("(")) {
                    t.advance();
                    compileExpression();
                    w.writeArithmetic(Command.NOT);
                    w.writeIf(followingLabel);
                } else if (t.isSymbol("{")) {
                    t.advance();
                    compileStatements();
                    w.writeGoto(whileLabel);
                } else if (t.isSymbol(")")) {
                    t.advance();
                    continue;
                } 
                if (t.isSymbol("}")) {
                    w.writeLabel(followingLabel);
                    if (t.hasMoreTokens()) {
                        t.advance();
                    }
                    break;
                }                
                t.advance();                  
            }
            labelCount++;
        }
   }
}