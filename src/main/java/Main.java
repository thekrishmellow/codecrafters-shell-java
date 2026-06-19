import java.util.Scanner;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        // System.out.print("$ ");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine();
            if (input.equals("exit")) {
                break;
            } else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (input.startsWith("cd ")) {
                String dirString = input.substring(3);
                if (dirString.startsWith("~")) {
                    String home = System.getenv("HOME");
                    if (home != null) {
                        dirString = home + dirString.substring(1);
                    }
                }
                Path currentDir = Path.of(System.getProperty("user.dir"));
                Path newDir = currentDir.resolve(dirString).normalize();
                if (Files.isDirectory(newDir)) {
                    System.setProperty("user.dir", newDir.toString());
                } else {
                    System.out.println("cd: " + dirString + ": No such file or directory");
                }
            } else if (input.startsWith("echo ")) {
                String cmdArgs = input.substring(5);
                System.out.println(String.join(" ", parseArguments(cmdArgs)));
            } else if (input.equals("echo")) {
                System.out.println();
            } else if (input.startsWith("type ")) {
                String cmd = input.substring(5);
                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("cd")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String pathStr = getExecutablePath(cmd);
                    if (pathStr != null) {
                        System.out.println(cmd + " is " + pathStr);
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            } else {
                List<String> parts = parseArguments(input);
                if (parts.isEmpty()) continue;
                String cmd = parts.get(0);
                String pathStr = getExecutablePath(cmd);
                if (pathStr != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(System.getProperty("user.dir")));
                        pb.inheritIO();
                        Process p = pb.start();
                        p.waitFor();
                    } catch (Exception e) {
                        System.out.println(cmd + ": " + e.getMessage());
                    }
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }
    }

    private static String getExecutablePath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path path = Path.of(dir, command);
                if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                    return path.toString();
                }
            }
        }
        return null;
    }

    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean inWord = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                inWord = true;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                inWord = true;
            } else if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (inWord) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                    inWord = false;
                }
            } else {
                currentArg.append(c);
                inWord = true;
            }
        }

        if (inWord) {
            args.add(currentArg.toString());
        }

        return args;
    }
}
