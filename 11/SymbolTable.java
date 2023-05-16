import java.util.*;

class SymbolTable {

    private Map<String, VariableInfo> classScope = new HashMap<>();

    private Map<String, VariableInfo> methodScope;

    public void startSubroutine() {
        this.methodScope = new HashMap<>();
    }

    public void define(String name, String type, AttributeKind kind) {
        switch (kind) {
            case STATIC, FIELD -> {
                VariableInfo v = new VariableInfo(name, type, kind, varCount(kind));
                classScope.put(name, v);
            }
            case ARG, VAR -> {
                VariableInfo v = new VariableInfo(name, type, kind, varCount(kind));
                methodScope.put(name, v);
            }
            default -> throw new RuntimeException();
        }
    }

    public int varCount(AttributeKind kind) {
        return switch (kind) {
            case STATIC, FIELD -> {
                yield (int) this.classScope.values().stream().filter(v -> v.getAttributeKind() == kind).count();
            }
            case ARG, VAR -> {
                yield (int) this.methodScope.values().stream().filter(v -> v.getAttributeKind() == kind).count();
            }
            default -> throw new RuntimeException();
        };      
    }

    public AttributeKind kindOf(String name) {
        VariableInfo v = this.methodScope.getOrDefault(name, this.classScope.get(name));
        if (v == null) {
            return AttributeKind.NONE;
        }
        return v.getAttributeKind();
    }

    public String typeOf(String name) {
        VariableInfo v = this.methodScope.getOrDefault(name, this.classScope.get(name));
        if (v == null) {
            throw new RuntimeException();
        }
        return v.getTypeName();
    }

    public int indexOf(String name) {
        VariableInfo v = this.methodScope.getOrDefault(name, this.classScope.get(name));
        if (v == null) {
            throw new RuntimeException();
        }
        return v.getIndex();
    }

    @Override
    public String toString() {
        return this.classScope.toString() + " " + this.methodScope.toString();
    }

    private static class VariableInfo {
        private String varName;
        private String typeName;
        private AttributeKind attributeKind;
        private int index;
        public VariableInfo(String varName, String typeName, AttributeKind attributeKind, int index) {
            this.varName = varName;
            this.typeName = typeName;
            this.attributeKind = attributeKind;
            this.index = index;
        }

        public String getVarName() {
            return this.varName;
        }

        public String getTypeName() {
            return this.typeName;
        }

        public AttributeKind getAttributeKind() {
            return this.attributeKind;
        }

        public int getIndex() {
            return this.index;
        }

        @Override
        public String toString() {
            return varName + " " + typeName + " " + attributeKind + " " + index;
        }
    }

}