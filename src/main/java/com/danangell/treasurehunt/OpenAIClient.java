package com.danangell.treasurehunt;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OpenAIClient {
    private static final String OPENAI_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static final String API_BASE = "https://api.openai.com/v1";
    private static final String MODEL_NAME = "gpt-3.5-turbo";

    public static String completion(String prompt) throws IOException {
        URL url = new URL(API_BASE + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + OPENAI_KEY);
        conn.setDoOutput(true);

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", "You are a dungeon master narrator"));
        messages.add(new Message("user", prompt));

        String jsonInputString = new Gson().toJson(new Request(MODEL_NAME, messages));
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(jsonInputString);
        }

        String response = new String(conn.getInputStream().readAllBytes());
        Response jsonResponse = new Gson().fromJson(response, Response.class);
        return jsonResponse.choices.get(0).message.content;
    }

    private static class Request {
        public String model;
        public List<Message> messages;

        public Request(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    private static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class Response {
        public List<Choice> choices;

        private static class Choice {
            Message message;
        }
    }
}