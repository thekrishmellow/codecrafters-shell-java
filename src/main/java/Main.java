import java.util.Scanner;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static class Job {
        int id;
        long pid;
        String command;
        Process process;

        Job(int id, long pid, String command, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }
    private static final List<Job> jobsList = new ArrayList<>();
    private static int getNextJobNumber() {
        if (jobsList.isEmpty()) {
            return 1;
        }
        int maxId = 0;
        for (Job job : jobsList) {
            if (job.id > maxId) {
                maxId = job.id;
            }
        }
        return maxId + 1;
    }
    private static void reapJobs(boolean printAll) {
        if (jobsList.isEmpty()) {
            return;
        }
        int currentId = -1;
        int previousId = -1;
        for (int i = jobsList.size() - 1; i >= 0; i--) {
            Job j = jobsList.get(i);
            if (currentId == -1) {
                currentId = j.id;
            } else if (previousId == -1) {
                previousId = j.id;
                break;
            }
        }
        List<Job> toRemove = new ArrayList<>();
        for (Job job : jobsList) {
            boolean alive = job.process.isAlive();
            String status = alive ? "Running" : "Done";
            if (printAll || !alive) {
                char marker = ' ';
                if (job.id == currentId) {
                    marker = '+';
                } else if (job.id == previousId) {
                    marker = '-';
                }
                String displayCommand = job.command;
                if (!alive) {
                    if (displayCommand.endsWith("&")) {
                        displayCommand = displayCommand.substring(0, displayCommand.length() - 1).trim();
                    }
                }
                System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, status, displayCommand);
            }
            if (!alive) {
                toRemove.add(job);
            }
        }
        jobsList.removeAll(toRemove);
    }
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            reapJobs(false);
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine();
            List<String> pipeSegments = splitOnPipe(input);
            if (pipeSegments.size() >= 2) {
                List<List<String>> cmds = new ArrayList<>();
                for (String seg : pipeSegments) {
                    List<String> parsed = parseArguments(seg.trim());
                    if (!parsed.isEmpty()) cmds.add(parsed);
                }
                
                if (cmds.size() >= 2) {
                    boolean allExternal = true;
                    boolean firstIsBuiltin = false;
                    boolean restAreExternal = true;
                    
                    if (isBuiltin(cmds.get(0).get(0))) {
                        firstIsBuiltin = true;
                        allExternal = false;
                    }
                    
                    for (int i = 1; i < cmds.size(); i++) {
                        if (isBuiltin(cmds.get(i).get(0))) {
                            allExternal = false;
                            restAreExternal = false;
                        }
                    }

                    try {
                        if (allExternal) {
                            List<ProcessBuilder> builders = new ArrayList<>();
                            for (int i = 0; i < cmds.size(); i++) {
                                ProcessBuilder pb = new ProcessBuilder(cmds.get(i));
                                pb.directory(new File(System.getProperty("user.dir")));
                                if (i == 0) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                if (i == cmds.size() - 1) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                builders.add(pb);
                            }
                            List<Process> pipeline = ProcessBuilder.startPipeline(builders);
                            for (Process p : pipeline) {
                                p.waitFor();
                            }
                        } else if (firstIsBuiltin && restAreExternal) {
                            List<ProcessBuilder> builders = new ArrayList<>();
                            for (int i = 1; i < cmds.size(); i++) {
                                ProcessBuilder pb = new ProcessBuilder(cmds.get(i));
                                pb.directory(new File(System.getProperty("user.dir")));
                                if (i == cmds.size() - 1) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                builders.add(pb);
                            }
                            List<Process> pipeline;
                            if (builders.size() == 1) {
                                pipeline = List.of(builders.get(0).start());
                            } else {
                                pipeline = ProcessBuilder.startPipeline(builders);
                            }
                            try (java.io.PrintStream ps = new java.io.PrintStream(pipeline.get(0).getOutputStream())) {
                                executeBuiltinToStream(cmds.get(0), ps);
                            }
                            for (Process p : pipeline) {
                                p.waitFor();
                            }
                        } else {
                            if (cmds.size() == 2) {
                                boolean leftIsBuiltin = isBuiltin(cmds.get(0).get(0));
                                boolean rightIsBuiltin = isBuiltin(cmds.get(1).get(0));
                                if (!leftIsBuiltin && rightIsBuiltin) {
                                    ProcessBuilder pbLeft = new ProcessBuilder(cmds.get(0));
                                    pbLeft.directory(new File(System.getProperty("user.dir")));
                                    pbLeft.redirectInput(ProcessBuilder.Redirect.INHERIT);
                                    pbLeft.redirectOutput(new File("/dev/null"));
                                    pbLeft.redirectError(ProcessBuilder.Redirect.INHERIT);
                                    Process leftProcess = pbLeft.start();
                                    executeBuiltinToStream(cmds.get(1), System.out);
                                    leftProcess.waitFor();
                                } else if (leftIsBuiltin && rightIsBuiltin) {
                                    executeBuiltinToStream(cmds.get(1), System.out);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                }
            } else if (input.equals("exit")) {
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
                List<String> echoParts = parseArguments(input.substring(5));
                String outPath = null;
                boolean isErrorRedirect = false;
                boolean isAppend = false;
                for (int i = 0; i < echoParts.size(); i++) {
                    if (echoParts.get(i).equals(">") || echoParts.get(i).equals("1>")) {
                        if (i + 1 < echoParts.size()) outPath = echoParts.get(i + 1);
                        echoParts.subList(i, echoParts.size()).clear();
                        break;
                    } else if (echoParts.get(i).equals(">>") || echoParts.get(i).equals("1>>")) {
                        if (i + 1 < echoParts.size()) outPath = echoParts.get(i + 1);
                        isAppend = true;
                        echoParts.subList(i, echoParts.size()).clear();
                        break;
                    } else if (echoParts.get(i).equals("2>")) {
                        if (i + 1 < echoParts.size()) outPath = echoParts.get(i + 1);
                        isErrorRedirect = true;
                        echoParts.subList(i, echoParts.size()).clear();
                        break;
                    } else if (echoParts.get(i).equals("2>>")) {
                        if (i + 1 < echoParts.size()) outPath = echoParts.get(i + 1);
                        isErrorRedirect = true;
                        isAppend = true;
                        echoParts.subList(i, echoParts.size()).clear();
                        break;
                    }
                }
                String output = String.join(" ", echoParts);
                if (outPath != null && !isErrorRedirect) {
                    if (isAppend) {
                        Files.writeString(Path.of(outPath), output + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        Files.writeString(Path.of(outPath), output + "\n");
                    }
                } else if (outPath != null && isErrorRedirect) {
                    if (isAppend) {
                        Files.writeString(Path.of(outPath), "", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        Files.writeString(Path.of(outPath), "");
                    }
                    System.out.println(output);
                } else {
                    System.out.println(output);
                }
            } else if (input.equals("echo")) {
                System.out.println();
            } else if (input.equals("jobs")) {
                reapJobs(true);
            } else if (input.startsWith("type ")) {
                String cmdArgs = input.substring(5);
                List<String> typeParts = parseArguments(cmdArgs);
                if (typeParts.isEmpty()) continue;
                String cmd = typeParts.get(0);
                if (isBuiltin(cmd)) {
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

                boolean runInBackground = false;
                if (parts.get(parts.size() - 1).equals("&")) {
                    runInBackground = true;
                    parts.remove(parts.size() - 1);
                }
                if (parts.isEmpty()) continue;
                String outFile = null;
                String errFile = null;
                boolean isAppend = false;
                boolean isErrAppend = false;
                for (int i = 0; i < parts.size(); i++) {
                    if (parts.get(i).equals(">") || parts.get(i).equals("1>")) {
                        if (i + 1 < parts.size()) outFile = parts.get(i + 1);
                        parts.subList(i, parts.size()).clear();
                        break;
                    } else if (parts.get(i).equals(">>") || parts.get(i).equals("1>>")) {
                        if (i + 1 < parts.size()) outFile = parts.get(i + 1);
                        isAppend = true;
                        parts.subList(i, parts.size()).clear();
                        break;
                    } else if (parts.get(i).equals("2>")) {
                        if (i + 1 < parts.size()) errFile = parts.get(i + 1);
                        parts.subList(i, parts.size()).clear();
                        break;
                    } else if (parts.get(i).equals("2>>")) {
                        if (i + 1 < parts.size()) errFile = parts.get(i + 1);
                        isErrAppend = true;
                        parts.subList(i, parts.size()).clear();
                        break;
                    }
                }
                String cmd = parts.get(0);
                String pathStr = getExecutablePath(cmd);
                if (pathStr != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(System.getProperty("user.dir")));
                        if (outFile != null) {
                            if (isAppend) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outFile)));
                            } else {
                                pb.redirectOutput(new File(outFile));
                            }
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        } else if (errFile != null) {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            if (isErrAppend) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errFile)));
                            } else {
                                pb.redirectError(new File(errFile));
                            }
                        } else {
                            pb.inheritIO();
                        }
                        Process p = pb.start();
                        if (runInBackground) {
                            int jobNum = getNextJobNumber();
                            System.out.println("[" + jobNum + "] " + p.pid());
                            jobsList.add(new Job(jobNum, p.pid(), input.trim(), p));
                        } else {
                            p.waitFor();
                        }
                    } catch (Exception e) {
                        System.out.println(cmd + ": " + e.getMessage());
                    }
                } else {
                    String commandName = runInBackground ? input.substring(0, input.lastIndexOf('&')).trim() : input;
                    System.out.println(commandName + ": command not found");
                }
            }
        }
    }
    private static List<String> splitOnPipe(String input) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && !inSingle) {
                current.append(c);
                if (i + 1 < input.length()) current.append(input.charAt(++i));
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
            } else if (c == '|' && !inSingle && !inDouble) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        segments.add(current.toString());
        return segments;
    }
    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")
                || cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs");
    }
    private static void executeBuiltinToStream(List<String> parts, java.io.PrintStream out) {
        String cmd = parts.get(0);
        if (cmd.equals("echo")) {
            if (parts.size() > 1) {
                out.println(String.join(" ", parts.subList(1, parts.size())));
            } else {
                out.println();
            }
        } else if (cmd.equals("type")) {
            for (int i = 1; i < parts.size(); i++) {
                String target = parts.get(i);
                if (isBuiltin(target)) {
                    out.println(target + " is a shell builtin");
                } else {
                    String path = getExecutablePath(target);
                    if (path != null) {
                        out.println(target + " is " + path);
                    } else {
                        out.println(target + ": not found");
                    }
                }
            }
        } else if (cmd.equals("pwd")) {
            out.println(System.getProperty("user.dir"));
        } else if (cmd.equals("jobs")) {
            // Delegate to normal reapJobs logic via System.out swap
            java.io.PrintStream old = System.out;
            System.setOut(out);
            reapJobs(true);
            System.setOut(old);
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
            if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"' || next == '$' || next == '`') {
                            currentArg.append(next);
                            i++;
                            inWord = true;
                        } else {
                            currentArg.append(c);
                            inWord = true;
                        }
                    } else {
                        currentArg.append(c);
                        inWord = true;
                    }
                } else {
                    if (i + 1 < input.length()) {
                        currentArg.append(input.charAt(i + 1));
                        i++;
                        inWord = true;
                    }
                }
            } else if (c == '\'' && !inDoubleQuotes) {
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