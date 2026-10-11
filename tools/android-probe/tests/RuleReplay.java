package io.adpass.probe;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RuleReplay {
    public static void main(String[] args) throws Exception {
        List<PageRules.Cue> cues = new ArrayList<>();
        String name = "", expected = "";
        int passed = 0;
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", -1);
            if (fields[0].equals("CASE")) { name = fields[1]; expected = fields[2]; cues.clear(); }
            else if (fields[0].equals("CUE")) {
                cues.add(new PageRules.Cue(decode(fields[1]), decode(fields[2])));
            } else if (fields[0].equals("END")) {
                String actual = PageRules.detect(cues);
                if (!actual.equals(expected)) throw new AssertionError(name + ": " + actual + " != " + expected);
                passed++;
            }
        }
        System.out.println("PASS: " + passed + " recorded page fixtures");
    }
    private static String decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}
