package com.danangell.treasurehunt;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.map.MinecraftFont;

import net.kyori.adventure.text.Component;

public class PageBuilder {
    /**
     * Break a string into pages of 14 lines each.
     */
    public static List<Component> breakIntoPages(String bookContents) {
        List<String> lines = getLines(bookContents);

        List<Component> pages = new ArrayList<>();

        // Turn lines into groups of 14 lines
        for (int index = 0; index < lines.size(); index += 14) {
            List<String> pageLines = lines.subList(index, Math.min(index + 14, lines.size()));
            pages.add(Component.text(String.join("\n", pageLines)));
        }

        return pages;
    }

    private static List<String> getLines(String rawText) {
        // Note that the only flaw with using MinecraftFont is that it can't account for
        // some UTF-8 symbols, it will throw an IllegalArgumentException
        final MinecraftFont font = new MinecraftFont();
        final int maxLineWidth = font.getWidth("LLLLLLLLLLLLLLLLLLL");

        // Get all of our lines
        List<String> lines = new ArrayList<>();
        try {
            String[] words = rawText.split(" ");
            String line = "";

            for (int index = 0; index < words.length; index++) {
                String word = words[index];
                String test = (line + " " + word);
                if (test.startsWith(" ")) {
                    test = test.substring(1);
                }

                // Current line + word is too long to be one line
                if (font.getWidth(test) > maxLineWidth) {
                    lines.add(line);
                    line = word;
                    continue;
                }

                // Add the current word to our current line
                line = test;
            }

            if (!line.equals("")) {
                lines.add(line);
            }
        } catch (IllegalArgumentException ex) {
            lines.clear();
        }

        return lines;
    }
}
