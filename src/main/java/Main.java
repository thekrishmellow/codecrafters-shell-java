import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        // System.out.print("$ ");
        System.out.print("$ ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();
        System.out.println(input + ": command not found");

         while (true) {
            System.out.print("$ ");
            String inpu = scanner.nextLine();
            if (inpu.equals("exit")) {
                break;
            }else if (inpu.startsWith("echo ")) {
                System.out.println(inpu.substring(5));
            } else {
                System.out.println(inpu + ": command not found");
            }
            System.out.println(inpu + ": command not found");
        }
    }
}
