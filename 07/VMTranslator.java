import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;


class VMTranslator {

    public static void main(String... args) throws Exception {
        Path vmFilePath = Paths.get(args[0]);
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
        for (String line: allCodeLines) {
            String[] tokens = line.split(" ");
            String opcode = tokens[0];
            asmInsts.add("// " + line);
            if ("push".equals(opcode)) {
                String target = tokens[1];
                String operand = tokens[2];
                if ("constant".equals(target)) {
                    asmInsts.add("@" + operand);
                    asmInsts.add("D=A");
                    asmInsts.add("@SP");
                    asmInsts.add("A=M");
                    asmInsts.add("M=D");
                } else {
                    if ("local".equals(target)) {
                        asmInsts.add("@LCL");
                        asmInsts.add("D=M");
                    } else if ("argument".equals(target)) {
                        asmInsts.add("@ARG");
                        asmInsts.add("D=M");
                    } else if ("this".equals(target)) {
                        asmInsts.add("@THIS");
                        asmInsts.add("D=M");
                    } else if ("that".equals(target)) {
                        asmInsts.add("@THAT");
                        asmInsts.add("D=M");
                    } else if ("temp".equals(target)) {
                        asmInsts.add("@5");
                        asmInsts.add("D=A");
                    }                    
                    asmInsts.add("@" + operand);
                    asmInsts.add("A=D+A");
                    asmInsts.add("D=M");
                    asmInsts.add("@SP");
                    asmInsts.add("A=M");
                    asmInsts.add("M=D");
                }
                asmInsts.add("@SP");
                asmInsts.add("M=M+1");

            } else if ("pop".equals(opcode)) {
                String target = tokens[1];
                String operand = tokens[2];

                if ("local".equals(target)) {
                    asmInsts.add("@LCL");
                    asmInsts.add("D=M");

                } else if ("argument".equals(target)) {
                    asmInsts.add("@ARG");
                    asmInsts.add("D=M");                    
                } else if ("this".equals(target)) {
                    asmInsts.add("@THIS");
                    asmInsts.add("D=M");                    
                } else if ("that".equals(target)) {
                    asmInsts.add("@THAT");
                    asmInsts.add("D=M");                    
                } else if ("temp".equals(target)) {
                    asmInsts.add("@5");
                    asmInsts.add("D=A");                    
                }
                asmInsts.add("@" + operand);
                asmInsts.add("D=D+A");
                asmInsts.add("@R15");
                asmInsts.add("M=D");

                asmInsts.add("@SP");
                asmInsts.add("M=M-1");
                asmInsts.add("A=M");
                asmInsts.add("D=M");

                asmInsts.add("@R15");
                asmInsts.add("A=M");
                asmInsts.add("M=D");

            } else if ("neg".equals(opcode) || "not".equals(opcode)) {
                asmInsts.add("@SP");
                asmInsts.add("M=M-1");
                asmInsts.add("A=M");
                asmInsts.add("D=M");
                if ("neg".equals(opcode)) {
                    asmInsts.add("M=-D");
                } else if ("not".equals(opcode)) {
                    asmInsts.add("M=!D");
                }
                asmInsts.add("@SP");
                asmInsts.add("M=M+1");

            } else {
                asmInsts.add("@SP");
                asmInsts.add("M=M-1");
                asmInsts.add("A=M");
                asmInsts.add("D=M");
                asmInsts.add("@SP");
                asmInsts.add("M=M-1");
                asmInsts.add("A=M");

                if ("add".equals(opcode)) {
                    asmInsts.add("M=D+M");
                } else if ("sub".equals(opcode)) {
                    asmInsts.add("M=M-D");
                } else if ("and".equals(opcode)) {
                    asmInsts.add("M=D&M");
                } else if ("or".equals(opcode)) {
                    asmInsts.add("M=D|M");
                } else {
                    asmInsts.add("D=M-D");
                    asmInsts.add("@VMTranslator" + jumpIndex);
                    if ("eq".equals(opcode)) {
                        asmInsts.add("D;JEQ");
                    } else if ("lt".equals(opcode)) {
                        asmInsts.add("D;JLT");
                    } else if ("gt".equals(opcode)) {
                        asmInsts.add("D;JGT");
                    }
                    asmInsts.add("@SP");
                    asmInsts.add("A=M");
                    asmInsts.add("M=0");
                    asmInsts.add("@VMTranslator" + jumpIndex + 1);
                    asmInsts.add("0;JMP");
                    asmInsts.add("(" + "VMTranslator" + jumpIndex + ")");
                    asmInsts.add("@SP");
                    asmInsts.add("A=M");
                    asmInsts.add("M=-1");
                    asmInsts.add("(" + "VMTranslator" + jumpIndex + 1 + ")");
                    jumpIndex = jumpIndex + 2;
                }

                asmInsts.add("@SP");
                asmInsts.add("M=M+1");
            } 
       }
       asmInsts.forEach(System.out::println);
    }
}