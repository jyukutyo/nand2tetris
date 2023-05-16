enum Command {
    ADD, SUB, NEG, EQ, GT, LT, AND, OR, NOT;

    public static Command of(String op) {
        return switch (op) {
            case "+" -> ADD;
            case "-" -> SUB;
            case "=" -> EQ;
            case "<" -> LT;
            case ">" -> GT;
            case "&" -> AND;
            case "|" -> OR;
            default -> throw new RuntimeException();
        };
    }
}