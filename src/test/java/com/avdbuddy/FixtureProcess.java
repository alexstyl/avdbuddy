package com.avdbuddy;

import java.nio.file.Files;
import java.nio.file.Path;

/** A real, headless child process; never opens windows or starts an emulator. */
public final class FixtureProcess {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "copy" -> Files.copy(Path.of(args[1]), System.out);
            case "fail" -> {
                System.out.print("[===] 55% Unzipping...\rWarning: An error occurred: No space left on device.\n");
                System.exit(1);
            }
            case "echo" -> {
                for (int i = 1; i < args.length; i++) System.out.println(args[i]);
                System.out.println(System.getenv("AVDBUDDY_TEST_VALUE"));
                System.out.print(new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            case "detach" -> {
                System.in.readAllBytes(); // Detached launch must close stdin portably.
                Files.writeString(Path.of(args[1]), "finished");
            }
            case "wait" -> Thread.sleep(30000);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
