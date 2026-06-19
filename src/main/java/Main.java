import java.util.Scanner;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;

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
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else if (input.equals("echo")) {
                System.out.println();
            } else if (input.startsWith("type ")) {
                String cmd = input.substring(5);
                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")) {
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
                String[] parts = input.split(" ");
                String cmd = parts[0];
                String pathStr = getExecutablePath(cmd);
                if (pathStr != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
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
}
