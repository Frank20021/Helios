package io.helios.parser;

public final class Languages {
    public static final String GO = "go";
    public static final String PYTHON = "python";
    public static final String JAVA = "java";
    public static final String JAVASCRIPT = "javascript";
    public static final String TYPESCRIPT = "typescript";
    public static final String TSX = "tsx";
    public static final String CPP = "cpp";
    public static final String C = "c";
    public static final String MARKDOWN = "markdown";
    public static final String TEXT = "text";

    private Languages() {
    }

    public static String fromPath(String relPath) {
        if (relPath == null) {
            return "";
        }
        int dot = relPath.lastIndexOf('.');
        String ext = dot < 0 ? "" : relPath.substring(dot).toLowerCase();
        return switch (ext) {
            case ".go" -> GO;
            case ".py" -> PYTHON;
            case ".java" -> JAVA;
            case ".js" -> JAVASCRIPT;
            case ".ts" -> TYPESCRIPT;
            case ".tsx" -> TSX;
            case ".cpp" -> CPP;
            case ".h" -> C;
            case ".md" -> MARKDOWN;
            case ".txt" -> TEXT;
            default -> "";
        };
    }

    public static boolean isCode(String language) {
        return switch (language) {
            case MARKDOWN, TEXT, "" -> false;
            default -> true;
        };
    }
}
