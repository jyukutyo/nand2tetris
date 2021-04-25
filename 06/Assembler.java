import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;


class Assembler {

    private static Map<String, String> symbolTable = new HashMap<>();
    static {
        symbolTable.put("SP", String.format("%016d", Long.valueOf(Long.toBinaryString(0L))));
        symbolTable.put("LCL", String.format("%016d", Long.valueOf(Long.toBinaryString(1L))));
        symbolTable.put("ARG", String.format("%016d", Long.valueOf(Long.toBinaryString(2L))));
        symbolTable.put("THIS", String.format("%016d", Long.valueOf(Long.toBinaryString(3L))));
        symbolTable.put("THAT", String.format("%016d", Long.valueOf(Long.toBinaryString(4L))));
        symbolTable.put("R0", String.format("%016d", Long.valueOf(Long.toBinaryString(0L))));
        symbolTable.put("R1", String.format("%016d", Long.valueOf(Long.toBinaryString(1L))));
        symbolTable.put("R2", String.format("%016d", Long.valueOf(Long.toBinaryString(2L))));
        symbolTable.put("R3", String.format("%016d", Long.valueOf(Long.toBinaryString(3L))));
        symbolTable.put("R4", String.format("%016d", Long.valueOf(Long.toBinaryString(4L))));
        symbolTable.put("R5", String.format("%016d", Long.valueOf(Long.toBinaryString(5L))));
        symbolTable.put("R6", String.format("%016d", Long.valueOf(Long.toBinaryString(6L))));
        symbolTable.put("R7", String.format("%016d", Long.valueOf(Long.toBinaryString(7L))));
        symbolTable.put("R8", String.format("%016d", Long.valueOf(Long.toBinaryString(8L))));
        symbolTable.put("R9", String.format("%016d", Long.valueOf(Long.toBinaryString(9L))));
        symbolTable.put("R10", String.format("%016d", Long.valueOf(Long.toBinaryString(10L))));
        symbolTable.put("R11", String.format("%016d", Long.valueOf(Long.toBinaryString(11L))));
        symbolTable.put("R12", String.format("%016d", Long.valueOf(Long.toBinaryString(12L))));
        symbolTable.put("R13", String.format("%016d", Long.valueOf(Long.toBinaryString(13L))));
        symbolTable.put("R14", String.format("%016d", Long.valueOf(Long.toBinaryString(14L))));
        symbolTable.put("R15", String.format("%016d", Long.valueOf(Long.toBinaryString(15L))));
        symbolTable.put("SCREEN", String.format("%016d", Long.valueOf(Long.toBinaryString(16384L))));
        symbolTable.put("KBD", String.format("%016d", Long.valueOf(Long.toBinaryString(24576L))));
    }

    private static long memoryIndex = 16L;

    public static String toBinary(String aInst) {
        return String.format("%016d", Long.valueOf(Long.toBinaryString(Long.valueOf(aInst.substring(1)))));
    }

    public static void main(String... args) throws Exception {
        Path asmFilePath = Paths.get(args[0]);
        List<String> allLines = Files.readAllLines(asmFilePath);

        List<String> allCodeLines = allLines
          .stream()
          .filter(line -> !line.startsWith("//"))
          .filter(line -> !line.isEmpty())
          .map(line -> line.indexOf("//") < 0 ? line : line.substring(0, line.indexOf("//")))
          .map(line -> line.replaceAll("^\\s+", ""))
          .map(line -> line.replaceAll("\\s+$", ""))
          .collect(Collectors.toList());

        int instIndex = 0;
        for (String line: allCodeLines) {
            if (line.startsWith("(")) {
                // label
                String labelName = line.substring(1, line.length() - 1);
                symbolTable.putIfAbsent(labelName, String.format("%016d", Long.valueOf(Long.toBinaryString(instIndex))));
            } else {
                instIndex++;
            }
        }
        List<String> allInstsLines = allCodeLines
          .stream()
          .filter(line -> !line.startsWith("("))
          .collect(Collectors.toList());

        List<String> machineInsts = new ArrayList<>();
        for (String line: allInstsLines) {
            if (line.startsWith("@")) {
                // A inst or variable
                if (line.substring(1, 2).matches("[0-9]")) {
                    // A inst
                    machineInsts.add(toBinary(line));
                } else {
                    // variable
                    String variableName = line.substring(1);
                    if (!symbolTable.containsKey(variableName)) {
                        symbolTable.put(variableName, String.format("%016d", Long.valueOf(Long.toBinaryString(memoryIndex++))));
                    }
                    machineInsts.add(symbolTable.get(variableName));
                }
            } else {
                // C inst
                int eqIndex = line.indexOf("=");
                String dest;
                String destTarget = eqIndex < 0 ? "" : line.substring(0, eqIndex);
                switch (destTarget) {
                    case "M":
                        dest = "001";
                        break;
                    case "D":
                        dest = "010";
                        break;
                    case "MD":
                        dest = "011";
                        break;
                    case "A":
                        dest = "100";
                        break;
                    case "AM":
                        dest = "101";
                        break;
                    case "AD":
                        dest = "110";
                        break;
                    case "AMD":
                        dest = "111";
                        break;
                    default:
                        dest = "000";
                }
                
                int semiColonIndex = line.indexOf(";", eqIndex);
                String compTarget = line.substring(eqIndex + 1, semiColonIndex < 0 ? line.length() : semiColonIndex);
                boolean aFlag = compTarget.contains("M");
                String comp;
                if (aFlag) {
                    switch (compTarget) {
                        case "M":
                            comp = "1110000";
                            break;
                        case "!M":
                            comp = "1110001";
                            break;
                        case "-M":
                            comp = "1110011";
                            break;
                        case "M+1":
                            comp = "1110111";
                            break;
                        case "M-1":
                            comp = "1110010";
                            break;
                        case "D+M":
                            comp = "1000010";
                            break;
                        case "D-M":
                            comp = "1010011";
                            break;
                        case "M-D":
                            comp = "1000111";
                            break;
                        case "D&M":
                            comp = "1000000";
                            break;
                        case "D|M":
                            comp = "1010101";
                            break;
                        default:
                            throw new RuntimeException();
                    }

                } else {
                    switch (compTarget) {
                        case "0":
                            comp = "0101010";
                            break;
                        case "1":
                            comp = "0111111";
                            break;
                        case "-1":
                            comp = "0111010";
                            break;
                        case "D":
                            comp = "0001100";
                            break;
                        case "A":
                            comp = "0110000";
                            break;
                        case "!D":
                            comp = "0001101";
                            break;
                        case "!A":
                            comp = "0110001";
                            break;
                        case "-D":
                            comp = "0001111";
                            break;
                        case "-A":
                            comp = "0110011";
                            break;
                        case "D+1":
                            comp = "0011111";
                            break;
                        case "A+1":
                            comp = "0110111";
                            break;
                        case "D-1":
                            comp = "0001110";
                            break;
                        case "A-1":
                            comp = "0110010";
                            break;
                        case "D+A":
                            comp = "0000010";
                            break;
                        case "D-A":
                            comp = "0010011";
                            break;
                        case "A-D":
                            comp = "0000111";
                            break;
                        case "D&A":
                            comp = "0000000";
                            break;
                        case "D|A":
                            comp = "0010101";
                            break;
                        default:
                            throw new RuntimeException();

                    }
                }

                String jump;
                String jumpTarget = line.contains(";") ? line.substring(semiColonIndex + 1) : "";
                switch (jumpTarget) {
                    case "JMP":
                        jump = "111";
                        break;
                    case "JGT":
                        jump = "001";
                        break;
                    case "JEQ":
                        jump = "010";
                        break;
                    case "JGE":
                        jump = "011";
                        break;
                    case "JLT":
                        jump = "100";
                        break;
                    case "JNE":
                        jump = "101";
                        break;
                    case "JLE":
                        jump = "110";
                        break;
                    default:
                        jump = "000";
                }
                String cInst = "111" + comp + dest + jump;
                machineInsts.add(cInst);
            }
        }
        machineInsts.forEach(System.out::println);
    }
}