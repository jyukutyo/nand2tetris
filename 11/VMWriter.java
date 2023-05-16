import java.util.*;

class VMWriter {
    private List<String> output = new ArrayList<>();

    public VMWriter() {
    }

    public void writePush(Segment segment, int index) {
        output.add("push " + segment.name().toLowerCase() + " " + index);
    }

    public void writePop(Segment segment, int index) {
        output.add("pop " + segment.name().toLowerCase() + " " + index);
    }

    public void writeArithmetic(Command command) {
        output.add(command.name().toLowerCase());
    }

    public void writeLabel(String label)  {
        output.add("label " + label);
    }

    public void writeGoto(String label) {
        output.add("goto " + label);
    }

    public void writeIf(String label) {
        output.add("if-goto " + label);
    }

    public void writeCall(String name, int nArgs) {
        output.add("call " + name + " " + nArgs);
    }

    public void writeFunction(String name, int nLocals) {
        output.add("function " + name + " " + nLocals);
    }

    public void writeReturn() {
        output.add("return");
    }

    public void close() {
        this.output = Collections.unmodifiableList(output);
    }

    public List<String> getOutput() {
        return this.output;
    }
}