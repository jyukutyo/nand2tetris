import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;


class VMTranslator {

    private static class Parser {

        private Iterator<Path> fileItr;
        private Path currentFile;
        private Iterator<String> itr;
        private String[] tokens;
        private String fileName;
        private boolean isDirectoryParsing;

        public String getFileName() {
            return fileName;
        }

        public boolean isDirectoryParsing() {
            return isDirectoryParsing;
        }

        public Parser(String pathString) {
            Path path = Paths.get(pathString);
            try {
                List<Path> files;
                if (Files.isDirectory(path)) {
                    files = Files.list(path).filter(p -> p.getFileName().toString().endsWith(".vm")).collect(Collectors.toList());
                    isDirectoryParsing = true;
                } else {
                    files = new ArrayList<>();
                    files.add(path);
                }
                fileItr = files.iterator();
                currentFile = fileItr.next();
                fileName = currentFile.getFileName().toString().replace(".vm", "");
                itr = getAllCodeLines(currentFile).iterator();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private List<String> getAllCodeLines(Path file) {
            try {
                List<String> allLines = Files.readAllLines(currentFile);
                return allLines
                    .stream()
                    .filter(line -> !line.startsWith("//"))
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.indexOf("//") < 0 ? line : line.substring(0, line.indexOf("//")))
                    .map(line -> line.replaceAll("^\\s+", ""))
                    .map(line -> line.replaceAll("\\s+$", ""))
                    .collect(Collectors.toList());            
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public boolean hasMoreElement() {
            if (itr.hasNext()) {
                return true;
            }
            if (fileItr.hasNext()) {
                currentFile = fileItr.next();
                fileName = currentFile.getFileName().toString().replace(".vm", "");                
                itr = getAllCodeLines(currentFile).iterator();
                return itr.hasNext();
            }
            return false;
        }

        public void advance() {
            tokens = itr.next().split(" ");
        }

        public CommandType commandType() {
            return of(tokens[0]);
        }

        public String arg1() {
            if (of(tokens[0]) == CommandType.C_ARITHMETIC) {
                return tokens[0];
            }
            return tokens[1];
        }

        public String arg2() {
            return tokens[2];
        }

        private CommandType of(String opcode) {
            return switch (opcode) {
                case "push" -> CommandType.C_PUSH;
                case "pop" -> CommandType.C_POP;
                case "label" -> CommandType.C_LABEL;
                case "goto" -> CommandType.C_GOTO;
                case "if-goto" -> CommandType.C_IF;
                case "function" -> CommandType.C_FUNCTION;
                case "return" -> CommandType.C_RETURN;
                case "call" -> CommandType.C_CALL;
                case "add", "sub", "neg", "and", "or", "not", "eq", "lt", "gt" -> CommandType.C_ARITHMETIC;
                default -> throw new RuntimeException(opcode);
            };
        }
    }

    private static class CodeWriter {
        private String fileName;
        private List<String> asmInsts = new ArrayList<>();
        private String currentFunctionName = "";
        private int returnIndex;
        private int jumpIndex = 0;

        public void writeInit() {
            add("@256");
            add("D=A");
            add("@SP");
            add("M=D");
            jumpToFunctionFor("Sys.init", 0);
        }

        private void add(String code) {
            asmInsts.add(code);
        }

        public void writeArithmetic(String opcode) {
            if ("neg".equals(opcode) || "not".equals(opcode)) {
                loadFromAddressInSPToDRegister();
                if ("neg".equals(opcode)) {
                    add("M=-D");
                } else if ("not".equals(opcode)) {
                    add("M=!D");
                }
                add("@SP");
                add("M=M+1");
            } else {
                // get 2 operands from stack
                loadFromAddressInSPToDRegister();

                add("@SP");
                add("M=M-1");
                add("A=M");

                if ("add".equals(opcode)) {
                    add("M=D+M");
                } else if ("sub".equals(opcode)) {
                    add("M=M-D");
                } else if ("and".equals(opcode)) {
                    add("M=D&M");
                } else if ("or".equals(opcode)) {
                    add("M=D|M");
                } else {
                    add("D=M-D");
                    add("@VMTranslator" + jumpIndex);
                    if ("eq".equals(opcode)) {
                        add("D;JEQ");
                    } else if ("lt".equals(opcode)) {
                        add("D;JLT");
                    } else if ("gt".equals(opcode)) {
                        add("D;JGT");
                    }
                    add("@SP");
                    add("A=M");
                    add("M=0");
                    add("@VMTranslator" + jumpIndex + 1);
                    add("0;JMP");
                    add("(" + "VMTranslator" + jumpIndex + ")");
                    add("@SP");
                    add("A=M");
                    add("M=-1");
                    add("(" + "VMTranslator" + jumpIndex + 1 + ")");
                    jumpIndex = jumpIndex + 2;
                }

                forwardSP();
            } 
        }

        public void writePushPop(CommandType opcode, String target, String operand) {
            if (opcode == CommandType.C_PUSH) {
                if ("constant".equals(target)) {
                    add("@" + operand);
                    add("D=A");
                    add("@SP");
                    add("A=M");
                    add("M=D");
                } else if ("static".equals(target)) {
                    add("@" + fileName + "." + operand);
                    add("D=M");
                    add("@SP");
                    add("A=M");
                    add("M=D");
                } else {
                    if ("local".equals(target)) {
                        add("@LCL");
                        add("D=M");
                    } else if ("argument".equals(target)) {
                        add("@ARG");
                        add("D=M");
                    } else if ("this".equals(target)) {
                        add("@THIS");
                        add("D=M");
                    } else if ("that".equals(target)) {
                        add("@THAT");
                        add("D=M");
                    } else if ("temp".equals(target)) {
                        add("@5");
                        add("D=A");
                    } else if ("pointer".equals(target)) {
                        add("@3");
                        add("D=A");
                    }
                    add("@" + operand);
                    add("A=D+A"); // base address + index
                    add("D=M");
                    add("@SP");
                    add("A=M");
                    add("M=D");
                }
                forwardSP();

            } else if (opcode == CommandType.C_POP) {
                if ("static".equals(target)) {
                    add("@" + fileName + "." + operand);
                    add("D=A");
                } else {
                    if ("local".equals(target)) {
                        add("@LCL");
                        add("D=M");

                    } else if ("argument".equals(target)) {
                        add("@ARG");
                        add("D=M");                    
                    } else if ("this".equals(target)) {
                        add("@THIS");
                        add("D=M");                    
                    } else if ("that".equals(target)) {
                        add("@THAT");
                        add("D=M");                    
                    } else if ("temp".equals(target)) {
                        add("@5");
                        add("D=A");                    
                    } else if ("pointer".equals(target)) {
                        add("@3");
                        add("D=A");
                    }
                    add("@" + operand);
                    add("D=D+A");
                }

                add("@R15");
                add("M=D");

                loadFromAddressInSPToDRegister();

                add("@R15");
                add("A=M");
                add("M=D");

            }            
        }

        public void writeLabel(String labelName) {
            add("(" + (currentFunctionName.isEmpty() ? labelName : currentFunctionName + "$" + labelName) + ")");
        }

        public void writeGoto(String labelName) {
            add("@" + (currentFunctionName.isEmpty() ? labelName : currentFunctionName + "$" + labelName));
            add("0;JMP");
        }

        public void writeIf(String labelName) {
            loadFromAddressInSPToDRegister();
            add("@" + (currentFunctionName.isEmpty() ? labelName : currentFunctionName + "$" + labelName));
            add("D;JNE");
        }

        public void writeCall(String functionName, String numArgs) {
            add("@" + currentFunctionName + "$ret" + returnIndex);
            jumpToFunctionFor(functionName, Integer.valueOf(numArgs));
            add("(" + currentFunctionName + "$ret" + returnIndex + ")");
            returnIndex++;
        }

        public void writeReturn() {
            // store return value to R14
            add("@SP");
            add("M=M-1");
            add("A=M");
            add("D=M");
            add("@R14");
            add("M=D");

            // store LCL value to R15                
            add("@LCL");
            add("D=M");
            add("@R15");
            add("M=D");

            // LCL-1 points caller THAT value
            add("M=M-1");
            add("A=M");
            add("D=M");
            add("@THAT");
            add("M=D");

            // LCL-2 points caller THIS value
            add("@R15");
            add("M=M-1");
            add("A=M");
            add("D=M");
            add("@THIS");
            add("M=D");

            // LCL-5 points return address
            add("@R15");
            add("M=M-1");
            add("M=M-1");
            add("M=M-1");
            add("A=M");
            add("D=M");

            // store return address to R13
            add("@R13");
            add("M=D");

            // set return value on ARG address
            add("@R14");
            add("D=M");
            add("@ARG");
            add("A=M");
            add("M=D");

            // store ARG value to R14
            add("@ARG");
            add("D=M");
            add("@R14");
            add("M=D");

            // LCL-4 points caller LCL value
            add("@R15");
            add("M=M+1");
            add("A=M");
            add("D=M");
            add("@LCL");
            add("M=D");

            // LCL-3 points caller ARG value
            add("@R15");
            add("M=M+1");
            add("A=M");
            add("D=M");
            add("@ARG");
            add("M=D");

            // set SP value with R14 value that stores callee ARG value
            add("@R14");
            add("D=M");
            add("@SP");
            add("M=D");
            add("M=M+1");

            // set return address with R13 value that stores caller return address
            add("@R13");
            add("A=M");
            add("0;JMP");
        }

        public void close() {
            asmInsts.forEach(System.out::println);            
        }

        private void writeFunction(String functionName, String numLocals) {
            add("(" + functionName + ")");
            add("@SP");
            add("A=M");
            add("D=A");
            add("@LCL");
            add("M=D");
            for (int i = 0; i < Integer.valueOf(numLocals); i++) {
                add("@SP");
                add("A=M");
                add("M=0");
                add("@SP");
                add("M=M+1");
            }
            currentFunctionName = functionName;
        }

        private void loadFromAddressInSPToDRegister() {
            add("@SP");
            add("M=M-1");
            add("A=M");
            add("D=M");
        } 

        private void jumpToFunctionFor(String functionName, int argCount) {
            add("D=A");
            add("@SP");
            add("A=M");
            add("M=D");
            add("@SP");
            add("M=M+1");
            saveValueToStackFrom("@LCL");
            saveValueToStackFrom("@ARG");
            saveValueToStackFrom("@THIS");
            saveValueToStackFrom("@THAT");

            add("@SP");
            add("D=M");
            add("@R15");
            add("M=D");
            for (int i = 0; i < argCount + 5; i++) {
                add("M=M-1");
            }
            add("D=M");
            add("@ARG");
            add("M=D");

            add("@SP");
            add("D=M");
            add("@LCL");
            add("M=D");

            add("@" + functionName);
            add("0;JMP");
        }
        
        private void saveValueToStackFrom(String SymbolName) {
            add(SymbolName);
            add("D=M");
            add("@SP");
            add("A=M");
            add("M=D");
            add("@SP");
            add("M=M+1");
        }

        private void forwardSP() {
            add("@SP");
            add("M=M+1");
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
    }

    private static enum CommandType {
        C_ARITHMETIC, C_PUSH, C_POP, C_LABEL, C_GOTO, C_IF, C_FUNCTION, C_RETURN, C_CALL
    }

    public static void main(String... args) throws Exception {
        Parser p = new Parser(args[0]);
        CodeWriter cw = new CodeWriter();
        if (p.isDirectoryParsing()) {
            cw.writeInit();
        }
        while (p.hasMoreElement()) {
            p.advance();
            CommandType c = p.commandType();
            switch (c) {
                case C_ARITHMETIC -> cw.writeArithmetic(p.arg1());
                case C_CALL -> cw.writeCall(p.arg1(), p.arg2());
                case C_FUNCTION -> cw.writeFunction(p.arg1(), p.arg2());
                case C_GOTO -> cw.writeGoto(p.arg1());
                case C_IF -> cw.writeIf(p.arg1());
                case C_LABEL -> cw.writeLabel(p.arg1());
                case C_POP, C_PUSH -> {
                    cw.setFileName(p.getFileName());
                    cw.writePushPop(c, p.arg1(), p.arg2());
                }
                case C_RETURN -> cw.writeReturn();
                default -> throw new RuntimeException();
            }
        }
        cw.close();
        /*
         Path path = Paths.get(args[0]);

        List<String> asmInsts = new ArrayList<>();
        List<Path> files;
        if (Files.isDirectory(path)) {
            files = Files.list(path).filter(p -> p.getFileName().toString().endsWith(".vm")).collect(Collectors.toList());
            add("@256");
            add("D=A");
            add("@SP");
            add("M=D");

            jumpToFunctionFor("Sys.init", 0));
        } else {
            files = new ArrayList<>();
            files.add(path);
        }
        
        for (Path p: files) {
            translate(p));
        }
        
        asmInsts.forEach(System.out::println);
        */
     }
/*
    private static List<String> translate(Path vmFilePath) throws Exception {
        String className = vmFilePath.getFileName().toString().replace(".vm", "");
        List<String> allLines = Files.readAllLines(vmFilePath);

        List<String> allCodeLines = allLines
          .stream()
          .filter(line -> !line.startsWith("//"))
          .filter(line -> !line.isEmpty())
          .map(line -> line.indexOf("//") < 0 ? line : line.substring(0, line.indexOf("//")))
          .map(line -> line.replaceAll("^\\s+", ""))
          .map(line -> line.replaceAll("\\s+$", ""))
          .collect(Collectors.toList());

        List<String> asmInsts = new ArrayList<>();
        int jumpIndex = 0;
        int returnIndex = 0;
        String functionName = "";
        for (String line: allCodeLines) {
            String[] tokens = line.split(" ");
            String opcode = tokens[0];

            add("// " + line);
            if ("push".equals(opcode)) {
                String target = tokens[1];
                String operand = tokens[2];
                if ("constant".equals(target)) {
                    add("@" + operand);
                    add("D=A");
                    add("@SP");
                    add("A=M");
                    add("M=D");
                } else if ("static".equals(target)) {
                    add("@" + className + "." + operand);
                    add("D=M");
                    add("@SP");
                    add("A=M");
                    add("M=D");
                } else {
                    if ("local".equals(target)) {
                        add("@LCL");
                        add("D=M");
                    } else if ("argument".equals(target)) {
                        add("@ARG");
                        add("D=M");
                    } else if ("this".equals(target)) {
                        add("@THIS");
                        add("D=M");
                    } else if ("that".equals(target)) {
                        add("@THAT");
                        add("D=M");
                    } else if ("temp".equals(target)) {
                        add("@5");
                        add("D=A");
                    } else if ("pointer".equals(target)) {
                        add("@3");
                        add("D=A");
                    }
                    add("@" + operand);
                    add("A=D+A"); // base address + index
                    add("D=M");
                    add("@SP");
                    add("A=M");
                    add("M=D");
                }
                forwardSP());

            } else if ("pop".equals(opcode)) {
                String target = tokens[1];
                String operand = tokens[2];

                if ("static".equals(target)) {
                    add("@" + className + "." + operand);
                    add("D=A");
                } else {
                    if ("local".equals(target)) {
                        add("@LCL");
                        add("D=M");

                    } else if ("argument".equals(target)) {
                        add("@ARG");
                        add("D=M");                    
                    } else if ("this".equals(target)) {
                        add("@THIS");
                        add("D=M");                    
                    } else if ("that".equals(target)) {
                        add("@THAT");
                        add("D=M");                    
                    } else if ("temp".equals(target)) {
                        add("@5");
                        add("D=A");                    
                    } else if ("pointer".equals(target)) {
                        add("@3");
                        add("D=A");
                    }
                    add("@" + operand);
                    add("D=D+A");
                }

                add("@R15");
                add("M=D");

                loadFromAddressInSPToDRegister());

                add("@R15");
                add("A=M");
                add("M=D");

            } else if ("neg".equals(opcode) || "not".equals(opcode)) {
                loadFromAddressInSPToDRegister());
                if ("neg".equals(opcode)) {
                    add("M=-D");
                } else if ("not".equals(opcode)) {
                    add("M=!D");
                }
                add("@SP");
                add("M=M+1");
            } else if ("label".equals(opcode)) {
                String target = tokens[1];
                add("(" + (functionName.isEmpty() ? target : functionName + "$" + target) + ")");
            } else if ("if-goto".equals(opcode) || "goto".equals(opcode)) {
                String target = tokens[1];         
                if ("if-goto".equals(opcode)) {
                    loadFromAddressInSPToDRegister());
                }                
                add("@" + (functionName.isEmpty() ? target : functionName + "$" + target));
                if ("if-goto".equals(opcode)) {
                    add("D;JNE");
                } else if ("goto".equals(opcode)) {
                    add("0;JMP");
                }
            } else if ("call".equals(opcode)) {
                String target = tokens[1];
                int argCount = Integer.valueOf(tokens[2]);

                add("@" + functionName + "$ret" + returnIndex);
                jumpToFunctionFor(target, argCount));
                add("(" + functionName + "$ret" + returnIndex + ")");
                returnIndex++;
            } else if ("function".equals(opcode)) {
                String target = tokens[1];
                int localVarCount = Integer.valueOf(tokens[2]);
                add("(" + target + ")");
                add("@SP");
                add("A=M");
                add("D=A");
                add("@LCL");
                add("M=D");
                for (int i = 0; i < localVarCount; i++) {
                    add("@SP");
                    add("A=M");
                    add("M=0");
                    add("@SP");
                    add("M=M+1");
                }
                functionName = target;
            } else if ("return".equals(opcode)) {
                // store return value to R14
                add("@SP");
                add("M=M-1");
                add("A=M");
                add("D=M");
                add("@R14");
                add("M=D");

                // store LCL value to R15                
                add("@LCL");
                add("D=M");
                add("@R15");
                add("M=D");

                // LCL-1 points caller THAT value
                add("M=M-1");
                add("A=M");
                add("D=M");
                add("@THAT");
                add("M=D");

                // LCL-2 points caller THIS value
                add("@R15");
                add("M=M-1");
                add("A=M");
                add("D=M");
                add("@THIS");
                add("M=D");

                // LCL-5 points return address
                add("@R15");
                add("M=M-1");
                add("M=M-1");
                add("M=M-1");
                add("A=M");
                add("D=M");

                // store return address to R13
                add("@R13");
                add("M=D");

                // set return value on ARG address
                add("@R14");
                add("D=M");
                add("@ARG");
                add("A=M");
                add("M=D");

                // store ARG value to R14
                add("@ARG");
                add("D=M");
                add("@R14");
                add("M=D");

                // LCL-4 points caller LCL value
                add("@R15");
                add("M=M+1");
                add("A=M");
                add("D=M");
                add("@LCL");
                add("M=D");

                // LCL-3 points caller ARG value
                add("@R15");
                add("M=M+1");
                add("A=M");
                add("D=M");
                add("@ARG");
                add("M=D");

                // set SP value with R14 value that stores callee ARG value
                add("@R14");
                add("D=M");
                add("@SP");
                add("M=D");
                add("M=M+1");

                // set return address with R13 value that stores caller return address
                add("@R13");
                add("A=M");
                add("0;JMP");
            } else {
                // get 2 operands from stack
                loadFromAddressInSPToDRegister());

                add("@SP");
                add("M=M-1");
                add("A=M");

                if ("add".equals(opcode)) {
                    add("M=D+M");
                } else if ("sub".equals(opcode)) {
                    add("M=M-D");
                } else if ("and".equals(opcode)) {
                    add("M=D&M");
                } else if ("or".equals(opcode)) {
                    add("M=D|M");
                } else {
                    add("D=M-D");
                    add("@VMTranslator" + jumpIndex);
                    if ("eq".equals(opcode)) {
                        add("D;JEQ");
                    } else if ("lt".equals(opcode)) {
                        add("D;JLT");
                    } else if ("gt".equals(opcode)) {
                        add("D;JGT");
                    }
                    add("@SP");
                    add("A=M");
                    add("M=0");
                    add("@VMTranslator" + jumpIndex + 1);
                    add("0;JMP");
                    add("(" + "VMTranslator" + jumpIndex + ")");
                    add("@SP");
                    add("A=M");
                    add("M=-1");
                    add("(" + "VMTranslator" + jumpIndex + 1 + ")");
                    jumpIndex = jumpIndex + 2;
                }

                forwardSP());
            } 
        }
        return asmInsts;
    }

    private static List<String> saveValueToStackFrom(String SymbolName) {
        List<String> l = new ArrayList<>();
        l.add(SymbolName);
        l.add("D=M");
        l.add("@SP");
        l.add("A=M");
        l.add("M=D");
        l.add("@SP");
        l.add("M=M+1");
        return l;
    }

    private static List<String> jumpToFunctionFor(String functionName, int argCount) {
        List<String> l = new ArrayList<>();

        l.add("D=A");
        l.add("@SP");
        l.add("A=M");
        l.add("M=D");
        l.add("@SP");
        l.add("M=M+1");
        l.addAll(saveValueToStackFrom("@LCL"));
        l.addAll(saveValueToStackFrom("@ARG"));
        l.addAll(saveValueToStackFrom("@THIS"));
        l.addAll(saveValueToStackFrom("@THAT"));

        l.add("@SP");
        l.add("D=M");
        l.add("@R15");
        l.add("M=D");
        for (int i = 0; i < argCount + 5; i++) {
            l.add("M=M-1");
        }
        l.add("D=M");
        l.add("@ARG");
        l.add("M=D");

        l.add("@SP");
        l.add("D=M");
        l.add("@LCL");
        l.add("M=D");

        l.add("@" + functionName);
        l.add("0;JMP");

        return l;
    }

    private static List<String> loadFromAddressInSPToDRegister() {
        List<String> l = new ArrayList<>();
        l.add("@SP");
        l.add("M=M-1");
        l.add("A=M");
        l.add("D=M");
        return l;
    }

    private static List<String> forwardSP() {
        List<String> l = new ArrayList<>();
        l.add("@SP");
        l.add("M=M+1");
        return l;
    }
*/
}