package io.helios.parser;

public record Passage(
        String symbolName,
        String symbolType,
        int startingLine,
        int endingLine,
        String content,
        String language,
        int tokenCount
) {
    public static final String TYPE_FUNCTION = "function";
    public static final String TYPE_METHOD = "method";
    public static final String TYPE_CLASS = "class";
    public static final String TYPE_STRUCT = "struct";
    public static final String TYPE_INTERFACE = "interface";
    public static final String TYPE_ENUM = "enum";
    public static final String TYPE_HEADING = "heading";
    public static final String TYPE_CHUNK = "chunk";
}
